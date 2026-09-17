use clap::Parser;

#[derive(Parser, Debug, Clone)]
#[command(
    author,
    version,
    about = "Terminal Mirror - Windows Host Agent (ConPTY Hardened)",
    long_about = None
)]
pub struct WindowsAgentConfig {
    /// Relay Server WebSocket URL (defaults to localhost or ZeroTier IP in private mode)
    #[arg(short, long, env = "RELAY_SERVER_URL", default_value = "ws://127.0.0.1:8080/ws")]
    pub relay_url: String,

    /// Unique host identifier
    #[arg(long, env = "HOST_ID", default_value = "win-host")]
    pub host_id: String,

    /// Friendly host name
    #[arg(short, long, env = "HOST_NAME", default_value = "Windows PC")]
    pub name: String,

    /// Preferred shell override (defaults to pwsh.exe, powershell.exe, or %COMSPEC%)
    #[arg(short, long)]
    pub shell: Option<String>,

    /// Authentication token for Relay Hub
    #[arg(long, env = "RELAY_AUTH_TOKEN", default_value = "change_this_secret_token")]
    pub auth_token: String,

    /// Optional explicit passphrase (defaults to 4-word random Diceware)
    #[arg(long, env = "PASSPHRASE")]
    pub passphrase: Option<String>,

    /// Run as background daemon in Windows Notification Area (System Tray)
    #[arg(long, default_value_t = false)]
    pub tray: bool,

    /// Register as a native unattended Windows Service
    #[arg(long, default_value_t = false)]
    pub service_install: bool,

    /// Debounce delay in milliseconds for ConPTY window resize events
    #[arg(long, env = "RESIZE_DEBOUNCE_MS", default_value_t = 200)]
    pub resize_debounce_ms: u64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = WindowsAgentConfig::parse_from(["terminal-mirror-windows"]);
        assert_eq!(config.relay_url, "ws://127.0.0.1:8080/ws");
        assert_eq!(config.host_id, "win-host");
        assert_eq!(config.name, "Windows PC");
        assert_eq!(config.shell, None);
        assert!(!config.tray);
        assert!(!config.service_install);
        assert_eq!(config.resize_debounce_ms, 200);
        assert_eq!(config.passphrase, None);
    }

    #[test]
    fn test_custom_flags_parsing() {
        let config = WindowsAgentConfig::parse_from([
            "terminal-mirror-windows",
            "--relay-url",
            "wss://relay.masmuf.cloud/ws",
            "--host-id",
            "thinkpad-x1",
            "--name",
            "ThinkPad Workstation",
            "--shell",
            "pwsh.exe",
            "--tray",
            "--resize-debounce-ms",
            "300",
        ]);

        assert_eq!(config.relay_url, "wss://relay.masmuf.cloud/ws");
        assert_eq!(config.host_id, "thinkpad-x1");
        assert_eq!(config.name, "ThinkPad Workstation");
        assert_eq!(config.shell.as_deref(), Some("pwsh.exe"));
        assert!(config.tray);
        assert_eq!(config.resize_debounce_ms, 300);
    }
}
