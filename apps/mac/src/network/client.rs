use futures_util::{sink::SinkExt, stream::StreamExt};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::Duration;
use terminal_mirror_protocol::{CompressionAlgorithm, Packet, PacketPayload};
use tokio::sync::{mpsc, Mutex};
use tokio_tungstenite::connect_async;
use tokio_tungstenite::tungstenite::Message;
use tracing::{error, info, warn};

pub struct RelayHostClient {
    pub relay_url: String,
    pub auth_token: String,
    pub session_id: String,
    sequence: Arc<AtomicU64>,
}

impl RelayHostClient {
    pub fn new(relay_url: String, auth_token: String, session_id: String) -> Self {
        Self {
            relay_url,
            auth_token,
            session_id,
            sequence: Arc::new(AtomicU64::new(0)),
        }
    }

    /// Constructs the full WebSocket connection URL including authentication and routing parameters.
    pub fn build_ws_url(&self) -> String {
        let separator = if self.relay_url.contains('?') { "&" } else { "?" };
        format!(
            "{}{separator}token={}&session_id={}&role=host",
            self.relay_url, self.auth_token, self.session_id
        )
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

                    // Task 1: Forward PTY output frames down to Relay Server
                    let mut forward_downstream = tokio::spawn(async move {
                        let mut rx = rx_handle.lock().await;
                        while let Some(bytes) = rx.recv().await {
                            let seq = sequence.fetch_add(1, Ordering::Relaxed);
                            let packet = Packet::new(
                                &session_id,
                                seq,
                                PacketPayload::TerminalOutput {
                                    bytes,
                                    compression: CompressionAlgorithm::None,
                                },
                            );

                            if let Ok(encoded) = packet.to_msgpack() {
                                if sink.send(Message::Binary(encoded)).await.is_err() {
                                    break;
                                }
                            }
                        }
                    });

                    // Task 2: Receive upstream mobile keystrokes and pass to PTY writer
                    let up_tx = upstream_tx.clone();
                    let mut receive_upstream = tokio::spawn(async move {
                        while let Some(Ok(msg)) = stream.next().await {
                            match msg {
                                Message::Binary(bytes) => {
                                    if let Ok(packet) = Packet::from_msgpack(&bytes) {
                                        if let PacketPayload::TerminalInput { bytes: input_bytes } = packet.payload {
                                            let _ = up_tx.send(input_bytes).await;
                                        }
                                    }
                                }
                                Message::Close(_) => break,
                                _ => {}
                            }
                        }
                    });

                    tokio::select! {
                        _ = &mut forward_downstream => {
                            receive_upstream.abort();
                        }
                        _ = &mut receive_upstream => {
                            forward_downstream.abort();
                        }
                    }

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
}
