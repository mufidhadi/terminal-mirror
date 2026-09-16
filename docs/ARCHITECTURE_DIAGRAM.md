# Architecture & Design Specifications
## Project: Terminal Mirror

---

### 1. System Context & Overview (C4 Context Diagram)

The following diagram illustrates the boundaries and actors interacting with the Terminal Mirror ecosystem:

```mermaid
C4Context
    title System Context Diagram - Terminal Mirror

    Person(developer, "Engineer / User", "Monitors and interacts with long-running terminal builds and commands.")
    
    System_Boundary(workstation_mac, "macOS Workstation") {
        System(host_mac, "Mac Host Agent", "Spawns Unix PTY, captures ANSI stream, encrypts packets.")
    }
    
    System_Boundary(workstation_win, "Windows Workstation") {
        System(host_win, "Windows Host Agent", "Spawns ConPTY, captures VT stream, encrypts packets.")
    }
    
    System_Boundary(vps_infra, "Private VPS (Hostinger)") {
        System(relay_server, "Relay Hub (Rust Tokio)", "Routes encrypted binary streams across sessions. Zero-Knowledge Blind Router.")
    }
    
    System_Boundary(mobile_device, "Android Smartphone") {
        System(android_app, "Terminal Mirror App", "Decodes stream, renders VT100, displays multi-session tabs.")
    }

    Rel(developer, host_mac, "Runs terminal tasks on", "Local Keyboard")
    Rel(developer, host_win, "Runs terminal tasks on", "Local Keyboard")
    Rel(developer, android_app, "Views & monitors on", "Touch Screen")

    Rel(host_mac, relay_server, "Publishes encrypted stream via", "WSS / ZeroTier")
    Rel(host_win, relay_server, "Publishes encrypted stream via", "WSS / ZeroTier")
    Rel(android_app, relay_server, "Subscribes to parallel streams via", "WSS / ZeroTier")
```

---

### 2. Container Architecture (C4 Container Diagram)

Detailed view of the internal software modules across Host, Server, and Mobile:

```mermaid
graph TD
    subgraph macOS_Host["macOS Host Machine"]
        ShellMac["Shell Process (zsh/bash)"]
        PTYMac["portable-pty (POSIX Master/Slave)"]
        RingBufMac["1MB FIFO Ring Buffer"]
        CryptoMac["E2EE Cipher (ChaCha20-Poly1305)"]
        WSClientMac["WebSocket Client (tokio-tungstenite)"]

        ShellMac <-->|stdin / stdout| PTYMac
        PTYMac -->|raw ANSI stream| RingBufMac
        RingBufMac -->|chunked stream| CryptoMac
        CryptoMac -->|encrypted MessagePack| WSClientMac
        WSClientMac -->|decrypted input keystrokes| PTYMac
    end

    subgraph Windows_Host["Windows Host Machine"]
        ShellWin["Shell Process (powershell.exe)"]
        ConPTYWin["portable-pty (Windows ConPTY)"]
        RingBufWin["1MB FIFO Ring Buffer"]
        CryptoWin["E2EE Cipher (ChaCha20-Poly1305)"]
        WSClientWin["WebSocket Client (tokio-tungstenite)"]

        ShellWin <-->|stdin / stdout| ConPTYWin
        ConPTYWin -->|raw VT stream| RingBufWin
        RingBufWin -->|chunked stream| CryptoWin
        CryptoWin -->|encrypted MessagePack| WSClientWin
        WSClientWin -->|decrypted input keystrokes| ConPTYWin
    end

    subgraph VPS_Relay["Central Relay Hub (VPS Hostinger)"]
        Listener["TCP / TLS Listener (port: 8080)"]
        AuthInterceptor["Auth & Token Interceptor"]
        SessionHub["Session Dispatcher (DashMap)"]
        BrodcastMac["Channel: session_mac"]
        BrodcastWin["Channel: session_win"]

        Listener --> AuthInterceptor
        AuthInterceptor --> SessionHub
        SessionHub --> BrodcastMac
        SessionHub --> BrodcastWin
    end

    subgraph Android_Client["Android Mobile Application"]
        WSClientDroid["WebSocket Network Engine (OkHttp)"]
        CryptoDroid["E2EE Decryptor / KeyStore"]
        TabController["Multi-Session Tab Manager"]
        TermViewMac["Terminal Emulator View: Mac (termux-view)"]
        TermViewWin["Terminal Emulator View: Windows (termux-view)"]
        KeyBar["Accessory Keyboard Bar (Ctrl, Alt, Esc)"]
        LockGuard["View-Only Safety Filter"]

        WSClientDroid --> CryptoDroid
        CryptoDroid --> TabController
        TabController --> TermViewMac
        TabController --> TermViewWin
        KeyBar --> LockGuard
        LockGuard -->|filtered input| WSClientDroid
    end

    WSClientMac <-->|Encrypted WSS| Listener
    WSClientWin <-->|Encrypted WSS| Listener
    Listener <-->|Multiplexed Streams| WSClientDroid
```

