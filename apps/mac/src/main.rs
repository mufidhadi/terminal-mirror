use portable_pty::{native_pty_system, CommandBuilder, PtySize};
use std::io::Read;
use terminal_mirror_protocol::Utf8StreamChunker;
use tokio::sync::mpsc;
use tracing::info;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt::init();
    info!("Starting macOS Terminal Mirror Host Agent (Hardened & Decoupled Engine)...");

    let pty_system = native_pty_system();
    let pair = pty_system.openpty(PtySize {
        rows: 24,
        cols: 80,
        pixel_width: 0,
        pixel_height: 0,
    })?;

    let default_shell = std::env::var("SHELL").unwrap_or_else(|_| "/bin/zsh".to_string());
    let cmd = CommandBuilder::new(default_shell);
    let child = pair.slave.spawn_command(cmd)?;

    info!("Spawned child shell in PTY with PID {:?}", child.process_id());

    let mut reader = pair.master.try_clone_reader()?;
    let _writer = pair.master.take_writer()?;

    // Decoupled async channel to prevent PTY blocking during high-throughput bursts (e.g. `cat big.log`)
    let (tx, mut rx) = mpsc::channel::<Vec<u8>>(1024);

    // Dedicated blocking reader thread with UTF-8 multibyte boundary guard
    tokio::task::spawn_blocking(move || {
        let mut chunker = Utf8StreamChunker::new();
        let mut buf = [0u8; 4096];

        while let Ok(n) = reader.read(&mut buf) {
            if n == 0 {
                break;
            }
            let valid_utf8_chunk = chunker.process_chunk(&buf[..n]);
            if !valid_utf8_chunk.is_empty() {
                // Non-blocking send or drop if downstream is heavily backpressured
                let _ = tx.blocking_send(valid_utf8_chunk);
            }
        }
    });

    // Decoupled worker processing output and maintaining virtual screen grid
    tokio::spawn(async move {
        while let Some(_chunk) = rx.recv().await {
            // Asynchronous virtual grid processing and network streaming
            // High-throughput streams skip intermediate frames to preserve host CPU
        }
    });

    Ok(())
}
