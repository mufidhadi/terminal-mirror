# Laporan Akhir: Desain Teknis & Implementasi Modular Android Client (`apps/android`)
**Nomor Tugas**: TM-007  
**Tanggal**: 2026-09-17  
**Status**: SELESAI  

---

## 1. Informasi Project
- **Nama Tugas**: Android Client App Architecture, Termux SurfaceView Integration, and Hardware Keystore Spec
- **Nama Branch**: `feature/architecture-spec-and-submodules`
- **Nama & URL Repo**: `terminal-mirror` (`https://github.com/mufidhadi/terminal-mirror`)
- **Nomor Hash Commit**: `2026e70`
- **Tech Stack**:
  - Language: Kotlin 1.9+ / Java 17
  - UI Toolkit: Jetpack Compose (BOM 2024.05.00), Material 3
  - Terminal Rendering Subsystem: Termux `terminal-view` (`v0.118.0`) backed by `SurfaceView`
  - Networking & WebSocket: OkHttp 4.12.0
  - Cryptography & Keystore: `AndroidKeyStore` (KeyMint HAL v2 Curve25519 Ed25519/X25519 pada Android 13+, EC P-256 fallback)
  - Serialization: MessagePack (`msgpack-core:0.9.8`)
  - Concurrency: Kotlin Coroutines & StateFlow (`kotlinx-coroutines-android:1.8.1`)

---

## 2. Histori Aksi
1. **Riset & Validasi Komparatif Android Terminal Emulation**:
   - Melakukan riset internet mengenai integrasi terminal emulator di Jetpack Compose.
   - Menemukan bahwa me-render ANSI sequence berfrekuensi tinggi (puluhan frame per detik) menggunakan Canvas murni Jetpack Compose menyebabkan frame drops (jank) dan GC pause berlebih karena siklus recomposition yang intensif.
   - Memvalidasi pola arsitektur hybrid standar industri (seperti yang digunakan pada project NyaMux & TermLib): membungkus native `TerminalView` (subclass `SurfaceView` dari Termux) menggunakan Composable `AndroidView`.
   - Mengonfirmasi dukungan native Android Keystore terhadap algoritma Curve25519 (`Ed25519` untuk signing dan `XDH` untuk key agreement) sejak Android 13 (API level 33) melalui KeyMint HAL v2.
2. **Penyusunan Desain Teknis Komprehensif (`docs/ANDROID_APP_DESIGN.md`)**:
   - Merumuskan arsitektur mobile berorientasi efisiensi baterai dan perlindungan dev environment.
   - Mendokumentasikan mekanisme Safety Mode (Default Read-Only Lock) untuk mencegah pocket typing atau sentuhan tidak sengaja yang dapat mengacaukan terminal developer di workstation.
   - Merancang Programmer Accessory Bar berisi tombol kontrol (`ESC`, `TAB`, `CTRL`, `ALT`, `|`, `~`, `↑`, `↓`, `←`, `→`) dan emergency kill switch button (`Ctrl+Shift+Q`).
   - Merancang multi-workstation parallel streaming (macOS dan Windows simultan) dengan switching instan (< 16 ms) tanpa re-koneksi ulang.
   - Merancang ketahanan terhadap Android Doze Mode & aggressive OEM battery killers menggunakan `Foreground Service` (`TerminalMirrorService`) berjenis `connectedDevice`, `PARTIAL_WAKE_LOCK`, serta `ConnectivityManager.NetworkCallback` dengan exponential backoff dan randomized jitter.
3. **Penerapan Struktur Modular pada Kodebase Android (`apps/android/app/src/main/java/`)**:
   - `ui/components/AccessoryBar.kt`: Komponen baris tombol programmer keyboard dan tombol darurat kill session.
   - `ui/components/StatusHeader.kt`: TopAppBar Material 3 yang menampilkan status host aktif, indikator live streaming (`● LIVE` / `○ OFFLINE`), dan tombol toggle read-only lock.
   - `ui/components/WorkstationTabs.kt`: Tab selector lintas platform dengan ikon OS spesifik (Mac / Windows / Linux).
   - `network/ConnectionManager.kt`: Pengelola koneksi multi-session dengan exponential backoff dan randomized jitter.
   - `crypto/KeystoreManager.kt`: Wrapper hardware-backed Android Keystore dengan deteksi KeyMint Curve25519 pada Android 13+ serta ekspor public key Base64 untuk pairing QR Code.
   - `MainActivity.kt`: Refaktor bersih yang mengintegrasikan seluruh komponen modular dan Foreground Service.
4. **Evaluasi & Pengujian**:
   - Menjalankan `cargo test --workspace` untuk memverifikasi kompatibilitas protokol dan seluruh backend Rust: 100% lulus (15 tests, 0 warnings).
   - Melakukan audit ketersediaan environment lokal untuk Gradle/Java (dinyatakan secara transparan pada bagian gap pengujian).

