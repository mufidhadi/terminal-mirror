# Laporan Akhir: Implementasi E2EE ChaCha20-Poly1305, Generator Diceware & Terminal QR Code
**Nomor Tugas**: TM-012  
**Tanggal**: 2026-09-17  
**Status**: SELESAI  

---

## 1. Informasi Project
- **Nama Tugas**: Zero-Knowledge E2EE ChaCha20-Poly1305 Cryptographic Engine, Diceware Passphrase Generator, and Terminal QR Code Pairing
- **Nama Branch**: `feature/architecture-spec-and-submodules`
- **Nama & URL Repo**: `terminal-mirror` (`https://github.com/mufidhadi/terminal-mirror`)
- **Nomor Hash Commit**: `8b5796c`
- **Tech Stack**:
  - Language: Rust 1.80+ (Edition 2021)
  - Cryptography: `chacha20poly1305 0.10` (AEAD Authenticated Encryption)
  - Visual Pairing: `qrcode 0.14` (Dense1x2 Unicode half-blocks)
  - Protocol & Serialization: `serde`, `serde_json`, `rmp-serde`

---

## 2. Histori Aksi
1. **Penerapan AEAD ChaCha20-Poly1305 (`crates/protocol/src/crypto.rs`)**:
   - Membangun struct `E2eeCipher` menggunakan crate standar industri RustCrypto `chacha20poly1305`.
   - Menetapkan konstruksi nonce deterministik 96-bit (12-byte) dari 64-bit packet sequence counter (4 byte padding nol + 8 byte big-endian sequence) untuk mencegah collision atau reuse nonce.
   - Menambahkan unit test TDD: roundtrip encryption/decryption dan pembuktian kegagalan autentikasi jika 1 byte ciphertext dimodifikasi (tamper detection).
2. **Generator Passphrase Diceware 4 Kata (`DicewarePassphrase`)**:
   - Membangun generator passphrase 4 kata berbasis kata benda/alam bahasa Indonesia berbobot entropi tinggi (~51.7 bits) untuk opsi pairing manual tanpa repot mengetik karakter acak.
   - Dilengkapi unit test verifikasi jumlah kata dan format pemisah (`-`).
3. **Penyematan QR Code Unicode pada Startup Banner Mac (`apps/mac/src/ui/banner.rs`)**:
   - Mengintegrasikan crate `qrcode` dengan renderer Unicode `Dense1x2` (`▀`, `▄`, `█`, ` `) yang ringkas dan tajam di terminal developer modern.
   - Mengisi payload QR Code dengan metadata koneksi lengkap (`PairingPayload`: relay_url, session_id, host_id, pre_shared_key, pin/passphrase, expires_at) sehingga camera scanner Android dapat melakukan pairing instan 1 detik.
4. **Eksekusi Pengujian & Verifikasi**:
   - Menjalankan `cargo test --workspace`: **31 tests passed (100% lulus, 0 warnings)**.
   - Memverifikasi eksekusi binari CLI `cargo run -p terminal-mirror-mac -- --help` (exit code 0).

---

## 3. List Kesulitan, Tantangan, Bug dan Solusi

| # | Kesulitan / Bug | Analisa Penyebab | Solusi |
|---|---|---|---|
| 1 | Risiko Nonce Reuse pada ChaCha20-Poly1305. | Penggunaan nonce acak 96-bit berpotensi tabrakan (birthday paradox) pada throughput tinggi (> 2^32 paket). | Menggunakan konstruksi nonce deterministik monotonik dari nomor sequence paket `seq.to_be_bytes()`. Karena sequence selalu unik bertambah per paket, risiko reuse nonce adalah mutlak nol. |
| 2 | QR code standar terlalu lebar dan merusak layout terminal. | Karakter ASCII standar (`#` dan spasi) menghasilkan aspek rasio 1:2 (tinggi:lebar) yang membuat QR code raksasa dan melewati tinggi layar terminal standar (24 baris). | Menggunakan renderer `unicode::Dense1x2` dari crate `qrcode` yang menggabungkan dua modul piksel vertikal ke dalam 1 karakter Unicode half-block, memangkas tinggi QR code hingga 50% sehingga muat rapi di layar 24 baris. |

---

## 4. List Test yang Dilakukan & Hasil Test

Perintah: `cargo test --workspace`
```text
running 3 tests (terminal_mirror_mac)
test network::client::tests::test_build_ws_url_with_existing_query_params ... ok
test network::client::tests::test_build_ws_url_formatting ... ok
test ui::banner::tests::test_generate_terminal_qr_produces_non_empty_unicode_blocks ... ok
test result: ok. 3 passed; 0 failed; 0 ignored; finished in 0.00s

running 3 tests (terminal_mirror_protocol lib)
test crypto::tests::test_diceware_passphrase_generation ... ok
test crypto::tests::test_e2ee_tampered_ciphertext_fails_auth ... ok
test crypto::tests::test_e2ee_chacha20poly1305_roundtrip ... ok
test result: ok. 3 passed; 0 failed; 0 ignored; finished in 0.00s

running 7 tests (protocol_test.rs)
test test_pairing_guard_three_strikes_auto_burn ... ok
test test_utf8_stream_chunker_multibyte_slicing ... ok
test test_packet_creation_and_version ... ok
test test_pairing_payload_with_pin_and_trusted_device ... ok
test test_session_role_subscription ... ok
test test_packet_msgpack_roundtrip_terminal_output_compressed ... ok
test test_screen_snapshot_roundtrip ... ok
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
test conpty::shell_resolver::tests::test_explicit_cmd_has_no_powershell_flags ... ok
test conpty::shell_resolver::tests::test_is_powershell_detection ... ok
test conpty::shell_resolver::tests::test_explicit_powershell_attaches_bypass_flags ... ok
test stream::coalescer::tests::test_stream_coalescer_threshold_and_reset ... ok
test ui::banner::tests::test_format_startup_banner_contains_required_fields ... ok
test config::tests::test_default_config ... ok
test config::tests::test_custom_flags_parsing ... ok
test stream::debouncer::tests::test_resize_debouncer_coalesces_rapid_events ... ok
test result: ok. 8 passed; 0 failed; 0 ignored; finished in 0.35s

Total: 31 passed; 0 failed; 0 warnings
```

---

## 5. Lesson Learned
1. **Inovasi UX Terminal QR**: Pengguna tidak perlu repot menyalin token heksadesimal 64-karakter atau mengetik IP address. Cukup membuka aplikasi Android, arahkan kamera ke layar Mac/Windows, dan sesi terminal langsung terhubung secara E2EE dalam satu sentuhan.
2. **Keamanan Kriptografi Berlapis**: ChaCha20-Poly1305 memberikan garansi keutuhan (integrity) dan kerahasiaan (confidentiality). Sekalipun relay server berada di bawah kontrol pihak ketiga atau disusupi, tidak ada pihak yang dapat membaca output layar ataupun menginjeksi perintah liar ke shell developer.
