# Laporan Akhir: Integrasi E2E Pipeline WebSocket (macOS Host Agent <-> Relay Server)
**Nomor Tugas**: TM-009  
**Tanggal**: 2026-09-17  
**Status**: SELESAI  

---

## 1. Informasi Project
- **Nama Tugas**: macOS Host Agent WebSocket Client Integration, Relay Server Library Refactor, and E2E Automated Testing
- **Nama Branch**: `feature/architecture-spec-and-submodules`
- **Nama & URL Repo**: `terminal-mirror` (`https://github.com/mufidhadi/terminal-mirror`)
- **Nomor Hash Commit**: `9be4d3a`
- **Tech Stack**:
  - Language: Rust 1.80+ (Edition 2021)
  - WebSocket Client & Server: `tokio-tungstenite 0.23`, `axum 0.7`
  - Async Runtime: `tokio` (mpsc, Mutex, select, spawn_blocking)
  - Serialization: MessagePack (`rmp-serde`), `terminal-mirror-protocol` (`Packet`, `PacketPayload`)
  - Testing: Rust Integration Tests (`tests/e2e_pipeline_test.rs`)

---

## 2. Histori Aksi
1. **Riset Reconnection & Resiliency**:
   - Melakukan riset internet mengenai strategi auto-reconnect WebSocket dengan exponential backoff dan jitter di Rust menggunakan Tokio.
   - Merancang loop koneksi tangguh pada client yang otomatis mencoba menyambung kembali saat relay server restart atau jaringan terputus (1s -> 2s -> 4s -> max 30s).
2. **Implementasi WebSocket Client di macOS Agent (`apps/mac/src/network/client.rs`)**:
   - Membangun struct `RelayHostClient` yang menyusun URL WebSocket terotentikasi (`ws://.../ws?token=...&session_id=...&role=host`).
   - Memisahkan aliran data menjadi dua task asinkron:
     - **Task Downstream**: Mengambil chunk output terminal dari PTY reader, membungkusnya ke dalam envelope `Packet::new(..., PacketPayload::TerminalOutput)`, men-serialize ke MessagePack, dan mengirimkannya ke WebSocket sink.
     - **Task Upstream**: Menerima frame biner dari WebSocket stream, mem-parse `PacketPayload::TerminalInput`, dan mengalirkannya ke PTY writer untuk dieksekusi di shell lokal (`/bin/zsh`).
   - Menyelesaikan issue kepemilikan borrow-checker loop reconnection dengan membungkus channel receiver di dalam `Arc<tokio::sync::Mutex<Receiver>>`.
   - Menambahkan unit test URL building query string.
3. **Refaktor Relay Server Menjadi Library + Binary (`services/relay-server`)**:
   - Memperbarui `services/relay-server/Cargo.toml` dengan target `[lib]` dan `[[bin]]`.
   - Mengekstrak fungsi `create_app(state)` ke `src/lib.rs` sehingga server dapat di-spawn secara instan di atas ephemeral port (`127.0.0.1:0`) dalam automated integration test.
4. **Pembuatan Automated End-to-End Test Suite (`services/relay-server/tests/e2e_pipeline_test.rs`)**:
   - `test_e2e_bidirectional_streaming_between_host_and_subscriber`: Menguji streaming dua arah dari Host Agent -> Relay Server -> Mobile Subscriber, dan sebaliknya (keystroke upstream dari Subscriber -> Host).
   - `test_e2e_unauthorized_connection_rejected`: Memverifikasi handshake ditolak dengan HTTP 401 Unauthorized jika auth token salah.
   - `test_e2e_rate_limiting_blocks_burst`: Memverifikasi IP rate limiter memblokir connection burst yang melampaui batas dengan HTTP 429 Too Many Requests.
5. **Eksekusi Pengujian Menyeluruh**:
   - Menjalankan `cargo test --workspace`: **27 tests passed (100% lulus, 0 warnings)**.

---

## 3. List Kesulitan, Tantangan, Bug dan Solusi

