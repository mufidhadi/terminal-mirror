# Laporan Akhir: Desain Teknis & Implementasi Modular Windows Host Agent (`apps/windows`)
**Nomor Tugas**: TM-006  
**Tanggal**: 2026-09-17  
**Status**: SELESAI  

---

## 1. Informasi Project
- **Nama Tugas**: Windows Host Agent Modular Architecture & ConPTY Hardening
- **Nama Branch**: `feature/architecture-spec-and-submodules`
- **Nama & URL Repo**: `terminal-mirror` (`https://github.com/mufidhadi/terminal-mirror`)
- **Nomor Hash Commit**: `3d950b0`
- **Tech Stack**:
  - Language: Rust 1.80+ (Edition 2021)
  - Subsystem: Windows Pseudo Console (`ConPTY` / `portable-pty`)
  - Async Runtime: `tokio` (mpsc, select, timers)
  - CLI & Config: `clap` (derive, env), `dotenvy`
  - Serialization & Protocol: `terminal-mirror-protocol` (MessagePack, Zstd, Utf8StreamChunker)
  - Logging & Telemetry: `tracing`, `tracing-subscriber`

---

## 2. Histori Aksi
1. **Riset & Audit Teknis**:
   - Melakukan riset internet mengenai praktik terbaik ConPTY di Rust dan mitigasi ConPTY "resize storm" yang memicu buffer redraw intensif saat client mobile berganti orientasi layar / membuka keyboard virtual.
   - Mengonfirmasi pola async sliding window debouncer (100–200ms) menggunakan Tokio actor channel sebagai standar industri terbaik.
2. **Penyusunan Desain Teknis Komprehensif (`docs/WINDOWS_APP_DESIGN.md`)**:
   - Memetakan 3 mode UX Windows: Interactive CLI, System Tray Daemon (`Shell_NotifyIcon`), dan Unattended Windows Service.
   - Mendokumentasikan arsitektur ConPTY, passthrough mode `0x8` (`PSEUDOCONSOLE_PASSTHROUGH_MODE` pada Win 11 22H2+), hierarki resolusi shell (`pwsh.exe` -> `powershell.exe` -> `cmd.exe`), UTF-8 code page `CP_UTF8 (65001)`, serta proteksi kredensial DPAPI (`CryptProtectData`).
3. **Penerapan Prinsip TDD & Implementasi Modular (`apps/windows/src/`)**:
   - `config.rs`: CLI parser berbasis `clap` dengan env fallback, flag `--tray`, `--service-install`, dan `--resize-debounce-ms`. Dilengkapi unit test parsing flags.
   - `conpty/shell_resolver.rs`: Mesin resolusi shell dengan injeksi otomatis flag `-NoLogo -ExecutionPolicy Bypass` khusus untuk PowerShell. Dilengkapi 3 unit test (explicit shell, PowerShell detection, Cmd fallback).
   - `conpty/session.rs`: ConPTY session lifecycle manager membungkus master/slave PTY dalam `Arc<Mutex<Box<dyn MasterPty + Send>>>` untuk thread safety lintas async runtime.
   - `stream/debouncer.rs`: ConPTY 200ms sliding window resize debouncer berbasis `tokio::select!` channel loop. Dilengkapi unit test async pembuktian batching event cepat menjadi 1 eksekusi akhir.
   - `stream/coalescer.rs`: Adaptive coalescer pendeteksi backpressure downstream (ambang batas 256 KB). Dilengkapi unit test batas threshold & reset.
   - `ui/banner.rs`: Formatter dan renderer ASCII status banner Windows Terminal. Dilengkapi unit test verifikasi string dan field status.
   - `main.rs`: Refaktor orkestrasi utama yang modular, decoupled, dan bersih mengikuti prinsip SOLID.
4. **Eksekusi Pengujian & Verifikasi**:
   - Menjalankan `cargo test` di seluruh workspace: 100% lulus (8 tests pada `terminal-mirror-windows`, 7 tests pada `terminal-mirror-protocol`).
   - Memverifikasi eksekusi binari CLI `cargo run -p terminal-mirror-windows -- --help` (exit code 0).

---

## 3. List Kesulitan, Tantangan, Bug dan Solusi

