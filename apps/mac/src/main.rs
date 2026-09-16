mod config;
mod pty;
mod stream;
mod ui;

use clap::Parser;
use config::MacAgentConfig;
use pty::DarwinPtySession;
use std::io::Read;
use stream::StreamCoalescer;
use terminal_mirror_protocol::Utf8StreamChunker;
use tokio::sync::mpsc;
use tracing::info;
use ui::render_startup_banner;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt::init();
    let config = MacAgentConfig::parse();

    let resolved_shell = config.resolved_shell();
    let session_id = format!("{}-{}", config.host_id, uuid::Uuid::new_v4().to_string()[..8].to_string());
    let sample_passphrase = "kuda-terbang-batu-merah";

    // Display ambient macOS developer banner
    render_startup_banner(&session_id, sample_passphrase, &resolved_shell);
    info!("Starting macOS Darwin PTY session with shell: {}", resolved_shell);

    // Spawn Darwin login shell (/bin/zsh -l)
    let pty_session = DarwinPtySession::spawn(&resolved_shell, 80, 24)?;
    let mut reader = pty_session.clone_reader()?;
    let _writer = pty_session.take_writer()?;

    // Decoupled bounded stream channel (1024 capacity)
    let (tx, mut rx) = mpsc::channel::<Vec<u8>>(1024);

    // Dedicated blocking reader thread with UTF-8 boundary slicing guard
    tokio::task::spawn_blocking(move || {
        let mut chunker = Utf8StreamChunker::new();
        let mut buf = [0u8; 4096];

        while let Ok(n) = reader.read(&mut buf) {
            if n == 0 {
                break; // Shell terminated (EOF)
            }
            let valid_utf8 = chunker.process_chunk(&buf[..n]);
            if !valid_utf8.is_empty() {
                let _ = tx.blocking_send(valid_utf8);
            }
        }
    });

    // Decoupled worker with adaptive coalescer
    tokio::spawn(async move {
        let mut coalescer = StreamCoalescer::new(256 * 1024); // 256 KB threshold

        while let Some(chunk) = rx.recv().await {
            if coalescer.should_coalesce(chunk.len()) {
                // Downstream backpressure detected; drop intermediate deltas
                // and wait for full ScreenStateSync trigger
                continue;
            }
            // Stream chunk downstream to relay
        }
    });

    Ok(())
}
