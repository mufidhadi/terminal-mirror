use portable_pty::{native_pty_system, CommandBuilder, PtySize};
use std::io::Read;
use tracing::info;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt::init();
    info!("Starting macOS Terminal Mirror Host Agent...");

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
    let mut _writer = pair.master.take_writer()?;

    // Read PTY output in background task
    tokio::task::spawn_blocking(move || {
        let mut buf = [0u8; 1024];
        while let Ok(n) = reader.read(&mut buf) {
            if n == 0 {
                break;
            }
            // In full implementation, forward buffer to relay client
        }
    });

    Ok(())
}
