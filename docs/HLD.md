# High-Level Design (HLD)
## Project: Terminal Mirror

---

### 1. System Objective
The Terminal Mirror system provides low-latency, resilient, cross-platform streaming of interactive terminal sessions from macOS and Windows developer workstations to an Android mobile device, using an asynchronous relay server on a private cloud VPS.

---

### 2. Architectural Principles
1. **Separation of Concerns (SoC)**:
   * **Host Agents**: Responsible purely for PTY management, raw byte streaming, and local encryption.
   * **Relay Server**: Stateless, zero-knowledge message broker routing binary frames based on session IDs.
   * **Mobile Client**: UI presentation layer, ANSI decoding, terminal emulation, and user input capture.
2. **SOLID Design**:
   * Interfaces for `PtySystem`, `NetworkTransport`, and `CryptoCipher` allow swappable implementations (e.g. swapping WebSocket for QUIC, or swapping Termux for Flutter).
3. **Fail-Fast & Auto-Heal**:
   * Dead sockets are culled immediately.
   * Mobile clients seamlessly re-synchronize viewport buffer upon network reconnect.

---

### 3. Component Architecture & Responsibilities

| Subsystem | Folder Location | Language / Framework | Primary Responsibilities |
| :--- | :--- | :--- | :--- |
| **Shared Protocol** | `crates/protocol` | Rust | Common packet structures, MessagePack codec, data schemas, validation logic. |
| **macOS Host** | `apps/mac` | Rust (`tokio`, `portable-pty`) | Spawn macOS PTY (`zsh`/`bash`), stream terminal chunks, listen for remote keystrokes. |
| **Windows Host** | `apps/windows` | Rust (`tokio`, `portable-pty`) | Spawn Windows ConPTY (`powershell`/`cmd`), stream terminal chunks, handle Win32 console events. |
| **Relay Hub** | `services/relay-server` | Rust (`tokio`, `axum`, `dashmap`)| Route packets between hosts and mobile clients, manage connection state and heartbeats. |
| **Android Client**| `apps/android` | Kotlin (Jetpack Compose) | Render VT100 terminal views, handle multi-session tabs, provide accessory keyboard toolbar. |

---

### 4. Network Topology & Protocol Stack

```
[Application Layer]       MessagePack Encrypted Envelopes (TerminalOutput, TerminalInput, Resize)
                                          │
[Session Security]        E2EE (ChaCha20-Poly1305 / Noise Protocol)
                                          │
[Transport Layer]         WebSocket (WSS) over TCP with TCP_NODELAY
                                          │
[Network Overlay]         ZeroTier Virtual Private LAN (Encrypted Mesh Layer 3)
                                          │
[Physical Layer]          Public Internet (4G/5G / Wi-Fi)
```
