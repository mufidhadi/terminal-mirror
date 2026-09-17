# Plan Remediasi UI/UX Android — Tindak Lanjut Report 021

**Sumber temuan**: `docs/ai_report/021_android_ui_ux_brutal_review_and_hardening.md` (10 temuan, berbasis AGY conversation `fcb24c77-4b74-4f51-a074-8f833e84c2fd`).
**Status**: PLAN — belum dieksekusi. Setiap WS dieksekusi di branch sendiri dengan TDD.
**Baseline terverifikasi**: `cargo test --workspace` = 34 passed; `uv run pytest -q` = 12 passed; Android Gradle belum dapat dijalankan di env ini (gap tercatat di 021).

---

## 0. Prinsip eksekusi (mengikat semua WS)

1. Satu branch per workstream, branch off `main` (`feature/`/`fix/`), tidak ada commit langsung ke `main` (ikut `AGENTS.md`).
2. TDD: helper murni (`ui/`, `network/`) wajib punya JUnit **dulu** sebelum refactor composable (`cargo test` untuk Rust, `pytest` untuk script).
3. Tidak ada IP/token/secret nyata di kode, screenshot, maupun laporan (ikut `AGENTS.md` §3). Secret hanya via `local.properties` (git-ignored) / env CI untuk build internal; default repo = placeholder.
4. Setiap WS ditutup dengan AI report di `docs/ai_report/xxx_<nama>.md` + update `docs/PLANNING.md`.
5. Verifikasi tiap WS: `cargo test --workspace`, `uv run pytest -q`, `gradle -p apps/android testDebugUnitTest`, matriks screenshot (emulator API 35 + Moto G45 × portrait/landscape × online/offline × locked/unlocked).

**Referensi dasar** (dipakai lintas WS):
- Material3 insets/TopAppBar/Scaffold: <https://developer.android.com/develop/ui/compose/system/material-insets>
- `TabRow` vs `ScrollableTabRow`: <https://developer.android.com/reference/kotlin/androidx/compose/material3/TabRow.composable> — "See `ScrollableTabRow` for a tab row that does not enforce equal size, and allows scrolling to tabs that do not fit on screen."
- Touch target ≥48dp: <https://support.google.com/accessibility/android/answer/7101858> dan <https://developer.android.com/guide/topics/ui/accessibility/views/apps-views>
- State machine WebSocket + backoff + drain queue: <https://dev.to/software_mvp-factory/websocket-connection-lifecycle-in-mobile-apps-5bep> dan <https://blog.stackademic.com/building-the-invisible-wire-designing-real-time-features-in-android-086d900e01c5>
- Secrets (BuildConfig/baked-in-APK tidak aman; fix tahan lama = secret di backend): <https://proandroiddev.com/gradle-properties-buildconfig-and-secrets-management-the-right-way-8b1b161aaefd>, <https://github.com/google/secrets-gradle-plugin>, <https://ptkd.com/journal/fix-exposed-api-keys-android>
- M3 theming (`MaterialTheme` color/type/shape, dynamic color): <https://developer.android.com/develop/ui/compose/designsystems/material3>, <https://developer.android.com/codelabs/jetpack-compose-theming>

---

## WS1 — Top bar: judul kepotong + subtitle wrap (temuan 1, 2)

- **Masalah**: judul terpotong ("erminal Mirror") di semua screenshot emulator; subtitle wrap 2 baris. Penyebab: `StatusHeader.kt` TopAppBar tanpa `maxLines/overflow`, string `"host • shell"` tanpa batas baris.
- **Solusi**: pastikan `Scaffold(contentWindowInsets)` + TopAppBar default insets aktif; title & subtitle `maxLines=1 + TextOverflow.Ellipsis`; subtitle via `TerminalUiHelpers.sessionSubtitle()`; tambah screenshot-test lebar 360dp.
- **File**: `ui/components/StatusHeader.kt`, `ui/TerminalUiHelpers.kt`, test.
- **Test**: unit subtitle (ada) + kasus >40 char; manual screenshot judul utuh.
- **Done jika**: tidak ada pemotongan huruf pertama; subtitle tidak pernah wrap.

