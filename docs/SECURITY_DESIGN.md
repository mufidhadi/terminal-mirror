# Security Architecture & Threat Model
## Project: Terminal Mirror (Hardened Open-Source Edition)

---

### 1. Executive Security Posture

Terminal Mirror treats both the network environment and the central relay server as untrusted, potentially hostile environments.

The architecture enforces 5 core defensive principles:
1. **Zero-Knowledge Blind Relay**: The relay forwards ciphertext payloads and cannot decrypt terminal data, keystrokes, or credentials.
2. **Anti-Abuse Relay Governance**: The relay server enforces strict IP-based connection rate limits (60/min), per-frame size caps (64 KB), and authenticated session keys to prevent misuse as an illegal Reverse Shell / C2 proxy.
3. **High-Entropy Pairing with 3-Strike Auto-Burn**: Replaces low-entropy 6-digit numeric PINs with **4-word Diceware Passphrases** (~50 bits entropy). Any 3 consecutive failed pairing attempts immediately and permanently incinerate the pairing session.
4. **Resilient Decoupled Stream Architecture**: Decouples PTY master reading from downstream crypto/network consumers using bounded asynchronous channels, preventing CPU starvation during high-volume output bursts.
5. **Lossless UTF-8 Boundary Assembly**: Incorporates a streaming multibyte state machine ensuring Unicode emojis and Nerd Fonts glyphs are never corrupted by buffer boundary slicing.

---

### 2. STRIDE Threat Model & Mitigations (Hardened)

| STRIDE Category | Threat Scenario | Impact | Defensive Countermeasure in Terminal Mirror |
| :--- | :--- | :--- | :--- |
| **Spoofing** | Attacker brute-forces short pairing PINs to hijack a remote workstation. | Critical | **3-Strike Auto-Burn + 4-Word Passphrase**: Host destroys pairing state after 3 failed attempts; passphrases provide 50+ bits of entropy. |
| **Tampering** | Intermediary relay flips bits in terminal stream to alter commands. | Critical | **AEAD Integrity (ChaCha20-Poly1305)**: Every packet includes an authenticated Poly1305 MAC tag verified before decryption. |
| **Repudiation** | Client denies executing a destructive command. | Low | **Monotonic Sequence Numbers & Local Audit Log**: Monotonically increasing sequence counters ensure non-repudiation. |
| **Information Disclosure** | Relay operator or ISP snoops on passwords or code. | Critical | **Layer 7 End-to-End Encryption**: Workstation encrypts data before sending; mobile decrypts locally. Relay sees only opaque ciphertext. |
| **Denial of Service** | Malicious botnet spams relay with connection floods or massive 1GB frames. | High | **Rate Limiter & 64KB Frame Capping**: Relay drops connections exceeding 60 conn/min per IP and rejects frames > 64 KB. |
| **Elevation of Privilege** | Attacker uses public relay as an untraceable Reverse Shell / C2 proxy. | Critical | **No Anonymous Relays**: Relay enforces session tokens; self-hosting is prioritized; abuse mitigation policies applied. |

---

### 3. Pairing Protocol & Brute-Force Immunity

```
Client (Attacker / User)                     Host Workstation (Daemon)
          │                                              │
          ├────────── Attempt 1: "wrong-pass" ───────────► Evaluates: Strike 1/3 (Fails)
          │◄───────── Response: Err(2 attempts left) ────┤
          │                                              │
          ├────────── Attempt 2: "wrong-pass" ───────────► Evaluates: Strike 2/3 (Fails)
          │◄───────── Response: Err(1 attempt left) ─────┤
          │                                              │
          ├────────── Attempt 3: "wrong-pass" ───────────► Evaluates: Strike 3/3!
          │                                              │ [STATE PERMANENTLY BURNED]
          │◄───────── Response: Err(0 - BURNED) ─────────┤
          │                                              │
          ├────────── Attempt 4: "correct-secret" ───────► REJECTED! Session is dead.
```

* **Passphrase Entropy**: By combining four random words (e.g. `kuda-terbang-batu-merah`) from a wordlist of 7,776 words (Diceware), entropy is $\log_2(7776^4) \approx 51.7$ bits.
* **Auto-Burn Guarantee**: Even against distributed botnet attacks, three incorrect guesses permanently terminate the session, rendering online brute-force attacks mathematically impossible.

---

### 4. Lossless Streaming: UTF-8 Multibyte Slicing Guard

Terminal emulators frequently encounter multibyte UTF-8 sequences (1 to 4 bytes):
* Standard ASCII: 1 byte (`A`)
* Box-drawing / Cyrillic: 2-3 bytes (`┌`, `─`)
* Emojis / Modern prompt glyphs: 4 bytes (`🚀`, ``)

When a 4-byte character is split across a 4096-byte chunk boundary:
* `Utf8StreamChunker` detects trailing partial bytes via `std::str::from_utf8` error slicing.
* Incomplete bytes (1-3 bytes) are retained in an internal scratch buffer.
* When the subsequent chunk arrives, bytes are prepended and re-assembled into a valid UTF-8 character, eliminating `InvalidUtf8Sequence` panic crashes and visual `` artifacts.
