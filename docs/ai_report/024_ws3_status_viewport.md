# AI Report 024: WS3 Status Viewport (temuan 4, Report 021)

## 1. Nama Tugas
Eksekusi WS3 dari plan — ganti banner ASCII 80-kolom dengan kartu status terstruktur di viewport Android.

## 2. Histori Aksi
1. Branch `fix/android-status-viewport` dari `fix/android-topbar-tabs-foundation`.
2. Ekstrak `statusLabel()`/`modeLabel()` murni dari `bannerLines()` + 1 test baru (merah→hijau).
3. Buat `ui/components/ConnectionStatusCard.kt`: Card berisi dot status berwarna + baris Host/Session/Relay/Status/Mode (label 64dp + value ellipsis) + hint.
4. `MainActivity`: viewport menampilkan kartu saat buffer kosong, teks terminal saat ada isi; hapus `buildInitialBanner()` yang mati.
5. Verifikasi, commit `68cd0ee`, push branch.

## 3. Nomor Hash Commit
- Commit fix: `68cd0ee`
- Commit laporan: (lihat `git log --oneline -3` setelah commit laporan)
- Base: `532a597`

## 4. Nama Branch
- `fix/android-status-viewport`

## 5. Nama dan URL Repo
- Nama: `terminal-mirror`
- Remote: `git@github.com:mufidhadi/terminal-mirror.git`
- Web: `https://github.com/mufidhadi/terminal-mirror`

## 6. Tech Stack
- Kotlin, Jetpack Compose Material3 (`Card`, `CardDefaults`), JUnit4.

## 7. List Kesulitan, Tantangan, Bug dan Solusi
- **Fungsi mati**: `buildInitialBanner()` tak terpakai setelah penggantian — *solusi*: hapus + bersihkan import agar tidak jadi utang.
- **Warna status vs tema**: hijau/amber dipakai lokal di komponen; sentralisasi menunggu WS8 (disengaja, bukan kelalaian — dicatat agar tidak diduplikasi di tempat lain).
- **Tri-state OFFLINE**: masih boolean (CONNECTED/CONNECTING); state OFFLINE eksplisit + retry UI menjadi bagian WS7 (state machine).

## 8. List Test yang Dilakukan dan Hasil
| Test | Command | Hasil | Keterangan |
|---|---|---|---|
| Python regresi | `uv run pytest -q` | **6 passed, 6 skipped** | tidak ada file py/rs tersentuh |
| Android unit (1 test baru) | `gradle testDebugUnitTest` | **TIDAK dijalankan (gap)** | env tanpa gradle; label diverifikasi baca kode |
| Statik | `git diff --stat` | 4 file, kartu baru 100+ baris | tidak ada sisa referensi banner ASCII di `MainActivity` |

## 9. Lesson Learned
- Empty-state terminal harus komponen layout (baris fluid + ellipsis), bukan teks pra-format — lebar viewport HP tidak pernah 80 kolom.
- Ekstrak label status ke helper murni membuat kartu bisa diuji tanpa emulator.

## 10. Tindak Lanjut
- Screenshot 360dp + TalkBack order kartu saat toolchain tersedia.
- Lanjut WS4 (finalisasi accessory + KILL konsisten) → WS8 → WS6 (Opsi A) → WS7.
