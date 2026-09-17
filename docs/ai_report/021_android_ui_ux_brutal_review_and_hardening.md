# AI Report 021: Android UI/UX Brutal Review & Hardening (lanjutan AGY fcb24c77)

## 1. Nama Tugas
Review brutal visual UI/UX hasil AGY conversation `fcb24c77-4b74-4f51-a074-8f833e84c2fd` ("Remote Terminal Mirroring System") + hardening prioritas Android client.

## 2. Histori Aksi
1. Pulihkan konteks AGY dari SQLite `~/.gemini/antigravity-cli/conversations/fcb24c77-*.db` (3257 steps): title `Remote Terminal Mirroring System`, workdir `/Users/anb-0826014/project/mufid`, Cwd efektif `terminal-mirror`, branch terakhir `feature/app-bundle-and-unified-icon` (commit `a70f092`).
2. Audit visual dari `docs/screenshots/*.png` (36 file) + baca kode: `MainActivity.kt` (384 baris), `StatusHeader.kt`, `WorkstationTabs.kt`, `AccessoryBar.kt`, `QrScannerDialog.kt`, `docs/ANDROID_APP_DESIGN.md`.
3. Riset referensi: Material3 insets/TopAppBar/Scaffold (developer.android.com), `ScrollableTabRow` vs `TabRow`, touch target 48dp (Material + Android accessibility).
4. Buat branch baru `fix/android-ui-ux-brutal-review` dari `a70f092` (anti merusak `main`/branch AGY).
5. TDD: tulis `TerminalUiHelpersTest.kt` (9 test) dulu, lalu implementasi `ui/TerminalUiHelpers.kt` + refactor 4 file UI.
6. Verifikasi: `cargo test --workspace` (34 passed), `uv run pytest -q` (12 passed). Android Gradle tidak dapat dijalankan di env ini (gap eksplisit).
7. Commit `ec0ae3c` + laporan ini, push branch.

## 3. Nomor Hash Commit
- Commit fix: `ec0ae3c`
- Commit laporan: (diisi setelah commit laporan; lihat `git log --oneline -3`)
- Base AGY: `a70f092` (`feature/app-bundle-and-unified-icon`), `main` di `3f5ac98`.

## 4. Nama Branch
- `fix/android-ui-ux-brutal-review`

## 5. Nama dan URL Repo
- Nama: `terminal-mirror`
- Remote: `git@github.com:mufidhadi/terminal-mirror.git`
- Web: `https://github.com/mufidhadi/terminal-mirror`

## 6. Tech Stack
- Android: Kotlin, Jetpack Compose Material3, CameraX + ML Kit, OkHttp WS, msgpack, Coroutines.
- Backend: Rust 1.84 workspace (Tokio, portable-pty, ChaCha20-Poly1305), relay Axum.
- Test: JUnit4 (baru), `cargo test`, `pytest` (scripts/build_assets.py).
- Referensi: developer.android.com (Material3 insets, TabRow/ScrollableTabRow, touch target 48dp).

## 7. Brutal Review (berbukti, bukan selera)

Semua poin di bawah direproduksi dari screenshot + `file:line` pada base `a70f092`.

