# Software Requirements Specification (SRS)
## Standard: IEEE 830-1998 Format
### Project: Terminal Mirror (Hardened Open-Source & Performance Edition)

---

### 1. Specific Functional Requirements (FR)

#### 1.1 Host Daemon Subsystem
* **FR-001**: Spawn default shell in PTY via `portable-pty`.
* **FR-002**: Stream ANSI output delta chunks wrapped in encrypted MessagePack envelopes.
* **FR-003**: Write validated `TerminalInput` keystrokes to PTY master writer.
* **FR-004**: Maintain in-memory VT100 virtual grid parser via `vt100`.
* **FR-005**: Emit `ScreenStateSync` snapshot on client reconnection.
* **FR-006**: Debounce Windows ConPTY resize requests with 200 ms settling window.
* **FR-007**: Enforce `SessionRole::Admin` vs `SessionRole::Spectator` RBAC permissions.
* **FR-008**: Generate ASCII QR Codes and 4-Word Diceware Passphrases (~51.7 bits entropy).
* **FR-009**: Enforce **3-Strike Auto-Burn** on failed pairing attempts.
* **FR-010**: Decouple PTY read loops via bounded `mpsc::channel(1024)` async queues.
* **FR-011**: Assemble multibyte UTF-8 characters across chunk boundaries via `Utf8StreamChunker`.
* **FR-012**: Physical keyboard emergency revocation hotkey (`Ctrl + Shift + Q`).
* **FR-027**: **Zstandard (`zstd`) Compression**: The Host Daemon SHALL compress `TerminalOutput` chunks exceeding 512 bytes using `zstd` level 1 compression, tagging the payload with `CompressionAlgorithm::Zstd`.
* **FR-028**: **Adaptive Frame Coalescing**: When downstream subscriber network queues exceed 256 KB, the Host Daemon SHALL discard queued delta frames and emit an updated `ScreenStateSync` visual grid snapshot to prevent memory bloat and laptop stall.

#### 1.2 Relay Hub Subsystem
* **FR-013**: Route binary MessagePack envelopes asynchronously over WebSockets.
* **FR-014**: Maintain Zero-Knowledge blind routing without payload decryption capability.
* **FR-015**: Enforce IP-based rate limiting (maximum 60 connections per minute per IP).
* **FR-016**: Enforce strict frame capping (maximum 64 KB per frame).
* **FR-017**: Emit 15-second heartbeat pings and purge sockets after 45 seconds of inactivity.
* **FR-030**: **Infrastructure Deployment Isolation**: In private workstation deployments, the Relay Hub SHALL support binding strictly to private overlay network adapters (e.g. ZeroTier IP `172.23.127.184`), completely isolated from public internet interfaces to safeguard shared server resources.

#### 1.3 Android Client Subsystem
* **FR-018**: Render terminal ANSI streams via native monospace canvas.
* **FR-019**: Store paired host public keys persistently in hardware-backed **Android KeyStore**.
* **FR-020**: Override soft keyboard `InputConnection` with `TYPE_NULL` to eliminate Gboard autocomplete bugs.
* **FR-021**: Provide floating accessory keyboard bar (`Esc`, `Tab`, `Ctrl`, `Alt`, Cursor Arrows).
* **FR-022**: Execute within an Android **Foreground Service** (`TerminalMirrorService`) holding a partial `WakeLock`.
* **FR-023**: Enforce default **View-Only Guard Mode** to eliminate accidental touch input.
* **FR-029**: **Hardware-Accelerated SurfaceView Rendering**: The Android Client SHALL render terminal characters directly to a native `SurfaceView` / `TextureView` backed by a pre-rendered bitmap texture atlas, strictly avoiding Composable `Text` recomposition loops to prevent device overheating.

---

### 2. Non-Functional Performance Requirements (NFR)
* **NFR-001 (Roundtrip Latency)**: < 50 ms over LAN/Wi-Fi; < 100 ms over 4G/5G mobile networks.
* **NFR-002 (Bandwidth Efficiency)**: With `zstd` compression, continuous terminal compile scrolling SHALL consume < 10 KB/sec bandwidth.
* **NFR-003 (Battery Impact)**: SurfaceView rendering + partial WakeLock SHALL consume < 4% battery per hour of continuous streaming.
* **NFR-004 (Host Resource Guarantee)**: Host daemon memory RSS SHALL remain < 25 MB and CPU usage < 1% during full-throughput PTY bursts.
