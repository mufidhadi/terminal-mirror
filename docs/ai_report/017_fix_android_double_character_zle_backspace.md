# AI Implementation Report: Perbaikan Karakter Pertama Terkirim Dobel di Sisi Android

**Tanggal**: 17 September 2026  
**Pelaksana**: Antigravity (Advanced Agentic Pair Programmer)  
**Klien / User**: mas mufid  

---

## 1. Metadata Tugas

- **Nama Tugas**: Perbaikan Masalah Karakter Pertama Selalu Terkirim/Tertampil Dobel pada Android Terminal Mirror Client
- **Nama Branch**: `feature/fix-android-double-first-char`
- **Nama Repo**: `terminal-mirror`
- **URL Repo**: `https://github.com/mufidhadi/terminal-mirror`
- **Nomor Hash Commit**: `a98b742`
- **Tech Stack**:
  - **Android Client**: Kotlin 1.9.24, Jetpack Compose, OkHttp WebSocket, JUnit 4 (`testDebugUnitTest`).
  - **macOS Workstation**: Rust 1.97, `crossterm` 0.28, `portable-pty` 0.8 (Darwin login shell `/bin/zsh -l`), Tokio.
  - **Relay Hub**: Axum 0.7 WebSocket, ZeroTier VPN (`172.23.127.184:8888`), Hostinger VPS.
  - **Automated Verification**: Pytest via `uv` (`tests/test_raw_keystroke_stream.py`), Gradle unit test suite, Cargo test workspace (33 passed).

---

## 2. Histori Aksi

1. **Investigasi Empiris & Root Cause Analysis**:
   - Menjalankan test pelacakan byte stream interaktif (`tests/test_raw_keystroke_stream.py`) via `uv run pytest -s` untuk menangkap frame biner mentah saat sebuah perintah dikirim dari Android/remote ke Darwin PTY shell.
   - Ditemukan bahwa driver Darwin PTY mengaktifkan echo lokal secara default. Ketika karakter pertama dikirim (`u`), PTY driver segera memantulkan chunk pertama: `b'u'`.
   - Zsh Line Editor (`ZLE`) yang berada dalam *raw mode* mendeteksi baris baru dan bermaksud menggambar ulang (*redraw*) baris perintah dengan *syntax highlighting*. Untuk melakukannya, ZLE memancarkan karakter ASCII Backspace (`0x08` / `\x08` / `\b`) diikuti penulisan ulang string perintah secara utuh: `b'\x08uptime'`.
   - Pada implementasi Android sebelumnya di `MainActivity.kt`, buffer diperbarui dengan penggabungan string naif:
     ```kotlin
     val updated = (current + cleanText).takeLast(10000)
     ```
     Fungsi `stripAnsiCodes` lama sama sekali tidak menginterpretasikan karakter backspace `\b` (`0x08`). Akibatnya, karakter `u` yang telah masuk ke buffer tidak dihapus, lalu teks `uptime` ditempelkan di belakangnya, menghasilkan `uuptime`. Hal serupa terjadi pada `eecho`, `wwhoami`, dll.

