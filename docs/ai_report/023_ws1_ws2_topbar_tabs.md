# AI Report 023: WS1/WS2 Top-Bar & Tab Foundation (temuan 1–3, Report 021)

## 1. Nama Tugas
Eksekusi WS1 (top bar judul/subtitle) + WS2 (tab workstation) dari `docs/PLAN_ANDROID_UI_UX_REMEDIATION.md`, lanjutan setelah WS5 + rotasi token selesai.

## 2. Histori Aksi
1. Branch `fix/android-topbar-tabs-foundation` dari `fix/android-ui-ux-brutal-review`.
2. Temuan saat review ulang: constraint `fillMaxWidth(0.55f)` peninggalan hardening kemarin justru memampatkan slot judul — dihapus (TopAppBar memberi slot judul seluruh ruang sisa actions secara otomatis).
3. Tambah helper murni `TerminalUiHelpers.shortHostLabel(host, maxChars=28)` + pakai di `WorkstationTabs` (lapis kedua setelah `ScrollableTabRow` + ellipsis).
4. TDD: 2 test baru (`intact`, `truncates with ellipsis` incl. anti trailing-space), lalu implementasi.
5. Verifikasi, commit `dd45536`, push branch.

## 3. Nomor Hash Commit
- Commit fix: `dd45536`
- Commit laporan: (lihat `git log --oneline -3` setelah commit laporan)
- Base: `9f74288` via `fix/android-ui-ux-brutal-review`

## 4. Nama Branch
- `fix/android-topbar-tabs-foundation`

## 5. Nama dan URL Repo
- Nama: `terminal-mirror`
- Remote: `git@github.com:mufidhadi/terminal-mirror.git`
- Web: `https://github.com/mufidhadi/terminal-mirror`

## 6. Tech Stack
- Kotlin, Jetpack Compose Material3 (`TopAppBar`, `ScrollableTabRow`), JUnit4.

## 7. List Kesulitan, Tantangan, Bug dan Solusi
- **Bug buatan sendiri**: `Modifier.fillMaxWidth(0.55f)` pada slot title (pengganti `weight` yang tidak compile di slot title) memaksa judul ke 55% lebar — memperparah temuan 1. *Solusi*: Column polos tanpa constraint; ruang dibagi TopAppBar secara native.
- **Truncation ganda**: ellipsis Compose saja tidak cukup di font-scale ekstrem. *Solusi*: pre-truncate di helper (`take(max-1).trimEnd() + "…"`) + ellipsis sebagai jaring kedua.
- **Ikon tab `contentDescription=null`**: benar apa adanya (ikon dekoratif, label teks dibacakan TalkBack; state selected diumumkan otomatis oleh role Tab).

## 8. List Test yang Dilakukan dan Hasil
| Test | Command | Hasil | Keterangan |
|---|---|---|---|
| Python regresi | `uv run pytest -q` | **6 passed, 6 skipped** | tidak ada file py/rs tersentuh; live skip tanpa env |
| Android unit (2 test baru) | `gradle testDebugUnitTest` | **TIDAK dijalankan (gap)** | env tanpa gradle/kotlinc; logika trunc diverifikasi manual (take(27).trimEnd()+"…" = 27 char, syarat terpenuhi) |
| Statik | `grep weight/fillMaxWidth` di 2 file + `git diff --stat` | bersih, 4 file 34+/2- | |

## 9. Lesson Learned
- Slot `title` TopAppBar bukan `RowScope`: `weight` tidak compile di sana, dan constraint lebar manual justru menciptakan bug pemotongan baru. Biarkan komponen Material membagi ruang.
- Aturan trunc yang bisa di-unit-test (helper murni) lebih bernilai daripada mengandalkan ellipsis visual yang hanya terlihat di screenshot.

## 10. Tindak Lanjut
- Screenshot ulang 360dp + uji TalkBack di emulator/Moto saat toolchain tersedia.
- Lanjut WS3 (status viewport kartu terstruktur) → WS4 (finalisasi accessoryEncoder) sesuai plan.
