# Data Dictionary & Wire Protocol Specification
## Project: Terminal Mirror

---

### 1. Protocol Overview
All communication between Host Daemons, Relay Hub, and Mobile Clients uses binary **MessagePack (Named Mapping)** encoding over WebSocket streams. 

* **Endianness**: Big-Endian (Network Byte Order).
* **Protocol Version**: `1` (Unsigned 16-bit integer).
* **Wire Envelope**: `Packet`

---

### 2. Envelope Schema: `Packet`

| Field | Data Type | Bytes / Format | Constraints | Description |
| :--- | :--- | :--- | :--- | :--- |
| `version` | `u16` | 2 bytes | Equal to `1` | Wire protocol major version. |
| `trace_id` | `String` | 36 characters (UUIDv4) | Valid UUID | Unique identifier for distributed tracing and packet correlation. |
| `session_id` | `String` | Max 64 characters | Alphanumeric + `-_` | Target or source terminal session identifier. |
| `sequence` | `u64` | 8 bytes | Monotonically increasing | Sequence number for replay attack mitigation and packet loss detection. |
| `timestamp_ms`| `u64` | 8 bytes | Epoch Milliseconds | UTC timestamp when packet was created by sender. |
| `payload` | `PacketPayload` | Variable (Tagged Enum) | Valid schema | The specific event or data payload. |

---

### 3. Payload Definitions (`PacketPayload`)

#### 3.1 `RegisterHost`
Dispatched by Host Daemons to register an active workstation with the Relay Server.

| Field | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `host_id` | `String` | Alphanumeric (`macbook-pro`) | Unique persistent device identifier. |
| `os_type` | `OsType` | Enum (`MacOS`, `Windows`, `Linux`) | Workstation operating system. |
| `hostname` | `String` | Max 128 characters | System hostname reported by host OS. |
| `token` | `String` | Secret string | Authentication token matching relay's `RELAY_AUTH_TOKEN`. |

#### 3.2 `HostRegistered`
Returned by Relay Server confirming or rejecting host registration.

| Field | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `success` | `bool` | `true` or `false` | Registration outcome. |
| `message` | `String` | Max 256 characters | Human-readable diagnostic or error string. |

#### 3.3 `SubscribeSession`
Dispatched by Mobile Client to initiate streaming for a specific session.

| Field | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `client_id` | `String` | Max 64 characters | Unique mobile client instance identifier. |
| `session_id` | `String` | Target session | The terminal session ID to subscribe to. |
| `auth_token` | `String` | Secret string | Authentication secret. |

#### 3.4 `TerminalInput`
Dispatched by Mobile Client sending keyboard keystrokes to Host PTY.

| Field | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `bytes` | `Vec<u8>` | Max 4,096 bytes per chunk | Raw keystroke bytes, ANSI control codes (e.g. `\x03` for Ctrl+C). |

#### 3.5 `TerminalOutput`
Dispatched by Host Daemon broadcasting terminal output to subscribed clients.

| Field | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `bytes` | `Vec<u8>` | Max 65,536 bytes per chunk | Raw ANSI escape sequences and text emitted by PTY master. |

#### 3.6 `TerminalResize`
Dispatched bidirectionally to negotiate terminal viewport dimensions.

| Field | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `cols` | `u16` | Range: `20` to `500` | Number of horizontal character columns. |
| `rows` | `u16` | Range: `5` to `200` | Number of vertical character rows. |

#### 3.7 `EncryptedBlob`
Used when End-to-End Encryption (Zero-Knowledge Relay) is active.

| Field | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `nonce` | `u64` | 8-byte nonce | Cryptographic nonce for AEAD cipher (ChaCha20-Poly1305). |
| `ciphertext` | `Vec<u8>` | Encrypted payload + MAC | Ciphertext containing an encrypted inner `PacketPayload`. |

#### 3.8 `Ping` & `Pong`
Liveness heartbeat frames.

| Field | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `nonce` | `u64` | 8 bytes | Echo identifier to measure round-trip time (RTT). |

---

### 4. Data Models & Entities

#### 4.1 `SessionDescriptor`
In-memory and wire entity representing the state of an active terminal stream.

| Attribute | Type | Nullable | Description |
| :--- | :--- | :--- | :--- |
| `session_id` | `String` | No | Primary session key. |
| `host_id` | `String` | No | Identifier of host machine running the PTY. |
| `host_name` | `String` | No | Friendly human-readable host name. |
| `os_type` | `OsType` | No | Host operating system. |
| `shell` | `String` | No | Path to binary shell (e.g. `/bin/zsh`, `powershell.exe`). |
| `cols` | `u16` | No | Active terminal width in columns. |
| `rows` | `u16` | No | Active terminal height in rows. |
| `status` | `SessionStatus`| No | Current state (`Starting`, `Active`, `Idle`, `Terminated`). |
| `connected_clients_count` | `usize` | No | Number of mobile clients actively subscribed. |
| `created_at_ms` | `u64` | No | UTC millisecond epoch creation time. |

#### 4.2 `PairingPayload`
Structure encoded into the terminal ASCII QR Code for mobile pairing.

| Attribute | Type | Description |
| :--- | :--- | :--- |
| `relay_url` | `String` | Complete WebSocket URL of the Relay Server (e.g. `wss://vpn.example.internal:8080/ws`). |
| `session_id` | `String` | Session identifier to pair with. |
| `host_id` | `String` | Host machine identifier. |
| `pre_shared_key`| `String` | High-entropy random secret key for AEAD encryption. |
| `public_key` | `String` | Base64-encoded X25519 public key. |
| `expires_at_ms` | `u64` | Expiration timestamp (default: 15 minutes from generation). |
