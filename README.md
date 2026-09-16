# Terminal Mirror

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Rust](https://img.shields.io/badge/rust-1.97%2B-orange.svg)](https://www.rust-lang.org)
[![Android](https://img.shields.io/badge/Android-14%2B-brightgreen.svg)](https://developer.android.com)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

> **The Open-Source, End-to-End Encrypted Terminal Streaming & Multiplexing Platform.**  
> *Seamlessly mirror and interact with terminal sessions from macOS & Windows workstations on Android with sub-50ms latency.*

---

## 🚀 Why Terminal Mirror?

Most remote terminal tools either force you to open insecure firewall ports for inbound SSH, suffer from high-latency graphical screen streaming (VNC/RDP), or route unencrypted plaintext through public servers (like public `tmate`).

**Terminal Mirror** is built like **RustDesk for Terminals**:
* 🔒 **Zero-Knowledge E2EE**: Terminal streams are encrypted client-to-client using **ChaCha20-Poly1305** and **X25519**. The relay server (community or private) is mathematically blind and can never read your keystrokes, passwords, or output.
* 🖥️ **Virtual Screen Grid Engine**: Integrates an in-memory `vt100` state grid. Reconnecting after cellular network drops restores a crystal-clear screen without garbled text or corrupted `nvim`/`htop` buffers.
* 🤝 **Zero-Friction Pairing ("Known Hosts")**: Pair once via QR code or 6-digit short PIN. Subsequent daily reconnections are instant 1-tap connects saved in your Android KeyStore.
* 👥 **Dual-Role Collaboration**: Share your session as **Interactive Admin** (for personal control) or **Read-Only Spectator** (for pair programming and demonstrations).
* 🛡️ **Fail-Safe View-Only Guard**: Default read-only lock on mobile prevents accidental keystrokes from pocket touches.
* ⚡ **Host Emergency Kill Switch**: Press `Ctrl + Shift + Q` on your laptop to revoke all remote viewers instantly.

---

## 🏗️ Architecture

```
┌─────────────────────────┐          ┌─────────────────────────┐
│     macOS Workstation   │          │   Windows Workstation   │
│ (Unix PTY + vt100 Grid) │          │ (ConPTY + Debounced)    │
└────────────┬────────────┘          └────────────┬────────────┘
             │ E2EE Stream                        │ E2EE Stream
             └─────────────────┬──────────────────┘
                               ▼
                ┌───────────────────────────────┐
                │   Zero-Knowledge Relay Hub    │
                │  (Public Community or Private)│
                └──────────────┬────────────────┘
                               │ Multiplexed Streams
                               ▼
                ┌───────────────────────────────┐
                │     Android Smartphone App    │
                │  [Mac Tab]       [Windows Tab]│
                │  [Known Hosts]   [Raw Key IME]│
                └───────────────────────────────┘
```

---

## 📁 Repository Structure

```
terminal-mirror/
├── apps/
│   ├── mac/              # macOS Terminal Host Agent (Rust + POSIX PTY)
│   ├── windows/          # Windows Terminal Host Agent (Rust + ConPTY Debouncer)
│   └── android/          # Android Terminal Mirror Client (Kotlin, Compose, KeyStore)
├── crates/
│   └── protocol/         # Shared wire protocol & MessagePack schemas (Rust)
├── services/
│   └── relay-server/     # High-throughput Relay Hub (Rust Tokio)
├── docs/                 # Complete Engineering Documentation Suite
│   ├── BRD.md            # Business Requirements Document (Open-Source Edition)
│   ├── PRD.md            # Product Requirements Document & User Personas
│   ├── SRS.md            # Software Requirements Specification (IEEE 830)
│   ├── PLANNING.md       # Open-Source Roadmap, Sprints & Risk Matrix
│   ├── ARCHITECTURE_DIAGRAM.md # System Architecture & Sequence Diagrams
│   ├── DATA_DICTIONARY.md # Wire Protocol Data Dictionary & Schemas
│   ├── SECURITY_DESIGN.md # Zero-Trust Security Architecture & Threat Model
│   ├── HLD.md            # High-Level Design
│   └── LLD.md            # Low-Level Design
├── CONTRIBUTING.md        # Open-source contributor guidelines
├── LICENSE                # MIT License
├── docker-compose.yml    # Self-hosted Relay Hub orchestration
└── .env.example          # Generic environment template
```

---

## ⚡ Quick Start

### 1. Build and Run Host Agent
```bash
# Run tests across workspace
cargo test

# Launch Host Agent on macOS
cargo run -p terminal-mirror-mac

# Launch Host Agent on Windows (PowerShell / ConPTY)
cargo run -p terminal-mirror-windows
```

### 2. Self-Host Private Relay Hub (Optional)
```bash
docker compose up -d --build
```

---

## 📄 Engineering Documentation
Explore the [`/docs`](docs/) directory for full specifications:
- [Business Requirements Document (BRD)](docs/BRD.md)
- [Product Requirements Document (PRD)](docs/PRD.md)
- [Software Requirements Specification (SRS)](docs/SRS.md)
- [System Architecture & Sequence Diagrams](docs/ARCHITECTURE_DIAGRAM.md)
- [Security Architecture & Threat Model](docs/SECURITY_DESIGN.md)
- [Protocol Data Dictionary](docs/DATA_DICTIONARY.md)

---

## 🤝 Contributing
Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) to get started.

---

## 📜 License
Distributed under the MIT License. See [LICENSE](LICENSE) for more information.
