# AI Task Report: 003 - Hardened Security & Stream Resilience Implementation
**Nama Tugas**: Penerapan 5 Solusi Arsitektural Defensif (Anti-Abuse Relay Governance, 3-Strike Auto-Burn Pairing, Asynchronous Decoupled PTY Engine, Android Foreground Service, dan Streaming UTF-8 Multibyte Chunker)  
**Tanggal Pelaksanaan**: 17 September 2026  
**Pelaksana**: Pair Programmer AI Assistant untuk Mas Mufid  
**Nama Repository**: `terminal-mirror`  
**URL Repository**: [https://github.com/mufidhadi/terminal-mirror](https://github.com/mufidhadi/terminal-mirror)  
**Nama Branch**: `feature/architecture-spec-and-submodules`  
**Nomor Hash Commit**: `3a6737a`  

---

### 1. Histori Aksi
1. **Penerapan Solusi 1: Anti-Abuse Relay Governance (`services/relay-server`)**:
   - Memodifikasi `services/relay-server/src/main.rs` dengan menambahkan **IP-based Rate Limiter** (maksimal 60 koneksi per menit per IP address).
   - Menambahkan pembatasan ukuran payload maksimal 64 KB (`MAX_PAYLOAD_BYTES = 65_536`) untuk mencegah serangan memory exhaustion atau abuse relay sebagai C2 reverse shell.
2. **Penerapan Solusi 2: High-Entropy Pairing & 3-Strike Auto-Burn (`crates/protocol`)**:
   - Mengimplementasikan `PairingGuard` di `crates/protocol/src/crypto.rs` dengan mekanisme *3-strikes lockout*: setelah 3 kali salah tebak, status sesi langsung di-*burn* secara permanen sehingga kebal dari serangan *online brute-force*.
   - Menambahkan dukungan untuk 4-word Diceware Passphrase (entropi ~51.7 bit) sebagai alternatif dari PIN 6-digit.
3. **Penerapan Solusi 3: Decoupled Asynchronous PTY Engine (`apps/mac` & `apps/windows`)**:
   - Menghubungkan PTY reader thread ke channel `mpsc::channel(1024)` bounded async di `apps/mac/src/main.rs` dan `apps/windows/src/main.rs`.
   - Mengeliminasi risiko PTY freeze atau CPU 100% saat pengguna menjalankan perintah berkecepatan tinggi seperti `cat big_file.log`.
4. **Penerapan Solusi 4: Android Foreground Service & WakeLock (`apps/android`)**:
   - Membuat `TerminalMirrorService.kt` yang mengimplementasikan `Service` dengan `NotificationCompat` dan `PowerManager.PARTIAL_WAKE_LOCK`.
   - Memperbarui `AndroidManifest.xml` dengan permission `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `WAKE_LOCK`, dan `POST_NOTIFICATIONS` untuk mencegah Android Doze Mode dan OEM battery killers memutus koneksi secara diam-diam.
5. **Penerapan Solusi 5: Streaming UTF-8 Boundary Chunker (`crates/protocol/src/utf8_chunker.rs`)**:
   - Mengimplementasikan `Utf8StreamChunker` yang mendeteksi pecahan byte multibyte UTF-8 (1-3 byte) di ujung buffer dan menahannya hingga chunk berikutnya tiba.
   - Mencegah error `InvalidUtf8Sequence` saat streaming emoji 4-byte (seperti 🚀) atau font Nerd Fonts () yang terpotong di perbatasan buffer.
6. **Pembaruan Dokumentasi Rekayasa Perangkat Lunak (`/docs`)**:
   - Memperbarui `docs/SECURITY_DESIGN.md`, `docs/SRS.md`, `docs/ARCHITECTURE_DIAGRAM.md`, dan `docs/DATA_DICTIONARY.md`.
7. **Pengujian TDD**:
   - Menambahkan unit test baru di `crates/protocol/tests/protocol_test.rs`:
     * `test_utf8_stream_chunker_multibyte_slicing` (menguji emoji roket 🚀 yang dipotong 2 byte di chunk 1 dan 2 byte di chunk 2).
     * `test_pairing_guard_three_strikes_auto_burn` (menguji kegagalan bertahap hingga auto-burn pada percobaan ke-3).
   - Menjalankan `cargo test` dan memastikan 7 unit test lulus 100% tanpa peringatan.

---

### 2. Tech Stack
* **Language**: Rust 1.97+
* **Async Engine**: Tokio MPSC channels, Axum, DashMap
* **Cryptography & Anti-Brute Force**: PairingGuard (3 strikes), ChaCha20-Poly1305, 4-word Diceware Passphrases
* **Encoding & Unicode**: Streaming UTF-8 boundary chunker
* **Android Native**: Kotlin, Foreground Service, Android WakeLock, Jetpack Compose

---

### 3. List Kesulitan, Tantangan, Bug dan Solusi
1. **Pemotongan Multibyte UTF-8**:
   * *Tantangan*: Karakter 4-byte bisa terpotong di tengah (misal byte index 4095 dari buffer 4096). Jika langsung di-parse, Rust akan panic atau melempar error decode.
   * *Solusi*: `Utf8StreamChunker` memanfaatkan method `std::str::from_utf8(&combined).err().unwrap().valid_up_to()` untuk menahan sisa byte yang belum tuntas ke dalam `pending_bytes` scratch buffer.
2. **Kekuatan Entropi vs Kemudahan Mobile**:
   * *Tantangan*: PIN 6-digit terlalu mudah di-brute force, tetapi passphrase 64 karakter terlalu sulit diketik di HP.
   * *Solusi*: Menggunakan kombinasi 4 kata pendek berbahasa alami (Diceware) ditambah pertahanan matematis *3-strikes auto-burn*.

---

### 4. List Test yang Dilakukan dan Hasilnya
Dijalankan via: `cargo test` di root workspace:

| Nama Test | Target / File | Hasil | Deskripsi |
| :--- | :--- | :--- | :--- |
| `test_utf8_stream_chunker_multibyte_slicing` | `crates/protocol` | **PASS (ok)** | Memastikan pemotongan emoji 4-byte (🚀) di tengah buffer disambung kembali secara sempurna tanpa error decode. |
| `test_pairing_guard_three_strikes_auto_burn` | `crates/protocol` | **PASS (ok)** | Memastikan percobaan salah ke-1 dan ke-2 menghasilkan peringatan, dan percobaan ke-3 memicu auto-burn permanen yang menolak percobaan berikutnya. |
| `test_pairing_payload_with_pin_and_trusted_device` | `crates/protocol` | **PASS (ok)** | Menguji format 4-word passphrase dan persistensi device pairing. |
| `test_session_role_subscription` | `crates/protocol` | **PASS (ok)** | Memverifikasi role `Spectator` vs `Admin`. |
| `test_screen_snapshot_roundtrip` | `crates/protocol` | **PASS (ok)** | Memverifikasi serialisasi snapshot grid visual `vt100`. |
| `test_packet_msgpack_roundtrip_terminal_output` | `crates/protocol` | **PASS (ok)** | Memverifikasi transmisi lossless ANSI stream. |
| `test_packet_creation_and_version` | `crates/protocol` | **PASS (ok)** | Memverifikasi envelope versi 1. |

Hasil Akhir: **7 passed; 0 failed; 0 ignored; 0 warnings; finished in 0.00s**.

---

### 5. Lesson Learned
1. Membangun terminal sharing yang aman di internet publik tidak cukup hanya dengan enkripsi data (E2EE), melainkan membutuhkan tata kelola anti-abuse di server relay agar server tidak disalahgunakan sebagai infrastruktur serangan malware (C2 reverse shell).
2. Mekanisme pertahanan *rate-limiting* dan *auto-burn* adalah kompensasi esensial ketika memberikan kemudahan input bagi pengguna mobile.