## WS2 — Tab workstation kepotong (temuan 3)

- **Masalah**: tab tampil "MacBook Pro (Darwin" (terpotong); tab non-aktif kontras rendah. Penyebab: `TabRow` fixed equal-width untuk label panjang tanpa `overflow=Ellipsis`.
- **Solusi**: `ScrollableTabRow(edgePadding=16.dp)` + ellipsis + contentDescription per tab; kontras selected/unselected cukup; pulihkan posisi scroll saat sesi pairing baru ditambah.
- **File**: `ui/components/WorkstationTabs.kt`, test.
- **Test**: unit label panjang; manual 2–4 sesi.
- **Done jika**: semua label terbaca via scroll/ellipsis; tab aktif jelas.

## WS3 — Banner ASCII pecah + status viewport (temuan 4)

- **Masalah**: box `┌──┐` + `padEnd(39)` (`MainActivity.kt`) mengasumsikan 80 kolom; viewport HP ~30–36 kolom @12sp → garis patah, `(172...)|` dan `Keystrokes Active)|` nyasar di semua screenshot idle.
- **Solusi**: hapus total box-drawing; empty-state jadi komponen terstruktur (baris Host/Session/Relay/Status/Mode, tiap baris ≤64 char); bedakan visual CONNECTING vs CONNECTED vs OFFLINE.
- **File**: `MainActivity.kt` viewport, `ui/TerminalUiHelpers.kt`, test.
- **Test**: unit (tanpa `┌/padEnd/IP`, tiap baris ≤64 char); screenshot idle online & offline.
- **Done jika**: nol garis patah/wrap artifak di 360dp.

## WS4 — Accessory bar + bug keystroke literal (temuan 5, 6)

- **Masalah**: 11 tombol `Row(weight).height(34dp)` → label "ES/TA/CT/AL", target <48dp (langgar Material a11y). Bug: `CTRL/ALT/|/~/↑` jatuh ke `else -> rawString` sehingga tombol CTRL mengirim teks "CTRL" ke shell; panah unicode tak dipetakan.
- **Solusi**: pertahankan `LazyRow` 48dp + label penuh; lengkapi `KeystrokeEncoder`: ENTER/TAB/ESC/CTRL+C/D/Z, panah kata + unicode, operator `| ~ ` ` ` - _`; bare CTRL/ALT → null + hint; tentukan semantik modifier (toggle vs momentary); seragamkan label KILL (satu bentuk).
- **File**: `ui/components/AccessoryBar.kt`, `ui/TerminalUiHelpers.kt` (`KeystrokeEncoder`), test.
- **Test**: unit semua label (tambah `` ` `` `-` `_`, CTRL+Z); manual tekan tiap tombol, verifikasi byte upstream.
- **Done jika**: nol singkatan tak terbaca; nol literal "CTRL"/"ALT" masuk shell.

## WS5 — Secret + IP ter-commit (temuan 7, PRIORITAS TERTINGGI, blokir rilis)

- **Masalah**: IP `172.23.127.184:8888` + token relay hardcoded di `MainActivity.kt`, tampil di screenshot; repo publik. Token lama anggap kompromi (APK bisa di-decompile).
- **Solusi**: (a) **rotasi token relay sekarang**; (b) host/token hanya dari `local.properties` (git-ignored)/env CI, default repo placeholder (`RelayConfig`); (c) hapus literal dari kode + screenshot baru; (d) pindai history (`git log -S`) dan catat (history publik tidak bisa "un-ship"); (e) secret-scan di CI; (f) jangka panjang: pairing QR/short-lived token via backend, bukan token statis di app.
- **File**: `MainActivity.kt`, `ui/TerminalUiHelpers.kt` (`RelayConfig`), `local.properties`, `.gitignore`, `.github/workflows/`.
- **Test**: unit `wsUrl()` tolak blank; `grep` nol IP/token; (opsional) decompile APK release cari pola token.
- **Done jika**: repo + APK bersih; token lama dirotasi; scan CI hijau.

