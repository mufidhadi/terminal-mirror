# Software Requirements Specification (SRS)
## Standard: IEEE 830-1998 Format
### Project: Terminal Mirror (Open-Source Edition)

---

### 1. Introduction

#### 1.1 Purpose
This document specifies the software requirements for the open-source **Terminal Mirror** platform, covering the multi-platform Host Agents (`apps/mac`, `apps/windows`, `apps/linux`), Central Relay Hub (`services/relay-server`), Shared Protocol Library (`crates/protocol`), and Android Mobile Application (`apps/android`).

#### 1.2 System Scope
The software provides universal, low-latency, end-to-end encrypted terminal mirroring and multiplexing capable of operating in both community public relay mode and self-hosted private hub mode.

---

### 2. Functional Requirements (FR)

#### 2.1 Host Daemon Subsystem
* **FR-001**: The Host Daemon SHALL spawn the user's default shell in a Pseudo-Terminal (PTY) using `portable-pty` (POSIX `/dev/ptmx` on macOS/Linux, ConPTY on Windows).
* **FR-002**: The Host Daemon SHALL continuously stream raw ANSI output delta chunks to the relay hub wrapped in encrypted envelopes.
* **FR-003**: The Host Daemon SHALL write validated `TerminalInput` keystrokes received from authenticated `Admin` clients into the PTY master stream.
* **FR-004**: **Virtual Screen Grid Engine**: The Host Daemon SHALL maintain an in-memory VT100 terminal emulator parser (via the `vt100` crate) that tracks current character cells, colors, cursor coordinates, and alternate screen status.
* **FR-005**: **Reconnection Snapshot**: When a client reconnects after network loss, the Host Daemon SHALL emit a `PacketPayload::ScreenStateSync` containing the current screen grid snapshot to prevent visual artifacts and garbled text.
* **FR-006**: **ConPTY Resize Debouncer**: On Windows hosts, the daemon SHALL debounce incoming `TerminalResize` events with a minimum 200 millisecond delay to prevent ConPTY frame storms and CPU saturation.
* **FR-007**: **Dual-Role Authorization**: The Host Daemon SHALL enforce access roles:
  * `SessionRole::Admin`: Permitted to stream output, send input keystrokes, and request terminal resizing.
  * `SessionRole::Spectator`: Permitted to stream output only. All received input packets MUST be immediately discarded.
* **FR-008**: **One-Time Pairing (Visual & PIN)**: The Host Daemon SHALL generate:
  * An ASCII QR Code for visual pairing with mobile cameras.
  * A 9-Digit Device ID and 6-Digit short PIN (e.g. `491-023`) valid for 10 minutes for pairing headless machines without monitors.
* **FR-009**: **Persistent Device Trust**: The Host Daemon SHALL store authorized client public keys in `~/.config/terminal-mirror/authorized_devices.toml`, allowing subsequent reconnections without user interaction.
* **FR-010**: **Emergency Kill Switch**: The Host Daemon SHALL intercept a physical keyboard shortcut (`Ctrl + Shift + Q`) to instantly disconnect all remote viewers, revoke active session tokens, and transition to isolated local-only mode.

#### 2.2 Relay Hub Subsystem (VPS & Community Relay)
* **FR-011**: The Relay Hub SHALL provide an asynchronous WebSocket endpoint (`/ws`) capable of routing binary MessagePack envelopes between hosts and mobile clients.
* **FR-012**: **Zero-Knowledge Blind Routing**: The Relay Hub SHALL route packets solely using plaintext outer routing headers (`session_id`, `trace_id`, `sequence`), without possessing cryptographic keys to inspect or modify encrypted payload blobs.
* **FR-013**: **Hybrid Deployment**: The Relay Hub SHALL function identically whether deployed as a public community rendezvous server or as a self-hosted private hub behind ZeroTier, WireGuard, or local LAN.
* **FR-014**: **Session Multiplexing**: The Relay Hub SHALL support multiplexing multiple host sessions to a single Android client connection.
* **FR-015**: The Relay Hub SHALL maintain a heartbeat ping interval of 15 seconds, terminating dead sockets after 3 missed cycles (45 seconds).

#### 2.3 Android Client Subsystem
* **FR-016**: The Android Client SHALL render ANSI X3.64 and VT100 terminal escape sequences cleanly via native Canvas or Termux `terminal-view`.
* **FR-017**: **Known Hosts List**: The Android Client SHALL store paired host descriptors and public keys in the **Android KeyStore**, allowing 1-tap reconnections without scanning QR codes.
* **FR-018**: **Raw Keyboard Input**: The Android Client SHALL configure soft keyboard input connections with `InputType.TYPE_NULL` to bypass Android IME word composition and autocorrect, preventing duplicate character bugs.
* **FR-019**: **Accessory Terminal Bar**: The Android Client SHALL display a floating toolbar offering essential hardware terminal keys (`Esc`, `Tab`, `Ctrl`, `Alt`, `|`, `~`, Cursor Arrows).
* **FR-020**: **View-Only Default Guard**: The Android Client SHALL default to **View-Only Mode**, rejecting screen touch input until explicitly toggled by the user.

---

### 3. Non-Functional Requirements (NFR)

* **NFR-001 (Keystroke Latency)**: End-to-end roundtrip latency for interactive keystrokes SHALL NOT exceed **50 ms** on LAN/Wi-Fi and **100 ms** on 4G/5G mobile networks.
* **NFR-002 (Reconnection Sync Speed)**: Full screen state resynchronization following network reconnection SHALL complete in under **300 ms**.
* **NFR-003 (Memory Footprint)**: Host daemon resident memory (RSS) SHALL NOT exceed **25 MB** during continuous heavy scroll operations.
* **NFR-004 (Host CPU Overhead)**: Host daemon CPU consumption SHALL NOT exceed **1.5%** of a modern CPU core under continuous stream output.
* **NFR-005 (Zero-Knowledge Guarantee)**: At no point in transmission SHALL plaintext terminal data traverse the Relay Hub unencrypted.

---

### 4. Security & Cryptographic Requirements (SEC)

* **SEC-001 (Symmetric Encryption)**: Payloads MUST be encrypted using **ChaCha20-Poly1305** (AEAD) with authenticated associated data (AAD) binding to the session ID and sequence number.
* **SEC-002 (Key Exchange)**: Ephemeral session keys MUST be derived using **X25519** Diffie-Hellman key exchange.
* **SEC-003 (Anti-Replay Protection)**: Receivers MUST enforce a sliding window verification of monotonically increasing sequence counters ($S_n$), dropping duplicate or delayed packets.
* **SEC-004 (Key Storage)**: Client cryptographic keys on Android MUST be protected using the hardware-backed **Android KeyStore Provider**.
* **SEC-005 (Least Privilege)**: Host daemons MUST execute under standard unprivileged user accounts.
