# Laporan Akhir: Deployment Produksi Relay Server Hub di VPS Hostinger
**Nomor Tugas**: TM-011  
**Tanggal**: 2026-09-17  
**Status**: SELESAI  

---

## 1. Informasi Project
- **Nama Tugas**: Production Deployment of Terminal Mirror Relay Hub on Hostinger VPS via Docker Compose & ZeroTier
- **Nama Branch**: `feature/architecture-spec-and-submodules`
- **Nama & URL Repo**: `terminal-mirror` (`https://github.com/mufidhadi/terminal-mirror`)
- **Nomor Hash Commit**: `f10d814`
- **Tech Stack**:
  - Target Server: VPS Hostinger (AMD EPYC 9354P Zen 4, 2 vCPUs, RAM 7.8 GiB)
  - Network Overlay: ZeroTier Private Mesh (`172.23.127.184:8888`)
  - Containerization: Docker 29+, Docker Compose
  - Base Image: `rust:1.97-slim` (builder) -> `debian:bookworm-slim` (runtime)
  - Protocols: HTTP/1.1 REST (`/healthz`, `/metrics`), WebSocket (`/ws`)

---

## 2. Histori Aksi
1. **Audit Pre-Deployment & Verifikasi Akses SSH**:
   - Menghubungkan SSH ke VPS Hostinger menggunakan kunci privat `~/.ssh/id_ed25519_personal` ke `root@172.23.127.184`.
   - Mengonfirmasi bahwa akun GitHub `mufidhadi` telah terotentikasi secara aman via SSH di server VPS (`Hi mufidhadi! You've successfully authenticated`).
2. **Setup Direktori & Kloning Repository di VPS**:
   - Menjalankan `git clone -b feature/architecture-spec-and-submodules git@github.com:mufidhadi/terminal-mirror.git ~/project/terminal-mirror`.
   - Membuat file `.env` produksi di dalam direktori `~/project/terminal-mirror/`:
     ```env
     RELAY_BIND_IP=172.23.127.184
     RELAY_PORT=8888
     RELAY_AUTH_TOKEN=[REDACTED — rotated 2026-09-17, see Report 022]
     MAX_CONN_PER_MIN=60
     MAX_PAYLOAD_BYTES=65536
     STALE_SESSION_TIMEOUT_SECS=300
     RUST_LOG=info
     ```
3. **Kompilasi & Peluncuran Container Docker**:
   - Menjalankan perintah `docker compose up -d --build`.
   - Docker melakukan build multi-stage secara terisolasi tanpa menyentuh target lokal berkat `.dockerignore`.
   - Container `terminal-mirror-relay` berhasil dibuat, dimulai, dan diikat ke antarmuka ZeroTier `172.23.127.184:8888`.
4. **Verifikasi Operasional & Healthcheck**:
   - Memverifikasi status container melalui remote SSH:
     `Status: Up 20 seconds (healthy)`
   - Melakukan pengujian HTTP curl langsung dari laptop MacBook Pro pengembangan (`172.23.220.206`) ke endpoint ZeroTier VPS (`http://172.23.127.184:8888/healthz`):
     - Response: `HTTP/1.1 200 OK`, body: `OK`.
   - Melakukan pengujian scraping Prometheus (`http://172.23.127.184:8888/metrics`):
     - Seluruh metrik telemetry aktif (`relay_active_sessions`, `relay_active_subscribers`, `relay_frames_routed_total`, dll).

---

## 3. List Kesulitan, Tantangan, Bug dan Solusi

| # | Kesulitan / Bug | Analisa Penyebab | Solusi |
|---|---|---|---|
| 1 | Menghindari interupsi terhadap 20 container produksi lain di VPS. | VPS menjalankan workload mission-critical (PostgreSQL, Qdrant, WAHA, N8N, Traefik). Build atau container baru yang tidak dibatasi dapat menyebabkan CPU throttle atau OOM kill. | Menetapkan batasan ketat pada `docker-compose.yml` (`limits: cpus 0.50, memory 256M`), dan mem-bind port secara eksklusif ke IP ZeroTier (`172.23.127.184:8888`), sehingga tidak bentrok dengan Traefik di `0.0.0.0:8080`. |
| 2 | Waktu build di VPS memakan waktu 4 menit 57 detik saat kompilasi pertama. | Cargo harus mendownload dan mengompilasi seluruh pohon dependensi release secara mandiri di container Docker. | Menggunakan `.dockerignore` ketat untuk meminimalkan pengiriman context build ke daemon Docker. Untuk build berikutnya, layer dependensi akan di-cache oleh Docker BuildKit. |

---

## 4. List Test yang Dilakukan & Hasil Test

### A. Docker Healthcheck di VPS
Perintah: `docker ps --filter "name=terminal-mirror-relay"`
```text
CONTAINER ID   IMAGE                          STATUS                    PORTS                           NAMES
d59b52f1a686   terminal-mirror-relay-server   Up 20 seconds (healthy)   172.23.127.184:8888->8080/tcp   terminal-mirror-relay
```

### B. Remote Endpoint Verification via ZeroTier
Perintah: `curl -i http://172.23.127.184:8888/healthz`
```text
HTTP/1.1 200 OK
content-type: text/plain; charset=utf-8
vary: origin, access-control-request-method, access-control-request-headers
access-control-allow-origin: *
content-length: 2
date: Wed, 16 Sep 2026 22:22:17 GMT

OK
```

### C. Telemetry Metrics Verification
Perintah: `curl -s http://172.23.127.184:8888/metrics`
```text
# HELP relay_active_sessions Number of registered active terminal sessions
# TYPE relay_active_sessions gauge
relay_active_sessions 0

# HELP relay_active_subscribers Number of connected mobile subscriber clients
# TYPE relay_active_subscribers gauge
relay_active_subscribers 0

# HELP relay_frames_routed_total Total number of binary terminal frames routed
# TYPE relay_frames_routed_total counter
relay_frames_routed_total 0

# HELP relay_dropped_frames_total Total number of frames dropped due to subscriber buffer lag
# TYPE relay_dropped_frames_total counter
relay_dropped_frames_total 0

# HELP relay_rate_limited_total Total number of connection attempts rejected by rate limiting
# TYPE relay_rate_limited_total counter
relay_rate_limited_total 0
```

---

## 5. Lesson Learned
1. **Binding Eksklusif pada Antarmuka Mesh VPN (ZeroTier)**: Membatasi binding service pada IP VPN (`172.23.127.184`) bukan `0.0.0.0` memberikan dua keuntungan sekaligus: mengisolasi port dari port conflict di interface publik, dan menambahkan proteksi firewall alami karena hanya device di jaringan ZeroTier yang dapat mengakses relay server.
2. **Kepatuhan Alur Kerja Deployment**: Mengikuti alur kerja yang terstruktur (`coding lokal` $\rightarrow$ `test TDD` $\rightarrow$ `docker-compose` $\rightarrow$ `push` $\rightarrow$ `pull di VPS` $\rightarrow$ `run docker compose` $\rightarrow$ `analisa log`) menjamin bahwa setiap artefak di server produksi 100% identik dan terlacak dengan commit hash di GitHub.
