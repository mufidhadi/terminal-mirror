use crate::config::RelayServerConfig;
use crate::hub::SessionHub;
use crate::metrics::RelayMetrics;
use crate::middleware::IpRateLimiter;
use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        ConnectInfo, Query, State,
    },
    http::StatusCode,
    response::IntoResponse,
};
use futures_util::{sink::SinkExt, stream::StreamExt};
use serde::Deserialize;
use std::net::SocketAddr;
use std::sync::atomic::Ordering;
use tokio::sync::broadcast::error::RecvError;
use tokio::sync::mpsc;
use tracing::{error, info, warn};

#[derive(Clone)]
pub struct AppState {
    pub config: RelayServerConfig,
    pub hub: SessionHub,
    pub rate_limiter: IpRateLimiter,
    pub metrics: RelayMetrics,
}

#[derive(Debug, Deserialize)]
pub struct WsQuery {
    pub token: Option<String>,
    pub session_id: Option<String>,
    pub role: Option<String>,
    pub host_name: Option<String>,
    pub shell: Option<String>,
}

pub async fn ws_handler(
    ws: WebSocketUpgrade,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    Query(query): Query<WsQuery>,
    State(state): State<AppState>,
) -> Result<impl IntoResponse, StatusCode> {
    // 1. IP Rate Limiting Guard
    if !state.rate_limiter.check_allowed(addr.ip()) {
        state.metrics.inc_rate_limited();
        warn!(
            "Rate limit exceeded for IP {}. Rejecting handshake.",
            addr.ip()
        );
        return Err(StatusCode::TOO_MANY_REQUESTS);
    }

    // 2. Auth Token Verification
    let provided_token = query.token.as_deref().unwrap_or_default();
    if provided_token != state.config.auth_token {
        warn!(
            "Unauthorized WebSocket attempt from {}: invalid auth token",
            addr
        );
        return Err(StatusCode::UNAUTHORIZED);
    }

    // 3. Session ID Validation
    let session_id = match query.session_id {
        Some(id) if !id.trim().is_empty() => id.trim().to_string(),
        _ => {
            warn!("Missing or empty session_id from {}", addr);
            return Err(StatusCode::BAD_REQUEST);
        }
    };

    let role = query
        .role
        .unwrap_or_else(|| "client".to_string())
        .to_lowercase();

    let host_name = query.host_name.clone();
    let shell = query.shell.clone();

    info!(
        "Upgrading WebSocket connection for session='{}' role='{}' from={}",
        session_id, role, addr
    );

    Ok(ws.on_upgrade(move |socket| {
        handle_socket(socket, addr, session_id, role, host_name, shell, state)
    }))
}

