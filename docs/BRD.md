# Business Requirements Document (BRD)
## Project: Terminal Mirror (Cross-Platform Real-Time Terminal Streaming & Multiplexing)

### 1. Executive Summary
Terminal Mirror is a secure, high-performance, low-latency terminal mirroring and multiplexing platform designed to allow engineers to monitor and interact with interactive terminal sessions running on their workstations (macOS and Windows) directly from an Android mobile device in real time. 

The system leverages a private relay server deployed on a remote Virtual Private Server (VPS), protected by private mesh networking (e.g., ZeroTier/WireGuard) and application-level End-to-End Encryption (E2EE) to guarantee zero leakage of critical command-line data, secrets, or keystrokes.

---

### 2. Business Problem & Opportunity
Modern software engineers, DevOps specialists, and system administrators frequently manage long-running local processes, builds, model trainings, and diagnostic tasks across multiple workstation environments (macOS and Windows). 

Current mobile remote terminal solutions present substantial friction:
1. **Inbound SSH Vulnerability & Firewall Traversal**: Workstations behind residential NATs or enterprise firewalls cannot accept direct inbound SSH connections without exposing ports or relying on third-party SaaS solutions.
2. **Session Fragmentation Across Heterogeneous Hosts**: Monitoring a build on a Mac laptop and a test suite on a Windows workstation concurrently requires juggling distinct VNC/RDP sessions or disparate SSH sessions.
3. **High Latency & Heavy Bandwidth**: Graphical screen sharing (VNC, AnyDesk, TeamViewer) consumes massive data bandwidth and introduces unacceptable typing lag on mobile networks.
4. **Data Privacy Risks in Public Relays**: Commercial terminal-sharing platforms decrypt or inspect traffic at their relay points, introducing extreme risk when developers enter `sudo` passwords, AWS keys, or production database credentials.

**The Opportunity**: Build a lightweight, self-hosted, cross-platform terminal mirroring utility delivering sub-50ms latency, parallel session monitoring across Mac and Windows, and guaranteed zero-knowledge end-to-end encryption.

---

### 3. Business Goals & Objectives
* **Goal 1 (Productivity)**: Provide seamless mobile terminal access enabling developers to monitor, pause, or interact with desktop tasks untethered from their desk.
* **Goal 2 (Low Latency Performance)**: Achieve sub-50ms keystroke echo latency and sub-100ms screen stream latency over mobile cellular connections (4G/5G).
* **Goal 3 (Absolute Data Confidentiality)**: Ensure relay infrastructure operates in a "Zero-Knowledge" capacity where terminal I/O streams are mathematically undecryptable by the relay.
* **Goal 4 (Cross-Platform Parity)**: Deliver identical session streaming capabilities for Unix PTY (macOS) and Windows ConPTY environments.
* **Goal 5 (Resource Efficiency)**: Maintain minimal host footprint (< 30 MB RAM per daemon on workstations; < 50 MB on VPS relay).

---

### 4. Stakeholder Analysis
| Stakeholder | Role | Primary Interests | Key Concerns |
| :--- | :--- | :--- | :--- |
| **Lead Engineer / Developer** | Primary User | Instant terminal responsiveness, dual-host monitoring (Mac + Win), easy pairing. | Pocket typing / accidental keystroke damage, connection drops. |
| **System Security Officer** | Auditor | Cryptographic posture, zero exposure to public internet, access controls. | Credential harvesting, relay compromise, session hijacking. |
| **DevOps Operator** | Infrastructure Maintainer | Trivial VPS deployment (Docker Compose), low maintenance overhead. | CPU/RAM hogging, runaway logs, memory leaks in long-running relays. |

---

### 5. Project Scope

#### In Scope (Phase 1):
* Host Daemon for macOS capturing POSIX PTY sessions (`zsh`, `bash`).
* Host Daemon for Windows capturing ConPTY sessions (`powershell`, `cmd`).
* Central Relay Hub deployed via Docker Compose on a private VPS.
* Android client application supporting multi-tab parallel terminal viewing.
* Binary MessagePack protocol over WebSockets for optimized low-overhead streaming.
* Dual-mode security: ZeroTier private mesh IP binding + End-to-End Encryption (E2EE).
* Pairing mechanism via ephemeral terminal QR Code generation.
* Safeguard "Read-Only / View-Only" toggle in the mobile client to prevent inadvertent touch-screen input.

#### Out of Scope (Phase 1):
* Web browser client (HTML5/Canvas).
* Biometric authentication prompts prior to command execution.
* Multi-user collaborative pair programming (multi-write access to a single PTY).
* Native iOS client application.

---

### 6. Cost-Benefit Analysis & ROI
* **Infrastructure Cost**: $0 additional cloud cost by utilizing existing private VPS and ZeroTier overlay network.
* **Operational Efficiency**: Eliminates context-switching and reduces build-monitoring time by ~30 minutes/day per engineer.
* **Security ROI**: Eliminates risk of third-party SaaS data breaches by retaining 100% data sovereign control within private infrastructure.

---

### 7. Constraints & Assumptions
* **Constraint 1 (OS Support)**: Windows host requires Windows 10 (version 1809+) or Windows 11 to support the Windows Pseudo Console (ConPTY) API.
* **Constraint 2 (ZeroTier Mesh Availability)**: Devices must be enrolled in the same private virtual network or reachable via authenticated TLS relay.
* **Assumption 1**: The mobile device has intermittent internet connectivity; the host daemon must maintain a local scrollback ring buffer to resynchronize the mobile client upon reconnection.
