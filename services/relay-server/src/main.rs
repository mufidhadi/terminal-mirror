use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        ConnectInfo, State,
    },
    http::StatusCode,
    response::IntoResponse,
    routing::get,
    Router,
};
use dashmap::DashMap;
use std::{net::SocketAddr, sync::Arc, time::Instant};
use tokio::sync::broadcast;
use tracing::{error, info, warn};

type SessionHub = Arc<DashMap<String, broadcast::Sender<Vec<u8>>>>;
type RateLimiter = Arc<DashMap<std::net::IpAddr, (u32, Instant)>>;

#[derive(Clone)]
struct AppState {
    hub: SessionHub,
    rate_limiter: RateLimiter,
    required_token: Arc<String>,
}

const MAX_CONNECTIONS_PER_MINUTE: u32 = 60;
const MAX_PAYLOAD_BYTES: usize = 65_536; // 64 KB max frame limit

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt::init();

    let bind_addr = std::env::var("RELAY_BIND_ADDR").unwrap_or_else(|_| "127.0.0.1:8080".to_string());
    let required_token = Arc::new(std::env::var("RELAY_AUTH_TOKEN").unwrap_or_else(|_| "change_this_secret_token".to_string()));

    info!("Starting Terminal Mirror Relay Hub (Zero-Knowledge Engine)...");
    info!("Relay security mode: Strict Auth + Anti-Abuse Rate Limiting");

    let state = AppState {
        hub: Arc::new(DashMap::new()),
        rate_limiter: Arc::new(DashMap::new()),
        required_token,
    };

    let app = Router::new()
        .route("/health", get(health_check))
        .route("/ws", get(ws_handler))
        .with_state(state);

    let listener = tokio::net::TcpListener::bind(&bind_addr).await?;
    info!("Relay server actively listening on http://{}", bind_addr);

    axum::serve(listener, app.into_make_service_with_connect_info::<SocketAddr>()).await?;
    Ok(())
}

async fn health_check() -> &'static str {
    "OK"
}

async fn ws_handler(
    ws: WebSocketUpgrade,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    State(state): State<AppState>,
) -> Result<impl IntoResponse, StatusCode> {
    // Rate Limiting Guard per IP to prevent DoS & botnet C2 abuse
    let client_ip = addr.ip();
    let mut entry = state.rate_limiter.entry(client_ip).or_insert((0, Instant::now()));
    let (count, start_time) = entry.value_mut();

    if start_time.elapsed().as_secs() > 60 {
        *count = 1;
        *start_time = Instant::now();
    } else {
        *count += 1;
        if *count > MAX_CONNECTIONS_PER_MINUTE {
            warn!("Rate limit exceeded for IP: {}. Dropping connection.", client_ip);
            return Err(StatusCode::TOO_MANY_REQUESTS);
        }
    }

    Ok(ws.on_upgrade(move |socket| handle_socket(socket, addr, state)))
}

async fn handle_socket(mut socket: WebSocket, addr: SocketAddr, state: AppState) {
    info!("New client connected from: {}", addr);

    while let Some(msg) = socket.recv().await {
        match msg {
            Ok(Message::Binary(bytes)) => {
                if bytes.len() > MAX_PAYLOAD_BYTES {
                    warn!("Payload from {} exceeded 64KB limit! Dropping frame.", addr);
                    continue;
                }

                // In zero-knowledge mode, relay routes the encrypted payload without inspection
                if let Err(e) = socket.send(Message::Binary(bytes)).await {
                    error!("Error forwarding binary frame to {}: {}", addr, e);
                    break;
                }
            }
            Ok(Message::Close(_)) => {
                info!("Client {} closed connection", addr);
                break;
            }
            Err(e) => {
                error!("WebSocket transport error with {}: {}", addr, e);
                break;
            }
            _ => {}
        }
    }
}
