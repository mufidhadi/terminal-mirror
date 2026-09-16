use portable_pty::{native_pty_system, CommandBuilder, PtySize};
use std::io::Read;
use tracing::info;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt::init();
    info!("Starting Windows Terminal Mirror Host Agent (ConPTY)...");

    // portable-pty automatically invokes Windows ConPTY API on Windows
    let pty_system = native_pty_system();
    let pair = pty_system.openpty(PtySize {
        rows: 24,
        cols: 80,
        pixel_width: 0,
        pixel_height: 0,
    })?;

    // Default to PowerShell or cmd.exe on Windows
    let default_shell = std::env::var("COMSPEC").unwrap_or_else(|_| "powershell.exe".to_string());
    let cmd = CommandBuilder::new(default_shell);
    let child = pair.slave.spawn_command(cmd)?;

    info!("Spawned ConPTY shell with PID {:?}", child.process_id());

    let mut reader = pair.master.try_clone_reader()?;
    let mut _writer = pair.master.take_writer()?;

    tokio::task::spawn_blocking(move || {
        let mut buf = [0u8; 1024];
        while let Ok(n) = reader.read(&mut buf) {
            if n == 0 {
                break;
            }
            // Stream bytes to relay
        }
    });

    Ok(())
}