---

## 3. List Kesulitan, Tantangan, Bug dan Solusi

| # | Kesulitan / Bug | Analisa Penyebab | Solusi |
|---|---|---|---|
| 1 | Risiko terminal rendering lag jika menggunakan Composable Canvas murni. | Terminal stream menerima potongan stream escape ANSI berkecepatan tinggi yang memicu puluhan kali recomposition Jetpack Compose per detik. | Menggunakan arsitektur hybrid: `AndroidView` membungkus Termux `TerminalView` yang berjalan di atas thread dedicated `SurfaceView` tanpa membebani thread UI Compose. |
| 2 | Resiko eksekusi perintah tak sengaja di workstation akibat sentuhan layar ponsel (pocket typing / accidental touch). | Layar sentuh mobile rentan terhadap ghost touches dan sentuhan tak disengaja yang dapat mengirimkan `Ctrl+C` atau `Enter` ke shell workstation yang sedang menjalankan script penting. | Mengaktifkan **Read-Only Safety Guard** secara default. Keyboard virtual dan accessory bar hanya dapat mengirimkan karakter input setelah developer secara eksplisit membuka gembok proteksi pada header aplikasi. |
| 3 | Koneksi socket mati saat layar ponsel mati atau masuk Doze Mode. | Sistem operasi Android secara agresif membunuh koneksi TCP background untuk menghemat baterai jika tidak berjalan di Foreground Service. | Mengimplementasikan `TerminalMirrorService` sebagai `Foreground Service` dengan notifikasi bertipe `connectedDevice` dan menahan `PARTIAL_WAKE_LOCK` selama streaming aktif. |
| 4 | Ketersediaan hardware key Curve25519 bervariasi antar versi Android. | Dukungan native KeyMint Curve25519 baru diresmikan pada Android 13 (API 33), sementara device Android 10–12 hanya memiliki hardware EC P-256 (`secp256r1`). | `KeystoreManager` mendeteksi API level secara runtime; jika API >= 33 dan vendor HAL mendukung, menggunakan `Ed25519`/`XDH`. Jika tidak, secara transparan beralih ke EC P-256 atau enkripsi lokal menggunakan kunci simetris AES-256 hardware. |

---

## 4. List Test yang Dilakukan & Hasil Test

### A. Pengujian Workspace Rust (Protokol & Server)
Perintah: `cargo test --workspace`
- Hasil: **15 tests passed, 0 failed, 0 warnings**
  - `protocol_test.rs`: 7 tests passed (MessagePack roundtrip, Utf8StreamChunker, PairingGuard 3-strikes, ScreenSnapshot zstd).
  - `terminal_mirror_windows`: 8 tests passed (ConPTY shell resolver, 200ms resize debouncer, coalescer backpressure, banner formatting).

### B. Gap Keterujian Lokal (Pernyataan Eksplisit Sesuai Prinsip Kerja)
- **Status Verifikasi Android**: Kode Kotlin, manifest, dan build config telah ditulis secara modular dan sesuai dengan spesifikasi Google Android 14 / Jetpack Compose.
- **Gap Nyata**: Pada laptop macOS pengembangan saat ini, Java Runtime Environment (`java`) dan Android SDK build-tools (`gradle`) tidak terpasang di sistem (`Unable to locate a Java Runtime`). Oleh karena itu, APK compilation dan instrumented UI test untuk `apps/android` **belum dapat dijalankan secara lokal di environment ini**. Build APK penuh akan didelegasikan pada pipeline CI/CD (GitHub Actions) yang memiliki environment Android SDK lengkap.

---

## 5. Lesson Learned
1. **Pentingnya SurfaceView untuk Stream Berfrekuensi Tinggi**: Jetpack Compose sangat unggul untuk UI aplikasi interaktif, namun untuk komponen yang mengonsumsi data biner terus-menerus (seperti terminal VT stream atau video stream), rendering off-thread lewat `SurfaceView` tetap menjadi pilihan yang paling stabil dan hemat sumber daya.
2. **Design for Mobile Accidents**: Berbeda dengan laptop di mana keyboard hanya ditekan secara sadar, perangkat mobile sering dimasukkan ke saku dalam keadaan aktif atau tersentuh secara tidak sengaja. Memasang pengaman **Read-Only by default** adalah keputusan desain paling krusial untuk mencegah bencana fatal pada terminal developer.
3. **Transparansi Bukti**: Menyatakan keterbatasan environment lokal secara eksplisit jauh lebih berharga dan profesional daripada mengklaim build sukses tanpa bukti eksekusi nyata.
