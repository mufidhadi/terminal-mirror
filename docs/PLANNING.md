# Engineering Execution, Roadmap & Project Progress
## Project: Terminal Mirror (Real-Time Zero-Knowledge Terminal Streaming)

**Repository**: [github.com/mufidhadi/terminal-mirror](https://github.com/mufidhadi/terminal-mirror)  
**Primary Architect**: mas mufid  
**Latest Verified Commit**: `3734f21`  
**Active Working Branch**: `feature/android-e2ee-pairing-and-ci`  

---

### 1. Sprint Completion Matrix

| Sprint / Workstream | Target Scope | Status | Verification & Proof |
|---|---|:---:|---|
| **Sprint 1: Core Protocol & Workspace Scaffolding** | Multi-crate Rust workspace, MessagePack wire protocol (`Packet`, `ScreenSnapshot`), UTF-8 stream chunker. | **100% DONE** | 11 unit & integration tests pass (`cargo test -p terminal-mirror-protocol`). |
| **Sprint 2: Server Relay Hub & Production Deployment** | Axum/Tokio WebSocket blind router, IP rate limiter (60/min), Prometheus metrics, ZeroTier isolation, Docker containerization on Hostinger VPS (`172.23.127.184:8888`). | **100% DONE** | Live VPS container healthy, HTTP 200 on `/healthz`, telemetry on `/metrics`, rate-limiting burst block verified. |
| **Sprint 3: Zero-Knowledge E2EE & Pairing Engine** | ChaCha20-Poly1305 AEAD, deterministic 96-bit sequence nonces, 4-word Diceware generator (~51.7 bits), 3-strike `PairingGuard`, Terminal QR code generator (`Dense1x2`), cross-platform SHA-256 KDF. | **100% DONE** | Cross-platform roundtrip tested across Rust, Kotlin, and Python. Zero relay access to plaintext. |
| **Sprint 4: Workstation Host Agents (macOS & Windows)** | Darwin PTY (`/bin/zsh -l`) with E2EE integration; Windows ConPTY with 200ms resize debouncer, PowerShell bypass execution policies, startup banners. | **90% DONE** | macOS agent fully integrated and live verified over VPS; Windows agent unit tests passing (8 tests). |
| **Sprint 5: Android Mobile Client & Pairing UX** | Jetpack Compose UI (workstation tabs, status header, programmer keyboard accessory bar), CameraX + ML Kit QR scanner, native Android ChaCha20-Poly1305 `E2eeManager`, Foreground Service. | **75% DONE** | CameraX & ML Kit scanner implemented; E2EE crypto matched with Rust; GitHub Actions APK CI configured. |
| **Sprint 6: Stress-Testing, Optimization & Production Launch** | Zstandard (`zstd`) stream compression, 100 MB/s PTY backpressure stress test (`cat 1GB.log`), Termux engine surface binding, physical Motorola battery profiling. | **25% DONE** | Basic coalescer and debouncer active; full soak test pending. |

---

### 2. Detailed Task Breakdown: Completed vs. Pending

#### A. Tasks Completed (DONE)
1. **Shared Wire Protocol & Crypto (`crates/protocol`)**:
   - [x] Protocol envelope `Packet` with MessagePack serialization.
   - [x] UTF-8 stream boundary protection (`Utf8StreamChunker`).
   - [x] Zero-Knowledge ChaCha20-Poly1305 AEAD cipher (`E2eeCipher`).
   - [x] Deterministic 12-byte nonce derivation (`derive_nonce(seq)`).
   - [x] Passphrase & Secret KDF (`E2eeCipher::from_secret` via SHA-256).
   - [x] 4-word curated Indonesian Diceware passphrase generator (`DicewarePassphrase`).
   - [x] Brute-force pairing guard with 3-strike auto-burn (`PairingGuard`).
   - [x] Visual grid snapshot data model (`ScreenSnapshot`).

2. **Relay Server Hub (`services/relay-server`)**:
   - [x] Zero-knowledge blind router with Axum WebSocket + Tokio `broadcast::channel(1024)`.
   - [x] Middleware IP Rate Limiter (`IpRateLimiter`: 60 conn/min per IP, burst rejection).
   - [x] Prometheus telemetry metrics endpoint (`/metrics`) and health check (`/healthz`).
   - [x] Idle session reaper (5-minute inactivity cleanup).
   - [x] Graceful shutdown handling (`SIGTERM` + `SIGINT`).
   - [x] Hardened Docker Compose setup with CPU/Memory limits (`0.50 CPU, 256M RAM`).

3. **VPS Hostinger Deployment (`172.23.127.184:8888`)**:
   - [x] Deployed live on Hostinger VPS in container `terminal-mirror-relay`.
   - [x] Bound exclusively to ZeroTier IP `172.23.127.184:8888` (preventing Traefik port 8080 conflict).
   - [x] Live ZeroTier verification: 200 OK on `/healthz`, active gauges on `/metrics`.

4. **macOS Host Agent (`apps/mac`)**:
   - [x] Darwin PTY session spawner (`/bin/zsh -l`) via `portable-pty`.
   - [x] Resilient auto-reconnecting WebSocket client with exponential backoff (`RelayHostClient`).
   - [x] Ambient startup banner with ASCII art, Diceware passphrase, and Unicode QR code.
   - [x] Zero-Knowledge ChaCha20-Poly1305 encryption integrated directly into the live PTY stream.
   - [x] CLI flags for `--no-e2ee`, `--session-id`, and `--passphrase`.

5. **Windows Host Agent (`apps/windows`)**:
   - [x] ConPTY session manager using `portable-pty`.
   - [x] Intelligent shell resolver (PowerShell with `-NoLogo -ExecutionPolicy Bypass` vs. CMD).
   - [x] 200ms sliding window resize debouncer preventing redraw storms (`ResizeDebouncer`).
   - [x] Stream coalescer for downstream backpressure protection.

6. **Android Client (`apps/android`)**:
   - [x] Jetpack Compose layout (workstation tabs, status header, programmer accessory keyboard bar).
   - [x] CameraX + Google ML Kit Barcode Scanning modal dialog (`QrScannerDialog.kt`).
   - [x] Native Android ChaCha20-Poly1305 encryption engine (`E2eeManager.kt`).
   - [x] Foreground Service with partial WakeLock for Doze Mode survival (`TerminalMirrorService.kt`).
   - [x] Hardware KeyStore manager (`KeystoreManager.kt`).

7. **CI/CD Automation Pipelines (`.github/workflows/`)**:
   - [x] Android CI (`android-ci.yml`): Automated debug APK build with Gradle 8.7, JDK 17, Android SDK 34, and artifact upload.
   - [x] Rust CI (`rust-ci.yml`): Multiplatform matrix test (Linux, macOS, Windows).

8. **Automated Verification Suites**:
   - [x] 33 Rust tests passing 100% (`cargo test --workspace`).
   - [x] 5 Python live VPS integration tests passing (`uv run pytest`), validating real Darwin `/bin/zsh` execution over ZeroTier.

---

#### B. Tasks Pending / In-Progress (TODO)

| Priority | Task Name | Component | Objective |
|:---:|---|---|---|
| **P0** | **Termux Engine SurfaceView Binding** | `apps/android` | Replace placeholder text preview with live Termux `TerminalView` surface, receiving raw ANSI stream from `ConnectionManager` and forwarding touch/keyboard input. |
| **P1** | **Offline SQLite / Room Device Storage** | `apps/android` | Persist paired workstations (`TrustedDevice`) locally with encrypted credentials to eliminate QR scan fatigue on subsequent app launches. |
| **P1** | **Adaptive Zstandard (`zstd`) Compression** | `crates/protocol` & `apps/mac` | Enable automatic Zstd level 1 compression on chunks > 512 bytes to reduce bandwidth by 70–85% on verbose CLI commands (`gcc`, `cargo build`). |
| **P2** | **Windows Agent Startup QR & E2EE Parity** | `apps/windows` | Attach `E2eeCipher` and Unicode QR terminal banner to the Windows agent binary, matching `apps/mac`. |
| **P2** | **Backpressure Stress-Test ("The `cat 1GB.log` Test")** | `tests/` & `apps/mac` | High-throughput soak test simulating 100 MB/s output to verify daemon RSS stays < 25 MB and intermediate frames drop cleanly to `ScreenStateSync`. |
| **P3** | **Mobile Thermal & Battery Profiling** | `apps/android` | Run 60-minute continuous streaming session on physical Motorola device (`172.23.191.143`) to measure battery drain (< 4%/hour target). |
