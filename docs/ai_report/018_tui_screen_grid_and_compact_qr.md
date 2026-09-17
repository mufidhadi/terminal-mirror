# Laporan Akhir: Implementasi 2D Terminal Screen Buffer (TUI ANSI Support) & Compact Side-by-Side QR Banner

## 1. Nama Tugas
**Implementasi 2D Terminal Screen Buffer Matrix untuk Render TUI Halus (Zero Stale Frame Stacking) dan Banner Terminal Compact QR Code Side-by-Side**

---

## 2. Informasi Eksekusi & Lingkungan
- **Tanggal**: 17 September 2026
- **Nama Branch**: `feature/tui-screen-grid-and-compact-qr`
- **Nama & URL Repository**: 
  - Nama: `terminal-mirror`
  - URL Remote: `git@github.com:mufidhadi/terminal-mirror.git`
- **Tech Stack**:
  - **Rust (1.75+)**: `crates/protocol` (Compact URI Scheme `tm://`, Zero-Knowledge ChaCha20-Poly1305, E2EE serialization), `apps/mac` (Banner Side-by-Side dengan `Dense1x2` Unicode half-blocks & Metadata layout 20 baris).
  - **Android (Kotlin, Jetpack Compose, Material3)**: `TerminalScreenBuffer` (2D character matrix, ANSI CSI parser, Alternate Screen Buffer `?1049`, Line/Screen Erasures `2J`/`2K`), `PairingPayloadParser` (`tm://` parser & JSON fallback), CameraX, Google ML Kit, Conscrypt E2EE engine.
  - **Python (via `uv`)**: Integration tests, WebSocket test harness, TUI benchmark animation suite (`tests/tui_spinner_demo.py`).
  - **Networking & Infra**: ZeroTier P2P mesh network (`856127940ccd3db1`), VPS Relay Server (`172.23.127.184:8888`), Motorola Moto G45 5G (`172.23.191.143:5555`), Android Studio AVD API 35 (`emulator-5554`).

---

