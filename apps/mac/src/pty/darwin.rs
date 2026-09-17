use portable_pty::{native_pty_system, CommandBuilder, PtyPair, PtySize};
use std::io::{self, Read, Write};
use tracing::info;

#[allow(dead_code)]
pub struct DarwinPtySession {
    pub pair: PtyPair,
    pub shell_path: String,
}

impl DarwinPtySession {
    /// Spawns a Darwin login shell inside a native PTY master/slave pair
    pub fn spawn(shell: &str, cols: u16, rows: u16) -> io::Result<Self> {
        let pty_system = native_pty_system();
        let pair = pty_system
            .openpty(PtySize {
                rows,
                cols,
                pixel_width: 0,
                pixel_height: 0,
            })
            .map_err(io::Error::other)?;

        // Explicitly spawn as login shell (-l) to source .zprofile, .zshrc, and PATH
        let mut cmd = CommandBuilder::new(shell);
        cmd.args(["-l"]);
        cmd.env("TERM", "xterm-256color");
        cmd.env("COLORTERM", "truecolor");
        cmd.env("TERMINAL_MIRROR_ACTIVE", "1");

        let child = pair.slave.spawn_command(cmd).map_err(io::Error::other)?;

        info!(
            "Spawned macOS login shell ({}) in PTY with PID {:?}",
            shell,
            child.process_id()
        );

        Ok(Self {
            pair,
            shell_path: shell.to_string(),
        })
    }

    pub fn clone_reader(&self) -> io::Result<Box<dyn Read + Send>> {
        self.pair
            .master
            .try_clone_reader()
            .map_err(io::Error::other)
    }

    pub fn take_writer(&self) -> io::Result<Box<dyn Write + Send>> {
        self.pair.master.take_writer().map_err(io::Error::other)
    }

    #[allow(dead_code)]
    pub fn resize(&self, cols: u16, rows: u16) -> io::Result<()> {
        self.pair
            .master
            .resize(PtySize {
                rows,
                cols,
                pixel_width: 0,
                pixel_height: 0,
            })
            .map_err(io::Error::other)
    }
}
