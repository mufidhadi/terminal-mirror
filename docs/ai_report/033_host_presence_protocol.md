# Laporan Akhir: Host Presence Protocol & Honest Live Status

- **Nama Tugas**: Solusi Masalah Status Terhubung Palsu: Host Presence Protocol & Honest Live Status (Opsi A)
- **Nomor Hash Commit**: `051b6c0` (kode implementasi & tests), `<pending>` (dokumentasi & screenshots)
- **Nama Branch**: `feature/host-presence-protocol`
- **Nama dan URL Repo**: `terminal-mirror` (`git@github.com:mufidhadi/terminal-mirror.git` / `https://github.com/mufidhadi/terminal-mirror`)
- **Tech Stack**:
  - Rust 1.97.0 (Cargo workspace, Axum 0.7, Tokio 1.38, DashMap, rmp-serde / MessagePack, tokio-tungstenite)
  - Android Kotlin (Jetpack Compose, AndroidX Insets API 35, OkHttp WebSocket, Jackson MessagePack)
  - Python 3.12 (uv, pytest)
  - ZeroTier VPN (Laptop: `172.23.220.206`, Moto G45: `172.23.191.143`, VPS Hostinger: `172.23.127.184`)
  - Docker & Docker Compose (VPS Relay Server)

---

## 1. Latar Belakang & Analisis Masalah

Sebelum implementasi ini, aplikasi Android sering menampilkan status `● LIVE` (hijau) meskipun laptop Mac mas mufid tidak sedang menyalakan terminal agent.

### Akar Masalah (Root Cause):
1. **Pencampuran Konsep Socket Relay vs Kehadiran Terminal Host**:
   Aplikasi Android hanya memantau apakah koneksi WebSocket ke Relay Server di VPS berhasil terbuka (`WebSocketListener.onOpen()`). Begitu socket ke relay terbuka, Android langsung mengasumsikan sesi dalam keadaan `● LIVE`.
2. **Tidak Ada Paket Notifikasi Kehadiran Host**:
   Protokol MessagePack sebelumnya hanya memiliki paket data (`TerminalOutput`, `TerminalInput`, `ScreenStateSync`, `Error`, `EncryptedBlob`). Tidak ada paket kontrol yang memberitahu client apakah host agent sedang aktif menyiarkan terminal atau sedang offline.
3. **Silent Keystroke Drop**:
   Karena status tampak `● LIVE`, pengguna mengetikkan perintah di keyboard Android. Keystroke dikirim ke relay, namun di relay di-drop diam-diam karena channel `host_tx` bernilai `None`. Pengguna mengira ada bug input drop.

---

## 2. Solusi yang Diimplementasikan (Opsi A & Dynamic Host Name)

Sesuai persetujuan mas mufid pada `plan_host_presence_protocol.md` (Opsi A):
1. **Protokol Crate (`crates/protocol`)**:
   - Menambahkan payload `HostPresencePayload` (`session_id`, `online: bool`, `host_name: Option<String>`, `shell: Option<String>`).
   - Menambahkan varian `PacketPayload::HostPresence(HostPresencePayload)` dan constructor `Packet::host_presence(...)`.
2. **Relay Server Hub & WebSocket Handler (`services/relay-server`)**:
   - `SessionRouter` diperluas dengan `host_tx: HostSender` (`Arc<Mutex<Option<(uuid::Uuid, mpsc::Sender<Vec<u8>>)>>>`) dan `host_meta: Arc<Mutex<Option<HostMetadata>>>`.
   - Mencegah race condition penggantian host: unregister host memverifikasi UUID koneksi aktif.
   - Saat subscriber connect, relay langsung mengirimkan status initial presence host (`online: true/false`).
   - Saat host connect, relay broadcast `HostPresence { online: true, host_name, shell }` ke seluruh subscriber.
   - Saat host disconnect, relay broadcast `HostPresence { online: false }` ke seluruh subscriber.
3. **Host Agent (`apps/mac`)**:
   - Menambahkan query parameters `&host_name=...&shell=...` pada handshake WebSocket.
   - Mengirimkan WS Ping heartbeat setiap 15 detik untuk menjaga socket tetap aktif dan mencegah idle zombie timeout.
