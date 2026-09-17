# AI Task Report 034: Fix QR Pairing, Token Synchronization, and macOS App Config

## 1. Identitas Tugas
- **Nama Tugas**: Fix QR Pairing, Relay Auth Token Sync, Shell Sourcing Syntax, and Android QR Scanner UX Hardening
- **Nomor Laporan**: 034
- **Branch**: `fix/qr-pairing-and-token-sync`
- **Base Commit**: `427f163`
- **Repository**: `terminal-mirror` (`git@github.com:mufidhadi/terminal-mirror.git`)
- **Tech Stack**:
  - macOS Host: Rust (1.80+), Tokio, `tokio-tungstenite`, Darwin PTY (`nix`), ChaCha20-Poly1305 E2EE
  - Android Client: Kotlin, Jetpack Compose, Material3, OkHttp WebSocket, CameraX, Google ML Kit Barcode Scanning
  - Packaging & Pipeline: Python 3.12 (`uv`), AppleScript / macOS `.app` bundle, Gradle 8.7

---

## 2. Histori Aksi & Kronologi Solusi

### A. Investigasi Akar Masalah (Root Cause Analysis)
1. **Error Shell Syntax (`command not found: Pro`)**:
   - File `/Applications/Terminal Mirror.app/Contents/Resources/.env` baris 5 berisi `HOST_NAME=MacBook Pro Mas Mufid` tanpa tanda kutip.
   - Script launcher mengeksekusi `set -a; source "$RESOURCES_DIR/.env"; set +a`, sehingga shell zsh/sh mengeksekusi kata kedua `Pro` sebagai perintah independen.
2. **Error 401 Unauthorized (`Failed to connect to relay server`)**:
   - File `.env` aplikasi Mac berisi token lama `masmufid_super_secret_relay_2026`.
   - VPS Hostinger (`172.23.127.184:8888`) dan konfigurasi Android aktif menggunakan token otentikasi hex 64-karakter: `48f2a3db2786bfbd887e4f094bb7a7942b4dd257fe79d18bc0bbf6ce46188771`.
   - Hal ini menyebabkan Mac Host Agent ditolak oleh relay server VPS dengan status `HTTP error: 401 Unauthorized`.
3. **Scan QR Tidak Merespon di Ponsel**:
   - Mac Agent mencetak payload dengan token lama. Saat di-scan, Android mencoba konek menggunakan token lama tersebut dan langsung ditolak 401 oleh relay.
   - `ConnectionManager` di Android memperlakukan 401 sama seperti error jaringan umum (hanya loop reconnect tanpa toast notifikasi kegagalan otentikasi).
   - `QrScannerDialog` belum memiliki haptic feedback (getar) saat barcode terbaca oleh ML Kit.

### B. Siklus TDD (RED ➔ GREEN)
1. **Python Asset Pipeline**:
   - Ditulis unit test `test_create_mac_app_bundle_quotes_env_values` di `tests/test_asset_pipeline.py`.
   - Status awal: **RED** (AssertionError: string tidak ter-quote dan shell syntax check gagal).
   - Diimplementasikan fungsi `sanitize_env_file()` di `scripts/asset_pipeline/mac_bundler.py` yang otomatis menambahkan kutip dua `"{v}"` jika nilai mengandung spasi atau belum ter-quote.
   - Status akhir: **GREEN** (7 passed di `uv run pytest`).
2. **Android URI Parsing**:
   - Ditulis unit test `testParseCompactUriWithHexTokenAndSpacedHostName` di `PairingPayloadParserTest.kt`.
   - Status awal: **RED** (ComparisonFailure: query parameter tidak ter-decode URL).
   - Diimplementasikan `URLDecoder.decode(rawVal, "UTF-8")` di `PairingPayloadParser.kt`.
   - Status akhir: **GREEN** (`BUILD SUCCESSFUL`).