## WS6 — Warna monoton + doc vs kode tidak sinkron (temuan 8)

- **Masalah**: satu warna `0xFF58A6FF` untuk semua output; `ANDROID_APP_DESIGN.md` §3 klaim Termux `TerminalView` SurfaceView, implementasi nyata Compose `Text`.
- **Keputusan (LOCKED 2026-09-17 oleh mas mufid): Opsi A** — tetap Compose `Text` + pewarnaan semantik (error/sukses/muted), lalu koreksi dokumen. Opsi B (Termux `TerminalView` asli) ditunda sampai ada bukti TUI nyata yang gagal dirender `TerminalScreenBuffer`.
- **File**: viewport terminal, `terminal/TerminalBufferProcessor.kt`, `terminal/TerminalScreenBuffer.kt`, `docs/ANDROID_APP_DESIGN.md`.
- **Test**: unit parser ANSI → semantik; manual beda warna error/sukses.
- **Done jika**: error/sukses/output distinguishable; tidak ada klaim arsitektur fiktif.

## WS7 — Ketik saat OFFLINE hilang (temuan 9)

- **Masalah**: status OFFLINE tapi input aktif; `sendKeystroke` kirim tanpa cek koneksi; tanpa antrean/retry UI.
- **Solusi**: state machine `DISCONNECTED→CONNECTING→CONNECTED→RECONNECTING/BACKING_OFF→DRAINING_QUEUE` (backoff ‖ jitter, ceiling 30s); antrean outbound **terbatas** + overflow policy; input disabled saat tidak connected (atau label "queued N"); countdown backoff + tombol retry; drain berurutan saat reconnect; tangani Doze sebagai disconnect terkendali.
- **File**: `network/ConnectionManager.kt`, `network/RelayClient.kt`, UI status, test.
- **Test**: unit backoff/jitter, drain berurutan, overflow; manual kill-network → ketik → restore (nol hilang/duplikat); screenshot tiap state.
- **Done jika**: nol keystroke hilang diam-diam; reconnect ber-jitter.

## WS8 — Tema, aksesibilitas, konsistensi copy (temuan 10)

- **Masalah**: 6 abu hardcoded tanpa `MaterialTheme`; semantik gembok membingungkan; kontras placeholder lemah; campuran ID/EN; label KILL inkonsisten dengan dokumen.
- **Solusi**: token terpusat `ui/theme/` (`Color.kt/Theme.kt/Type.kt/Shape.kt`, dukung dark/light + dynamic color bila memungkinkan); semantik status/aksi tunggal; semua target ≥48dp; contentDescription bermakna; seragamkan bahasa + label.
- **File**: `ui/theme/*`, semua composable, string.
- **Test**: screenshot light/dark + cek kontras; audit touch target & TalkBack; landscape.
- **Done jika**: nol warna hardcoded di composable; semua tombol ≥48dp; copy konsisten.

---

## Urutan eksekusi

1. **WS5 dulu** (keamanan — blokir rilis).
2. WS1 → WS2 → WS3 → WS4 (fondasi layout + input).
3. WS8 (tema di atas layout benar).
4. WS6 (butuh token tema) → WS7 terakhir (paling besar).

## Verifikasi akhir per WS

- `cargo test --workspace` hijau (baseline 34), `uv run pytest -q` hijau (baseline 12), `gradle -p apps/android testDebugUnitTest` hijau.
- Matriks screenshot: emulator API 35 + Moto G45 × portrait/landscape × online/offline × locked/unlocked.
- WS5: bukti rotasi token + hasil scan CI + `grep` bersih.