4. **Android Client (`apps/android`)**:
   - Memisahkan status koneksi relay (`isRelayConnected`) dan keberadaan host agent (`isHostOnline`).
   - Status `isLive` adalah `isRelayConnected && isHostOnline`.
   - **Chip TopBar**:
     - Jika relay terhubung tapi host agent mati: Menampilkan `○ HOST OFF` (oranye / `TerminalColors.Warning`).
     - Jika host agent menyala: Berubah seketika menjadi `● LIVE` (hijau / `TerminalColors.Live`).
     - Jika relay putus: Menampilkan `○ OFF` atau retry `… R1 2s`.
   - **Input Locking & Queue Prevention**:
     - Saat host offline, input text field dan programmer accessory bar dikunci (`input paused`) untuk mencegah hilangnya keystroke tanpa feedback.
     - Teks honest status di bagian bawah layar menginformasikan status secara akurat.
   - **Auto-Update Host Name**:
     - Subtitle TopBar dan label tab workstation secara dinamis ter-update dari metadata host agent (mis. `"MacBook Pro Mas Mufid"` dan `"/bin/zsh"`).

---

## 3. Histori Aksi

1. **Step 1 (Protocol Crate TDD)**:
   - Menambahkan unit tests T1.1 - T1.3 di `crates/protocol/tests/protocol_test.rs` (RED).
   - Mengimplementasikan `HostPresencePayload` di `crates/protocol/src/packet.rs` (GREEN).
2. **Step 2 (Relay Server Hub & Handler TDD)**:
   - Menambahkan unit test T2.1 - T2.4 di `services/relay-server/src/hub/session_hub.rs` (RED).
   - Mengimplementasikan `HostMetadata`, UUID connection guard, `register_host`, `unregister_host`, dan `get_host_presence` (GREEN).
   - Menambahkan e2e pipeline test T3.1 - T3.5 di `services/relay-server/tests/e2e_pipeline_test.rs` (GREEN).
3. **Step 3 (Host Agent Mac)**:
   - Menambahkan metadata query encoder dan 15s heartbeat ping loop di `apps/mac/src/network/client.rs` dan `apps/mac/src/main.rs`.
   - Verifikasi format URL via unit tests `cargo test -p terminal-mirror-mac`.
4. **Step 4 (Android Client TDD)**:
   - Menambahkan unit tests T1.4 - T1.5 di `ProtocolCodecTest.kt` dan T4.1 - T4.2 di `TerminalUiHelpersTest.kt` (RED).
   - Konfigurasi JDK 21 di `gradle.properties` (`org.gradle.java.home`).
   - Mengimplementasikan `DecodedPayload.HostPresence` di `ProtocolCodec.kt` (GREEN).
   - Mengimplementasikan `isHostOnline` di `TerminalUiHelpers.kt` (GREEN).
   - Mengintegrasikan state management ke `StatusHeader.kt`, `ConnectionStatusCard.kt`, dan `MainActivity.kt`.
   - Lolos seluruh 51 unit tests JUnit Android dan build APK `app-debug.apk`.
5. **Step 5 (Python Regression & Secret Scan)**:
   - Menjalankan `uv run pytest` (6 passed, 6 skipped).
   - Scan git diff untuk pencegahan kebocoran IP/kredensial internal (`grep -rnE "172\.23\.|192\.168\."`).
6. **Step 6 (Physical On-Device Verification)**:
   - Instalasi APK ke Motorola Moto G45 5G (`172.23.191.143:5555`).
   - Verifikasi Test 6.1 (Host Off): Screenshot `docs/screenshots/moto_g45_host_offline_verified.png`.
   - Deploy image relay-server terbaru ke VPS Hostinger (`172.23.127.184`) via Git pull & Docker Compose up.
   - Verifikasi Test 6.2 (Live Transition): Mac Agent connect -> Android seketika `● LIVE`. Screenshot `docs/screenshots/moto_g45_host_online_verified.png`.
   - Verifikasi Test 6.3 (Two-way Keystroke): Eksekusi perintah `uname -a` dari Android ke Darwin Mac PTY. Screenshot `docs/screenshots/moto_g45_uname_result_verified.png`.
   - Verifikasi Test 6.4 (Disconnect Transition): Mac Agent dihentikan -> Android seketika kembali ke `○ HOST OFF` dan input terkunci. Screenshot `docs/screenshots/moto_g45_host_disconnect_verified.png`.

---

## 4. Bukti dan Hasil Pengujian (Data-Driven Evidence)

### A. Pengujian Unit & Integrasi Rust
- **Crate `terminal-mirror-protocol`**: 14 tests passed (100% GREEN).
- **Crate `terminal-mirror-relay`**: 16 tests passed (11 unit + 5 e2e pipeline, 100% GREEN).
- **Crate `terminal-mirror-mac`**: 5 tests passed (100% GREEN).
- **Crate `terminal-mirror-windows`**: 8 tests passed (100% GREEN).
- **Clippy**: 0 warnings (`cargo clippy --workspace --all-targets`).
- **Formatter**: Clean (`cargo fmt --check`).

