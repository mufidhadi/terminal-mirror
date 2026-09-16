# Product Requirements Document (PRD)
## Project: Terminal Mirror

### 1. Product Vision & Overview
Terminal Mirror is an ultra-fast, secure, developer-first terminal streaming tool. It replicates active CLI sessions from any developer workstation (macOS and Windows) to an Android smartphone with native mobile terminal emulation, zero cloud vendor lock-in, and military-grade end-to-end encryption.

---

### 2. Target Personas
* **Persona: Mufid (Senior Full-Stack & Infrastructure Engineer)**
  * *Pain Point*: Starts heavy Docker builds, data migrations, or AI model training on MacBook Pro and Windows ThinkPad; needs to step away from his desk without missing logs or interrupting progress.
  * *Needs*: Quick glance on phone, parallel tabs for Mac and Windows, assurance that passwords typed on Mac are not visible to anyone on the VPS, and protection against accidental touch screen inputs.

---

### 3. User Journeys

#### Journey 1: Host Initialization & Pairing
1. User opens terminal on Mac: runs `terminal-mirror host --name "MacBook Pro"`.
2. Host spawns a new shell inside a PTY, generates an ephemeral cryptographic keypair, connects out-of-band to the Relay Hub, and renders an ASCII QR Code in the terminal.
3. User opens the Android App, taps "Scan Host QR", points camera at laptop screen.
4. App parses pairing envelope, performs mutual cryptographic handshake, and saves host descriptor.
5. Terminal Mirror screen opens instantly with active shell output.

#### Journey 2: Dual Parallel Monitoring (Mac + Windows)
1. User repeats the host command on Windows ThinkPad.
2. Android App displays a notification: "New session detected: Windows ThinkPad".
3. A new tab appears in the Android top bar: `[MacBook Pro (zsh)]` and `[ThinkPad (pwsh)]`.
4. User can switch between tabs instantly or view both side-by-side in landscape split mode.
5. Output streams continuously in the background without dropping buffer frames.

#### Journey 3: Safe Interactive Input
1. By default, the session is in **Read-Only / Lock Mode** (green lock icon). Tapping on the screen scrolls the buffer but sends no keystrokes.
2. User taps the lock icon to enter **Interactive Mode** (amber unlock icon).
3. The accessory terminal keyboard bar slides up containing `ESC`, `TAB`, `CTRL`, `ALT`, and Arrow Keys.
4. User taps `CTRL` + `C` to terminate a runaway build script on the Mac laptop.
5. User locks input again to prevent accidental pocket typing.

---

### 4. Detailed Feature Breakdown

| Feature ID | Feature Name | Priority | User Story | Technical Implementation |
| :--- | :--- | :--- | :--- | :--- |
| **FEAT-01** | Cross-Platform PTY Daemon | P0 | As a developer, I want to mirror my interactive terminal on Mac and Windows without changing my shell. | Rust binary leveraging `portable-pty` for Unix POSIX PTY and Windows ConPTY. |
| **FEAT-02** | Zero-Knowledge Relay Hub | P0 | As a security-conscious engineer, I want the relay server to be blind to my terminal data. | Tokio async relay forwarding opaque encrypted binary MessagePack packets. |
| **FEAT-03** | Parallel Multi-Session Tabs | P0 | As a developer, I want to monitor both my Mac and Windows workstations concurrently on my phone. | Jetpack Compose / Flutter multi-tab container with independent terminal instances. |
| **FEAT-04** | QR Code Instant Pairing | P1 | As a mobile user, I want to connect my phone without typing IP addresses or complex tokens. | `qrcode` crate on host CLI; ZXing/CameraX scanner on Android. |
| **FEAT-05** | View-Only Safety Lock | P0 | As a mobile user, I want to prevent accidental touches from executing commands on my computer. | Client-side input filter intercepting touch and soft keyboard events. |
| **FEAT-06** | Scrollback Ring Buffer | P1 | As a mobile user, I want to see the last 1,000 lines of terminal history when reconnecting after signal loss. | Host-side FIFO ring buffer caching raw ANSI byte streams. |
| **FEAT-07** | Accessory Terminal Keyboard | P1 | As a mobile user, I need essential keys (`Ctrl`, `Alt`, `Esc`, `Tab`, arrows) missing on phone keyboards. | Floating sticky toolbar on top of standard Android IME. |
| **FEAT-08** | Dynamic Terminal Resizing | P2 | As a mobile user, I want the host terminal to adapt when I rotate my phone to landscape. | Bidirectional `TerminalResize` packet with `PtySize` updates on host. |

---

### 5. Success Metrics & Key Performance Indicators (KPIs)
* **Keystroke Echo Latency**: < 50 ms over Wi-Fi, < 100 ms over 4G/5G cellular.
* **Stream Bandwidth**: < 20 KB/sec during continuous scroll output (via MessagePack binary framing).
* **Connection Re-establishment**: < 1.5 seconds when transitioning between Wi-Fi and Cellular.
* **Crash-Free Sessions**: 99.9% uptime for the host daemon and relay hub over 30-day continuous runs.
