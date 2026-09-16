# Software Requirements Specification (SRS)
## Standard: IEEE 830-1998 Format
### Project: Terminal Mirror System

---

### 1. Introduction

#### 1.1 Purpose
This document specifies the complete functional and non-functional software requirements for the **Terminal Mirror System**, comprising the macOS Host Daemon (`apps/mac`), Windows Host Daemon (`apps/windows`), Central Relay Server (`services/relay-server`), Shared Protocol Library (`crates/protocol`), and Android Mobile Application (`apps/android`).

#### 1.2 Document Conventions
* The keywords **SHALL**, **SHALL NOT**, **SHOULD**, **RECOMMENDED**, and **MAY** in this specification are interpreted as described in [RFC 2119](https://datatracker.ietf.org/doc/html/rfc2119).
* Requirement identifiers:
  * `FR-xxx`: Functional Requirement
  * `NFR-xxx`: Non-Functional Requirement
  * `SEC-xxx`: Security Requirement
  * `IF-xxx`: Interface Requirement

#### 1.3 Intended Audience
Systems engineers, software architects, mobile developers, security auditors, and DevOps engineers maintaining the infrastructure.

---

### 2. Overall Description

#### 2.1 Product Perspective
Terminal Mirror operates as an autonomous client-server-agent system. The Relay Server acts as a non-persistent message router. The Host Daemons act as PTY controllers. The Android App acts as a VT100/ANSI terminal emulator frontend.

#### 2.2 System Interfaces
* **POSIX PTY Interface** on macOS (`/dev/ptmx`, `termios`).
* **ConPTY Subsystem** on Windows (`CreatePseudoConsole`, `ClosePseudoConsole`).
* **Network Socket Layer**: WebSocket over TCP/TLS, optionally bound to ZeroTier Virtual Network Adapter.
* **Android OS Native View**: Jetpack Compose and native Canvas/SurfaceView rendering via `termux-view`.

---

### 3. Specific Requirements

#### 3.1 Functional Requirements (FR)

##### 3.1.1 Host Daemon Subsystem (macOS & Windows)
* **FR-001**: The Host Daemon SHALL spawn the user's default shell (e.g. `/bin/zsh`, `/bin/bash` on macOS, `powershell.exe`, `cmd.exe` on Windows) within a Pseudo-Terminal (PTY) instance upon startup.
* **FR-002**: The Host Daemon SHALL capture stdout and stderr from the PTY master stream non-blockingly and encapsulate raw byte chunks into `PacketPayload::TerminalOutput` envelopes.
* **FR-003**: The Host Daemon SHALL receive `PacketPayload::TerminalInput` envelopes from authenticated mobile clients and write the decoded bytes directly into the PTY master input writer.
* **FR-004**: The Host Daemon SHALL support terminal window resizing upon receiving `PacketPayload::TerminalResize`, dynamically invoking `TIOCSWINSZ` on macOS and `ResizePseudoConsole` on Windows.
* **FR-005**: The Host Daemon SHALL maintain an in-memory FIFO Ring Buffer retaining the most recent 1 MB of terminal output stream to serve instant replay buffers upon client reconnection.
* **FR-006**: The Host Daemon SHALL generate a cryptographically signed QR Code on stdout using ASCII blocks containing session token, public key, and relay connection parameters.

##### 3.1.2 Relay Server Subsystem (VPS)
* **FR-007**: The Relay Server SHALL provide a high-throughput WebSocket endpoint (`/ws`) capable of handling concurrent binary streams across multiple hosts and clients.
* **FR-008**: The Relay Server SHALL implement session routing using an in-memory thread-safe map (`DashMap`), associating `session_id` with active broadcast channels.
* **FR-009**: The Relay Server SHALL reject unauthenticated connections that do not provide a valid `RELAY_AUTH_TOKEN` in the initial handshake headers.
* **FR-010**: The Relay Server SHALL support multiplexed client subscriptions, permitting an Android client to subscribe to multiple `session_id` streams over a single physical WebSocket connection.
* **FR-011**: The Relay Server SHALL emit a periodic heartbeat ping frame every 15 seconds to detect stale connections and reclaim orphaned session resources.

##### 3.1.3 Android Client Subsystem
* **FR-012**: The Android Client SHALL render terminal byte streams accurately adhering to ANSI X3.64 and VT100 escape sequence standards (colors, cursor positioning, clear screen).
* **FR-013**: The Android Client SHALL provide a multi-session Tab Bar enabling immediate switching between active sessions (e.g. Mac Host vs Windows Host).
* **FR-014**: The Android Client SHALL provide a **View-Only Guard Mode** enabled by default, discarding all user touch and soft-keyboard input until explicitly toggled off by the user.
* **FR-015**: The Android Client SHALL provide an Accessory Keyboard Toolbar with quick-access hardware keys: `ESC`, `TAB`, `CTRL`, `ALT`, `PIPE (|)`, and Cursor Navigation (`↑`, `↓`, `←`, `→`).
* **FR-016**: The Android Client SHALL provide a QR Code camera scanner to capture host pairing credentials without requiring manual IP address or secret key entry.

---

#### 3.2 Non-Functional Requirements (NFR)

##### 3.2.1 Performance & Latency
* **NFR-001**: End-to-end keystroke latency (keystroke sent from Android -> written to Host PTY -> echo output rendered on Android) SHALL be less than **50 ms** over local network / ZeroTier LAN, and less than **100 ms** over 4G/5G mobile cellular networks.
* **NFR-002**: Binary serialization and deserialization overhead per packet SHALL NOT exceed **1 millisecond**.

##### 3.2.2 Resource Utilization
* **NFR-003**: Host Daemon resident memory (RSS) SHALL NOT exceed **35 MB** under peak streaming load.
* **NFR-004**: Host Daemon CPU consumption SHALL NOT exceed **2%** of a single CPU core during heavy terminal scrolling (e.g. running `cat bigfile.log`).
* **NFR-005**: Relay Server memory consumption SHALL NOT exceed **50 MB** when routing up to 10 concurrent active sessions.

##### 3.2.3 Reliability & Availability
* **NFR-006**: The system SHALL automatically recover from transient network disconnects within **2,000 ms** of network interface restoration.
* **NFR-007**: When a host daemon exits or the child shell terminates, the Relay Server and connected Android clients SHALL receive a graceful `SessionStatus::Terminated` notification within **500 ms**.

---

#### 3.3 Security Requirements (SEC)

* **SEC-001**: **Zero-Knowledge Relay Assurance**: The Relay Server SHALL NOT possess cryptographic private keys or shared symmetric secrets required to decrypt terminal payloads. Terminal output and keystrokes MUST be end-to-end encrypted between Host and Client using **ChaCha20-Poly1305** or **AES-256-GCM**.
* **SEC-002**: **Network Boundary Protection**: The Relay Server configuration SHALL permit binding strictly to a private virtual interface (e.g. ZeroTier IP `10.x.x.x` or loopback) to prevent exposure to the public internet.
* **SEC-003**: **Replay Attack Mitigation**: Each packet envelope SHALL include a monotonically increasing 64-bit sequence number and UTC millisecond timestamp; clients and hosts SHALL reject packets with duplicate or backward sequence numbers.
* **SEC-004**: **Token Secrecy**: Pairing tokens and private keys SHALL NEVER be committed to version control, logged to disk in plaintext, or transmitted unencrypted.
* **SEC-005**: **Least Privilege**: The Host Daemon SHALL execute under the permissions of the invoking standard user and SHALL NOT require or request elevated root/administrator privileges.

---

#### 3.4 Interface Requirements (IF)
* **IF-001**: All wire communications SHALL strictly conform to the `Packet` envelope schema defined in `terminal-mirror-protocol` serialized via MessagePack (`rmp-serde`).
* **IF-002**: The Relay Server HTTP health probe SHALL return HTTP `200 OK` with payload `"OK"` on endpoint `GET /health`.
