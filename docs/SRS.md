# Software Requirements Specification (SRS)
## Standard: IEEE 830-1998 Format
### Project: Terminal Mirror (Hardened Open-Source Edition)

---

### 1. Introduction
This specification defines the functional, non-functional, interface, and security requirements for the **Terminal Mirror Platform**, incorporating hardened security controls, decoupled asynchronous PTY stream processing, and resilient mobile foreground operation.

---

### 2. Functional Requirements (FR)

#### 2.1 Host Daemon Subsystem
* **FR-001**: The Host Daemon SHALL spawn the user's default shell in a Pseudo-Terminal (PTY) using `portable-pty`.
* **FR-002**: The Host Daemon SHALL continuously stream raw ANSI output delta chunks wrapped in encrypted envelopes.
* **FR-003**: The Host Daemon SHALL write validated `TerminalInput` keystrokes into the PTY master stream.
* **FR-004**: **Virtual Screen Grid Engine**: The Host Daemon SHALL maintain an in-memory VT100 terminal emulator parser (via `vt100`) tracking character cells, cursor coordinates, and alternate screen buffers.
* **FR-005**: **Reconnection Snapshot**: The Host Daemon SHALL emit a `PacketPayload::ScreenStateSync` snapshot upon client reconnection to prevent garbled text.
* **FR-006**: **ConPTY Debounced Resizing**: The Host Daemon SHALL debounce incoming `TerminalResize` requests on Windows with a 200ms settling window.
* **FR-007**: **Dual-Role RBAC**: The Host Daemon SHALL enforce `SessionRole::Admin` (full interactive write) vs `SessionRole::Spectator` (read-only stream).
* **FR-008**: **High-Entropy Pairing**: The Host Daemon SHALL support visual ASCII QR Codes and **4-Word Diceware Passphrases** (~51.7 bits entropy).
* **FR-009**: **3-Strike Auto-Burn Guard**: The Host Daemon SHALL track failed pairing attempts; upon the 3rd consecutive incorrect attempt, the daemon SHALL permanently incinerate the pairing session.
* **FR-010**: **Decoupled Asynchronous Streaming**: The Host Daemon SHALL decouple PTY reads from network output using bounded MPSC channels (`mpsc::channel(1024)`), preventing PTY freeze during high-volume bursts (`cat bigfile.log`).
* **FR-011**: **Streaming UTF-8 Chunker**: The Host Daemon SHALL buffer trailing incomplete multibyte UTF-8 sequences (1-3 bytes) across chunk boundaries, ensuring emoji and glyph integrity.
* **FR-012**: **Emergency Kill Switch**: The Host Daemon SHALL intercept `Ctrl + Shift + Q` to instantly revoke all remote viewer sessions.

#### 2.2 Relay Hub Subsystem
* **FR-013**: The Relay Hub SHALL provide an asynchronous WebSocket endpoint (`/ws`) routing binary MessagePack frames.
* **FR-014**: **Zero-Knowledge Blind Routing**: The Relay Hub SHALL route frames solely via outer headers (`session_id`, `trace_id`), possessing no keys to decrypt payloads.
* **FR-015**: **Anti-Abuse Rate Limiter**: The Relay Hub SHALL enforce an IP-based rate limit of maximum 60 new connections per minute per IP address, rejecting excess connections with HTTP `429 Too Many Requests`.
* **FR-016**: **Maximum Frame Capping**: The Relay Hub SHALL reject and drop any binary frame exceeding 65,536 bytes (64 KB).
* **FR-017**: The Relay Hub SHALL emit periodic heartbeats every 15 seconds, dropping dead connections after 45 seconds of inactivity.

#### 2.3 Android Client Subsystem
* **FR-018**: The Android Client SHALL render terminal escape sequences via Termux `terminal-view` or native Canvas.
* **FR-019**: **Persistent Known Hosts**: The Android Client SHALL store paired host descriptors in the hardware-backed **Android KeyStore**.
* **FR-020**: **Raw Key Input (`TYPE_NULL`)**: The Android Client SHALL bypass Gboard/IME autocomplete and predictive composition to prevent duplicate character bugs.
* **FR-021**: **Accessory Keyboard Bar**: The Android Client SHALL display floating hardware terminal keys (`Esc`, `Tab`, `Ctrl`, `Alt`, Cursor Arrows).
* **FR-022**: **Foreground Service & WakeLock**: The Android Client SHALL execute as an Android **Foreground Service** (`TerminalMirrorService`) with a persistent status bar notification and partial `WakeLock`, preventing Android Doze Mode and aggressive OEM battery killers from silently dropping active terminal sessions.
* **FR-023**: **View-Only Default Guard**: The Android Client SHALL default to **View-Only Mode**, ignoring screen touch input until explicitly toggled off.

---

### 3. Non-Functional Requirements (NFR)
* **NFR-001 (Latency)**: Keystroke echo roundtrip latency SHALL remain under **50 ms** on LAN/Wi-Fi and under **100 ms** on 4G/5G mobile networks.
* **NFR-002 (Doze Immunity)**: The Android app SHALL maintain continuous stream connectivity for a minimum of 60 minutes with screen locked in user pocket.
* **NFR-003 (Memory & CPU)**: Host daemon RSS SHALL NOT exceed **25 MB**; CPU consumption under continuous 1 MB/s PTY output SHALL NOT exceed **2%** of a CPU core.
* **NFR-004 (UTF-8 Integrity)**: 0% UTF-8 decode errors or visual replacement glyphs (``) across chunk boundaries.
