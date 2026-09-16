# Terminal Mirror

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Rust](https://img.shields.io/badge/rust-1.97%2B-orange.svg)](https://www.rust-lang.org)
[![Android](https://img.shields.io/badge/Android-14%2B-brightgreen.svg)](https://developer.android.com)

> **Real-time, End-to-End Encrypted Terminal Mirroring across macOS, Windows, and Android via Private Relay.**

---

## 🚀 Overview

**Terminal Mirror** is an ultra-low-latency terminal streaming and multiplexing platform. It empowers developers to monitor and interact with active CLI sessions running on multiple workstations (macOS and Windows) directly from an Android mobile device in real time.

Built from the ground up for high performance, modularity, and zero-knowledge data privacy:
- **Cross-Platform Host Daemons**: Native Unix PTY (`/dev/ptmx`) on macOS and ConPTY on Windows via Rust's `portable-pty`.
- **Zero-Knowledge VPS Relay**: The relay router is blind to terminal data; payloads are end-to-end encrypted (E2EE) with ChaCha20-Poly1305.
- **ZeroTier / Private Mesh Integration**: Can be bound strictly to private network interfaces (`10.x.x.x`), eliminating public internet attack surfaces.
- **Mobile Multi-Session Tabs**: Monitor a build on Mac and a test suite on Windows side-by-side or tab-switched on your phone.
- **Safe Mobile Guard**: Default **View-Only Mode** prevents accidental keystrokes from pocket touches.

---

## 🏗️ Architecture

```
┌─────────────────────────┐          ┌─────────────────────────┐
│     macOS Workstation   │          │   Windows Workstation   │
│   (portable-pty / zsh)  │          │  (ConPTY / powershell)  │
└────────────┬────────────┘          └────────────┬────────────┘
             │ E2EE Stream                        │ E2EE Stream
             └─────────────────┬──────────────────┘
                               ▼
                ┌───────────────────────────────┐
                │     Private VPS Relay Hub     │
                │     (Rust Tokio WebSocket)    │
                └──────────────┬────────────────┘
                               │ Multiplexed Streams
                               ▼
                ┌───────────────────────────────┐
                │     Android Smartphone App    │
                │     [Mac Tab]  [Windows Tab]  │
                └───────────────────────────────┘
```

---

## 📁 Repository Structure

```
terminal-mirror/
├── apps/
│   ├── mac/              # macOS Terminal Host Agent (Rust)
│   ├── windows/          # Windows Terminal Host Agent (ConPTY Rust)
│   └── android/          # Android Terminal Mirror Client (Kotlin & Compose)
├── crates/
│   └── protocol/         # Shared wire protocol & MessagePack schemas (Rust)
├── services/
│   └── relay-server/     # High-throughput VPS Relay Hub (Rust Tokio)
├── docs/                 # Comprehensive Engineering Documentation
│   ├── BRD.md            # Business Requirements Document
│   ├── PRD.md            # Product Requirements Document
│   ├── SRS.md            # Software Requirements Specification (IEEE 830)
│   ├── PLANNING.md       # Project Planning, Milestones, Sprints & Risk Matrix
│   ├── ARCHITECTURE_DIAGRAM.md # System Architecture & Sequence Diagrams
│   ├── DATA_DICTIONARY.md # Wire Protocol Data Dictionary & Schemas
│   ├── SECURITY_DESIGN.md # Threat Model (STRIDE) & Cryptographic Architecture
│   ├── HLD.md            # High-Level Design
│   └── LLD.md            # Low-Level Design
├── docker-compose.yml    # Relay Hub deployment on VPS
├── .env.example          # Generic configuration template
└── README.md
```

---

## 🔒 Security Best Practices

Terminal sessions handle extremely sensitive data (passwords, API tokens, production keys). Terminal Mirror ensures maximum protection:
1. **End-to-End Encryption (E2EE)**: Payloads are encrypted before leaving your laptop using **ChaCha20-Poly1305**. The VPS relay forwards opaque binary blobs without access to decryption keys.
2. **Network Isolation**: Relay server can bind strictly to private overlay networks (e.g. ZeroTier/WireGuard) without opening public ports.
3. **One-Tap QR Pairing**: Generates an ephemeral cryptographic pairing token displayed directly in your terminal.
4. **Input Safety Lock**: Mobile app defaults to **View-Only**, requiring explicit unlock before accepting touch keyboard input.

---

## ⚡ Quick Start

### 1. Build and Run Protocol & Host Agent (macOS / Windows)
```bash
# Verify and run tests across workspace
cargo test

# Run macOS Host Agent
cargo run -p terminal-mirror-mac

# Run Windows Host Agent (on Windows)
cargo run -p terminal-mirror-windows
```

### 2. Run Relay Server (VPS / Docker)
```bash
docker compose up -d --build
```

---

## 📄 Documentation
For detailed engineering specifications, refer to the [`/docs`](docs/) directory:
- [Software Requirements Specification (SRS)](docs/SRS.md)
- [Product Requirements Document (PRD)](docs/PRD.md)
- [System Architecture & Diagrams](docs/ARCHITECTURE_DIAGRAM.md)
- [Security Architecture & Threat Model](docs/SECURITY_DESIGN.md)
- [Data Dictionary & Protocol Schemas](docs/DATA_DICTIONARY.md)
- [Project Planning & Milestones](docs/PLANNING.md)

---

## 📜 License
MIT License. See [LICENSE](LICENSE) for details.
