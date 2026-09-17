use futures_util::{sink::SinkExt, stream::StreamExt};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::Duration;
use terminal_mirror_protocol::{CompressionAlgorithm, E2eeCipher, Packet, PacketPayload};
use tokio::sync::{mpsc, Mutex};
use tokio_tungstenite::connect_async;
use tokio_tungstenite::tungstenite::Message;
use tracing::{error, info, warn};

fn url_encode(input: &str) -> String {
    let mut encoded = String::new();
    for b in input.bytes() {
        match b {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                encoded.push(b as char);
            }
            b' ' => encoded.push('+'),
            _ => encoded.push_str(&format!("%{:02X}", b)),
        }
    }
    encoded
}

pub struct RelayHostClient {
    pub relay_url: String,
    pub auth_token: String,
    pub session_id: String,
    pub host_name: Option<String>,
    pub shell: Option<String>,
    pub cipher: Option<Arc<E2eeCipher>>,
    sequence: Arc<AtomicU64>,
}

impl RelayHostClient {
    pub fn new(relay_url: String, auth_token: String, session_id: String) -> Self {
        Self {
            relay_url,
            auth_token,
            session_id,
            host_name: None,
            shell: None,
            cipher: None,
            sequence: Arc::new(AtomicU64::new(0)),
        }
    }

    /// Attaches host metadata (friendly name and resolved shell) for display on remote clients
    pub fn with_metadata(mut self, host_name: Option<String>, shell: Option<String>) -> Self {
        self.host_name = host_name;
        self.shell = shell;
        self
    }

    /// Attaches an E2EE cipher engine for authenticated zero-knowledge transport
    pub fn with_cipher(mut self, cipher: Arc<E2eeCipher>) -> Self {
        self.cipher = Some(cipher);
        self
    }

    /// Constructs the full WebSocket connection URL including authentication, routing, and metadata parameters.
    pub fn build_ws_url(&self) -> String {
        let separator = if self.relay_url.contains('?') {
            "&"
        } else {
            "?"
        };
        let mut url = format!(
            "{}{separator}token={}&session_id={}&role=host",
            self.relay_url,
            url_encode(&self.auth_token),
            url_encode(&self.session_id)
        );
        if let Some(host_name) = &self.host_name {
            url.push_str(&format!("&host_name={}", url_encode(host_name)));
        }
        if let Some(shell) = &self.shell {
            url.push_str(&format!("&shell={}", url_encode(shell)));
        }
        url
    }