3. **Android Connection Lifecycle & 401 Auth Error**:
   - Ditulis unit test di `ConnectionStateTest.kt` dan `ConnectionManagerTest.kt`.
   - Status awal: **RED** (Compilation error: `ConnectionState.AuthFailed` belum ada).
   - Ditambahkan `ConnectionState.AuthFailed(val message: String)` di `ConnectionState.kt`.
   - Diperbarui `RelayClient.kt` untuk mendeteksi HTTP 401 dan memanggil callback `onAuthError()`.
   - Diperbarui `ConnectionManager.kt` untuk menghentikan loop reconnect saat otentikasi gagal dan menyetel state ke `AuthFailed`.
   - Status akhir: **GREEN** (`BUILD SUCCESSFUL`).

### C. macOS App Bundle & Configuration Update
- File `/Applications/Terminal Mirror.app/Contents/Resources/.env` diperbarui dengan token resmi VPS dan nilai string ter-quote rapi:
  ```env
  RELAY_SERVER_URL="ws://172.23.127.184:8888/ws"
  RELAY_AUTH_TOKEN="48f2a3db2786bfbd887e4f094bb7a7942b4dd257fe79d18bc0bbf6ce46188771"
  HOST_ID="macbook-pro"
  HOST_NAME="MacBook Pro Mas Mufid"
  SESSION_ID="mac-live-session"
  PASSPHRASE="batu-merah-kuda-terbang"
  RUST_LOG="info"
  ```
- Dilakukan kompilasi binary release terbaru: `cargo build --release -p terminal-mirror-mac`.
- Binary di-deploy ke `/Applications/Terminal Mirror.app/Contents/MacOS/terminal-mirror-mac`.
- Diverifikasi dengan `zsh -n` dan `sh -n`: 0 syntax error.

### D. Android Client UX Hardening & Deployment
- Di `QrScannerDialog.kt`:
  - Ditambahkan haptic feedback `view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)` saat barcode terdeteksi.
  - Ditambahkan `ContextCompat.getMainExecutor(ctx).execute { onPayloadScanned(payload) }` untuk menjamin eksekusi di Main Looper thread.
- Di `StatusHeader.kt`:
  - State `ConnectionState.AuthFailed` ditampilkan dengan chip merah bertuliskan `✕ 401 AUTH`.
- Di `MainActivity.kt`:
  - Ditambahkan Toast informatif: `"Koneksi ditolak relay: Token tidak cocok (401 Unauthorized)"`.
- Di `AndroidManifest.xml`:
  - Ditambahkan `<meta-data android:name="com.google.mlkit.vision.DEPENDENCIES" android:value="barcode" />`.
- Build APK debug (`gradle -p apps/android :app:assembleDebug`).
- Deploy dan install ke perangkat Motorola Moto G45 5G (`172.23.191.143:5555`) dan emulator Android (`emulator-5554`).

---

## 3. Hasil Pengujian & Verifikasi Nyata (Evidence-Based)

### A. Automated Test Suites
1. **Rust Workspace Tests (`cargo test --workspace`)**:
   - Total: **43 passed, 0 failed, 0 ignored**
   - Rincian: Mac agent (5), Protocol (14), Relay server (16), Windows agent (8).
2. **Rust Lints & Formatting**:
   - `cargo clippy --workspace --all-targets`: **0 warnings**
   - `cargo fmt --check`: **Clean**
3. **Python Pipeline Tests (`uv run pytest`)**:
   - Total: **7 passed, 6 skipped** (1.56s)
4. **Android Unit Tests (`gradle -p apps/android :app:testDebugUnitTest`)**:
   - Total: **All tests passed** (`BUILD SUCCESSFUL in 2s`)

### B. Verifikasi Keamanan (Secret & IP Leak Scan)
- Scan diff git untuk private IP dan auth token nyata:
  ```bash
  git diff | grep -E "172\.23\.|192\.168\."
  # Exit code 1 (Zero matches found)
  ```

