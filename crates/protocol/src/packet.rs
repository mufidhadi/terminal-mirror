use serde::{Deserialize, Serialize};

/// Wire protocol version
pub const PROTOCOL_VERSION: u16 = 1;

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
    },
    /// Host registration confirmation
    HostRegistered {
        success: bool,
        message: String,
    },
    /// Client subscription to a terminal session
    SubscribeSession {
        client_id: String,
        session_id: String,
        auth_token: String,
    },
    /// Subscription confirmation
    SessionSubscribed {
        session_id: String,
        success: bool,
        message: String,
    },
    /// Raw terminal input (keystrokes, commands) sent to PTY
    TerminalInput {
        bytes: Vec<u8>,
    },
    /// Raw terminal output (ANSI/VT100 stream) from PTY
    TerminalOutput {
        bytes: Vec<u8>,
    },
    /// Terminal dimension change
    TerminalResize {
        cols: u16,
        rows: u16,
    },
    /// End-to-End Encrypted payload (Zero-Knowledge Relay mode)
    EncryptedBlob {
        nonce: u64,
        ciphertext: Vec<u8>,
    },
    /// Heartbeat ping
    Ping {
        nonce: u64,
    },
    /// Heartbeat response
    Pong {
        nonce: u64,
    },
    /// Protocol or operational error
    Error {
        code: u32,
        message: String,
    },
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum OsType {
    MacOS,
    Windows,
    Linux,
    Android,
}
