# Data Dictionary & Wire Protocol Specification
## Project: Terminal Mirror (Hardened Open-Source Edition)

---

### 1. Protocol Architecture
Binary **MessagePack** over WebSocket.

* **Version**: `1`
* **Transport**: WebSocket Binary Frames
* **Max Frame Size**: `65,536` bytes (64 KB enforced by Relay)
* **Envelope**: `Packet`

---

### 2. Envelope Schema: `Packet`

| Field | Type | Description |
| :--- | :--- | :--- |
| `version` | `u16` | Wire version (`1`). |
| `trace_id` | `String` | UUIDv4 for packet tracing. |
| `session_id` | `String` | Target terminal session identifier. |
| `sequence` | `u64` | Monotonically increasing anti-replay sequence number. |
| `timestamp_ms`| `u64` | UTC millisecond timestamp. |
| `payload` | `PacketPayload` | Inner message variant. |

---

### 3. Core Entities & Hardened Structures

#### 3.1 `PairingGuard`
Host-side state machine protecting against online brute-force attacks on pairing passphrases/PINs.

| Field | Type | Description |
| :--- | :--- | :--- |
| `session_id` | `String` | Unique session ID. |
| `secret` | `String` | The correct passphrase / secret string. |
| `attempts` | `u8` | Counter of failed attempts (max: `3`). |
| `is_burned` | `bool` | `true` if 3 strikes reached; permanently rejects all further attempts. |

#### 3.2 `Utf8StreamChunker`
Host-side buffer manager preventing Unicode corruption across buffer chunk boundaries.

| Field | Type | Description |
| :--- | :--- | :--- |
| `pending_bytes` | `Vec<u8>` | Buffer holding trailing incomplete multibyte UTF-8 bytes (1 to 3 bytes) between chunk reads. |

#### 3.3 `PairingPayload`
Encapsulates out-of-band pairing credentials (QR Code / Passphrase).

| Field | Type | Description |
| :--- | :--- | :--- |
| `relay_url` | `String` | WebSocket endpoint URL. |
| `session_id` | `String` | Ephemeral session ID. |
| `host_id` | `String` | Host machine UUID. |
| `pre_shared_key`| `String` | High-entropy random secret for AEAD. |
| `public_key` | `String` | Host's X25519 public key. |
| `passphrase_words`| `Option<Vec<String>>` | 4-word Diceware passphrase (e.g. `["kuda", "terbang", "batu", "merah"]`). |
| `pin_code` | `Option<String>` | Fallback 6-digit numeric PIN (protected by 3-strike auto-burn). |
| `expires_at_ms` | `u64` | Expiration timestamp. |

#### 3.4 `ScreenSnapshot`
Visual terminal grid transmitted upon client reconnection.

| Field | Type | Description |
| :--- | :--- | :--- |
| `cols` | `u16` | Grid width in columns. |
| `rows` | `u16` | Grid height in rows. |
| `cursor_x` | `u16` | Current cursor column index. |
| `cursor_y` | `u16` | Current cursor row index. |
| `in_alternate_screen` | `bool` | `true` if application is running in alternate screen mode (e.g. `vim`, `htop`). |
| `lines` | `Vec<String>` | Text content of each row in the visible viewport. |
