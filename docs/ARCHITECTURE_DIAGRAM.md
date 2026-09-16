# Architecture & Design Specifications
## Project: Terminal Mirror (Hardened Open-Source & Performance Edition)

---

### 1. Performance Pipeline & Backpressure Flow

```mermaid
graph TD
    subgraph Host_Workstation["Host Workstation (macOS / Windows)"]
        PTY["PTY Master Writer/Reader"]
        UTF8["Utf8StreamChunker"]
        MPSCQueue["Bounded MPSC Queue (1024 chunks)"]
        VTGrid["vt100 Virtual Grid Parser"]
        Coalescer["Adaptive Delta Coalescer"]
        Compressor["Zstandard (zstd Level 1) Engine"]
        Encryptor["ChaCha20-Poly1305 Cipher"]
        Sender["Tokio WebSocket Sender"]

        PTY -->|raw bytes| UTF8
        UTF8 -->|valid UTF-8| MPSCQueue
        MPSCQueue -->|async worker| VTGrid
        MPSCQueue -->|async worker| Coalescer
        
        Coalescer -->|payload > 512B| Compressor
        Coalescer -->|payload <= 512B| Encryptor
        Compressor -->|compressed blob| Encryptor
        Encryptor -->|E2EE envelope| Sender

        VTGrid -.->|downstream lag > 256KB: emit snapshot| Coalescer
    end

    subgraph Transport["Relay Transport"]
        Relay["Relay Hub (Rate-Limited & Blind)"]
    end

    subgraph Android_Client["Android Mobile Client"]
        Receiver["OkHttp WebSocket Receiver"]
        Decryptor["ChaCha20-Poly1305 Decryptor"]
        Decompressor["Zstandard Decompressor"]
        SurfaceEngine["Hardware-Accelerated SurfaceView Engine"]

        Sender ==>|Encrypted WSS| Relay
        Relay ==>|Encrypted WSS| Receiver
        Receiver --> Decryptor
        Decryptor -->|if zstd tagged| Decompressor
        Decryptor -->|if uncompressed| SurfaceEngine
        Decompressor --> SurfaceEngine
    end
```

---

### 2. Infrastructure Deployment Isolation (Zero-Contention Topology)

```mermaid
graph LR
    subgraph Private_Infra["Mas Mufid Personal Infrastructure (ZeroTier Overlay)"]
        MacLaptop["MacBook Pro (172.23.220.206)"]
        WinLaptop["ThinkPad Windows"]
        MotorolaPhone["Motorola Phone (172.23.191.143)"]
        
        subgraph Hostinger_VPS["VPS Hostinger (172.23.127.184)"]
            PrivateRelay["Terminal Mirror Private Relay (:8080)"]
            ProdContainers["20 Active Containers: Postgres, Qdrant, WAHA, N8N, Traefik..."]
        end

        MacLaptop <==>|ZeroTier Encrypted L3| PrivateRelay
        WinLaptop <==>|ZeroTier Encrypted L3| PrivateRelay
        MotorolaPhone <==>|ZeroTier Encrypted L3| PrivateRelay
    end

    subgraph Community_Infra["Open-Source Community Ecosystem (Self-Hosted)"]
        PublicUser["Community Developer"]
        HomeServer["User's Own VPS / Homelab"]
        UserPhone["User Phone"]

        PublicUser <==>|Docker Compose Relay| HomeServer
        UserPhone <==>|E2EE Direct/Relay| HomeServer
    end
```
