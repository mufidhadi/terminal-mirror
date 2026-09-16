# Architecture & Design Specifications
## Project: Terminal Mirror (Open-Source Edition)

---

### 1. High-Level System Architecture

```mermaid
C4Context
    title System Context - Terminal Mirror (Community & Self-Hosted)

    Person(developer, "Host Engineer", "Runs long-running tasks on workstation.")
    Person(mobile_user, "Mobile User", "Monitors & interacts via Android phone.")
    Person(spectator, "Viewer / Mentee", "Observes terminal session in read-only mode.")

    System_Boundary(workstations, "Host Workstations") {
        System(host_mac, "macOS Host Daemon", "POSIX PTY + vt100 Grid + E2EE")
        System(host_win, "Windows Host Daemon", "ConPTY + vt100 Grid + Debouncer")
    }

    System_Boundary(relays, "Relay Infrastructure") {
        System(public_relay, "Community Rendezvous Relay", "Zero-Knowledge blind router for public use")
        System(private_relay, "Self-Hosted Private Hub", "Docker Compose on private VPS / ZeroTier")
    }

    System_Boundary(mobiles, "Client Applications") {
        System(android_app, "Terminal Mirror Android App", "Multi-tab UI + Termux engine + KeyStore")
    }

    Rel(developer, host_mac, "Interacts locally with")
    Rel(developer, host_win, "Interacts locally with")
    
    Rel(host_mac, public_relay, "Encrypted Stream (E2EE)", "WSS")
    Rel(host_win, private_relay, "Encrypted Stream (E2EE)", "WSS / ZeroTier")
    
    Rel(android_app, public_relay, "Subscribes (Admin)", "WSS")
    Rel(android_app, private_relay, "Subscribes (Admin)", "WSS / ZeroTier")
    
    Rel(spectator, public_relay, "Subscribes (Spectator)", "WSS")
```

---

### 2. Internal Container Architecture & Virtual Grid Engine

```mermaid
graph TD
    subgraph Host_Daemon["Host Workstation Daemon (Rust)"]
        PTY["portable-pty Master"]
        VTGrid["vt100 Parser (Virtual Screen Grid)"]
        Debouncer["ConPTY Resize Debouncer (200ms)"]
        RBAC["Role & Auth Filter (Admin vs Spectator)"]
        CryptoEngine["E2EE Engine (ChaCha20-Poly1305)"]
        WSTransport["Async WebSocket Client (Tokio)"]

        PTY -->|raw ANSI bytes| VTGrid
        PTY -->|delta stream| CryptoEngine
        VTGrid -.->|snapshot on reconnect| CryptoEngine
        CryptoEngine -->|encrypted MessagePack| WSTransport
        WSTransport -->|incoming packets| RBAC
        RBAC -->|validated Admin input| PTY
        RBAC -->|resize event| Debouncer
        Debouncer -->|debounced resize| PTY
    end

    subgraph Relay_Server["Relay Hub (VPS / Community)"]
        Router["Session Hub & Stream Dispatcher (DashMap)"]
    end

    subgraph Android_Client["Android Mobile Client (Kotlin & Compose)"]
        WSClient["OkHttp WebSocket Client"]
        KeyStore["Android KeyStore (Persistent Known Hosts)"]
        Decryptor["E2EE Decryptor"]
        TabManager["Multi-Session Tab Controller"]
        TermRenderer["Termux TerminalView (Native Canvas)"]
        RawKeyIME["Raw Input Interceptor (TYPE_NULL)"]

        WSClient --> Decryptor
        Decryptor --> TabManager
        TabManager --> TermRenderer
        RawKeyIME --> Decryptor
        Decryptor --> WSClient
        KeyStore <--> Decryptor
    end

    WSTransport <==>|Encrypted WSS| Router
    Router <==>|Encrypted WSS| WSClient
```

---

### 3. Sequence Diagrams

#### 3.1 Headless PIN Pairing Handshake
```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer (Laptop)
    participant Host as Host Daemon
    participant Relay as Relay Hub
    participant Phone as Android App

    Dev->>Host: Run `terminal-mirror host`
    Host->>Relay: Register Session with 6-Digit PIN (e.g. 491-023)
    Host->>Dev: Display "Pairing PIN: 491-023 (Valid 10m)"

    Dev->>Phone: Open App -> Enter PIN "491-023"
    Phone->>Relay: Request Pairing { PIN: 491-023, Client_PublicKey }
    Relay->>Host: Forward Pairing Request
    Host->>Host: Verify PIN & Derive Shared Key
    Host->>Relay: Confirm Pairing { Host_PublicKey, AuthToken }
    Relay->>Phone: Forward Confirmation
    Phone->>Phone: Store Host in Android KeyStore ("Known Hosts")
    Host->>Host: Store Phone in `authorized_devices.toml`
    Note over Host,Phone: Devices paired permanently! Future connections are 1-tap.
```

#### 3.2 Seamless Roaming & Screen Snapshot Resync
```mermaid
sequenceDiagram
    autonumber
    actor User as Mobile User
    participant Phone as Android App
    participant Relay as Relay Hub
    participant Host as Host Daemon
    participant VT as vt100 Virtual Grid

    Note over Host,VT: Running `nvim` with custom statusline
    Note over Phone: User steps into elevator -> Wi-Fi drops!
    Phone->>Phone: Detect socket drop -> Exponential backoff reconnect
    Note over Phone: Elevator doors open -> 4G signal restored!
    Phone->>Relay: Reconnect WebSocket & Re-subscribe
    Relay->>Host: Client Reconnected Notification
    Host->>VT: Fetch current visual screen buffer (lines, cursor, alternate screen)
    VT-->>Host: ScreenSnapshot (80x24 characters + attributes)
    Host->>Host: Encrypt `ScreenStateSync { snapshot }`
    Host->>Relay: Send EncryptedBlob
    Relay->>Phone: Forward EncryptedBlob
    Phone->>Phone: Decrypt snapshot & render instantly
    Note over User,Phone: Screen restores perfectly without garbled characters!
```
