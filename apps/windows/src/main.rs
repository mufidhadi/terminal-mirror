mod config;
mod conpty;
mod stream;
mod ui;

use clap::Parser;
use config::WindowsAgentConfig;
use conpty::{resolve_windows_shell, ConPtySession};
use std::io::Read;
use std::sync::Arc;
use std::time::Duration;
use stream::{ResizeDebouncer, StreamCoalescer};
use terminal_mirror_protocol::{DicewarePassphrase, Utf8StreamChunker};
use tokio::sync::mpsc;
use tracing::info;
use ui::render_startup_banner;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    dotenvy::dotenv().ok();
    tracing_subscriber::fmt::init();
    let config = WindowsAgentConfig::parse();

    let resolved = resolve_windows_shell(config.shell.as_deref());
    let session_id = format!(
        "{}-{}",
        config.host_id,
        &uuid::Uuid::new_v4().to_string()[..8]
    );
    // Passphrase is never hardcoded: explicit --passphrase/PASSPHRASE wins,
    // otherwise a fresh ephemeral 4-word Diceware passphrase is generated.
    let formatted_passphrase = if let Some(custom) = &config.passphrase {
        custom.clone()
    } else {
        DicewarePassphrase::format(&DicewarePassphrase::generate(4))
    };
    let sample_passphrase = formatted_passphrase.as_str();

    if config.tray {
        info!("Running Windows Agent in System Tray mode");
    }

    // Render Windows Terminal status header
    render_startup_banner(&session_id, sample_passphrase, &resolved.executable);
    info!(
        "Initializing Windows ConPTY host with shell: {} {:?}",
        resolved.executable, resolved.arguments
    );

    // Spawn native ConPTY session
    let pty_session = Arc::new(ConPtySession::spawn(
        &resolved.executable,
        &resolved.arguments,
        80,
        24,
    )?);
    let mut reader = pty_session.clone_reader()?;
    let _writer = pty_session.take_writer()?;

    // Initialize 200ms ConPTY Resize Debouncer
    let pty_for_resize = Arc::clone(&pty_session);
    let debouncer = ResizeDebouncer::new(
        Duration::from_millis(config.resize_debounce_ms),
        move |cols, rows| {
            info!(
                "Debounced ConPTY resize executing: cols={}, rows={}",
                cols, rows
            );
            if let Err(e) = pty_for_resize.resize(cols, rows) {
                tracing::warn!("Failed to resize ConPTY session: {}", e);
            }
        },
    );

    // Decoupled bounded stream channel (1024 frames)
    let (tx, mut rx) = mpsc::channel::<Vec<u8>>(1024);

    // Dedicated blocking reader thread with UTF-8 boundary slicing guard
    tokio::task::spawn_blocking(move || {
        let mut chunker = Utf8StreamChunker::new();
        let mut buf = [0u8; 4096];

        while let Ok(n) = reader.read(&mut buf) {
            if n == 0 {
                info!("ConPTY shell process terminated (EOF)");
                break;
            }
            let valid_utf8 = chunker.process_chunk(&buf[..n]);
            if !valid_utf8.is_empty() {
                let _ = tx.blocking_send(valid_utf8);
            }
        }
    });

    // Decoupled streaming worker with adaptive coalescer
    tokio::spawn(async move {
        let mut coalescer = StreamCoalescer::new(256 * 1024); // 256 KB backpressure threshold

        while let Some(chunk) = rx.recv().await {
            if coalescer.should_coalesce(chunk.len()) {
                // Downstream backpressure detected; drop intermediate delta
                // and wait for full ScreenStateSync trigger
                continue;
            }
            // Stream chunk downstream to relay hub
        }
    });

    // Simulated initial resize request to demonstrate debouncer integration
    let _ = debouncer.request_resize(80, 24).await;

    Ok(())
}
