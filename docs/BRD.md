# Business Requirements Document (BRD)
## Project: Terminal Mirror (Hardened Open-Source & Performance Architecture)

---

### 1. Executive Summary
Terminal Mirror is a secure, high-performance, open-source terminal streaming platform. It enables developers and sysadmins to mirror interactive CLI sessions from desktop workstations (macOS and Windows) to an Android mobile device with sub-50ms latency, zero cloud vendor lock-in, and End-to-End Encryption (E2EE).

Following deep performance analysis and capacity evaluation of production infrastructure, Terminal Mirror adopts a **"Self-Host First" Strategy**:
* **Personal Workloads**: Deployed on private infrastructure (e.g. VPS Hostinger) bound strictly to private mesh networks (ZeroTier IP `172.23.127.184`), completely isolated from the public internet.
* **Community Workloads**: Distributed as an autonomous 1-click Docker container for community self-hosting. Public community relays (if deployed) reside on dedicated unmetered infrastructure (e.g. 20 TB unmetered cloud instances) to prevent cross-service bandwidth throttling and CPU contention.

---

### 2. Infrastructure Capacity & Risk Evaluation (Hostinger VPS Analysis)

#### 2.1 Hardware Baseline & Resource Contention
A live operational audit of the primary Hostinger VPS reveals:
* **vCPU Allocation**: 2 vCPUs (AMD EPYC 9354P Zen 4 @ 2.0GHz).
* **RAM**: 7.8 GiB (2.5 GiB used, 5.2 GiB available).
* **Active Services**: **20 production Docker containers running concurrently** (including Traefik, PostgreSQL, Qdrant Vector DB, WAHA WhatsApp API, N8N, MinIO, and production client applications).
* **Load Average**: 0.29 – 0.36.

#### 2.2 Capacity Thresholds
1. **Private Usage (Mas Mufid Personal)**:
   * Footprint: ~15 MB RAM, < 0.1% CPU, < 2 KB/s average egress.
   * Assessment: **100% SANGAT SANGGUP**. Zero impact on existing 20 production containers.
2. **Public Community Relay Risk (Why it must NOT run on this VPS)**:
   * **Hostinger 10 Mbps Throttling Policy**: If total egress exceeds the monthly allocation (4–8 TB), Hostinger automatically throttles VPS speed to **10 Mbps** for the remainder of the billing cycle.
   * **Cascading Failure**: A fleet of 100 concurrent public terminal sessions consuming 24 Mbps continuous bandwidth would burn ~7.7 TB/month, triggering the 10 Mbps throttle and crippling all 20 business production containers.
   * **Decision**: The private VPS remains dedicated to personal ZeroTier workloads. The open-source product is marketed as **Self-Host First**.

---

### 3. Business Goals & Technical ROI
* **Bandwidth Optimization**: Integrate **Zstandard (`zstd`)** compression to reduce egress transfer by **70%–85%**, cutting mobile data usage.
* **Battery & Device Health**: Offload Android UI rendering to native SurfaceView/TextureView, preventing phone thermal throttling and excessive battery drain.
* **Zero Production Risk**: Guaranteed isolation between personal business operations and open-source software experiments.
