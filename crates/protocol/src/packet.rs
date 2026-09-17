use serde::{Deserialize, Serialize};

/// Wire protocol version
pub const PROTOCOL_VERSION: u16 = 1;

/// Access role granted to a connected client
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum SessionRole {
    /// Full interactive write access (keystrokes + resize)
    Admin,
    /// Read-only spectator access (cannot emit keystrokes or resize)
    Spectator,
}

/// Compression algorithm applied to high-throughput terminal payloads
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
pub enum CompressionAlgorithm {
    #[default]
    None,
    /// Zstandard compression (level 1) for 70-85% bandwidth reduction
    Zstd,
}

/// Top-level wire message envelope
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Packet {
    pub version: u16,
    pub trace_id: String,
    pub session_id: String,
    pub sequence: u64,
    pub timestamp_ms: u64,
    pub payload: PacketPayload,
}

impl Packet {
    pub fn new(session_id: impl Into<String>, sequence: u64, payload: PacketPayload) -> Self {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;

        Self {
            version: PROTOCOL_VERSION,
            trace_id: uuid::Uuid::new_v4().to_string(),
            session_id: session_id.into(),
            sequence,
            timestamp_ms: now,
            payload,
        }
    }

    pub fn to_msgpack(&self) -> Result<Vec<u8>, rmp_serde::encode::Error> {
        rmp_serde::to_vec_named(self)
    }

    pub fn from_msgpack(bytes: &[u8]) -> Result<Self, rmp_serde::decode::Error> {
        rmp_serde::from_slice(bytes)
    }

    pub fn host_presence(
        session_id: impl Into<String>,
        online: bool,
        host_name: Option<String>,
        shell: Option<String>,
    ) -> Self {
        let s_id = session_id.into();
        Self::new(
            s_id.clone(),
            0,
            PacketPayload::HostPresence(HostPresencePayload {
                session_id: s_id,
                online,
                host_name,
                shell,
            }),
        )
    }
}

/// Visual snapshot of terminal screen grid (prevents garbled text upon reconnect)
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ScreenSnapshot {
    pub cols: u16,
    pub rows: u16,
    pub cursor_x: u16,
    pub cursor_y: u16,
    pub in_alternate_screen: bool,
    pub lines: Vec<String>,
}

/// Enumeration of all protocol payload variants
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(tag = "type", content = "data")]
pub enum PacketPayload {
    /// Host registration with relay hub
    RegisterHost {
        host_id: String,
        os_type: OsType,
        hostname: String,
        token: String,
        public_key: Option<String>,
    },
    /// Host registration confirmation
    HostRegistered {
        success: bool,
        message: String,
        device_id: String,
    },
    /// Client subscription to a terminal session
    SubscribeSession {
        client_id: String,
        session_id: String,
        auth_token: String,
        requested_role: SessionRole,
    },
    /// Subscription confirmation with assigned role
    SessionSubscribed {
        session_id: String,
        success: bool,
        assigned_role: SessionRole,
        message: String,
    },
    /// Raw terminal input (keystrokes, commands) sent to PTY
    TerminalInput { bytes: Vec<u8> },
    /// Raw terminal output (ANSI/VT100 delta stream) from PTY
    TerminalOutput {
        bytes: Vec<u8>,
        #[serde(default)]
        compression: CompressionAlgorithm,
    },
    /// Complete visual state snapshot sent upon client reconnect
    ScreenStateSync { snapshot: ScreenSnapshot },
    /// Terminal dimension change (debounced)
    TerminalResize { cols: u16, rows: u16 },
    /// End-to-End Encrypted payload (Zero-Knowledge Relay mode)
    EncryptedBlob { nonce: u64, ciphertext: Vec<u8> },
    /// Pairing request using 6-digit short PIN
    PairWithPin {
        pin: String,
        client_public_key: String,
    },
    /// Pairing response confirming persistent association
    PairingConfirmed {
        success: bool,
        host_name: String,
        host_public_key: String,
        auth_token: String,
    },
    /// Host-initiated emergency kill switch (instant revocation)
    SessionRevoked { reason: String },
    /// Heartbeat ping
    Ping { nonce: u64 },
    /// Heartbeat response
    Pong { nonce: u64 },
    /// Protocol or operational error
    Error { code: u32, message: String },
    /// Real-time host presence lifecycle emitted by relay hub to subscribers
    HostPresence(HostPresencePayload),
}

/// Metadata payload describing host presence lifecycle
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct HostPresencePayload {
    pub session_id: String,
    pub online: bool,
    pub host_name: Option<String>,
    pub shell: Option<String>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum OsType {
    MacOS,
    Windows,
    Linux,
    Android,
}
