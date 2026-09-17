# AI Report 027: Brutal Review Pass + WS8 Theme Tokens (temuan 10, Report 021)

## 1. Nama Tugas
Brutal review atas seluruh kerja tahap ini (WS5–WS4+DISC, kode sendiri) → perbaiki temuan → lanjut WS8 tema terpusat.

## 2. Histori Aksi
1. Review baca-kode penuh: `build.gradle.kts`, `StatusHeader`, `MainActivity`, 4 composable, `ConnectionManager`, script shell/python, workflow CI.
2. Temuan & perbaikan:
   - Blok `buildFeatures` ganda di gradle → digabung (valid tapi ceroboh).
   - Import mati: `Alignment` (StatusHeader), `CoroutineScope/Dispatchers/launch` (MainActivity, warisan AGY), `Color` di 4 file pasca-tema.
   - Verifikasi tertunda: live-test `test_live_mac_pty_over_vps.py` belum pernah dieksekusi pasca-sanitasi → dijalankan dengan env lokal: **1 passed**.
   - Cek `git check-ignore`: `.env` + `local.properties` ter-ignore ✓; 1 caller `AccessoryBar` ✓.
3. WS8: `ui/theme/TerminalTheme.kt` (`TerminalColors`, 22 token) + ganti seluruh literal di 6 file; verifikasi `grep` nol literal di luar file tema.
4. Verifikasi, commit `25d1f0a` + `db6b830`, push branch.

## 3. Nomor Hash Commit
- Commit tema: `25d1f0a`; commit bersih import: `db6b830`
- Commit laporan: (lihat `git log --oneline -3` setelah commit laporan)
- Base: `084bdb3`

## 4. Nama Branch
- `fix/android-theme-tokens`

## 5. Nama dan URL Repo
- Nama: `terminal-mirror`
- Remote: `git@github.com:mufidhadi/terminal-mirror.git`
- Web: `https://github.com/mufidhadi/terminal-mirror`

## 6. Tech Stack
- Kotlin, Jetpack Compose Material3, JUnit4 (gap), pytest, cargo (tidak tersentuh).

## 7. List Kesulitan, Tantangan, Bug dan Solusi
- **Script edit massal nyaris menghapus import yang masih dipakai**: assert pertama salah (substring `Color` vs `TerminalColors`) dan menggagalkan sebelum ada perubahan — *solusi*: assert whole-word, verifikasi per file; tidak ada file rusak (commit berikutnya membuktikan).
- **Dead code warisan AGY** (`CoroutineScope/Dispatchers/launch`): tidak pernah dipakai `MainActivity` (scope dari `rememberCoroutineScope`). *Solusi*: hapus; pemakaian `launch` hanya di `ConnectionManager` yang import sendiri.
- **Skema gelap/terang dinamis penuh** (Material You) belum dikerjakan — token terpusat adalah fondasinya; dinamis menjadi follow-up eksplisit, bukan klaim.

## 8. List Test yang Dilakukan dan Hasil
| Test | Command | Hasil | Keterangan |
|---|---|---|---|
| Live mac PTY (tertunda) | env dari `.env` lokal + `uv run pytest tests/test_live_mac_pty_over_vps.py -q` | **1 passed (3.60s)** | semua 6 live-test kini terverifikasi pasca-sanitasi |
| Python regresi | `uv run pytest -q` | **6 passed, 6 skipped** | |
| Literal check | `grep Color(0xFF)` di main | **nol di luar TerminalTheme.kt** | |
| Android unit | `gradle testDebugUnitTest` | **TIDAK dijalankan (gap)** | |
| Import check | scan whole-word per file | bersih | |

## 9. Lesson Learned
- Review diri sendiri menemukan 3 kelas cacat yang lolos saat menulis: duplikasi blok DSL, import mati, verifikasi tertunda. Checklist tetap (import, DSL, verifikasi) menghemat satu putaran revisi.
- Assert pada script edit massal harus presisi (whole-word) — assert ceroboh sama bahayanya dengan tanpa assert.

## 10. Tindak Lanjut
- Screenshot + TalkBack + unit Kotlin saat toolchain tersedia.
- Sisa plan: WS6 (Opsi A: warna semantik terminal + koreksi dokumen) → WS7 (state machine offline).