1. **Judul kepotong ("erminal Mirror")** — Bukti: `emulator_current.png`, `emulator_paired_live.png`, `emulator_tui_dual_client_success.png` semua memotong huruf pertama. Penyebab: `StatusHeader.kt:30-46` TopAppBar tanpa `maxLines/overflow`, tidak ada handling inset/status-bar sehingga judul mentok tepi. Level: junior — layout top bar tidak pernah diuji di device 360dp.
2. **Subtitle wrap pecah 2 baris** — Bukti: `emulator_current.png` ("MacBook Pro (Darwin zsh) • /" + "bin/zsh" di baris bawah). Penyebab: `StatusHeader.kt:39-43` + `MainActivity.kt:115` string `"${hostName} • ${shell}"` tanpa `maxLines=1/ellipsis`. Perbaikan: `TerminalUiHelpers.sessionSubtitle()` + ellipsis.
3. **Tab workstation kepotong** — Bukti: `emulator_current.png` tab hanya tampil "MacBook Pro (Darwin" (kurang ")"), tab kedua redup kontras rendah. Penyebab: `WorkstationTabs.kt:28-33` memakai `TabRow` fixed equal-width untuk label panjang + `Text(maxLines=1)` tanpa `overflow=Ellipsis`. Dok resmi: `TabRow` equal-space; label panjang wajib `ScrollableTabRow`. Perbaikan: ganti ke `ScrollableTabRow(edgePadding=16.dp)` + ellipsis.
4. **Banner ASCII 80-kolom selalu pecah di HP** — Bukti: semua screenshot idle (`emulator_current`, `live_terminal_streaming_avd35`, `emulator_paired_live`) menampilkan box `┌──┐` patah: `"Status : CONNECTING to VPS Relay"` wrap ke `"(172.23.127.184:8888)...|"`, `"Keystrokes Active)|"` nyasar. Penyebab: `MainActivity.kt:362-377` `padEnd(39)` mengasumsikan lebar 80 kolom; viewport HP ~30-36 kolom @12sp sehingga wrap tak terhindarkan. Ini bukan "terminal real", ini placeholder yang di-render sebagai teks statis. Perbaikan: `bannerLines()` tanpa box-drawing, tiap baris ≤64 char, tanpa IP/token.
5. **Tombol accessory tidak terbaca ("ES/TA/CT/AL")** — Bukti: semua screenshot bar bawah menampilkan `ES TA CT AL | ~ ↑ ↓ ← → KILL`. Penyebab: `AccessoryBar.kt:20-47` 11 tombol dalam satu `Row` dengan `weight(1f).height(34.dp)` → tiap tombol ~32dp, teks `ESC/TAB/CTRL/ALT` terpotong, tinggi <48dp melanggar Material accessibility (min 48x48dp, sumber: Material Design + Android a11y). Perbaikan: `LazyRow` scroll horizontal, `widthIn(min=56.dp).height(48.dp)`, label penuh, font 12sp.
6. **Bug fungsional tersembunyi: CTRL/ALT terkirim sebagai teks literal** — Bukti kode `MainActivity.kt:211-230` (base): `when` hanya memetakan ENTER/TAB/ESC/CTRL+C/D/Z/UP/DOWN/LEFT/RIGHT; `"CTRL"`, `"ALT"`, `"|"`, `"~"`, `"↑"` jatuh ke `else -> rawString.toByteArray()` sehingga mengetik tombol CTRL di HP mengirim huruf "CTRL" ke shell remote. Tombol `↑↓←→` versi unicode juga tidak dipetakan (hanya `UP/DOWN/...`). Ini bug, bukan kosmetik. Perbaikan: `KeystrokeEncoder.encode()` kembalikan `null` untuk bare modifier + toast hint; petakan `↑↓←→` ke `ESC[A/B/D/C`.
7. **Rahasia + IP asli ter-commit di repo publik** — Bukti kode base `MainActivity.kt:160-161,253`: `ws://172.23.127.184:8888/ws?token=masmufid_super_secret_relay_2026...`, `buildInitialBanner` menampilkan `172.23.127.184:8888` di layar (terlihat di semua screenshot). Melanggar `AGENTS.md` §3 (never commit real IPs/secrets). Perbaikan: `RelayConfig` dengan `PLACEHOLDER_HOST=relay.example.internal:8888`, `PLACEHOLDER_TOKEN=RELAY_TOKEN_PLACEHOLDER`, `wsUrl()` validasi non-blank; tidak ada lagi literal IP/token di `MainActivity.kt` (terverifikasi via `git diff`).
8. **Satu warna untuk semua output terminal** — `MainActivity.kt:295-302` `color=0xFF58A6FF` untuk banner, error, output, prompt. Error vs sukses tidak bisa dibedakan; kontras placeholder `TextField` 12sp di atas `0xFF1F242C` lemah. Desain doc (`ANDROID_APP_DESIGN.md` §3) menjanjikan Termux `TerminalView` SurfaceView off-thread, implementasi nyata hanya Compose `Text` + `verticalScroll` — doc vs kode tidak sinkron.
9. **Ketik saat OFFLINE diam-diam hilang** — Bukti: `emulator_current.png` status `○ OFFLINE` tapi `Mode: UNLOCKED` + input field aktif; `sendKeystroke` tetap encode+send tanpa cek `isConnected`. Tidak ada antrian/retry countdown di UI. Setelah fix sebagian: reconnect path tersentralisasi, tapi antrian offline tetap backlog (belum dikerjakan).
10. **Theming hardcoded 6 abu-abu** — `0xFF181818/202020/0D1117/1F242C/242424/383838` tersebar tanpa `MaterialTheme`, tanpa shape/typography scale; warna status LIVE (hijau `1B5E20`) vs OFFLINE (merah `B71C1C`) + ikon QR biru + refresh biru-muda + gembok oranye-merah tidak satu bahasa. Lock semantics terbalik-bingungkan: `isReadOnly=true -> Lock hijau`, `false -> LockOpen oranye` (`StatusHeader.kt:83-90`).

