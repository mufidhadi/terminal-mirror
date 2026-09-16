# Architecture & Design Specifications
## Project: Terminal Mirror (Hardened Open-Source Edition)

---

### 1. Hardened System Architecture Diagram

```mermaid
graph TD
    subgraph Host_Daemon["Host Workstation Daemon (Rust)"]
        PTY["portable-pty Master (zsh / pwsh)"]
        UTF8Chunker["Utf8StreamChunker (Multibyte Slicing Guard)"]
        DecoupledMPSC["Bounded MPSC Channel (Capacity: 1024)"]
        VTGrid["vt100 Parser (Virtual Screen Grid)"]
        PairGuard["PairingGuard (3-Strikes Auto-Burn)"]
        CryptoEngine["E2EE Cipher (ChaCha20-Poly1305)"]
        WSTransport["Async WebSocket Client (Tokio)"]

        PTY -->|raw byte stream| UTF8Chunker
        UTF8Chunker -->|valid UTF-8 chunks| DecoupledMPSC
        DecoupledMPSC -->|async worker| VTGrid
        DecoupledMPSC -->|async worker| CryptoEngine
        VTGrid -.->|snapshot on reconnect| CryptoEngine
        CryptoEngine -->|encrypted MessagePack| WSTransport
        PairGuard -.->|auth verification| WSTransport
    end

    subgraph Relay_Server["Central Relay Hub (Hardened VPS)"]
        RateLimiter["IP Rate Limiter (Max 60 conn/min)"]
        FrameCap["Frame Size Validator (Max 64 KB)"]
        Router["Session Dispatcher (DashMap)"]

        RateLimiter --> FrameCap
        FrameCap --> Router
    end

    subgraph Android_Client["Android Mobile Application"]
        FgService["TerminalMirrorService (Foreground + Partial WakeLock)"]
        WSClient["OkHttp WebSocket Client"]
        KeyStore["Android KeyStore (Persistent Known Hosts)"]
        Decryptor["E2EE Decryptor"]
        TermView["Termux TerminalView Engine"]
        RawIME["Raw Input Interceptor (TYPE_NULL)"]

        FgService -->|keeps alive in pocket / Doze mode| WSClient
        WSClient --> Decryptor
        Decryptor --> TermView
        RawIME --> Decryptor
        Decryptor --> WSClient
        KeyStore <--> Decryptor
    end

    WSTransport <==>|Encrypted WSS| RateLimiter
    Router <==>|Encrypted WSS| WSClient
```

---

### 2. Sequence Diagram: 3-Strike Auto-Burn Defense

```mermaid
sequenceDiagram
    autonumber
    actor Attacker as Malicious Script / Botnet
    participant Relay as Relay Hub
    participant Host as Host Daemon (PairingGuard)

    Note over Host: Pairing Mode Active: "kuda-terbang-batu-merah"
    Attacker->>Relay: Try Guess #1: "123456"
    Relay->>Host: Forward Attempt
    Host->>Host: PairingGuard: Strike 1/3
    Host-->>Relay: Err(2 attempts left)
    Relay-->>Attacker: 401 Unauthorized (2 left)

    Attacker->>Relay: Try Guess #2: "admin1"
    Relay->>Host: Forward Attempt
    Host->>Host: PairingGuard: Strike 2/3
    Host-->>Relay: Err(1 attempt left)
    Relay-->>Attacker: 401 Unauthorized (1 left)

    Attacker->>Relay: Try Guess #3: "password"
    Relay->>Host: Forward Attempt
    Host->>Host: PairingGuard: Strike 3/3 -> AUTO-BURN TRIGGERED!
    Host->>Host: Destroy Session Secret & Invalidate Keys
    Host-->>Relay: Err(0 - Session Burned)
    Relay-->>Attacker: 403 Forbidden (Session Burned)

    Note over Attacker,Host: Even if Attacker guesses correctly on Attempt #4:
    Attacker->>Relay: Try Guess #4: "kuda-terbang-batu-merah"
    Relay->>Host: Forward Attempt
    Host->>Host: Session is permanently dead.
    Host-->>Relay: 403 Forbidden
```
