# Terminal Mirror Android App

Mobile client for real-time terminal mirroring, supporting parallel sessions across macOS and Windows hosts.

## Tech Stack
- **Language**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose (Material3)
- **Terminal View**: Termux `terminal-view` & `terminal-emulator` engine
- **Networking**: OkHttp WebSocket with MessagePack binary framing
- **Security**: Local KeyStore pairing storage, E2EE ChaCha20-Poly1305 decryption, and Read-Only Safety Lock.

## Architecture Highlights
- Multi-session Tab Bar switching instantly between Mac and Windows terminal buffers.
- Accessory Virtual Keyboard Bar providing hardware terminal keys (`Esc`, `Tab`, `Ctrl`, `Alt`, Navigation arrows).
- View-Only default guard preventing accidental keystrokes while mobile.
