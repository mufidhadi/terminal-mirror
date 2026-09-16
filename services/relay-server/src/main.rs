use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        State,
    },
    response::IntoResponse,
    routing::get,
    Router,
};
use dashmap::DashMap;
use std::{net::SocketAddr, sync::Arc};
use tokio::sync::broadcast;
use tracing::{error, info};

type SessionHub = Arc<DashMap<String, broadcast::Sender<Vec<u8>>>>;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt::init();

    let bind_addr = std::env::var("RELAY_BIND_ADDR").unwrap_or_else(|_| "0.0.0.0:8080".to_string());
    let hub: SessionHub = Arc::new(DashMap::new());

    let app = Router::new()
        .route("/health", get(health_check))
        .route("/ws", get(ws_handler))
        .with_state(hub);

    let listener = tokio::net::TcpListener::bind(&bind_addr).await?;
    info!("Relay server listening on {}", bind_addr);

    axum::serve(listener, app.into_make_service_with_connect_info::<SocketAddr>()).await?;
    Ok(())
}

async fn health_check() -> &'static str {
    "OK"
}

async fn ws_handler(
    ws: WebSocketUpgrade,
    State(hub): State<SessionHub>,
) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_socket(socket, hub))
}

async fn handle_socket(mut socket: WebSocket, _hub: SessionHub) {
    info!("New WebSocket connection established");
    while let Some(msg) = socket.recv().await {
        match msg {
            Ok(Message::Binary(bytes)) => {
                // Forward or process binary message
                if let Err(e) = socket.send(Message::Binary(bytes)).await {
                    error!("Error sending echo: {}", e);
                    break;
                }
            }
            Ok(Message::Close(_)) => break,
            Err(e) => {
                error!("WebSocket error: {}", e);
                break;
            }
            _ => {}
        }
    }
}
