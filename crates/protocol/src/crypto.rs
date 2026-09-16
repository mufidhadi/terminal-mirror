use serde::{Deserialize, Serialize};

/// Pairing configuration exchanged out-of-band (via QR code or 6-digit PIN)
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct PairingPayload {
    pub relay_url: String,
    pub session_id: String,
    pub host_id: String,
    pub pre_shared_key: String,
    pub public_key: String,
    pub pin_code: Option<String>,
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

/// Known trusted device saved in local storage (eliminates QR scan fatigue)
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct TrustedDevice {
    pub device_id: String,
    pub friendly_name: String,
    pub public_key: String,
    pub last_relay_url: String,
    pub paired_at_ms: u64,
}
