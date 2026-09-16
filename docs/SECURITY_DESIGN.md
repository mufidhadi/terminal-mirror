# Security Architecture & Threat Model
## Project: Terminal Mirror (Open-Source Edition)

---

### 1. Executive Security Posture

In a global open-source environment, users run terminal sessions across untrusted coffee shop Wi-Fi networks, cellular connections, and public community relays.

Terminal Mirror enforces a **Zero-Trust Security Architecture**:
1. **The Relay is Always Untrusted**: Whether using a free community relay or a self-hosted VPS, the relay server is treated as an active adversary position. It never possesses decryption keys.
2. **Network Agnostic E2EE**: Security does not depend on ZeroTier, WireGuard, or local LAN boundaries. All packets are encrypted at Layer 7 before reaching any network transport.
3. **One-Time Trust, Zero Friction**: Cryptographic pairing occurs once; persistent device keys prevent credential fatigue while maintaining mutual authentication.
4. **Immediate Revocation**: The workstation host holds supreme authority, capable of revoking active remote sessions at any moment with an instant physical hotkey.

---

### 2. Cryptographic Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    HOST WORKSTATION                         │
│  1. Generates Ephemeral Keypair (X25519)                    │
│  2. Performs Diffie-Hellman Handshake with Mobile Client    │
│  3. Derives Symmetric Session Key (K_sess) via HKDF-SHA256  │
└──────────────────────────────┬──────────────────────────────┘
                               │
               Ciphertext: ChaCha20-Poly1305
               [Payload + 16-byte Poly1305 MAC]
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 RELAY HUB (ZERO-KNOWLEDGE)                  │
│  • Reads: session_id, sequence, trace_id                    │
│  • Cannot Read: ciphertext payload                          │
│  • Forwards opaque binary envelope to subscribed clients    │
└──────────────────────────────┬──────────────────────────────┘
                               │
               Ciphertext: ChaCha20-Poly1305
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   ANDROID MOBILE CLIENT                     │
│  1. Authenticates MAC tag using K_sess                      │
│  2. Decrypts payload into ANSI stream / ScreenStateSync     │
│  3. Renders clean VT100 grid                                │
└─────────────────────────────────────────────────────────────┘
```

#### 2.1 Cryptographic Primitives
* **Key Exchange**: Curve25519 ECDH (X25519) via `snow` or `ring`.
* **Symmetric Encryption**: **ChaCha20-Poly1305** (AEAD, IETF RFC 8439).
* **Key Derivation Function**: **HKDF-SHA256** (RFC 5869) combining ephemeral shared secret + pre-shared pairing secret.
* **Nonce Strategy**: 64-bit monotonically increasing sequence number formatted as a 96-bit nonce $(0^{32} \parallel S_n)$, ensuring nonces are never repeated for a given key.

---

### 3. Pairing & Persistent Device Trust Protocol

#### 3.1 Initial Pairing Handshake (First Contact)
1. **Host CLI outputs**:
   * Public Key ($PK_{host}$)
   * Pairing Secret ($S_{pair}$)
   * Encoded as an ASCII QR code OR a 6-digit short PIN ($PIN_{otp}$).
2. **Mobile Client connects via Relay**:
   * Sends $PK_{client}$ encrypted with $S_{pair}$ / $PIN_{otp}$.
3. **Mutual Key Exchange**:
   * Both endpoints derive $K_{sess}$.
4. **Persistent Fingerprint Storage**:
   * **Host**: Appends client public key fingerprint to `~/.config/terminal-mirror/authorized_devices.toml`.
   * **Android**: Saves host descriptor and public key in **Android KeyStore** (`KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT`).

#### 3.2 Subsequent Daily Reconnections (Known Hosts)
* Mobile client issues a `SubscribeSession` packet containing an HMAC authentication tag generated with its stored persistent key.
* Host verifies the HMAC against its authorized devices list.
* Session stream starts immediately. **No QR scan or manual input required.**

---

### 4. Granular Role-Based Access Control (RBAC)

Terminal Mirror implements capability-based tokens:

| Token Type | Capabilities | Typical Use Case |
| :--- | :--- | :--- |
| **Interactive Admin Token** | • Receive output stream<br>• Send keystrokes (`TerminalInput`)<br>• Request window resize (`TerminalResize`) | Personal workstation remote control from user's own phone. |
| **Spectator / Read-Only Token** | • Receive output stream<br>• Receive screen snapshots<br>• **All inputs discarded by Host PTY** | Sharing builds with colleagues, student mentoring, public demos. |

---

### 5. Host Emergency Revocation (The Kill Switch)
* **Physical Hotkey**: Pressing `Ctrl + Shift + Q` in the host terminal immediately triggers `SessionRevoked`.
* **Action**:
  1. Closes all active WebSocket connections to the relay.
  2. Rotates the ephemeral session key.
  3. De-registers the session ID from the relay.
  4. Returns the host terminal to normal un-streamed mode.
