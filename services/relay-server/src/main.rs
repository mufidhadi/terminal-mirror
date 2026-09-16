mod config;
mod hub;
mod metrics;
mod middleware;
mod ws;

use axum::{extract::State, routing::get, Router};
use clap::Parser;
use config::RelayServerConfig;
use hub::SessionHub;
use metrics::RelayMetrics;
use middleware::IpRateLimiter;
use std::net::SocketAddr;
use std::time::Duration;
use tracing::info;
use ws::{ws_handler, AppState};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    dotenvy::dotenv().ok();
    tracing_subscriber::fmt::init();

    let config = RelayServerConfig::parse();
    info!("Starting Terminal Mirror Relay Hub (Zero-Knowledge Engine)...");
    info!("Relay server binding to: {}", config.bind_addr);
    info!(
        "Rate limit: {} handshakes/min, Max payload: {} bytes",
        config.max_connections_per_min, config.max_payload_bytes
    );

    let state = AppState {
        rate_limiter: IpRateLimiter::new(config.max_connections_per_min),
        hub: SessionHub::new(),
        metrics: RelayMetrics::new(),
        config: config.clone(),
    };

    // Stale session garbage collector background task (runs every 60s)
    let reaper_hub = state.hub.clone();
    let stale_timeout = Duration::from_secs(config.stale_session_timeout_secs);
    tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(60));
        loop {
            interval.tick().await;
            let count = reaper_hub.reap_stale(stale_timeout);
            if count > 0 {
                info!("Garbage collector reaped {} inactive sessions", count);
            }
        }
    });

    let app = Router::new()
        .route("/healthz", get(health_check))
        .route("/health", get(health_check))
        .route("/metrics", get(metrics_handler))
        .route("/ws", get(ws_handler))
        .with_state(state);

    let listener = tokio::net::TcpListener::bind(&config.bind_addr).await?;
    info!("Relay server actively listening on http://{}", config.bind_addr);

    axum::serve(
        listener,
        app.into_make_service_with_connect_info::<SocketAddr>(),
    )
    .await?;

    Ok(())
}

async fn health_check() -> &'static str {
    "OK"
}

async fn metrics_handler(State(state): State<AppState>) -> String {
    let active_sessions = state.hub.active_sessions_count();
    let active_subscribers = state.hub.total_subscribers_count();
    state.metrics.render_prometheus(active_sessions, active_subscribers)
}
