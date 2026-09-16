# Engineering Execution & Project Planning Document
## Project: Terminal Mirror (Hardened Open-Source & Performance Edition)

---

### 1. Release Roadmaps & Performance Milestones

```
[Sprint 1: Core Protocol & Workspace Scaffolding] (COMPLETED)
       │
       ▼
[Sprint 2: Host Daemons + vt100 Grid + zstd Compression]
       │
       ▼
[Sprint 3: Zero-Knowledge E2EE + 3-Strike PairingGuard + Relay Rate Limiter]
       │
       ▼
[Sprint 4: Android SurfaceView Engine + Foreground Service + KeyStore]
       │
       ▼
[Sprint 5: Stress-Testing, Self-Host Packaging & Community Launch]
```

---

### 2. Dedicated Performance & Capacity Workstreams

#### Task P.1: Zstandard (`zstd`) Benchmark Validation
* Integrate `zstd` level 1 compression into `TerminalOutput` chunks > 512 bytes.
* Benchmark compression ratio and CPU overhead using Criterion.rs against real GCC/Rust build logs.
* Target: 70–85% payload reduction with < 0.2ms CPU compression cost per chunk.

#### Task P.2: Backpressure Stress-Testing (The `cat 1GB.log` Test)
* Simulate a runaway script producing 100 MB/s PTY output.
* Validate that `mpsc::channel(1024)` bounded buffer drops intermediate delta frames when downstream is throttled, falling back cleanly to `ScreenStateSync` without memory growth.
* Target: Host daemon RSS stays strictly under 25 MB under infinite loop stress.

#### Task P.3: Mobile Thermal & Battery Profiling
* Profile Android client on Motorola device using Android Studio Profiler over a 60-minute continuous stream session.
* Target: GPU/CPU utilization < 5%, zero UI frame jank (constant 60 FPS), battery discharge < 4%/hour.

#### Task P.4: VPS Hostinger Capacity Isolation
* Validate that Relay Server container running on VPS Hostinger (`172.23.127.184`) uses < 20 MB RAM and 0.05% CPU.
* Enforce ZeroTier-only binding in `docker-compose.yml`, verifying that no public traffic touches the host and zero bandwidth is counted against Hostinger's monthly quota.
