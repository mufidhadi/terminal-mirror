# AI Task Report: 005 - macOS Host Agent Architecture & Modular Implementation
**Nama Tugas**: Perancangan Khusus Arsitektur macOS Host Agent (`apps/mac`), Penyusunan Dokumen Desain Komprehensif (`docs/MAC_APP_DESIGN.md`), dan Implementasi Modularitas Darwin PTY  
**Tanggal Pelaksanaan**: 17 September 2026  
**Pelaksana**: Pair Programmer AI Assistant untuk Mas Mufid  
**Nama Repository**: `terminal-mirror`  
**URL Repository**: [https://github.com/mufidhadi/terminal-mirror](https://github.com/mufidhadi/terminal-mirror)  
**Nama Branch**: `feature/architecture-spec-and-submodules`  
**Nomor Hash Commit**: `db60a98`  

---

### 1. Histori Aksi
1. **Penyusunan Dokumen Desain Khusus macOS (`docs/MAC_APP_DESIGN.md`)**:
   - Menganalisis karakteristik mendalam sistem operasi macOS (Darwin):
     * Alokasi kernel PTY `/dev/ptmx` via `portable-pty`.
     * Eksekusi *login shell* wajib (`/bin/zsh -l`) agar file inisialisasi `.zprofile`, `.zshrc`, Homebrew `/opt/homebrew/bin`, dan variabel lingkungan (PATH) ter-load secara utuh.
     * Pengaturan `termios` raw mode dan penanganan `SIGWINCH` untuk resize jendela.
     * Integrasi lifecycle power management Apple (deteksi lid close / clamshell sleep via `kIOMessageSystemWillSleep` dan wake via `kIOMessageSystemHasPoweredOn`).
     * Strategi packaging Universal 2 Binary (`aarch64` Apple Silicon + `x86_64` Intel via `lipo`), Homebrew Formula, dan Apple Notarization (`notarytool`).
2. **Refactoring Modularitas `apps/mac` Mengikuti Prinsip SOLID**:
   - Memecah arsitektur monolitik menjadi modul-modul terpisah:
     * `apps/mac/src/config.rs`: Parser CLI dan variabel lingkungan menggunakan `clap` dengan dukungan `env`.
     * `apps/mac/src/pty/mod.rs` & `apps/mac/src/pty/darwin.rs`: Abstraksi Darwin PTY Session yang membungkus pemanggilan `/bin/zsh -l` dan propagasi environment terminal (`TERM=xterm-256color`, `COLORTERM=truecolor`).
     * `apps/mac/src/stream/mod.rs` & `apps/mac/src/stream/coalescer.rs`: Pengontrol backpressure aliran data dan adaptive coalescing.
     * `apps/mac/src/ui/mod.rs` & `apps/mac/src/ui/banner.rs`: Perender banner status interaktif dan informasi pairing.
     * `apps/mac/src/main.rs`: Orchestrator utama yang menghubungkan seluruh komponen secara asinkron via Tokio.
3. **Pengujian & Verifikasi CLI**:
   - Menjalankan `cargo test` di level root workspace (7 unit test lulus 100%).
   - Menjalankan binary macOS `cargo run -p terminal-mirror-mac -- --help`, memverifikasi output opsi CLI (relay-url, host-id, name, shell, auth-token, menu-bar) berjalan tanpa error (exit code 0).

---

### 2. Tech Stack
* **Target OS**: macOS 13+ (macOS Ventura, Sonoma, Sequoia)
* **Architectures**: Apple Silicon (M1/M2/M3/M4 ARM64) & Intel x86_64
* **PTY Subsystem**: Darwin `/dev/ptmx` via `portable-pty` 0.8
* **Login Shell**: `/bin/zsh -l`
* **Async Engine**: Tokio (kqueue event driver)
* **CLI & Config**: `clap` 4.5 (derive + env features)

---

### 3. List Kesulitan, Tantangan, Bug dan Solusi
1. **Ketiadaan Variabel PATH pada Non-Login Shell**:
   * *Tantangan*: Jika shell di-spawn langsung dengan `/bin/zsh`, command Homebrew seperti `code`, `uv`, atau tool custom yang ada di `.zshrc` tidak akan ditemukan di sesi remote.
   * *Solusi*: `DarwinPtySession` selalu menambahkan argumen `["-l"]` ke `CommandBuilder` agar Darwin menjalankan shell sebagai login shell resmi yang me-load seluruh profile lingkungan developer.
2. **Missing Feature `env` pada Crate Clap**:
   * *Bug/Issue*: Saat menambahkan atribut `#[arg(env = "...")]`, compiler sempat melempar error `method not found in Arg`.
   * *Solusi*: Menambahkan feature `"env"` secara eksplisit pada dependency `clap` di `apps/mac/Cargo.toml`.

---

### 4. List Test yang Dilakukan dan Hasilnya
1. `cargo test` (Root Workspace): **7 passed; 0 failed; 0 warnings**.
2. `cargo run -p terminal-mirror-mac -- --help`: **Exit code 0 (Berhasil)**.

---

### 5. Lesson Learned
1. Pada platform macOS, pemanggilan shell tanpa argumen `-l` adalah kesalahan umum yang sering merusak workflow developer karena binary di `/opt/homebrew/bin` atau `~/.cargo/bin` menjadi tidak dikenali.
2. Memisahkan PTY session Darwin ke modul terdedikasi (`pty::darwin`) menjaga kode agent Mac tetap rapi, modular, dan terisolasi dari kode agent Windows yang nanti akan menggunakan ConPTY.
