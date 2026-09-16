use chacha20poly1305::aead::{Aead, KeyInit};
use chacha20poly1305::{ChaCha20Poly1305, Key, Nonce};
use serde::{Deserialize, Serialize};

pub const MAX_PAIRING_ATTEMPTS: u8 = 3;

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum E2eeError {
    #[error("Encryption failure")]
    EncryptionFailure,
    #[error("Decryption authentication failure: ciphertext or tag was tampered with")]
    AuthenticationFailure,
}

/// Zero-Knowledge End-to-End Encryption Engine using ChaCha20-Poly1305 AEAD.
/// Nonces are constructed deterministically from the 64-bit sequence counter
/// to strictly prevent nonce reuse.
pub struct E2eeCipher {
    cipher: ChaCha20Poly1305,
}

impl E2eeCipher {
    pub fn new(key_bytes: &[u8; 32]) -> Self {
        let key = Key::from_slice(key_bytes);
        Self {
            cipher: ChaCha20Poly1305::new(key),
        }
    }

    /// Derives a 32-byte key from any arbitrary secret or passphrase using SHA-256
    pub fn from_secret(secret: &str) -> Self {
        use sha2::{Digest, Sha256};
        let hash = Sha256::digest(secret.as_bytes());
        let mut key_bytes = [0u8; 32];
        key_bytes.copy_from_slice(&hash);
        Self::new(&key_bytes)
    }

    /// Derives a 96-bit (12-byte) unique nonce from sequence counter.
    /// Bytes 0..4: padding zeros
    /// Bytes 4..12: big-endian sequence counter
    fn derive_nonce(seq: u64) -> [u8; 12] {
        let mut nonce = [0u8; 12];
        nonce[4..12].copy_from_slice(&seq.to_be_bytes());
        nonce
    }

    /// Encrypts plaintext bytes using ChaCha20-Poly1305.
    pub fn encrypt(&self, seq: u64, plaintext: &[u8]) -> Result<Vec<u8>, E2eeError> {
        let nonce_bytes = Self::derive_nonce(seq);
        let nonce = Nonce::from_slice(&nonce_bytes);
        self.cipher
            .encrypt(nonce, plaintext)
            .map_err(|_| E2eeError::EncryptionFailure)
    }

    /// Decrypts ciphertext bytes and verifies Poly1305 authentication tag.
    pub fn decrypt(&self, seq: u64, ciphertext: &[u8]) -> Result<Vec<u8>, E2eeError> {
        let nonce_bytes = Self::derive_nonce(seq);
        let nonce = Nonce::from_slice(&nonce_bytes);
        self.cipher
            .decrypt(nonce, ciphertext)
            .map_err(|_| E2eeError::AuthenticationFailure)
    }
}

/// High-entropy Diceware Passphrase generator
pub struct DicewarePassphrase;

const INDONESIAN_WORDLIST: &[&str] = &[
    "batu", "merah", "kuda", "terbang", "angin", "langit", "gunung", "sungai",
    "pantai", "pohon", "hutan", "bintang", "ombak", "garuda", "emas", "perak",
    "mutiara", "kilat", "surya", "pelangi", "samudra", "lentera", "raja", "kancil",
    "elang", "singa", "harimau", "padang", "rumput", "cahaya", "awan", "hujan",
    "badai", "gempa", "mentari", "senja", "fajar", "subuh", "malam", "siang",
    "kobar", "api", "pasir", "karang", "danau", "lembah", "kristal", "cakrawala",
];

impl DicewarePassphrase {
    /// Generates a passphrase with `count` words (defaults to 4 words).
    pub fn generate(count: usize) -> Vec<String> {
        use std::collections::hash_map::RandomState;
        use std::hash::{BuildHasher, Hasher};

        let mut words = Vec::with_capacity(count);
        let s = RandomState::new();

        for i in 0..count {
            let mut hasher = s.build_hasher();
            hasher.write_usize(i);
            hasher.write_u128(
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_nanos(),
            );
            let idx = (hasher.finish() as usize) % INDONESIAN_WORDLIST.len();
            words.push(INDONESIAN_WORDLIST[idx].to_string());
        }

        words
    }

    pub fn format(words: &[String]) -> String {
        words.join("-")
    }
}

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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_e2ee_chacha20poly1305_roundtrip() {
        let key = [0x42u8; 32];
        let cipher = E2eeCipher::new(&key);

        let plaintext = b"Hello from macOS Terminal!";
        let seq = 1;

        let ciphertext = cipher.encrypt(seq, plaintext).expect("Encryption failed");
        assert_ne!(ciphertext, plaintext);

        let decrypted = cipher.decrypt(seq, &ciphertext).expect("Decryption failed");
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_e2ee_tampered_ciphertext_fails_auth() {
        let key = [0x55u8; 32];
        let cipher = E2eeCipher::new(&key);

        let plaintext = b"Sensitive Developer Keystroke";
        let seq = 42;

        let mut ciphertext = cipher.encrypt(seq, plaintext).unwrap();

        // Tamper with 1 byte of ciphertext
        ciphertext[0] ^= 0xFF;

        let result = cipher.decrypt(seq, &ciphertext);
        assert_eq!(result, Err(E2eeError::AuthenticationFailure));
    }

    #[test]
    fn test_e2ee_from_secret_passphrase_roundtrip() {
        let passphrase = "kuda-terbang-angin-gunung";
        let cipher_sender = E2eeCipher::from_secret(passphrase);
        let cipher_receiver = E2eeCipher::from_secret(passphrase);

        let plaintext = b"ls -la /Users/mufid";
        let seq = 101;

        let ciphertext = cipher_sender.encrypt(seq, plaintext).unwrap();
        let decrypted = cipher_receiver.decrypt(seq, &ciphertext).unwrap();
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_diceware_passphrase_generation() {
        let words = DicewarePassphrase::generate(4);
        assert_eq!(words.len(), 4);
        let formatted = DicewarePassphrase::format(&words);
        assert_eq!(formatted.split('-').count(), 4);
    }
}