### C. Verifikasi Fisik End-to-End (On-Device)
1. **Koneksi Mac Host Agent ke Relay Server**:
   - Command: `terminal-mirror-mac` dengan `.env` aktif.
   - Log asli:
     ```text
     2026-09-17T08:59:33.331677Z  INFO terminal_mirror_mac: Starting macOS Darwin PTY session with shell: /bin/zsh
     2026-09-17T08:59:33.333139Z  INFO terminal_mirror_mac::pty::darwin: Spawned macOS login shell (/bin/zsh) in PTY with PID Some(90997)
     2026-09-17T08:59:33.333259Z  INFO terminal_mirror_mac: Zero-Knowledge End-to-End Encryption (ChaCha20-Poly1305) ENABLED.
     2026-09-17T08:59:33.333285Z  INFO terminal_mirror_mac::network::client: Connecting to Relay Server: ws://172.23.127.184:8888/ws
     2026-09-17T08:59:33.367866Z  INFO terminal_mirror_mac::network::client: Successfully connected to Relay Hub as Host Publisher!
     ```
2. **Pairing & Sinkronisasi Android Client**:
   - Status chip berubah menjadi `● LIVE` (hijau).
   - Tab header otomatis sinkron menampilkan `MacBook Pro Mas Mufid`.
   - Screenshot pairing: `docs/screenshots/emulator_paired_success.png`.
3. **Eksekusi Perintah Dua Arah (Bidirectional PTY Streaming)**:
   - Perintah diketik dan dikirim dari Android client melalui ChaCha20-Poly1305 E2EE ke VPS Relay (`172.23.127.184:8888`), didekripsi oleh Mac Agent, dan dieksekusi di Darwin PTY `/bin/zsh`.
   - Respon shell zsh di-stream balik secara real-time ke Android Screen Buffer.
   - Bukti screenshot output mirrored: `docs/screenshots/emulator_command_output_mirrored.png`.

---

## 4. Daftar Kesulitan, Bug, dan Solusi

| Masalah / Bug | Dampak | Solusi |
|---|---|---|
| Unquoted `HOST_NAME` di `.env` Mac | Shell mengeksekusi `Pro` sebagai perintah (`command not found: Pro`). | Dibuat fungsi otomatis `sanitize_env_file()` di pipeline python dan file `.env` dikuoti (`"MacBook Pro Mas Mufid"`). |
| Auth Token Mismatch | Mac ditolak VPS Relay dengan HTTP 401 Unauthorized; QR code mencetak token salah. | Disinkronkan token di Mac `.env` dengan token VPS dan Android (`48f2a3db...`). |
| Infinite Reconnect on 401 | Klien terus-menerus mencoba reconnect saat token salah tanpa memberi tahu user. | Diimplementasikan state `ConnectionState.AuthFailed`, penghentian loop reconnect, chip `✕ 401 AUTH`, dan pesan Toast. |
| URL Encoding Spaced Host Name | Nama workstation di tab Android muncul sebagai `MacBook%20Pro`. | Ditambahkan `URLDecoder.decode(..., "UTF-8")` pada parser query string compact URI. |
| Wireless ADB Streamed Install Timeout | `adb install` via ZeroTier menggantung di "Performing Streamed Install". | Digunakan metode `adb push` ke `/data/local/tmp/app-debug.apk` dilanjutkan `pm install -r` lokal di device. |

---

## 5. Lesson Learned
1. **Shell Quoting Discipline**: Variabel lingkungan yang dimuat menggunakan `source` atau `set -a` dalam bash/zsh wajib diapit tanda kutip jika nilainya mengandung spasi, untuk mencegah shell memecah token menjadi argumen atau perintah terpisah.
2. **Explicit Authentication State**: Status otentikasi (seperti HTTP 401 Unauthorized) harus memiliki state siklus hidup terpisah dari kegagalan jaringan biasa. Mengulang koneksi (reconnect retry) saat token salah hanya membuang bandwidth dan menyembunyikan akar masalah dari pengguna.
3. **Physical Device Feedback**: Fitur berbasis kamera seperti scan QR wajib memberikan umpan balik fisik langsung (haptic vibration) begitu pola barcode terdeteksi agar pengguna mengetahui operasi telah berhasil tanpa harus menatap layar konfirmasi.
