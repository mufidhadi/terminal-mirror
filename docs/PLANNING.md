# Engineering Execution & Project Planning Document
## Project: Terminal Mirror (Open-Source Edition)

---

### 1. Release Roadmaps & Community Milestones

```
[Sprint 1: Core Protocol & Workspace Scaffolding] (COMPLETED)
       │
       ▼
[Sprint 2: Host Daemons + vt100 Virtual Grid & ConPTY Debouncer]
       │
       ▼
[Sprint 3: Zero-Knowledge E2EE & Persistent PIN/QR Pairing]
       │
       ▼
[Sprint 4: Android App + Known Hosts UI + Raw Key IME Engine]
       │
       ▼
[Sprint 5: Open-Source Release, Packaging & Public Relay Hub]
```

---

### 2. Sprint Breakdown

#### Sprint 1: Protocol, Specs & Architecture (COMPLETED)
* Monorepo layout with Rust workspace and Android submodule.
* MessagePack wire protocol with `ScreenSnapshot`, `SessionRole`, `PairWithPin`.
* 100% passing unit tests on protocol serialization.
* Full documentation suite in `/docs`.

#### Sprint 2: Resilient Host Daemons (`apps/mac` & `apps/windows`)
* **Task 2.1**: Integrate `vt100` parser crate into Host Daemon to track visual screen grid in real time.
* **Task 2.2**: Implement `ScreenStateSync` generation upon client reconnect signal.
* **Task 2.3**: Build ConPTY Resize Debouncer on Windows with 200 ms settling window and `PASSTHROUGH_MODE`.
* **Task 2.4**: Implement physical keyboard emergency revocation hook (`Ctrl+Shift+Q`).

#### Sprint 3: Zero-Knowledge E2EE & Universal Pairing
* **Task 3.1**: Implement ChaCha20-Poly1305 AEAD cipher engine on Host and Client.
* **Task 3.2**: Add dual pairing generators: ASCII QR Code on terminal and 6-digit PIN generator.
* **Task 3.3**: Implement `authorized_devices.toml` persistent storage on host.
* **Task 3.4**: Deploy public community rendezvous relay on VPS with Docker Compose.

#### Sprint 4: Android App & Native UX
* **Task 4.1**: Jetpack Compose multi-session tab manager with native `termux-view` rendering.
* **Task 4.2**: Hardware-backed **Android KeyStore** integration for persistent "Known Hosts" 1-tap connect.
* **Task 4.3**: Override `InputConnection` with `TYPE_NULL` to eliminate Gboard autocomplete bugs.
* **Task 4.4**: Floating accessory terminal keyboard (`Esc`, `Tab`, `Ctrl`, `Alt`, navigation arrows).

#### Sprint 5: Open-Source Packaging & Community Launch
* **Task 5.1**: Homebrew formula (`brew install mufidhadi/tap/terminal-mirror`).
* **Task 5.2**: Windows Winget package manifest.
* **Task 5.3**: Android APK release on GitHub Releases & F-Droid repository submission.
* **Task 5.4**: Public launch on Hacker News (Show HN), Reddit (`r/rust`, `r/commandline`), and Product Hunt.

---

### 3. Open-Source Quality & Security Standards
* **TDD Requirement**: Every new protocol message or parser routine must include automated tests in `crates/protocol/tests/`.
* **Zero Secret Leakage**: Continuous GitHub Actions check verifying no private IP addresses or tokens exist in commits.
* **Code Formatting**: Strict enforcement of `cargo fmt --check` and `cargo clippy -- -D warnings`.
