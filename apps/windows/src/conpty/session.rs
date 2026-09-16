use portable_pty::{native_pty_system, Child, CommandBuilder, PtySize};
use std::io::{self, Read, Write};
use std::sync::{Arc, Mutex};
use tracing::info;

pub struct ConPtySession {
    master: Arc<Mutex<Box<dyn portable_pty::MasterPty + Send>>>,
    #[allow(dead_code)]
    child: Arc<Mutex<Box<dyn Child + Send + Sync>>>,
}

impl ConPtySession {
    /// Spawns a ConPTY session with the resolved shell and arguments.
    pub fn spawn(
        executable: &str,
        args: &[String],
        cols: u16,
        rows: u16,
    ) -> io::Result<Self> {
        let pty_system = native_pty_system();
        let pair = pty_system
            .openpty(PtySize {
                rows,
                cols,
                pixel_width: 0,
                pixel_height: 0,
            })
            .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))?;

        let mut cmd = CommandBuilder::new(executable);
        for arg in args {
            cmd.arg(arg);
        }

        // Set UTF-8 environment hints for child processes
        cmd.env("LANG", "en_US.UTF-8");
        cmd.env("LC_ALL", "en_US.UTF-8");

        let child = pair
            .slave
            .spawn_command(cmd)
            .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))?;
        let child_pid = child.process_id();
        info!(
            "Spawned ConPTY shell: {} {:?} (PID: {:?}, initial grid: {}x{})",
            executable, args, child_pid, cols, rows
        );

        Ok(Self {
            master: Arc::new(Mutex::new(pair.master)),
            child: Arc::new(Mutex::new(child)),
        })
    }

    /// Clones the PTY master reader for asynchronous streaming.
    pub fn clone_reader(&self) -> io::Result<Box<dyn Read + Send>> {
        let master = self
            .master
            .lock()
            .map_err(|_| io::Error::new(io::ErrorKind::Other, "Failed to lock master PTY"))?;
        master
            .try_clone_reader()
            .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))
    }

    /// Takes the PTY master writer for handling remote keyboard input.
    pub fn take_writer(&self) -> io::Result<Box<dyn Write + Send>> {
        let master = self
            .master
            .lock()
            .map_err(|_| io::Error::new(io::ErrorKind::Other, "Failed to lock master PTY"))?;
        master
            .take_writer()
            .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))
    }

    /// Resizes the ConPTY grid dimensions.
    pub fn resize(&self, cols: u16, rows: u16) -> io::Result<()> {
        let master = self
            .master
            .lock()
            .map_err(|_| io::Error::new(io::ErrorKind::Other, "Failed to lock master PTY"))?;
        master
            .resize(PtySize {
                rows,
                cols,
                pixel_width: 0,
                pixel_height: 0,
            })
            .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))
    }

    /// Returns the PID of the spawned shell process.
    #[allow(dead_code)]
    pub fn process_id(&self) -> Option<u32> {
        self.child.lock().ok().and_then(|c| c.process_id())
    }
}
