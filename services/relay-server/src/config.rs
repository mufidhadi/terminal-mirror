use clap::Parser;

#[derive(Parser, Debug, Clone)]
#[command(
    author,
    version,
    about = "Terminal Mirror - Zero-Knowledge Relay Server Hub",
    long_about = None
)]
pub struct RelayServerConfig {
    /// Host address and port to bind
    #[arg(short, long, env = "RELAY_BIND_ADDR", default_value = "0.0.0.0:8080")]
    pub bind_addr: String,

    /// Authentication token required for host agents and subscribers
    #[arg(
        long,
        env = "RELAY_AUTH_TOKEN",
        default_value = "change_this_secret_token"
    )]
    pub auth_token: String,

    /// Maximum incoming WebSocket connection handshakes per minute per IP
    #[arg(
        long,
        alias = "max-conn-per-min",
        env = "MAX_CONN_PER_MIN",
        default_value_t = 60
    )]
    pub max_connections_per_min: u32,

    /// Maximum allowed WebSocket frame size in bytes (hard cap to prevent buffer bloat)
    #[arg(long, env = "MAX_PAYLOAD_BYTES", default_value_t = 65536)]
    pub max_payload_bytes: usize,

    /// Timeout in seconds before an idle/abandoned session is reaped from memory
    #[arg(long, env = "STALE_SESSION_TIMEOUT_SECS", default_value_t = 300)]
    pub stale_session_timeout_secs: u64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = RelayServerConfig::parse_from(["terminal-mirror-relay"]);
        assert_eq!(config.bind_addr, "0.0.0.0:8080");
        assert_eq!(config.auth_token, "change_this_secret_token");
        assert_eq!(config.max_connections_per_min, 60);
        assert_eq!(config.max_payload_bytes, 65536);
        assert_eq!(config.stale_session_timeout_secs, 300);
    }

    #[test]
    fn test_custom_cli_args() {
        let config = RelayServerConfig::parse_from([
            "terminal-mirror-relay",
            "--bind-addr",
            "127.0.0.1:9090",
            "--auth-token",
            "super_secret_vps_token",
            "--max-conn-per-min",
            "120",
            "--max-payload-bytes",
            "131072",
            "--stale-session-timeout-secs",
            "600",
        ]);

        assert_eq!(config.bind_addr, "127.0.0.1:9090");
        assert_eq!(config.auth_token, "super_secret_vps_token");
        assert_eq!(config.max_connections_per_min, 120);
        assert_eq!(config.max_payload_bytes, 131072);
        assert_eq!(config.stale_session_timeout_secs, 600);
    }
}