async fn handle_socket(
    socket: WebSocket,
    addr: SocketAddr,
    session_id: String,
    role: String,
    host_name: Option<String>,
    shell: Option<String>,
    state: AppState,
) {
    let (mut ws_sink, mut ws_stream) = socket.split();
    let router = state.hub.get_or_create(&session_id);

    if role == "host" {
        info!(
            "Host Agent connected for session '{}' from {}",
            session_id, addr
        );
        let conn_id = uuid::Uuid::new_v4();
        let (upstream_tx, mut upstream_rx) = mpsc::channel::<Vec<u8>>(256);
        state.hub.register_host(
            &session_id,
            conn_id,
            upstream_tx,
            host_name.clone(),
            shell.clone(),
        );

        // Broadcast host presence: online = true
        let presence_pkt = terminal_mirror_protocol::Packet::host_presence(
            session_id.clone(),
            true,
            host_name.clone(),
            shell.clone(),
        );
        if let Ok(bytes) = presence_pkt.to_msgpack() {
            let _ = router.broadcast_tx.send(bytes);
        }

        let s_id = session_id.clone();
        // Task A: Forward upstream client keystrokes down to Host
        let mut send_to_host = tokio::spawn(async move {
            while let Some(bytes) = upstream_rx.recv().await {
                if ws_sink.send(Message::Binary(bytes)).await.is_err() {
                    break;
                }
            }
        });

        // Task B: Receive host terminal output and broadcast downstream to subscribers
        let router_broadcast = router.clone();
        let metrics = state.metrics.clone();
        let max_payload = state.config.max_payload_bytes;

        let mut recv_from_host = tokio::spawn(async move {
            while let Some(msg) = ws_stream.next().await {
                match msg {
                    Ok(Message::Binary(bytes)) => {
                        if bytes.len() > max_payload {
                            warn!("Payload exceeded {} bytes cap, dropping frame", max_payload);
                            continue;
                        }
                        metrics.inc_frames_routed(bytes.len());
                        router_broadcast.touch();
                        let _ = router_broadcast.broadcast_tx.send(bytes);
                    }
                    Ok(Message::Ping(_)) | Ok(Message::Pong(_)) => {
                        router_broadcast.touch();
                    }
                    Ok(Message::Close(_)) => break,
                    Err(e) => {
                        error!("WebSocket error on host stream {}: {}", addr, e);
                        break;
                    }
                    _ => {}
                }
            }
        });

        tokio::select! {
            _ = &mut send_to_host => recv_from_host.abort(),
            _ = &mut recv_from_host => send_to_host.abort(),
        }

        let was_active = state.hub.unregister_host(&s_id, conn_id);
        if was_active {
            let offline_pkt =
                terminal_mirror_protocol::Packet::host_presence(s_id.clone(), false, None, None);
            if let Ok(bytes) = offline_pkt.to_msgpack() {
                let _ = router.broadcast_tx.send(bytes);
            }
        }

        info!(
            "Host Agent disconnected for session '{}' from {}",
            s_id, addr
        );
    } else {
        // Subscriber client (Android / Mobile viewer)
        info!(
            "Subscriber connected to session '{}' from {}",
            session_id, addr
        );
        router.subscribers_count.fetch_add(1, Ordering::SeqCst);

        // 1. Subscribe to broadcast channel FIRST (prevents join race condition)
        let mut broadcast_rx = router.broadcast_tx.subscribe();

        // 2. Query current host presence and send initial packet directly to new subscriber
        let (is_online, current_host, current_shell) = state.hub.get_host_presence(&session_id);
        let initial_pkt = terminal_mirror_protocol::Packet::host_presence(
            session_id.clone(),
            is_online,
            current_host,
            current_shell,
        );
        if let Ok(bytes) = initial_pkt.to_msgpack() {
            let _ = ws_sink.send(Message::Binary(bytes)).await;
        }

        let metrics = state.metrics.clone();

        // Task A: Receive broadcast terminal frames and push to subscriber
        let mut send_to_subscriber = tokio::spawn(async move {
            loop {
                match broadcast_rx.recv().await {
                    Ok(bytes) => {
                        if ws_sink.send(Message::Binary(bytes)).await.is_err() {
                            break;
                        }
                    }
                    Err(RecvError::Lagged(skipped)) => {
                        metrics.inc_dropped_frames(skipped);
                        warn!("Subscriber lagged, dropped {} buffer frames", skipped);
                    }
                    Err(RecvError::Closed) => break,
                }
            }
        });

        // Task B: Receive keystrokes/commands from subscriber and forward upstream to host
        let router_upstream = router.clone();
        let mut recv_from_subscriber = tokio::spawn(async move {
            while let Some(msg) = ws_stream.next().await {
                match msg {
                    Ok(Message::Binary(bytes)) => {
                        router_upstream.touch();
                        let maybe_host = router_upstream
                            .host_tx
                            .lock()
                            .ok()
                            .and_then(|h| h.as_ref().map(|(_, tx)| tx.clone()));
                        if let Some(host_tx) = maybe_host {
                            let _ = host_tx.send(bytes).await;
                        }
                    }
                    Ok(Message::Close(_)) => break,
                    Err(_) => break,
                    _ => {}
                }
            }
        });

        tokio::select! {
            _ = &mut send_to_subscriber => recv_from_subscriber.abort(),
            _ = &mut recv_from_subscriber => send_to_subscriber.abort(),
        }

        router.subscribers_count.fetch_sub(1, Ordering::SeqCst);
        info!("Subscriber disconnected from session '{}'", session_id);
    }
}