    /// Runs the resilient connection loop, handling automatic reconnection with exponential backoff.
    pub async fn run(
        &self,
        downstream_rx: mpsc::Receiver<Vec<u8>>,
        upstream_tx: mpsc::Sender<Vec<u8>>,
    ) {
        let shared_rx = Arc::new(Mutex::new(downstream_rx));
        let mut backoff = Duration::from_secs(1);
        let max_backoff = Duration::from_secs(30);

        loop {
            let ws_url = self.build_ws_url();
            info!("Connecting to Relay Server: {}", self.relay_url);

            match connect_async(&ws_url).await {
                Ok((ws_stream, _response)) => {
                    info!("Successfully connected to Relay Hub as Host Publisher!");
                    backoff = Duration::from_secs(1); // Reset backoff on successful connection

                    let (mut sink, mut stream) = ws_stream.split();
                    let session_id = self.session_id.clone();
                    let sequence = Arc::clone(&self.sequence);
                    let rx_handle = Arc::clone(&shared_rx);
                    let cipher_downstream = self.cipher.clone();
                    let cipher_upstream = self.cipher.clone();

                    let (ws_out_tx, mut ws_out_rx) = mpsc::channel::<Message>(256);

                    // Task 1: Dedicated WebSocket Sink sender
                    let mut send_to_ws = tokio::spawn(async move {
                        while let Some(msg) = ws_out_rx.recv().await {
                            if sink.send(msg).await.is_err() {
                                break;
                            }
                        }
                    });

                    // Task 2: 15-second heartbeat ping loop (prevents idle disconnect & keeps presence honest)
                    let ping_tx = ws_out_tx.clone();
                    let mut heartbeat_ping = tokio::spawn(async move {
                        let mut interval = tokio::time::interval(Duration::from_secs(15));
                        interval.tick().await; // Initial tick is immediate, skip
                        loop {
                            interval.tick().await;
                            if ping_tx.send(Message::Ping(vec![])).await.is_err() {
                                break;
                            }
                        }
                    });

                    // Task 3: Forward PTY output frames down to Relay Server
                    let pty_out_tx = ws_out_tx.clone();
                    let mut forward_downstream = tokio::spawn(async move {
                        let mut rx = rx_handle.lock().await;
                        while let Some(bytes) = rx.recv().await {
                            let seq = sequence.fetch_add(1, Ordering::Relaxed);
                            let payload = if let Some(cipher) = &cipher_downstream {
                                match cipher.encrypt(seq, &bytes) {
                                    Ok(ciphertext) => PacketPayload::EncryptedBlob {
                                        nonce: seq,
                                        ciphertext,
                                    },
                                    Err(e) => {
                                        error!("Failed to encrypt downstream frame: {:?}", e);
                                        continue;
                                    }
                                }
                            } else {
                                PacketPayload::TerminalOutput {
                                    bytes,
                                    compression: CompressionAlgorithm::None,
                                }
                            };

                            let packet = Packet::new(&session_id, seq, payload);

                            if let Ok(encoded) = packet.to_msgpack() {
                                if pty_out_tx.send(Message::Binary(encoded)).await.is_err() {
                                    break;
                                }
                            }
                        }
                    });

                    // Task 4: Receive upstream mobile keystrokes and pass to PTY writer
                    let up_tx = upstream_tx.clone();
                    let mut receive_upstream = tokio::spawn(async move {
                        while let Some(Ok(msg)) = stream.next().await {
                            match msg {
                                Message::Binary(bytes) => {
                                    if let Ok(packet) = Packet::from_msgpack(&bytes) {
                                        match packet.payload {
                                            PacketPayload::EncryptedBlob { nonce, ciphertext } => {
                                                if let Some(cipher) = &cipher_upstream {
                                                    match cipher.decrypt(nonce, &ciphertext) {
                                                        Ok(input_bytes) => {
                                                            let _ = up_tx.send(input_bytes).await;
                                                        }
                                                        Err(e) => {
                                                            warn!("Failed to decrypt upstream packet (auth tag invalid): {:?}", e);
                                                        }
                                                    }
                                                }
                                            }
                                            PacketPayload::TerminalInput { bytes: input_bytes } => {
                                                let _ = up_tx.send(input_bytes).await;
                                            }
                                            _ => {}
                                        }
                                    }
                                }
                                Message::Close(_) => break,
                                _ => {}
                            }
                        }
                    });

                    tokio::select! {
                        _ = &mut forward_downstream => {},
                        _ = &mut receive_upstream => {},
                        _ = &mut send_to_ws => {},
                        _ = &mut heartbeat_ping => {},
                    }

                    forward_downstream.abort();
                    receive_upstream.abort();
                    send_to_ws.abort();
                    heartbeat_ping.abort();

                    warn!("Relay connection dropped. Reconnecting in {:?}", backoff);
                }
                Err(e) => {
                    error!(
                        "Failed to connect to relay server ({}): {}. Retrying in {:?}",
                        self.relay_url, e, backoff
                    );
                }
            }

            tokio::time::sleep(backoff).await;
            backoff = (backoff * 2).min(max_backoff);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_build_ws_url_formatting() {
        let client = RelayHostClient::new(
            "ws://127.0.0.1:8080/ws".to_string(),
            "secret123".to_string(),
            "mac-workstation-1".to_string(),
        );

        let url = client.build_ws_url();
        assert_eq!(
            url,
            "ws://127.0.0.1:8080/ws?token=secret123&session_id=mac-workstation-1&role=host"
        );
    }

    #[test]
    fn test_build_ws_url_with_existing_query_params() {
        let client = RelayHostClient::new(
            "wss://relay.masmuf.cloud/ws?debug=true".to_string(),
            "tokenABC".to_string(),
            "session99".to_string(),
        );

        let url = client.build_ws_url();
        assert_eq!(
            url,
            "wss://relay.masmuf.cloud/ws?debug=true&token=tokenABC&session_id=session99&role=host"
        );
    }

    #[test]
    fn test_build_ws_url_with_metadata() {
        let client = RelayHostClient::new(
            "ws://127.0.0.1:8080/ws".to_string(),
            "secret123".to_string(),
            "sess-mac".to_string(),
        )
        .with_metadata(
            Some("MacBook Pro Mas Mufid".to_string()),
            Some("/bin/zsh".to_string()),
        );

        let url = client.build_ws_url();
        assert_eq!(
            url,
            "ws://127.0.0.1:8080/ws?token=secret123&session_id=sess-mac&role=host&host_name=MacBook+Pro+Mas+Mufid&shell=%2Fbin%2Fzsh"
        );
    }
}
