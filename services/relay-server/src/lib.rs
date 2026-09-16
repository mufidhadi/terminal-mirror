pub mod config;
pub mod hub;
pub mod metrics;
pub mod middleware;
pub mod ws;

use axum::{extract::State, routing::get, Router};
use std::time::Duration;
use tower_http::cors::CorsLayer;
use tracing::info;
use ws::{ws_handler, AppState};

/// Builds the Axum router with all endpoints, CORS, and state configured.
pub fn create_app(state: AppState) -> Router {
    Router::new()
        .route("/healthz", get(health_check))
        .route("/health", get(health_check))
        .route("/metrics", get(metrics_handler))
        .route("/ws", get(ws_handler))
        .layer(CorsLayer::permissive())
        .with_state(state)
}

pub async fn health_check() -> &'static str {
    "OK"
}

pub async fn metrics_handler(State(state): State<AppState>) -> String {
    let active_sessions = state.hub.active_sessions_count();
    let active_subscribers = state.hub.total_subscribers_count();
    state.metrics.render_prometheus(active_sessions, active_subscribers)
}

/// Spawns the background stale session garbage collector task.
pub fn spawn_stale_session_reaper(hub: hub::SessionHub, timeout: Duration) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(60));
        loop {
            interval.tick().await;
            let count = hub.reap_stale(timeout);
            if count > 0 {
                info!("Garbage collector reaped {} inactive sessions", count);
            }
        }
    })
}

/// Listens for OS shutdown signals (SIGINT/Ctrl+C or Docker SIGTERM) for graceful termination.
pub async fn shutdown_signal() {
    let ctrl_c = async {
        tokio::signal::ctrl_c()
            .await
            .expect("failed to install Ctrl+C signal handler");
    };

    #[cfg(unix)]
    let terminate = async {
        tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("failed to install SIGTERM signal handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => info!("Received SIGINT (Ctrl+C), initiating graceful shutdown..."),
        _ = terminate => info!("Received SIGTERM from Docker orchestrator, initiating graceful shutdown..."),
    }
}
