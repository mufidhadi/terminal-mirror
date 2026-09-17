# AI Report 029: WS7 Offline State Machine (temuan 9, Report 021)

## 1. Nama Tugas
Eksekusi WS7 — lifecycle koneksi eksplisit + antrean outbound + input gating. WS terakhir plan remediasi.

## 2. Histori Aksi
1. Branch `fix/android-offline-state-machine` dari `fix/android-semantic-terminal`.
2. Baca `RelayClient`: `sendBinary()` drop diam-diam saat socket mati + tanpa flag liveness — akar temuan 9.
3. TDD: `ConnectionState.kt` (sealed states + `ReconnectPolicy` + `OutboundQueue` murni) + 5 test (`ConnectionStateTest`), lalu retrofit `ConnectionManager`.
4. Self-review menemukan race: callback telat dari client lama bisa memicu reconnect untuk client baru → tambah generation guard (+2 test implisit via logika, diverifikasi baca kode).
5. UI: chip 4-state, input hanya saat CONNECTED, baris status offline jujur (attempt + queued/dropped), kartu pakai state.
6. Verifikasi, commit `cb9ee14`, push branch.

## 3. Nomor Hash Commit
- Commit fix: `cb9ee14`
- Commit laporan: (lihat `git log --oneline -3` setelah commit laporan)
- Base: `4ede21a`

## 4. Nama Branch
- `fix/android-offline-state-machine`

## 5. Nama dan URL Repo
- Nama: `terminal-mirror`
- Remote: `git@github.com:mufidhadi/terminal-mirror.git`
- Web: `https://github.com/mufidhadi/terminal-mirror`

## 6. Tech Stack
- Kotlin (StateFlow-less: callback + ConcurrentHashMap, cocok dengan pola existing), coroutines, JUnit4 (gap).

## 7. List Kesulitan, Tantangan, Bug dan Solusi
- **Liveness tak terobservasi**: `RelayClient` tanpa `isConnected`. *Solusi*: lacak di manager (`socketLive`), jangan pernah serahkan byte ke socket mati.
- **Reconnect buta**: retry loop tanpa identitas client. *Solusi*: generation guard di callback + retry coroutine + `disconnectSession` bump.
- **Antrean tak terbatas = OOM diam-diam**: *solusi* kapasitas 200, drop-oldest + counter jujur di UI.
- **Callback legacy**: `onSessionStatusChanged` dipertahankan (titik session list) dan digerakkan dari `setState` — satu sumber kebenaran.

## 8. List Test yang Dilakukan dan Hasil
| Test | Command | Hasil | Keterangan |
|---|---|---|---|
| Python regresi | `uv run pytest -q` | **6 passed, 6 skipped** | tidak ada file py/rs tersentuh |
| Android unit (5 test baru) | `gradle testDebugUnitTest` | **TIDAK dijalankan (gap)** | backoff bounds + FIFO + drop + clear ditelusur manual |
| Kontrak | baca `ConnectionManager` penuh | generation konsisten di 6 titik | |

## 9. Lesson Learned
- Boolean `isConnected` tidak cukup untuk UI jaringan: butuh minimal 4 state + alasan (attempt/delay) agar UI bisa jujur.
- Setiap retry loop butuh identitas generasi — tanpa itu, reconnect adalah sumber bug race, bukan fitur ketahanan.

## 10. Tindak Lanjut
- E2E + brutal review final (perintah mas mufid berikut).
- Uji chaos manual: kill-network → ketik → restore (tidak hilang), DISC saat reconnect (tidak nyambung lagi), 300 ketikan offline (counter dropped jujur).
