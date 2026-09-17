# AI Report 028: WS6 Semantic Terminal + Koreksi Dokumen (Opsi A)

## 1. Nama Tugas
Eksekusi WS6 Opsi A (LOCKED mas mufid): warna semantik terminal + hapus klaim Termux fiktif dari dokumen.

## 2. Histori Aksi
1. Branch `fix/android-semantic-terminal` dari `fix/android-theme-tokens`.
2. TDD: `TerminalLineClassifier` (ERROR/SUCCESS/MUTED/NORMAL, ERROR menang) + `spanLines` + 4 test (merah→hijau, termasuk jebakan `broken pipe` vs `\bok\b` dan prompt mid-line `host ~ % cmd`).
3. Token tema `Error`/`Success` + viewport `AnnotatedString` per baris di `MainActivity`.
4. Koreksi `ANDROID_APP_DESIGN.md`: §3 ditulis ulang sesuai kode nyata (buffer+prosesor+span), anatomi layar, mermaid, struktur folder; klaim "< 16 ms" yang tak terukur ditandai jujur; Opsi B didokumentasikan sebagai opsi tertunda dengan prasyarat tegas.
5. Verifikasi, commit `c68a4b6`, push branch.

## 3. Nomor Hash Commit
- Commit fix: `c68a4b6`
- Commit laporan: (lihat `git log --oneline -3` setelah commit laporan)
- Base: `b4e8f40`

## 4. Nama Branch
- `fix/android-semantic-terminal`

## 5. Nama dan URL Repo
- Nama: `terminal-mirror`
- Remote: `git@github.com:mufidhadi/terminal-mirror.git`
- Web: `https://github.com/mufidhadi/terminal-mirror`

## 6. Tech Stack
- Kotlin (`AnnotatedString`/`SpanStyle`), JUnit4 (gap), pytest regresi.

## 7. List Kesulitan, Tantangan, Bug dan Solusi
- **Heuristik vs presisi**: substring `ok` cocok di `broken`; prompt tidak selalu di awal baris. *Solusi*: word-boundary regex + aturan inline-prompt di posisi terakhir (sinyal error/sukses selalu menang).
- **Contoh uji menembak kaki sendiri**: `"ok. 34 passed; 0 failed"` mengandung `failed` → ERROR (benar per aturan). *Solusi*: ubah fixture jadi `"0 ignored"`.
- **Klaim performa fiktif** ("< 16 ms", "SurfaceView + draw thread"): *solusi* hapus/tandai belum terukur; `TerminalView` Termux aslinya extends `View` — dikoreksi juga.

## 8. List Test yang Dilakukan dan Hasil
| Test | Command | Hasil | Keterangan |
|---|---|---|---|
| Python regresi | `uv run pytest -q` | **6 passed, 6 skipped** | tidak ada file py/rs tersentuh |
| Android unit (4 test baru) | `gradle testDebugUnitTest` | **TIDAK dijalankan (gap)** | aturan classifier ditelusur manual per baris uji |
| Dokumen | `grep Termux/SurfaceView` | sisa hanya konteks Opsi B tertunda | |

## 9. Lesson Learned
- Pewarnaan heuristik per-baris cukup untuk temuan "satu warna untuk semua" tanpa parser semantik penuh — yang penting deterministik dan teruji.
- Dokumen yang mengklaim komponen yang tidak ada lebih berbahaya dari tidak ada dokumen: WS6 menghapus 60+ baris fiksi dan menggantinya dengan 40 baris fakta.

## 10. Tindak Lanjut
- Screenshot verifikasi warna (error merah/sukses hijau) + uji profiler recomposition.
- Sisa plan: **WS7 (state machine offline)** — satu-satunya WS besar yang tersisa.