| # | Kesulitan / Bug | Analisa Penyebab | Solusi |
|---|---|---|---|
| 1 | `dyn MasterPty + Send` tidak mengimplementasikan `Sync` saat dibungkus `Arc<ConPtySession>`. | Rust `Arc<T>` mensyaratkan `T: Send + Sync` agar `Arc<T>` dapat dikirim antar-thread Tokio closure (`move \|cols, rows\|`). Tipe trait object dari `portable-pty` hanya `Send`. | Membungkus handle master dalam `Arc<Mutex<Box<dyn MasterPty + Send>>>`. Mutex menyediakan garansi `Sync` sehingga `ConPtySession` aman digunakan di lintas worker thread tanpa data race. |
| 2 | `anyhow::Error` dari `portable-pty` tidak bisa langsung dicast ke `Box<dyn std::error::Error>`. | `anyhow::Error` memiliki type semantic berbeda dengan trait object standar Rust. | Mengonversi error secara eksplisit menggunakan `std::io::Error::new(io::ErrorKind::Other, e.to_string())` yang mengimplementasikan `std::error::Error + Send + Sync`. |
| 3 | Test `test_default_config` gagal karena membaca environment variable `$SHELL` dari mesin macOS host (`/bin/zsh`). | Atribut `#[arg(short, long, env = "SHELL")]` pada struct config membaca env host saat test dijalankan. | Menghapus pembacaan env langsung di macro `clap` dan mendelegasikannya ke `resolve_windows_shell()` yang memiliki hierarki evaluasi ketat dan terisolasi. |
| 4 | ConPTY Resize Storm jika mobile mengirim event resize beruntun. | ConPTY mengkalkulasi ulang seluruh virtual buffer dan memuntahkan seluruh baris jika ukuran layar berubah secara cepat (mis. saat rotasi layar ponsel). | Mengimplementasikan `ResizeDebouncer` dengan sliding timer 200ms. Seluruh resize request dalam rentang 200ms digabung dan hanya event terakhir yang diteruskan ke PTY master. |

---

## 4. List Test yang Dilakukan & Hasil Test

Eksekusi: `cargo test`
```text
running 7 tests (protocol_test.rs)
test test_utf8_stream_chunker_multibyte_slicing ... ok
test test_pairing_guard_three_strikes_auto_burn ... ok
test test_packet_creation_and_version ... ok
test test_pairing_payload_with_pin_and_trusted_device ... ok
test test_session_role_subscription ... ok
test test_packet_msgpack_roundtrip_terminal_output_compressed ... ok
test test_screen_snapshot_roundtrip ... ok
test result: ok. 7 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.00s

running 8 tests (terminal-mirror-windows)
test conpty::shell_resolver::tests::test_explicit_cmd_has_no_powershell_flags ... ok
test conpty::shell_resolver::tests::test_is_powershell_detection ... ok
test conpty::shell_resolver::tests::test_explicit_powershell_attaches_bypass_flags ... ok
test stream::coalescer::tests::test_stream_coalescer_threshold_and_reset ... ok
test ui::banner::tests::test_format_startup_banner_contains_required_fields ... ok
test config::tests::test_default_config ... ok
test config::tests::test_custom_flags_parsing ... ok
test stream::debouncer::tests::test_resize_debouncer_coalesces_rapid_events ... ok
test result: ok. 8 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.35s
```

Verifikasi CLI: `cargo run -p terminal-mirror-windows -- --help`
- Status: Exit code 0
- Output bantuan rapi dengan dukungan flag, short codes, default values, dan environment variable fallback.

---

## 5. Lesson Learned
1. **ConPTY vs Unix PTY Semantics**: Unix PTY (macOS/Linux) merespons perubahan `TIOCSWINSZ` dengan mengirimkan sinyal `SIGWINCH` ke foreground process group secara asinkron tanpa merombak internal kernel buffer. Di Windows, ConPTY mengelola screen buffer virtual di user-mode dan mengkomputasi ulang glyph alignment secara sinkron saat `ResizePseudoConsole` dipanggil. Debouncing bukan sekadar optimasi opsional di Windows, melainkan mitigasi esensial untuk mencegah screen flickering dan CPU spike.
2. **Cross-Platform Compilation Hygiene**: Memisahkan layer resolusi shell dan PTY abstraction memungkinkan kode Windows di-compile dan di-test unit secara menyeluruh bahkan di host developer macOS tanpa memecah build pipeline.
3. **Execution Policy Friction**: Pengembang Windows sering menghadapi issue script execution policy (`Restricted`). Menginjeksi argumen `-ExecutionPolicy Bypass` secara transparan saat meluncurkan PowerShell memastikan shell environment selalu siap pakai tanpa memaksa user mengubah registry sistem.
