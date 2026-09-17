use crate::packet::OsType;
use serde::{Deserialize, Serialize};

/// Status of an active terminal session
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum SessionStatus {
    Starting,
    Active,
    Idle,
    Disconnected,
    Terminated,
}

/// Information descriptor for an active terminal session
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct SessionDescriptor {
    pub session_id: String,
    pub host_id: String,
    pub host_name: String,
    pub os_type: OsType,
    pub shell: String,
    pub cols: u16,
    pub rows: u16,
    pub status: SessionStatus,
    pub connected_clients_count: usize,
    pub created_at_ms: u64,
}

impl SessionDescriptor {
    pub fn new(
        session_id: impl Into<String>,
        host_id: impl Into<String>,
        host_name: impl Into<String>,
        os_type: OsType,
        shell: impl Into<String>,
        cols: u16,
        rows: u16,
    ) -> Self {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;

        Self {
            session_id: session_id.into(),
            host_id: host_id.into(),
            host_name: host_name.into(),
            os_type,
            shell: shell.into(),
            cols,
            rows,
            status: SessionStatus::Starting,
            connected_clients_count: 0,
            created_at_ms: now,
        }
    }
}