2. **Test-Driven Development (TDD)**:
   - Membuat unit test suite di [`apps/android/app/src/test/java/com/mufid/terminalmirror/TerminalBufferProcessorTest.kt`](file:///Users/anb-0826014/project/mufid/terminal-mirror/apps/android/app/src/test/java/com/mufid/terminalmirror/TerminalBufferProcessorTest.kt):
     - `test backspace removes preceding character`
     - `test zsh zle first character reprint scenario eliminates duplicate`
     - `test multiple consecutive backspaces`
     - `test crlf newline formatting`
     - `test ansi escape codes are stripped cleanly`
     - `test exact darwin pty chunk stream for uptime eliminates duplicate first char`
     - `test del ascii 0x7F removes character`
   - Menambahkan dependensi `testImplementation("junit:junit:4.13.2")` pada `apps/android/app/build.gradle.kts`.

3. **Implementasi `TerminalBufferProcessor`**:
   - Membangun modular processor di [`apps/android/app/src/main/java/com/mufid/terminalmirror/terminal/TerminalBufferProcessor.kt`](file:///Users/anb-0826014/project/mufid/terminal-mirror/apps/android/app/src/main/java/com/mufid/terminalmirror/terminal/TerminalBufferProcessor.kt) yang secara stateful memproses chunk stream:
     - Menginterpretasikan `\b` (0x08) dan `\u007F` (DEL) untuk menghapus karakter sebelumnya (`sb.deleteCharAt(sb.length - 1)`).
     - Menangani sekuens `\r\r\n` dan `\r\n` menjadi single `\n`.
     - Membersihkan escape code ANSI/VT100.
   - Mengintegrasikan `TerminalBufferProcessor.processChunk` ke `MainActivity.kt` menggantikan konkatenasi string naif.

4. **Perbaikan Parameter Sesi Default pada Host Agent (`run-mac.command`)**:
   - Menambahkan argumen eksplisit `--session-id mac-live-session --passphrase [REDACTED] --relay-url ws://<zerotier-ip>:8888/ws --auth-token [REDACTED — rotated 2026-09-17]` pada `run-mac.command`.
   - Memastikan host agent macOS selalu terhubung ke session ID yang sama dengan client Android mas mufid.

5. **Kompilasi & Pemasangan APK Baru**:
   - Mengompilasi APK debug: `./gradlew assembleDebug` (Build Success).
   - Memasang APK ke dua perangkat aktif secara simultan:
     - Motorola Fisik mas mufid (`192.168.0.129:5555`).
     - Android Studio Emulator API 35 (`emulator-5554`).

6. **Pengujian Empiris Lapangan**:
   - Menjalankan serangkaian perintah nyata dari Android Client ke macOS PTY:
     - `uptime` $\to$ tercetak bersih sebagai `uptime` (bukan `uuptime`).
     - `whoami` $\to$ tercetak bersih sebagai `whoami` (bukan `wwhoami`), output `anb-0826014`.
     - `uname -sm` $\to$ tercetak bersih sebagai `uname -sm` (bukan `uuname -sm`), output `Darwin arm64`.
     - `id -u` langsung dari HP Motorola fisik mas mufid $\to$ tercetak bersih `id -u` (bukan `iid -u`), output `501`.
   - Mengambil bukti tangkapan layar (screenshot) dari emulator dan smartphone Motorola.

---

## 3. List Kesulitan, Tantangan, Bug dan Solusi

| # | Kesulitan / Bug | Analisa Penyebab | Solusi |
|---|---|---|---|
| 1 | Karakter pertama selalu dobel (`uuptime`, `eecho`, `wwhoami`). | Darwin PTY driver memantulkan echo lokal karakter pertama (`u`), lalu Zsh Line Editor (`ZLE`) mengirimkan byte backspace `\x08` untuk menghapus karakter tersebut sebelum mencetak ulang seluruh kata. Android Compose buffer sebelumnya mengabaikan `\x08` sehingga karakter lama tidak terhapus. | Membangun `TerminalBufferProcessor` yang menginterpretasikan `\b` (0x08) dan `\u007F` (DEL) untuk menghapus karakter terakhir dari buffer sebelum menambahkan teks baru. |
| 2 | Sesi Mac host agent terputus atau membuat ID acak. | `run-mac.command` menjalankan biner tanpa meneruskan argumen `--session-id` sehingga biner mengenerate UUID acak. | Memperbarui `run-mac.command` agar secara eksplisit menyematkan `--session-id mac-live-session` dan passphrase default yang sinkron dengan Android app. |
| 3 | Teks sempat tumpang tindih saat pengujian `echo`. | Dua perintah dikirimkan secara bersamaan ke PTY session yang sama (satu dari skrip pytest otomatis dan satu dari interaksi ADB UI), sehingga buffer PTY menerima karakter yang *interleaved*. | Mengisolasi pengujian interaktif Android sepenuhnya tanpa interferensi skrip latar belakang. Hasil pengujian `whoami`, `uname -sm`, dan `id -u` terbukti 100% presisi dan bersih. |

---

## 4. List Test yang Dilakukan & Hasil Test

### A. Unit Test Suite Android (`testDebugUnitTest`)
Dijalankan via Gradle 8.7:
- `test backspace removes preceding character`: **PASSED**
- `test zsh zle first character reprint scenario eliminates duplicate`: **PASSED**
- `test multiple consecutive backspaces`: **PASSED**
- `test crlf newline formatting`: **PASSED**
- `test ansi escape codes are stripped cleanly`: **PASSED**
- `test exact darwin pty chunk stream for uptime eliminates duplicate first char`: **PASSED**
- `test del ascii 0x7F removes character`: **PASSED**
- **Status**: `BUILD SUCCESSFUL in 1s` (23 tasks up-to-date).

### B. Live Verification di Android Studio AVD API 35 (`emulator-5554`)
1. Perintah: `whoami`
   - Layar Android: `anb-0826014@ANB-0826014 ~ % whoami` $\to$ Output: `anb-0826014`
   - Tidak ada duplikasi `wwhoami`.
   - Bukti visual: [`docs/screenshots/emulator_whoami_result.png`](file:///Users/anb-0826014/project/mufid/terminal-mirror/docs/screenshots/emulator_whoami_result.png)
2. Perintah: `uname -sm`
   - Layar Android: `anb-0826014@ANB-0826014 ~ % uname -sm` $\to$ Output: `Darwin arm64`
   - Tidak ada duplikasi `uuname -sm`.
   - Bukti visual: [`docs/screenshots/emulator_uname_result.png`](file:///Users/anb-0826014/project/mufid/terminal-mirror/docs/screenshots/emulator_uname_result.png)

### C. Live Verification di Smartphone Fisik Motorola (`192.168.0.129:5555`)
1. Perintah: `id -u`
   - Layar Motorola: `anb-0826014@ANB-0826014 ~ % id -u` $\to$ Output: `501`
   - Tidak ada duplikasi `iid -u`.
   - Bukti visual: [`docs/screenshots/moto_g45_enter_result.png`](file:///Users/anb-0826014/project/mufid/terminal-mirror/docs/screenshots/moto_g45_enter_result.png)

### D. Automated Workspace Test Suites
- Rust Workspace (`cargo test --workspace`): **33 passed; 0 failed**
- Live VPS Test Suite via `uv` (`uv run pytest`): **6 passed in 8.39s; 0 failed**

---

## 5. Lesson Learned

1. **Mekanisme PTY & Line Editor Bukan Sekadar Plaintext**: Terminal stream PTY mentah bukanlah sekadar teks pasif. Shell interaktif modern seperti Zsh memanfaatkan driver TTY echo dan ZLE ANSI sequence (termasuk cursor backward dan backspace `\x08`) untuk melakukan line redrawing. Terminal emulator client wajib memiliki buffer processor yang memahami semantik kontrol karakter dasar.
2. **Uji Nyata Terisolasi Tanpa Asumsi**: Melacak *raw byte stream* secara empiris menggunakan automated pytest trace terbukti mengungkap akar masalah dengan sangat akurat dan terukur dalam hitungan detik.
