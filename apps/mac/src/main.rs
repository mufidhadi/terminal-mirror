mod config;
mod network;
mod pty;
mod stream;
mod ui;

use clap::Parser;
use config::MacAgentConfig;
use network::RelayHostClient;
use pty::DarwinPtySession;
use std::io::{Read, Write};
use stream::StreamCoalescer;
use terminal_mirror_protocol::{DicewarePassphrase, PairingPayload, Utf8StreamChunker};
use tokio::sync::mpsc;
use tracing::info;
use ui::render_startup_banner;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    dotenvy::dotenv().ok();
    tracing_subscriber::fmt::init();
    let config = MacAgentConfig::parse();

    let resolved_shell = config.resolved_shell();
    let session_id = format!("{}-{}", config.host_id, &uuid::Uuid::new_v4().to_string()[..8]);

    // Generate high-entropy 4-word Diceware passphrase
    let passphrase_words = DicewarePassphrase::generate(4);
    let formatted_passphrase = DicewarePassphrase::format(&passphrase_words);

    let pairing_payload = PairingPayload {
        relay_url: config.relay_url.clone(),
        session_id: session_id.clone(),
        host_id: config.host_id.clone(),
        pre_shared_key: config.auth_token.clone(),
        public_key: "".to_string(),
        pin_code: None,
        passphrase_words: Some(passphrase_words),
        expires_at_ms: std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64
            + 600_000, // 10 minutes expiry
    };

    let qr_json = pairing_payload.to_qr_string().ok();

    // Display ambient macOS developer banner with embedded QR code
    render_startup_banner(&session_id, &formatted_passphrase, &resolved_shell, qr_json.as_deref());
    info!("Starting macOS Darwin PTY session with shell: {}", resolved_shell);

    // Spawn Darwin login shell (/bin/zsh -l)
    let pty_session = DarwinPtySession::spawn(&resolved_shell, 80, 24)?;
    let mut reader = pty_session.clone_reader()?;
    let mut writer = pty_session.take_writer()?;

    // Channels for downstream (PTY output -> Relay) and upstream (Mobile input -> PTY)
    let (downstream_raw_tx, mut downstream_raw_rx) = mpsc::channel::<Vec<u8>>(1024);
    let (downstream_filtered_tx, downstream_filtered_rx) = mpsc::channel::<Vec<u8>>(1024);
    let (upstream_tx, mut upstream_rx) = mpsc::channel::<Vec<u8>>(256);

    // 1. Dedicated blocking PTY Reader thread with UTF-8 boundary slicing guard
    tokio::task::spawn_blocking(move || {
        let mut chunker = Utf8StreamChunker::new();
        let mut buf = [0u8; 4096];

        while let Ok(n) = reader.read(&mut buf) {
            if n == 0 {
                info!("Darwin PTY shell process terminated (EOF)");
                break;
            }
            let valid_utf8 = chunker.process_chunk(&buf[..n]);
            if !valid_utf8.is_empty() {
                let _ = downstream_raw_tx.blocking_send(valid_utf8);
            }
        }
    });

    // 2. Dedicated blocking PTY Writer thread for remote mobile keystrokes
    tokio::task::spawn_blocking(move || {
        while let Some(input_bytes) = upstream_rx.blocking_recv() {
            if writer.write_all(&input_bytes).is_err() || writer.flush().is_err() {
                break;
            }
        }
    });

    // 3. Adaptive Coalescer task (backpressure protection)
    tokio::spawn(async move {
        let mut coalescer = StreamCoalescer::new(256 * 1024); // 256 KB threshold

        while let Some(chunk) = downstream_raw_rx.recv().await {
            if coalescer.should_coalesce(chunk.len()) {
                // Downstream backpressure detected; drop intermediate delta
                continue;
            }
            if downstream_filtered_tx.send(chunk).await.is_err() {
                break;
            }
        }
    });

    // 4. Relay WebSocket Network Client (auto-reconnects with exponential backoff)
    let client = RelayHostClient::new(
        config.relay_url.clone(),
        config.auth_token.clone(),
        session_id.clone(),
    );

    client.run(downstream_filtered_rx, upstream_tx).await;

    Ok(())
}
