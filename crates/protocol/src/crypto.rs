use serde::{Deserialize, Serialize};

/// Pairing configuration exchanged out-of-band (e.g. via QR code)
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct PairingPayload {
    pub relay_url: String,
    pub session_id: String,
    pub host_id: String,
    pub pre_shared_key: String,
    pub public_key: String,
    pub expires_at_ms: u64,
}

impl PairingPayload {
    pub fn to_qr_string(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string(self)
    }

    pub fn from_qr_string(s: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(s)
    }
}
