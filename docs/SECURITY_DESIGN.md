# Security Architecture & Threat Model
## Project: Terminal Mirror

---

### 1. Executive Security Philosophy
Terminal sessions are among the highest-risk data flows in an enterprise or private developer workflow. Keystrokes frequently contain administrative `sudo` passwords, temporary SSH passphrases, cloud provider access keys, and proprietary application logs.

The Terminal Mirror security architecture is built on three core pillars:
1. **Zero-Knowledge Relay (E2EE)**: The central relay node on VPS is treated as an untrusted intermediary. Even in the event of total VPS root compromise, the attacker cannot read or forge terminal streams.
2. **Network Perimeter Cloaking (ZeroTier / VPN Mesh)**: Eliminating public internet attack surface by binding endpoints strictly to private virtual overlay network adapters.
3. **Fail-Safe Operational Guards**: Strict client-side input isolation (View-Only mode) to eliminate unintended execution of destructive terminal commands.

---

### 2. STRIDE Threat Analysis & Mitigations

| STRIDE Threat Category | Potential Attack Vector | Impact | Mitigating Control in Terminal Mirror |
| :--- | :--- | :--- | :--- |
| **Spoofing** | Rogue host registers with an existing `session_id` to intercept mobile traffic. | Critical | **HMAC Token & Pre-Shared Secret**: Host must prove possession of the session key derived during initialization; Relay denies duplicate session registration. |
| **Tampering** | Intermediary modifies terminal output to hide malicious activity or inject commands into input stream. | High | **AEAD Integrity (ChaCha20-Poly1305 / AES-256-GCM)**: Every packet contains an authenticated message tag (Poly1305 MAC). Any bit tampering causes immediate packet drop. |
| **Repudiation** | Disconnected client claims it never sent an interrupted command (e.g. `rm -rf`). | Low | **Monotonic Sequence Numbers & Local Audit Log**: Host maintains a local timestamped log of commands received via PTY. |
| **Information Disclosure** | VPS host provider, host snooper, or packet sniffer captures sensitive logs or passwords. | Critical | **Zero-Knowledge E2EE**: Terminal payloads are encrypted on the workstation before transmission. Relay only inspects transport headers (`session_id`, `sequence`). |
| **Denial of Service** | Flooding relay server with oversized terminal packets or socket exhaustion. | Medium | **Rate Limiting & Max Chunk Capping**: Relay enforces max frame size of 64 KB and maximum concurrent active sessions per IP address. |
| **Elevation of Privilege** | Remote command injection from mobile client bypassing shell constraints. | Critical | **Least-Privilege Execution**: Host daemon runs strictly with invoking user's permissions, never as root/Administrator. |

---

### 3. Cryptographic Implementation Details

#### 3.1 Primitives
* **Key Exchange (Handshake)**: Diffie-Hellman over Curve25519 (X25519) via Noise Protocol (`Noise_XX` pattern) or out-of-band ephemeral Pre-Shared Key (PSK) transfer via QR Code.
* **Symmetric Cipher**: **ChaCha20-Poly1305** (AEAD, IETF RFC 8439).
* **Hash Function**: **BLAKE2s** / SHA-256 for key derivation and integrity hashing.
* **Entropy Source**: Cryptographically Secure Pseudorandom Number Generator (CSPRNG via `getrandom` crate / Android `SecureRandom`).

#### 3.2 Anti-Replay Mechanism
* Every packet envelope contains a 64-bit integer sequence counter: $S_n$.
* The receiving host and client maintain a sliding window of size 128.
* Packets with $S \le S_{max} - 128$ or packets already seen in the sliding window are instantly rejected and discarded.

---

### 4. ZeroTier Private Network Integration Guidelines

In production deployment, the Relay Server and Workstations should leverage the existing ZeroTier mesh network:
* **Relay Bind Address**: Bind exclusively to the ZeroTier virtual IP address (e.g. `10.147.x.x` or loopback behind reverse proxy), **NEVER** `0.0.0.0` on the public cloud interface.
* **Firewall Configuration (UFW on VPS)**:
  ```bash
  # Block public access to relay port
  sudo ufw default deny incoming
  # Allow incoming relay connections ONLY from the ZeroTier interface (e.g., zt0)
  sudo ufw allow in on zt+ to any port 8080 proto tcp
  ```
* **Client Routing**: Android smartphone connects to the Relay Hub via its private ZeroTier managed IP, preventing port scanning by Shodan, Censys, or unauthorized parties.

---

### 5. Accidental Input Prevention (Safe Mobile UI)
Typing into an interactive shell via a touch screen introduces substantial risk of executing accidental destructive keystrokes.
* **Default State**: **Read-Only / Screen Lock Mode**.
* In Read-Only mode, touch gestures (scrolling, pinch-to-zoom) are processed purely within the local terminal emulator viewport buffer and do not emit byte streams.
* Switching to **Interactive Mode** requires an explicit user toggle on the AppBar with visual color indication (Green = Safe/Locked, Amber = Live Input).