### B. Pengujian Android JUnit
- **Total Tests**: 51 passed, 0 failed (`gradle -p apps/android :app:testDebugUnitTest`).
- **Build APK**: `BUILD SUCCESSFUL` (`apps/android/app/build/outputs/apk/debug/app-debug.apk`, 23MB).

### C. Pengujian Python (uv)
- `uv run pytest` -> 6 passed, 6 skipped.

### D. Pengujian Fisik On-Device (Motorola Moto G45 5G, Android 15 API 35)
1. **Kondisi Host Offline (Relay Connected)**:
   - Path Screenshot: `docs/screenshots/moto_g45_host_offline_verified.png`
   - Hasil: TopBar Chip menunjukkan `○ HOST OFF` (oranye), card viewport menunjukkan `RELAY OK — HOST OFFLINE`, input field terkunci dengan pesan jujur `Relay connected — host agent offline — input paused. Tap refresh to reconnect.`.
2. **Kondisi Host Online (Live Transition)**:
   - Path Screenshot: `docs/screenshots/moto_g45_host_online_verified.png`
   - Hasil: Chip berubah seketika menjadi `● LIVE` (hijau), nama workstation ter-update dinamis menjadi `MacBook Pro Mas Mufid`, input field & programmer accessory bar terbuka otomatis.
3. **Kondisi Eksekusi Perintah Dua Arah (Bidirectional PTY)**:
   - Path Screenshot: `docs/screenshots/moto_g45_uname_result_verified.png`
   - Hasil: Perintah `uname -a` diketik dari keyboard Android, dieksekusi di Darwin Mac (`Darwin ANB-0826014.local 25.6.0 ... arm64`), dan output ter-mirror kembali secara real-time ke layar Moto G45.
4. **Kondisi Host Disconnect**:
   - Path Screenshot: `docs/screenshots/moto_g45_host_disconnect_verified.png`
   - Hasil: Begitu Mac agent dimatikan, TopBar chip langsung kembali ke `○ HOST OFF` (oranye), input text field langsung terkunci untuk mencegah hilangnya keystroke, dan riwayat terminal buffer tetap utuh.

---

## 5. Kesulitan, Tantangan, Bug, dan Solusi

1. **Tantangan: Java Version Incompatibility pada Gradle Build**:
   - *Problem*: Terminal macOS default mengarah ke OpenJDK 26 (`/opt/homebrew/Cellar/openjdk/26.0.2.1`), yang menyebabkan `jlink` error pada `android-35` `core-for-system-modules.jar`.
   - *Solusi*: Mengidentifikasi JDK 21 yang disediakan Android Studio di `/Applications/Android Studio.app/Contents/jbr/Contents/Home` dan mengonfigurasikannya secara eksplisit di `apps/android/gradle.properties` (`org.gradle.java.home=...`).
2. **Tantangan: Race Condition Disconnect Host (UUID Collision Guard)**:
   - *Problem*: Jika Host reconnect atau digantikan oleh instance baru, paket disconnect dari instance lama dapat secara keliru mematikan instance baru jika unregister hanya berdasarkan `session_id`.
   - *Solusi*: Menggunakan UUID v4 unik per koneksi (`connection_id`). Method `unregister_host(session_id, conn_id)` hanya menghapus host jika UUID cocok.
3. **Tantangan: Subscriber Join Race Condition**:
   - *Problem*: Jika subscriber melakukan query status kehadiran host sebelum berlangganan channel broadcast, paket update status yang terjadi di antara kedua operasi tersebut akan hilang.
   - *Solusi*: Subscriber memanggil `broadcast_tx.subscribe()` terlebih dahulu, baru kemudian mengirimkan initial presence packet ke socket subscriber.

---

## 6. Lesson Learned

- Prinsip kejujuran sistem (Honest Live Status): Jangan pernah menyamakan "socket terhubung ke relay" dengan "terminal host aktif". Dua layer ini memiliki state yang berbeda dan harus ditampilkan apa adanya kepada pengguna.
- Pendekatan TDD (Test-Driven Development) terbukti menghemat waktu dan mencegah regresi pada sistem multi-tier (Protocol -> Relay Server Hub -> Mac Agent -> Android Client).
- Verifikasi fisik on-device nyata (Motorola Moto G45 5G) memberikan kepastian 100% bahwa user experience di lapangan berjalan sesuai spesifikasi dan tidak ada asumsi yang tidak teruji.
