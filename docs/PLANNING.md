# Engineering Execution & Project Planning Document
## Project: Terminal Mirror (Cross-Platform Multi-Session Terminal Streaming)

---

### 1. Project Roadmap & Milestones

The project is structured into 4 sequential, test-driven engineering sprints:

```
[Sprint 1: Core Protocol & Workspace Scaffolding]
       │
       ▼
[Sprint 2: Relay Hub & Host Daemons (Mac & Win)]
       │
       ▼
[Sprint 3: Security, E2EE & QR Pairing Integration]
       │
       ▼
[Sprint 4: Android App & End-to-End Field Validation]
```

---

### 2. Detailed Sprint Breakdown

#### Sprint 1: Architecture, Protocol & Monorepo Scaffolding (Completed in this phase)
* **Goal**: Establish the repository, workspace dependencies, binary serialization schemas, and core specifications.
* **Deliverables**:
  1. Root Cargo workspace configuration with member crates (`crates/protocol`, `services/relay-server`, `apps/mac`, `apps/windows`).
  2. Public GitHub repository setup with strict security `.gitignore` and sanitized `.env.example`.
  3. `crates/protocol` implementation: MessagePack binary framing, `Packet`, `PacketPayload`, `SessionDescriptor`.
  4. Unit test suite for protocol serialization and validation (passing 100%).
  5. Complete documentation suite (BRD, PRD, SRS, Planning, Data Dictionary, Architecture Diagram, Security Design, HLD, LLD).

#### Sprint 2: Relay Hub & Cross-Platform Host Daemons
* **Goal**: Implement high-performance async streaming between Host Daemons and Relay Hub.
* **Tasks**:
  * **Relay-01**: Implement Tokio WebSocket broadcast channels with session multiplexing (`DashMap<SessionId, broadcast::Sender>`).
  * **Relay-02**: Add authentication token interceptor and heartbeat keeper.
  * **Host-Mac-01**: Connect `portable-pty` on macOS to async Tokio stream readers/writers.
  * **Host-Win-01**: Connect Windows ConPTY to async Tokio stream readers/writers.
  * **Host-Core-02**: Implement 1 MB local FIFO ring buffer for scrollback replay.
* **Exit Criteria**: Mac and Windows hosts can both connect to Relay Server and stream live shell output to a CLI test client.

#### Sprint 3: End-to-End Cryptography (E2EE) & QR Pairing
* **Goal**: Guarantee zero-knowledge relaying and seamless out-of-band mobile pairing.
* **Tasks**:
  * **Crypto-01**: Integrate Noise Protocol (`snow` crate) or ChaCha20-Poly1305 symmetric envelope encryption.
  * **Crypto-02**: Implement terminal ASCII QR code generation on host startup using `qrcode` crate.
  * **Host-UI-01**: Add terminal status banner showing active viewers and connection state.
  * **Audit-01**: Verify that relay server memory dumps contain only ciphertext and zero plaintext shell commands.
* **Exit Criteria**: Complete E2EE handshake passes automated unit and integration tests.

#### Sprint 4: Android Mobile Client & Multi-Session UI
* **Goal**: Build and test the native Android mobile app with multi-session tabs and terminal keyboard.
* **Tasks**:
  * **Droid-01**: Build Jetpack Compose UI with TabRow supporting simultaneous Mac and Windows connections.
  * **Droid-02**: Integrate Termux `terminal-view` or Flutter `xterm3` widget.
  * **Droid-03**: Implement View-Only input lock and accessory terminal keyboard bar.
  * **Droid-04**: Implement CameraX QR Code scanner for one-tap host pairing.
  * **Droid-05**: Field testing across Wi-Fi, 4G, and 5G connections measuring latency and reconnection behavior.
* **Exit Criteria**: Android client streams Mac and Windows terminals in parallel with < 100ms latency.

---

### 3. Work Breakdown Structure (WBS) Matrix

| Module | Component | Estimated Effort (Story Points) | Dependencies | Target Branch |
| :--- | :--- | :--- | :--- | :--- |
| **Protocol** | `crates/protocol` | 3 SP | None | `feature/protocol-core` |
| **Relay** | `services/relay-server` | 5 SP | Protocol | `feature/relay-hub` |
| **Mac Host** | `apps/mac` | 5 SP | Protocol, Relay | `feature/mac-agent` |
| **Win Host** | `apps/windows` | 5 SP | Protocol, Relay | `feature/win-agent` |
| **Android** | `apps/android` | 8 SP | Protocol, Relay | `feature/android-app` |
| **Docs/QA** | `docs/*` | 4 SP | All | `feature/qa-and-docs` |

---

### 4. Risk Register & Mitigation Strategy

| Risk ID | Description | Severity | Probability | Mitigation Strategy |
| :--- | :--- | :--- | :--- | :--- |
| **RISK-01** | Mobile terminal resize corrupts desktop editor layout (e.g. Vim). | High | High | Support Virtual Canvas mode where host preserves 80x24 layout and mobile client provides pan/zoom, with opt-in responsive resize. |
| **RISK-02** | Cellular network transitions drop WebSocket connections. | Medium | High | Implement automatic exponential backoff reconnection with instant ring-buffer state synchronization. |
| **RISK-03** | Windows ConPTY compatibility issues across Windows builds. | Medium | Low | Use `portable-pty` which handles ConPTY feature detection and falls back to Win32 Console APIs if running on legacy versions. |
| **RISK-04** | Accidentally typing commands into mobile screen in pocket. | Critical | High | Hardcoded default **View-Only Mode**; inputs are discarded unless the user explicitly toggles the physical lock button. |
| **RISK-05** | Sensitive credentials leaked to relay server. | Critical | Medium | Zero-Knowledge E2EE payload encryption ensures relay sees only encrypted blobs. ZeroTier private binding adds network-level isolation. |

---

### 5. Definition of Done (DoD)
A task or user story is considered **Done** only when:
1. Written strictly adhering to Test-Driven Development (TDD) principles.
2. 100% of unit tests pass locally (`cargo test` for Rust, Gradle test for Android).
3. Zero warnings on compiler (`cargo clippy` or linting).
4. Code passes SOLID principles review and modular architecture standards.
5. All relevant documentation updated in `/docs`.
6. AI completion report generated in `docs/ai_report/xxx_<task_name>.md`.
7. Committed cleanly to feature branch and pushed to remote without co-authoring attributions.
