# Business Requirements Document (BRD)
## Project: Terminal Mirror (Open-Source Universal Terminal Streaming & Multiplexing)

---

### 1. Executive Summary
Terminal Mirror is a secure, high-performance, open-source terminal streaming and multiplexing platform designed for global software developers, sysadmins, DevOps engineers, and students. It enables users to mirror and interact with active command-line sessions running on heterogeneous workstations (macOS, Windows, Linux) directly from mobile devices (Android) with sub-50ms latency, zero cloud lock-in, and uncompromising End-to-End Encryption (E2EE).

Positioned as the **"RustDesk of Terminal Sharing"**, Terminal Mirror addresses the security shortcomings of legacy tools (such as public `tmate` servers lacking E2EE) while offering a seamless out-of-the-box user experience backed by 1-click self-hosting capabilities.

---

### 2. Market Opportunity & Problem Statement

#### 2.1 The Core Problems
1. **The Inbound Connectivity Barrier**: 90%+ of developers work behind NATs, firewalls, or dynamic cellular IPs where traditional inbound SSH is impossible without risky port forwarding or complex VPN setups.
2. **The "Tmate Security Dilemma"**: Existing terminal sharing utilities (like `tmate`) decrypt terminal sessions on their public servers, exposing critical credentials (`sudo`, SSH keys, cloud API tokens) to third-party relay operators.
3. **Session Reconnection Corruption**: Naive raw-byte streaming tools corrupt full-screen TUI apps (`nvim`, `htop`, `lazygit`) when mobile devices switch networks, resulting in unreadable, garbled character grids.
4. **UX Friction & Pairing Fatigue**: Requiring users to re-scan camera QR codes or re-authenticate on every terminal restart destroys developer flow.

#### 2.2 The Open-Source Value Proposition
* **Zero-Configuration Community Relay**: Anyone can install the client (`brew install terminal-mirror` or download the APK) and immediately mirror sessions via a public, privacy-preserving rendezvous relay.
* **True Zero-Knowledge E2EE**: All terminal keystrokes, output deltas, and screen state snapshots are encrypted client-to-client using **ChaCha20-Poly1305** and **X25519**. The relay server is mathematically blind.
* **1-Click Self-Hosting (Data Sovereignty)**: Organizations, privacy purists, and homelabbers can deploy their own relay hub in seconds via Docker Compose.
* **Collaborative Modes (Pairing & Mentorship)**: Dual-token capability enables sharing terminal sessions as **Interactive Admin** or **Read-Only Spectator**.

---

### 3. Target Audiences & Use Cases

| User Segment | Typical Use Case | Primary Need |
| :--- | :--- | :--- |
| **Individual Developers** | Stepping away from desk while waiting for long Docker builds, test suites, or ML training. | Low-latency mobile glance, battery efficiency, safe view-only lock. |
| **Open-Source Maintainers & Mentors** | Live-coding sessions, debugging user issues, terminal demonstrations. | Read-Only Spectator links, instant URL/PIN sharing, clean ANSI rendering. |
| **DevOps & Sysadmins** | Triage and emergency restarts during on-call incidents from a phone. | Reliable keystroke transmission, special key access (`Ctrl`, `Esc`, `Tab`). |
| **Enterprises & Privacy Purists** | Air-gapped or strictly governed development environments. | Self-hosted relay hub, ZeroTier/Tailscale private network binding. |

---

### 4. Strategic Objectives & Success Metrics
* **Community Adoption**: 1,000+ GitHub stars within 6 months of public release; active community package maintainers for Homebrew, Winget, Arch AUR, and F-Droid.
* **Rock-Solid Reliability**: Zero report of visual terminal desynchronization (garbled screens) on network reconnect thanks to the **Virtual Screen Grid Engine**.
* **Zero Security Compromise**: 100% test coverage on cryptographic handshakes and AEAD envelopes; zero plaintext leaks verified by independent open-source audits.
* **Frictionless Onboarding**: Time-to-first-stream < 15 seconds from installation.

---

### 5. Open-Source Governance & Sustainability Model
* **License**: **MIT License** (permitting maximum developer freedom, enterprise adoption, and community contributions).
* **Funding & Infrastructure**: Free public community relay funded via GitHub Sponsors, Open Collective, and community infrastructure grants.
* **Community Contributions**: Transparent RFC process for protocol changes, automated CI testing, and strict contributor guidelines.