Yang TIDAK saya klaim: saya tidak menilai orangnya; yang dinilai artefak di atas dengan bukti file+pixel.

## 8. List Test yang Dilakukan dan Hasil
| Test | Command | Hasil | Keterangan |
|---|---|---|---|
| Rust workspace | `cargo test --workspace` | **34 passed, 0 failed** | 4+4+7+7+4+8 per suite; total via `grep -c "test .* ok$"` = 34 |
| Python asset/E2E | `uv run pytest -q` | **12 passed (8.86s)** | suite `scripts/build_assets.py` + E2E |
| Android unit (baru, 9 test) | `gradle -p apps/android testDebugUnitTest` | **TIDAK dijalankan (gap)** | env ini tanpa `gradle`/`kotlinc` (`which` negatif); file `TerminalUiHelpersTest.kt` + implementasi ditulis TDD-style tapi belum dieksekusi di sini |
| Verifikasi manual | baca 7 screenshot + `git diff` | dilakukan | judul/tab/banner/tombol/secret dicek per-pixel dan per-baris |

## 9. Lesson Learned
- Box-drawing ASCII fixed-width tidak pernah cocok untuk viewport HP 360dp; status awal harus komponen terstruktur (atau baris pendek ≤64 char), bukan `padEnd(39)`.
- `TabRow` untuk label dinamis = kepotong pasti; default untuk sesi multi-host adalah `ScrollableTabRow` + ellipsis.
- `Row(weight)` untuk 11 tombol = singkatan tak terbaca + target sentuh <48dp; `LazyRow` + `widthIn` + 48dp adalah floor, bukan polish.
- Bare modifier tidak boleh punya byte mapping; `null` + hint lebih aman daripada mengirim literal "CTRL".
- IP/token nyata tidak boleh ada di `MainActivity.kt` walau "repo private sementara" — repo ini publik (`mufidhadi/terminal-mirror`) dan screenshot ikut membocorkan IP.

## 10. Tindak Lanjut (backlog jujur)
- Jalankan `gradle testDebugUnitTest` + screenshot ulang di emulator (API 35) dan Moto G45; verifikasi judul tidak kepotong, tab scroll, accessory full-label.
- Antrian keystroke saat OFFLINE + indikator retry/backoff di UI (saat ini keystroke offline masih drop).
- Sinkronkan `ANDROID_APP_DESIGN.md` §3 (Termux SurfaceView) dengan implementasi (Compose Text) atau implementasikan beneran.
- Sistem tema terpusat (`Color.kt/Theme.kt`) + semantik warna status/aksi tunggal; perbaiki kontras placeholder.
- SSOT bahasa toast (campuran Indonesia/Inggris) dan label KILL konsisten dengan dokumen.
