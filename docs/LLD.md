# Low-Level Design (LLD)
## Project: Terminal Mirror

---

### 1. PTY Management Subsystem (`apps/mac` & `apps/windows`)

#### 1.1 `PtySession` Lifecycle
The PTY session is managed through the `portable-pty` crate abstraction:

```rust
pub struct PtySession {
    pub pair: portable_pty::PtyPair,
    pub child: Box<dyn portable_pty::Child + Send + Sync>,
    pub ring_buffer: Arc<parking_lot::Mutex<RingBuffer>>,
}
```

* **Reader Thread**: A dedicated blocking task runs an infinite loop reading from `pair.master.try_clone_reader()` into a 4 KB buffer:
  ```rust
  let mut buffer = [0u8; 4096];
  while let Ok(n) = reader.read(&mut buffer) {
      if n == 0 { break; } // EOF (shell closed)
      let chunk = &buffer[..n];
      ring_buffer.lock().append(chunk);
      outbound_tx.send(PacketPayload::TerminalOutput { bytes: chunk.to_vec() });
  }
  ```
* **Writer Thread**: Incoming `PacketPayload::TerminalInput` frames are dispatched to `pair.master.take_writer()` non-blockingly using Tokio `spawn_blocking`.
* **Resize Handler**: Window resize events are processed by invoking:
  ```rust
  pair.master.resize(PtySize {
      rows: new_rows,
      cols: new_cols,
      pixel_width: 0,
      pixel_height: 0,
  })?;
  ```

---

### 2. Relay Hub Routing Subsystem (`services/relay-server`)

#### 2.1 Concurrency & Session Dispatching
The central relay maintains session routing via a concurrent hash map:

```rust
pub struct SessionHub {
    // Maps session_id to a broadcast channel sender
    pub channels: DashMap<String, broadcast::Sender<Vec<u8>>>,
    // Maps session_id to connected client count
    pub subscribers: DashMap<String, usize>,
}
```

* When a Host registers a session, a new `broadcast::channel(1024)` is instantiated.
* When an Android client subscribes to a session, it receives a `broadcast::Receiver` instance.
* All incoming binary frames from the Host are broadcast directly to subscribers in $O(1)$ lookup time without inspecting or modifying payload ciphertext.

---

### 3. Android Terminal Emulator Subsystem (`apps/android`)

#### 3.1 Jetpack Compose Integration
* **`TerminalView` Embedding**: Uses `AndroidView` to wrap the Termux `TerminalView` widget:
  ```kotlin
  AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
          TerminalView(context, null).apply {
              attachSession(terminalSession)
              setTextSize(spToPx(12f))
          }
      }
  )
  ```
* **Virtual Keyboard Dispatcher**:
  ```kotlin
  fun sendSpecialKey(key: SpecialKey) {
      val code = when (key) {
          SpecialKey.CTRL_C -> byteArrayOf(0x03)
          SpecialKey.ESC -> byteArrayOf(0x1B)
          SpecialKey.TAB -> byteArrayOf(0x09)
          SpecialKey.UP -> byteArrayOf(0x1B, 0x5B, 0x41)
          SpecialKey.DOWN -> byteArrayOf(0x1B, 0x5B, 0x42)
          SpecialKey.LEFT -> byteArrayOf(0x1B, 0x5B, 0x44)
          SpecialKey.RIGHT -> byteArrayOf(0x1B, 0x5B, 0x43)
      }
      relayClient.sendBinary(encodeInputPacket(code))
  }
  ```