## 3. Histori Aksi
1. **Pemeriksaan Bukti Empiris Masalah TUI Lama**:
   - Mengambil tangkapan layar langsung via adb dari perangkat Motorola Moto G45 5G: [`docs/screenshots/moto_g45_tui_chaos_evidence.png`](file:///Users/anb-0826014/project/mufid/terminal-mirror/docs/screenshots/moto_g45_tui_chaos_evidence.png).
   - Terlihat tumpukan puluhan baris animasi spinner CLI (`agy`) dan progress bar yang rusak parah karena aplikasi Android lama memperlakukan stream PTY sebagai 1D string log append tanpa menghapus karakter/baris lama saat menerima ANSI cursor addressing atau `\r`.
2. **Analisis & Solusi Compact QR Code (macOS Host Agent)**:
   - Ditemukan bahwa QR code lama berukuran besar (tinggi total banner 38 baris terminal) karena payload JSON 280 bytes dengan default error correction dan quiet zone 4 modul.
   - Mengimplementasikan format compact URI scheme: `tm://<relay_host>?s=<session>&k=<psk>&p=<passphrase>&pub=...&pin=...` pada `crates/protocol/src/crypto.rs`. Ukuran payload menyusut dari 280 bytes menjadi <80 bytes.
   - Mengubah layout rendering banner terminal pada `apps/mac/src/ui/banner.rs` menjadi **Side-by-Side**:
     - Kolom kiri: Unicode half-block QR Code compact (`Dense1x2`, `EcLevel::L`, `quiet_zone(false)`).
     - Kolom kanan: Metadata box berisi status active host, shell, session ID, Diceware passphrase, dan shortcut kill-switch.
     - Total tinggi seluruh banner + QR code turun menjadi **20 baris**, muat 100% di jendela terminal standar macOS (80x24) tanpa perlu resize!
3. **Penerapan TDD: 2D Terminal Screen Buffer (Android Kotlin)**:
   - Membuat unit test suite [`TerminalScreenBufferTest.kt`](file:///Users/anb-0826014/project/mufid/terminal-mirror/apps/android/app/src/test/java/com/mufid/terminalmirror/TerminalScreenBufferTest.kt) untuk memvalidasi:
     - Cursor positioning (`\x1b[H`, `\x1b[r;cH`).
     - Cursor movement (`A` Up, `B` Down, `C` Forward, `D` Backward).
     - Screen & Line erasures (`2J` clear screen, `2K` clear line, `0K`/`1K`).
     - Carriage return overwrite (`\r`).
     - Alternate screen buffer toggling (`?1049h` enter, `?1049l` exit).
     - Simulasi animasi progress bar / spinner bertimpa pada baris yang sama.
   - Mengimplementasikan modul [`TerminalScreenBuffer.kt`](file:///Users/anb-0826014/project/mufid/terminal-mirror/apps/android/app/src/main/java/com/mufid/terminalmirror/terminal/TerminalScreenBuffer.kt) dengan matriks 2D karakter (`cols=80`, `rows=24`), scrollback buffer history, dan stateful ANSI escape parser.
   - Membuat parser modular [`PairingPayloadParser.kt`](file:///Users/anb-0826014/project/mufid/terminal-mirror/apps/android/app/src/main/java/com/mufid/terminalmirror/model/PairingPayloadParser.kt) serta test suite [`PairingPayloadParserTest.kt`](file:///Users/anb-0826014/project/mufid/terminal-mirror/apps/android/app/src/test/java/com/mufid/terminalmirror/PairingPayloadParserTest.kt).
4. **Optimasi Ukuran APK & R8 Minification**:
   - Membatasi arsitektur NDK pada `arm64-v8a` di `build.gradle.kts`.
   - Mengaktifkan `isMinifyEnabled = true` dan `isShrinkResources = true` dengan aturan ProGuard lengkap (`proguard-rules.pro`).
   - Ukuran paket APK berhasil ditekan dari **37.4 MB** menjadi **7.9 MB** (pengurangan 79%). Waktu transfer via ZeroTier berkurang dari ~25 menit menjadi hanya **30 detik**.
5. **Dukungan Deep Link `tm://` & Multi-Client Dual Streaming**:
   - Menambahkan intent filter `<data android:scheme="tm" />` pada `AndroidManifest.xml` dan `onNewIntent` handler pada `MainActivity.kt`.
   - Menambahkan deteksi otomatis environment emulator untuk bridging relay via `127.0.0.1:8888` (ADB reverse proxy) berdampingan dengan koneksi ZeroTier fisik Motorola (`172.23.191.143`).
6. **Eksekusi Pengujian End-to-End Live TUI**:
   - Menjalankan script animasi TUI benchmark [`tests/tui_spinner_demo.py`](file:///Users/anb-0826014/project/mufid/terminal-mirror/tests/tui_spinner_demo.py) yang melakukan overwrite ANSI spinner dan progress bar pada baris yang sama secara real-time.
   - Memverifikasi hasil tampilan layar secara visual di perangkat: animasi berjalan mulus dan bersih dengan **0 duplicate stacked lines**!

---

## 4. Hasil Pengujian (Test Results)

| Test Suite | Framework / Tool | Jumlah Test | Status | Keterangan |
|---|---|:---:|:---:|---|
| **Rust Workspace Tests** | `cargo test --workspace` | 34 | **PASSED** (100%) | Mencakup protocol, mac banner compact height test, crypto E2EE roundtrip, relay hub lifecycle, windows debouncer |
| **Python Integration Tests** | `uv run pytest` | 6 | **PASSED** (100%) | Validasi Darwin PTY, VPS Relay streaming, raw keystroke passthrough |
| **Android Unit Tests** | `./gradlew testDebugUnitTest` | 17 | **PASSED** (100%) | Validasi `TerminalScreenBufferTest`, `PairingPayloadParserTest`, `TerminalBufferProcessorTest` |
| **TUI Benchmark Realtime** | `uv run tests/tui_spinner_demo.py` | 15 frames | **PASSED** | Overwrite satu baris berjalan sempurna tanpa frame sampah |
| **Multi-Subscriber Concurrency** | Relay `/metrics` query | 2 clients | **PASSED** | Motorola fisik & Emulator AVD streaming bersamaan dari 1 host macOS |

---

## 5. Bukti Empiris (Screenshots & Logs)
1. **Bukti Masalah TUI Lama (Frame Tumpuk & Berantakan)**:
   - Path: [`docs/screenshots/moto_g45_tui_chaos_evidence.png`](file:///Users/anb-0826014/project/mufid/terminal-mirror/docs/screenshots/moto_g45_tui_chaos_evidence.png)
   - Deskripsi: Tampilan layar Motorola sebelum perbaikan, puluhan baris frame animasi spinner bertumpuk dan tidak terhapus.
2. **Bukti Live Compact Banner Side-by-Side (Terminal macOS)**:
   - Log AppleScript Terminal.app: Tinggi banner hanya 20 baris terminal, muat utuh di atas prompt zsh standar 80x24 tanpa perlu maximize/resize window.
3. **Bukti Live Render TUI Berhasil di Android**:
   - Path: [`docs/screenshots/emulator_tui_render_success.png`](file:///Users/anb-0826014/project/mufid/terminal-mirror/docs/screenshots/emulator_tui_render_success.png)
   - Deskripsi: Hasil rendering animasi spinner dan progress bar. Teks `[✔] TUI Render Successful! Zero duplicate stacked lines.` tampil rapi di baris akhir tanpa tumpukan frame lama.
4. **Bukti Dual Client Concurrency**:
   - Path: [`docs/screenshots/emulator_tui_dual_client_success.png`](file:///Users/anb-0826014/project/mufid/terminal-mirror/docs/screenshots/emulator_tui_dual_client_success.png)
   - Metric Relay: `relay_active_subscribers 2`, `relay_frames_routed_total 1770`.

---

## 6. List Kesulitan, Tantangan, Bug & Solusi

1. **Tantangan 1: TUI Animation Frame Stacking di Android**
   - *Penyebab*: Stream Darwin PTY mengirim ANSI cursor commands (`\x1b[H`, `\x1b[2K`) dan `\r` untuk meng-overwrite teks baris sebelumnya saat beranimasi. Kode lama hanya melakukan string concatenation (`buffer + chunk`), sehingga baris lama tidak pernah tertimpa.
   - *Solusi*: Mengimplementasikan `TerminalScreenBuffer` berbasis matriks karakter 2D (`Array<CharArray>(rows, cols)`). Karakter ditulis ke koordinat baris/kolom spesifik; `\r` mengembalikan kursor ke kolom 0 baris aktif; `\x1b[2K` mengosongkan baris aktif; `\x1b[?1049h` mengaktifkan alternate screen buffer untuk TUI full-screen.

2. **Tantangan 2: QR Code Terpotong pada Terminal Standar macOS (80x24)**
   - *Penyebab*: Payload JSON 280 bytes menghasilkan matriks QR besar (versi 7-8). Ketika di-render vertikal di atas metadata box, total tinggi mencapai 38 baris terminal, terpotong di window standar 80x24.
   - *Solusi*: Mengadopsi URI scheme ringkas `tm://` (<80 bytes) dengan `EcLevel::L` dan `quiet_zone(false)`. Mengatur layout banner secara horizontal (side-by-side) antara QR code dan info box, membatasi total tinggi menjadi 20 baris terminal.

3. **Tantangan 3: Bottleneck Transfer APK 37MB via Wireless ADB over ZeroTier**
   - *Penyebab*: Protokol `adb push` / `adb install` menggunakan paket synchronous SYNC (chunk 64KB) yang membutuhkan ACK per-chunk. Dengan RTT ~200ms di tunnel ZeroTier, throughput teoritis maksimal tercekik di ~300 KB/s (realita ~40 KB/s), membutuhkan 20+ menit.
   - *Solusi*: Mengonfigurasi NDK `arm64-v8a` dan mengaktifkan R8 minification (`isMinifyEnabled = true`, `isShrinkResources = true`). Ukuran APK turun drastis dari 37.4 MB menjadi 7.9 MB (pangkas 79%). File 7.9 MB berhasil di-push ke `/data/local/tmp/` dalam 30 detik, lalu diinstal secara lokal via `pm install -r` dalam 3 detik!

4. **Tantangan 4: Emulator Android AVD Tidak Dapat Me-route Subnet ZeroTier (`172.23.0.0/16`)**
   - *Penyebab*: QEMU virtual network pada Android emulator hanya mengarahkan trafik ke subnet `10.0.2.0/24`, sehingga alamat IP VPS ZeroTier `172.23.127.184` berstatus `Network is unreachable`.
   - *Solusi*: Membuat automated TCP forwarder `scripts/tcp_forwarder.py` di host macOS (`0.0.0.0:8888 -> 172.23.127.184:8888`) dipadukan dengan `adb reverse tcp:8888 tcp:8888` dan deteksi runtime emulator pada `MainActivity.kt`. Emulator secara transparan terkoneksi ke relay yang sama tanpa mengganggu perangkat fisik.

---

## 7. Lesson Learned
- Format payload QR code pada terminal console harus dirancang seringkas mungkin (<100 bytes) menggunakan URI scheme padat agar versi QR tetap rendah (Versi 3-4), memungkinkan rendering berdampingan (side-by-side) yang ramah pada resolusi terminal standar 80x24.
- Rendering terminal pada klien mobile tidak boleh menggunakan linear text appending jika ingin mendukung aplikasi developer modern (seperti Antigravity CLI, top, vim, k9s); representasi 2D screen grid dengan cursor addressing dan alternate screen buffer adalah fondasi mutlak terminal emulation.
- Optimasi ukuran binary (NDK ABI filtering dan R8 minification) bukan sekadar efisiensi penyimpanan, melainkan kunci kelancaran remote OTA/ADB deployments di lingkungan jaringan bertaraf latensi tinggi (VPN/ZeroTier).
