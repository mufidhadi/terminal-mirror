use serde::{Deserialize, Serialize};

pub const MAX_PAIRING_ATTEMPTS: u8 = 3;

/// Pairing configuration exchanged out-of-band (via QR code, 6-digit PIN, or 4-Word Diceware Passphrase)
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct PairingPayload {
    pub relay_url: String,
    pub session_id: String,
    pub host_id: String,
    pub pre_shared_key: String,
    pub public_key: String,
    pub pin_code: Option<String>,
    pub passphrase_words: Option<Vec<String>>,
    pub expires_at_ms: u64,
}

impl PairingPayload {
    pub fn to_qr_string(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string(self)
    }

    pub fn from_qr_string(s: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(s)
    }

    /// Formats the 4-word passphrase if available
    pub fn formatted_passphrase(&self) -> Option<String> {
        self.passphrase_words.as_ref().map(|w| w.join("-"))
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

/// Host-side pairing guard to prevent online brute-force attacks on PINs/Passphrases
#[derive(Debug, Clone)]
pub struct PairingGuard {
    pub session_id: String,
    pub secret: String,
    pub attempts: u8,
    pub is_burned: bool,
}

impl PairingGuard {
    pub fn new(session_id: impl Into<String>, secret: impl Into<String>) -> Self {
        Self {
            session_id: session_id.into(),
            secret: secret.into(),
            attempts: 0,
            is_burned: false,
        }
    }

    /// Validates an incoming attempt. Returns Ok(true) if correct,
    /// Err(attempts_left) if incorrect, or Err(0) if burned.
    pub fn verify_attempt(&mut self, candidate: &str) -> Result<(), u8> {
        if self.is_burned {
            return Err(0);
        }

        if self.secret == candidate {
            Ok(())
        } else {
            self.attempts += 1;
            if self.attempts >= MAX_PAIRING_ATTEMPTS {
                self.is_burned = true;
                Err(0) // Burned immediately
            } else {
                Err(MAX_PAIRING_ATTEMPTS - self.attempts)
            }
        }
    }
}