| # | Kesulitan / Bug | Analisa Penyebab | Solusi |
|---|---|---|---|
| 1 | `use of moved value: downstream_rx` di dalam reconnection loop. | Tokio `spawn` memindahkan (*move*) `mpsc::Receiver` ke dalam async closure, sehingga pada iterasi loop reconnection berikutnya variabel sudah tidak tersedia. | Membungkus `downstream_rx` dalam `Arc<tokio::sync::Mutex<mpsc::Receiver<Vec<u8>>>>`. Closure di dalam loop hanya mengklon handle `Arc` dan mengunci mutex secara asinkron saat membaca frame. |
| 2 | Test `test_e2e_bidirectional_streaming` gagal karena assert langsung raw bytes pada stream host. | Relay server beroperasi dalam mode Zero-Knowledge sehingga tidak meng-unpack paket subscriber; relay meneruskan frame biner `Packet` MessagePack apa adanya ke host. | Meng-update assert test untuk men-decode MessagePack `Packet::from_msgpack(&host_bytes)` dan mencocokkan payload `TerminalInput.bytes`, sesuai dengan arsitektur Zero-Knowledge. |
| 3 | Relay server awalnya hanya target binary `[[bin]]`, menyulitkan integration test eksternal. | Integration test di folder `tests/` berjalan sebagai crate independen dan tidak bisa mengimpor modul dari root binari `main.rs`. | Mengonfigurasi target `[lib]` di `Cargo.toml` dan memindahkan modul `config`, `hub`, `metrics`, `middleware`, `ws` ke `lib.rs`. Binari `main.rs` menjadi wrapper tipis di atas library. |

---

## 4. List Test yang Dilakukan & Hasil Test

Perintah: `cargo test --workspace`
```text
running 2 tests (terminal_mirror_mac)
test network::client::tests::test_build_ws_url_formatting ... ok
test network::client::tests::test_build_ws_url_with_existing_query_params ... ok
test result: ok. 2 passed; 0 failed; 0 ignored; 0 measured; finished in 0.00s

running 7 tests (protocol_test.rs)
test test_utf8_stream_chunker_multibyte_slicing ... ok
test test_pairing_guard_three_strikes_auto_burn ... ok
test test_packet_creation_and_version ... ok
test test_pairing_payload_with_pin_and_trusted_device ... ok
test test_session_role_subscription ... ok
test test_packet_msgpack_roundtrip_terminal_output_compressed ... ok
test test_screen_snapshot_roundtrip ... ok
test result: ok. 7 passed; 0 failed; 0 ignored; 0 measured; finished in 0.00s

running 7 tests (terminal_mirror_relay unit)
test metrics::tests::test_metrics_counter_increments ... ok
test middleware::rate_limiter::tests::test_rate_limiter_allows_up_to_limit ... ok
test middleware::rate_limiter::tests::test_rate_limiter_isolates_different_ips ... ok
test hub::session_hub::tests::test_stale_session_reaping ... ok
test hub::session_hub::tests::test_session_hub_lifecycle ... ok
test config::tests::test_default_config ... ok
test config::tests::test_custom_cli_args ... ok
test result: ok. 7 passed; 0 failed; 0 ignored; 0 measured; finished in 0.00s

running 3 tests (e2e_pipeline_test.rs integration)
test test_e2e_unauthorized_connection_rejected ... ok
test test_e2e_rate_limiting_blocks_burst ... ok
test test_e2e_bidirectional_streaming_between_host_and_subscriber ... ok
test result: ok. 3 passed; 0 failed; 0 ignored; 0 measured; finished in 0.00s

running 8 tests (terminal_mirror_windows)
test stream::coalescer::tests::test_stream_coalescer_threshold_and_reset ... ok
test conpty::shell_resolver::tests::test_is_powershell_detection ... ok
test conpty::shell_resolver::tests::test_explicit_cmd_has_no_powershell_flags ... ok
test conpty::shell_resolver::tests::test_explicit_powershell_attaches_bypass_flags ... ok
test ui::banner::tests::test_format_startup_banner_contains_required_fields ... ok
test config::tests::test_default_config ... ok
test config::tests::test_custom_flags_parsing ... ok
test stream::debouncer::tests::test_resize_debouncer_coalesces_rapid_events ... ok
test result: ok. 8 passed; 0 failed; 0 ignored; 0 measured; finished in 0.35s

Total: 27 passed; 0 failed; 0 warnings
```

---

## 5. Lesson Learned
1. **Pemisahan Binary vs Library pada Crate Server**: Memisahkan logika server ke dalam `lib.rs` dan menyediakan entrypoint tipis di `main.rs` memberikan fleksibilitas testing tingkat tinggi. Integration test dapat mem-bind port 0 (ephemeral OS port) dan menjalankan skenario multi-client yang realistis secara paralel tanpa konflik port jaringan.
2. **Desain Client Berorientasi Reconnect**: Koneksi WebSocket ke relay server tidak boleh diasumsikan selalu hidup. Memasang reconnection loop dengan backoff eksponensial di layer client menjamin bahwa restart relay server atau gangguan jaringan sesaat tidak akan mematikan shell proses terminal di workstation developer.
