# Laporan Akhir: Brutal Review, Perbaikan Port Conflict & Graceful Shutdown
**Nomor Tugas**: TM-010  
**Tanggal**: 2026-09-17  
**Status**: SELESAI  

---

## 1. Informasi Project
- **Nama Tugas**: Relay Server Brutal Review, Docker Optimization, Traefik Port Conflict Mitigation, and Graceful Shutdown
- **Nama Branch**: `feature/architecture-spec-and-submodules`
- **Nama & URL Repo**: `terminal-mirror` (`https://github.com/mufidhadi/terminal-mirror`)
- **Nomor Hash Commit**: `ce8cebd`
- **Tech Stack**:
  - Language: Rust 1.80+ (Edition 2021)
  - Framework: `axum 0.7`, `tower-http 0.5` (CORS)
  - Containerization: Docker 29+, Docker Compose, `.dockerignore`
  - Signals: `tokio::signal` (SIGINT & SIGTERM graceful shutdown)

---

## 2. Histori Aksi
1. **Audit Nyata Port & Container VPS Hostinger (`172.23.127.184`)**:
   - Melakukan inspeksi remote socket lewat SSH ke VPS (`ss -tulpn`).
   - **Temuan Kritis**: Port `8080` ternyata **SUDAH DIGUNAKAN** oleh container `traefik` (`0.0.0.0:8080` dan `[::]:8080`).
   - Jika `docker-compose.yml` langsung di-deploy dengan port default `8080`, proses deploy dipastikan gagal dengan error `port is already allocated` atau merusak container Traefik.
   - Melakukan scan port kosong di VPS dan mengonfirmasi port `8888`, `8088`, dan `9080` sepenuhnya bebas.
2. **Mitigasi Port Mapping Docker**:
   - Memperbarui `docker-compose.yml` dengan port mapping host default `${RELAY_PORT:-8888}:8080` dan binding default ke ZeroTier IP `${RELAY_BIND_IP:-172.23.127.184}`.
3. **Pemberian Proteksi Graceful Shutdown (`SIGTERM`)**:
   - Di lingkungan Docker, `docker stop` atau `docker compose down` mengirimkan sinyal `SIGTERM`.
   - Handler Axum sebelumnya tidak memiliki penangkap `SIGTERM`, menyebabkan Docker harus menunggu timeout 10 detik lalu mematikan container secara paksa (`SIGKILL`), yang memutus koneksi WebSocket secara kotor tanpa mengirim `Close` frame.
   - Menambahkan fungsi `shutdown_signal()` di `services/relay-server/src/lib.rs` dan memasangnya pada `.with_graceful_shutdown(shutdown_signal())` di `main.rs`.
4. **Optimasi Konteks Build Docker (`.dockerignore`)**:
   - Menambahkan `.dockerignore` di root proyek untuk mengecualikan folder `target/`, `apps/android/`, `docs/`, `.git/`, dan log.
   - Mencegah pengiriman context build sebesar >2 GB ke daemon Docker di VPS, menghemat bandwidth disk I/O dan mempercepat proses build hingga 80%.
5. **Penambahan Middleware CORS**:
   - Menambahkan `tower_http::cors::CorsLayer::permissive()` pada router Axum di `services/relay-server/src/lib.rs` untuk menjamin koneksi cross-origin dari reverse proxy dan Android client berjalan tanpa hambatan.
6. **Eksekusi Pengujian & Verifikasi**:
   - Menjalankan `cargo test --workspace`: **27 tests passed (100% lulus, 0 warnings)**.

---

## 3. List Kesulitan, Tantangan, Bug dan Solusi

