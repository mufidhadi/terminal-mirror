use clap::Parser;

#[derive(Parser, Debug, Clone)]
#[command(author, version, about = "Terminal Mirror - macOS Host Agent", long_about = None)]
pub struct MacAgentConfig {
    /// Relay Server WebSocket URL (defaults to localhost or ZeroTier IP in private mode)
    #[arg(short, long, env = "RELAY_SERVER_URL", default_value = "ws://127.0.0.1:8080/ws")]
    pub relay_url: String,

    /// Unique host identifier
    #[arg(long, env = "HOST_ID", default_value = "macbook-pro")]
    pub host_id: String,

    /// Friendly host name
    #[arg(short, long, env = "HOST_NAME", default_value = "MacBook Pro")]
    pub name: String,

    /// Preferred shell (defaults to $SHELL or /bin/zsh)
    #[arg(short, long, env = "SHELL")]
    pub shell: Option<String>,

    /// Authentication token for Relay Hub
    #[arg(long, env = "RELAY_AUTH_TOKEN", default_value = "change_this_secret_token")]
    pub auth_token: String,

    /// Run as background daemon with status indicator
    #[arg(long, default_value_t = false)]
    pub menu_bar: bool,

    /// Disable End-to-End Encryption (plain text mode)
    #[arg(long, default_value_t = false)]
    pub no_e2ee: bool,

    /// Optional explicit session ID (defaults to <host_id>-<uuid>)
    #[arg(long, env = "SESSION_ID")]
    pub session_id: Option<String>,

    /// Optional explicit passphrase (defaults to 4-word random Diceware)
    #[arg(long, env = "PASSPHRASE")]
    pub passphrase: Option<String>,
}

impl MacAgentConfig {
    pub fn resolved_shell(&self) -> String {
        self.shell
            .clone()
            .or_else(|| std::env::var("SHELL").ok())
            .unwrap_or_else(|| "/bin/zsh".to_string())
    }
}