---

### 3. End-to-End Sequence Diagrams

#### 3.1 Host Registration and QR Code Pairing
```mermaid
sequenceDiagram
    autonumber
    actor User as Engineer (Mas Mufid)
    participant Host as Host Daemon (Mac/Win)
    participant Relay as VPS Relay Hub
    participant Phone as Android App

    User->>Host: Execute `terminal-mirror host`
    Host->>Host: Open PTY / ConPTY session
    Host->>Host: Generate Ephemeral Keypair (X25519) + PreSharedKey
    Host->>Relay: Connect WSS + `RegisterHost { host_id, token }`
    Relay-->>Host: `HostRegistered { success: true }`
    Host->>User: Display Terminal QR Code (contains pairing payload)

    User->>Phone: Open Terminal Mirror App -> Tap "Scan QR"
    Phone->>Phone: Scan & Parse QR Payload
    Phone->>Relay: Connect WSS + `SubscribeSession { session_id, token }`
    Relay-->>Phone: `SessionSubscribed { success: true }`
    Relay-->>Host: Client Attached Notification
    Host->>Phone: Stream RingBuffer Snapshot (Recent terminal state)
    Phone->>User: Render Terminal Screen
```

#### 3.2 Real-Time Streaming and Input Flow
```mermaid
sequenceDiagram
    autonumber
    actor User as Engineer (Mas Mufid)
    participant Phone as Android App
    participant Relay as VPS Relay Hub
    participant Host as Host Daemon
    participant PTY as Workstation PTY

    Note over Host,PTY: Long-running build running (`cargo build`)
    PTY->>Host: Emit ANSI stream bytes
    Host->>Host: Encrypt stream payload (ChaCha20-Poly1305)
    Host->>Relay: Send `Packet::EncryptedBlob (TerminalOutput)`
    Relay->>Phone: Forward `Packet::EncryptedBlob` to subscriber
    Phone->>Phone: Decrypt payload using session key
    Phone->>User: Render colored output in Terminal View

    alt User Sends Keystroke (Input Mode Active)
        User->>Phone: Tap "CTRL + C"
        Phone->>Phone: Verify LockGuard is UNLOCKED
        Phone->>Phone: Encrypt keystroke bytes `\x03`
        Phone->>Relay: Send `Packet::EncryptedBlob (TerminalInput)`
        Relay->>Host: Forward to target host
        Host->>Host: Decrypt payload
        Host->>PTY: Write `\x03` to PTY Master Writer
        PTY-->>PTY: SIGINT sent to child process!
    end
```

---

### 4. Resiliency & Network Reconnection State Machine

```mermaid
stateDiagram-v2
    [*] --> Disconnected
    Disconnected --> Connecting: App launch / Network restored
    Connecting --> Authenticating: Socket established
    Authenticating --> Streaming: Subscription confirmed
    Streaming --> Paused: Network switch (Wi-Fi to 4G)
    Paused --> Connecting: Exponential backoff (100ms..2000ms)
    Streaming --> Terminated: Host exited / Process killed
    Terminated --> [*]
```
