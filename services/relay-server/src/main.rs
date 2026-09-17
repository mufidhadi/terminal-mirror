use clap::Parser;
use std::net::SocketAddr;
use std::time::Duration;
use terminal_mirror_relay::config::RelayServerConfig;
use terminal_mirror_relay::hub::SessionHub;
use terminal_mirror_relay::metrics::RelayMetrics;
use terminal_mirror_relay::middleware::IpRateLimiter;
use terminal_mirror_relay::ws::AppState;
use terminal_mirror_relay::{create_app, shutdown_signal, spawn_stale_session_reaper};
use tracing::info;

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

    // Spawn stale session reaper background worker
    spawn_stale_session_reaper(
        state.hub.clone(),
        Duration::from_secs(config.stale_session_timeout_secs),
    );

    let app = create_app(state);
    let listener = tokio::net::TcpListener::bind(&config.bind_addr).await?;
    info!(
        "Relay server actively listening on http://{}",
        config.bind_addr
    );

    axum::serve(
        listener,
        app.into_make_service_with_connect_info::<SocketAddr>(),
    )
    .with_graceful_shutdown(shutdown_signal())
    .await?;

    info!("Relay server shut down cleanly.");
    Ok(())
}
