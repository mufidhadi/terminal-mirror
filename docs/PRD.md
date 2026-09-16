# Product Requirements Document (PRD)
## Project: Terminal Mirror (Hardened Open-Source & Performance Edition)

---

### 1. Product Vision & Architecture Philosophy
Terminal Mirror provides effortless, low-latency, and battery-efficient terminal mirroring from developer workstations to Android smartphones. Built with high-performance Rust and native mobile rendering, it prioritizes **Self-Hosting Sovereignty**, **Uncompromising E2EE**, and **Cellular Network Resilience**.

---

### 2. High-Performance Feature Breakdown

| Feature ID | Feature Name | Priority | Technical Specification |
| :--- | :--- | :--- | :--- |
| **FEAT-11** | Zstandard (`zstd`) Compression | P0 | Terminal output chunks > 512 bytes are compressed via `zstd` level 1, reducing network payload size by 70–85%. |
| **FEAT-12** | Adaptive Delta Coalescing | P0 | When mobile client downstream buffer latency exceeds 256 KB on poor 4G/5G connections, intermediate deltas are dropped in favor of an updated `ScreenStateSync` snapshot, preventing OOM crashes. |
| **FEAT-13** | SurfaceView / Canvas Rendering | P0 | Android terminal viewport uses hardware-accelerated Canvas/SurfaceView with cached monospace texture atlases, avoiding Jetpack Compose recomposition overhead. |
| **FEAT-14** | Self-Host First 1-Click Compose| P0 | 1-file `docker-compose.yml` for self-hosters; zero reliance on third-party cloud infrastructure. |
| **FEAT-15** | Bounded Memory Queues | P1 | Host PTY read thread uses bounded `mpsc::channel(1024)` to decouple I/O and prevent laptop terminal freezes during high-volume `cat` bursts. |

---

### 3. User Experience Under Adversarial Network Conditions

#### Scenario: Mobile in Weak Cellular Area (Elevator / Transit)
1. User starts a 500 MB log build (`cargo test --all --nocapture`).
2. Phone moves into a low-signal cellular area (bandwidth drops to 100 Kbps, RTT spikes to 350 ms).
3. **Without Coalescing (Legacy failure)**: Server memory balloons with buffered raw bytes; mobile app locks up trying to process a backlog of 50,000 lines.
4. **With Terminal Mirror Performance Engine**:
   * Host detects queue saturation (> 256 KB).
   * Host coalesces intermediate streaming frames.
   * Mobile receives a lightweight `zstd`-compressed `ScreenStateSync` snapshot representing the exact current screen.
   * Terminal renders instantly at 60 FPS without memory bloat or thermal throttling.

---

### 4. Performance & Battery Key Performance Indicators (KPIs)
* **Bandwidth Consumption**: < 5 KB/s average during high-volume compilation (via `zstd` compression).
* **Battery Consumption**: < 4% battery discharge per hour of active terminal streaming on Android.
* **Host CPU Overhead**: < 1.0% of a single CPU core during 1 MB/s terminal output bursts.
* **Zero OOM Incidents**: 100% immunity to memory exhaustion over 24-hour continuous stream stress tests.