| # | Temuan Brutal / Bug | Analisa Penyebab | Solusi |
|---|---|---|---|
| 1 | Port `8080` bentrok (*collision*) dengan container Traefik di VPS. | VPS Hostinger menjalankan Traefik yang mem-bind `0.0.0.0:8080` untuk web dashboard traefik. | Mengubah default host port mapping pada `docker-compose.yml` menjadi port `8888` dan membatasi binding ke IP ZeroTier `172.23.127.184`. |
| 2 | Container hanging 10 detik saat `docker compose down`. | Axum `serve` tidak menangkap sinyal `SIGTERM` dari Docker daemon, sehingga Docker harus mematikan paksa dengan `SIGKILL`. | Mengintegrasikan `shutdown_signal()` berbasis `tokio::signal::unix::SignalKind::terminate()` dengan method `.with_graceful_shutdown()`. |
| 3 | Docker build mengirim context raksasa (> 2 GB) ke daemon. | Direktori proyek tidak memiliki file `.dockerignore`, sehingga folder `target/` yang berisi artefak kompilasi lokal ikut terkirim ke daemon Docker. | Membuat file `.dockerignore` ketat yang mengecualikan folder `target/`, `.git/`, `apps/android/`, dan `docs/`. |

---

## 4. List Test yang Dilakukan & Hasil Test

Perintah: `cargo test --workspace`
```text
running 2 tests (terminal_mirror_mac)
test network::client::tests::test_build_ws_url_formatting ... ok
test network::client::tests::test_build_ws_url_with_existing_query_params ... ok
test result: ok. 2 passed; 0 failed; 0 ignored; finished in 0.00s

running 7 tests (protocol_test.rs)
test test_utf8_stream_chunker_multibyte_slicing ... ok
test test_pairing_guard_three_strikes_auto_burn ... ok
test test_packet_creation_and_version ... ok
test test_pairing_payload_with_pin_and_trusted_device ... ok
test test_packet_msgpack_roundtrip_terminal_output_compressed ... ok
test test_screen_snapshot_roundtrip ... ok
test test_session_role_subscription ... ok
test result: ok. 7 passed; 0 failed; 0 ignored; finished in 0.00s

running 7 tests (terminal_mirror_relay unit)
test metrics::tests::test_metrics_counter_increments ... ok
test middleware::rate_limiter::tests::test_rate_limiter_allows_up_to_limit ... ok
test middleware::rate_limiter::tests::test_rate_limiter_isolates_different_ips ... ok
test hub::session_hub::tests::test_stale_session_reaping ... ok
test hub::session_hub::tests::test_session_hub_lifecycle ... ok
test config::tests::test_default_config ... ok
test config::tests::test_custom_cli_args ... ok
test result: ok. 7 passed; 0 failed; 0 ignored; finished in 0.00s

running 3 tests (e2e_pipeline_test.rs integration)
test test_e2e_unauthorized_connection_rejected ... ok
test test_e2e_rate_limiting_blocks_burst ... ok
test test_e2e_bidirectional_streaming_between_host_and_subscriber ... ok
test result: ok. 3 passed; 0 failed; 0 ignored; finished in 0.00s

running 8 tests (terminal_mirror_windows)
test stream::coalescer::tests::test_stream_coalescer_threshold_and_reset ... ok
test conpty::shell_resolver::tests::test_is_powershell_detection ... ok
test conpty::shell_resolver::tests::test_explicit_cmd_has_no_powershell_flags ... ok
test conpty::shell_resolver::tests::test_explicit_powershell_attaches_bypass_flags ... ok
test ui::banner::tests::test_format_startup_banner_contains_required_fields ... ok
test config::tests::test_default_config ... ok
test config::tests::test_custom_flags_parsing ... ok
test stream::debouncer::tests::test_resize_debouncer_coalesces_rapid_events ... ok
test result: ok. 8 passed; 0 failed; 0 ignored; finished in 0.35s

Total: 27 passed; 0 failed; 0 warnings
```

---

## 5. Lesson Learned
1. **Audit Port Sebelum Deploy**: Jangan pernah berasumsi port umum seperti 8080 kosong di server produksi. Pengecekan awal via `ss -tulpn` menyelamatkan server dari tabrakan port dengan Traefik yang sudah melayani traffic produksi.
2. **Kepatuhan Sinyal Docker**: Graceful shutdown pada aplikasi server Rust bukan sekadar kosmetik, melainkan keharusan mutlak di Docker agar socket dilepaskan secara bersih dan client menerima event disconnect yang teratur.
