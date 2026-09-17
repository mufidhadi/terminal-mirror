use futures_util::{SinkExt, StreamExt};
use std::net::SocketAddr;
use terminal_mirror_protocol::{CompressionAlgorithm, Packet, PacketPayload};
use terminal_mirror_relay::config::RelayServerConfig;
use terminal_mirror_relay::create_app;
use terminal_mirror_relay::hub::SessionHub;
use terminal_mirror_relay::metrics::RelayMetrics;
use terminal_mirror_relay::middleware::IpRateLimiter;
use terminal_mirror_relay::ws::AppState;
use tokio_tungstenite::connect_async;
use tokio_tungstenite::tungstenite::Message;

async fn spawn_test_server(max_conn_per_min: u32) -> (SocketAddr, String) {
    let auth_token = "e2e_secret_token".to_string();
    let config = RelayServerConfig {
        bind_addr: "127.0.0.1:0".to_string(),
        auth_token: auth_token.clone(),
        max_connections_per_min: max_conn_per_min,
        max_payload_bytes: 65536,
        stale_session_timeout_secs: 300,
    };

    let state = AppState {
        rate_limiter: IpRateLimiter::new(config.max_connections_per_min),
        hub: SessionHub::new(),
        metrics: RelayMetrics::new(),
        config,
    };

    let app = create_app(state);
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();

    tokio::spawn(async move {
        axum::serve(
            listener,
            app.into_make_service_with_connect_info::<SocketAddr>(),
        )
        .await
        .unwrap();
    });

    (addr, auth_token)
}

#[tokio::test]
async fn test_e2e_bidirectional_streaming_between_host_and_subscriber() {
    let (addr, token) = spawn_test_server(60).await;
    let session_id = "test-session-e2e";

    // 1. Connect Host Agent
    let host_url = format!("ws://{addr}/ws?token={token}&session_id={session_id}&role=host");
    let (ws_host, _) = connect_async(&host_url)
        .await
        .expect("Host failed to connect");
    let (mut host_sink, mut host_stream) = ws_host.split();

    // 2. Connect Mobile Subscriber
    let sub_url = format!("ws://{addr}/ws?token={token}&session_id={session_id}&role=client");
    let (ws_sub, _) = connect_async(&sub_url)
        .await
        .expect("Subscriber failed to connect");
    let (mut sub_sink, mut sub_stream) = ws_sub.split();

    // 3. Host publishes terminal output packet downstream
    let output_text = b"\x1b[32mroot@macbook:~$ cargo test\x1b[0m\r\n".to_vec();
    let out_packet = Packet::new(
        session_id,
        1,
        PacketPayload::TerminalOutput {
            bytes: output_text.clone(),
            compression: CompressionAlgorithm::None,
        },
    );
    let encoded = out_packet.to_msgpack().unwrap();
    host_sink.send(Message::Binary(encoded)).await.unwrap();

    // 4. Subscriber receives initial HostPresence first, then the terminal output packet
    let first_msg = sub_stream.next().await.unwrap().unwrap();
    if let Message::Binary(sub_bytes) = first_msg {
        let presence_packet = Packet::from_msgpack(&sub_bytes).unwrap();
        match presence_packet.payload {
            PacketPayload::HostPresence(presence) => {
                assert!(presence.online);
                assert_eq!(presence.session_id, session_id);
            }
            other => panic!("Expected initial HostPresence, got: {:?}", other),
        }
    } else {
        panic!("Expected binary WebSocket message for initial presence");
    }

    let sub_msg = sub_stream.next().await.unwrap().unwrap();
    if let Message::Binary(sub_bytes) = sub_msg {
        let received_packet = Packet::from_msgpack(&sub_bytes).unwrap();
        match received_packet.payload {
            PacketPayload::TerminalOutput { bytes, .. } => {
                assert_eq!(bytes, output_text);
            }
            other => panic!("Unexpected payload type received: {:?}", other),
        }
    } else {
        panic!("Expected binary WebSocket message");
    }

    // 5. Subscriber sends mobile keystroke packet upstream
    let input_text = b"ls -la\n".to_vec();
    let in_packet = Packet::new(
        session_id,
        2,
        PacketPayload::TerminalInput {
            bytes: input_text.clone(),
        },
    );
    let encoded_in = in_packet.to_msgpack().unwrap();
    sub_sink.send(Message::Binary(encoded_in)).await.unwrap();

    // 6. Host receives MessagePack packet and decodes keystrokes
    let host_msg = host_stream.next().await.unwrap().unwrap();
    if let Message::Binary(host_bytes) = host_msg {
        let received_in_packet = Packet::from_msgpack(&host_bytes).unwrap();
        match received_in_packet.payload {
            PacketPayload::TerminalInput { bytes } => {
                assert_eq!(bytes, input_text);
            }
            other => panic!("Unexpected upstream payload: {:?}", other),
        }
    } else {
        panic!("Expected binary WebSocket message on host stream");
    }
}

#[tokio::test]
async fn test_e2e_unauthorized_connection_rejected() {
    let (addr, _token) = spawn_test_server(60).await;
    let bad_url = format!("ws://{addr}/ws?token=invalid_secret&session_id=session-x&role=client");

    let result = connect_async(&bad_url).await;
    assert!(result.is_err(), "Unauthorized connection should fail");
}

#[tokio::test]
async fn test_e2e_rate_limiting_blocks_burst() {
    // Allow maximum 2 connections per minute
    let (addr, token) = spawn_test_server(2).await;

    let url1 = format!("ws://{addr}/ws?token={token}&session_id=s1&role=client");
    let url2 = format!("ws://{addr}/ws?token={token}&session_id=s2&role=client");
    let url3 = format!("ws://{addr}/ws?token={token}&session_id=s3&role=client");

    let c1 = connect_async(&url1).await;
    assert!(c1.is_ok(), "1st connection should succeed");

    let c2 = connect_async(&url2).await;
    assert!(c2.is_ok(), "2nd connection should succeed");

    // 3rd attempt exceeds limit of 2
    let c3 = connect_async(&url3).await;
    assert!(
        c3.is_err(),
        "3rd connection should be rejected with 429 Too Many Requests"
    );
}

#[tokio::test]
async fn test_e2e_chacha20poly1305_zero_knowledge_relay() {
    use terminal_mirror_protocol::E2eeCipher;

    let (addr, token) = spawn_test_server(60).await;
    let session_id = "e2ee-session-42";
    let shared_passphrase = "kilo-lima-sierra-tango";

    let host_cipher = E2eeCipher::from_secret(shared_passphrase);
    let subscriber_cipher = E2eeCipher::from_secret(shared_passphrase);

    // 1. Connect Host Agent
    let host_url = format!("ws://{addr}/ws?token={token}&session_id={session_id}&role=host");
    let (ws_host, _) = connect_async(&host_url)
        .await
        .expect("Host failed to connect");
    let (mut host_sink, mut host_stream) = ws_host.split();

    // 2. Connect Mobile Subscriber
    let sub_url = format!("ws://{addr}/ws?token={token}&session_id={session_id}&role=client");
    let (ws_sub, _) = connect_async(&sub_url)
        .await
        .expect("Subscriber failed to connect");
    let (mut sub_sink, mut sub_stream) = ws_sub.split();

    // 3. Host encrypts terminal output and publishes EncryptedBlob downstream
    let raw_terminal_output = b"\x1b[32muser@vps:~$ htop\x1b[0m\r\n";
    let seq_down = 1001;
    let ciphertext_down = host_cipher
        .encrypt(seq_down, raw_terminal_output)
        .expect("Host encryption failed");

    let out_packet = Packet::new(
        session_id,
        seq_down,
        PacketPayload::EncryptedBlob {
            nonce: seq_down,
            ciphertext: ciphertext_down,
        },
    );
    host_sink
        .send(Message::Binary(out_packet.to_msgpack().unwrap()))
        .await
        .unwrap();

    // 4. Subscriber receives initial HostPresence first, then EncryptedBlob
    let first_msg = sub_stream.next().await.unwrap().unwrap();
    if let Message::Binary(sub_bytes) = first_msg {
        let packet = Packet::from_msgpack(&sub_bytes).unwrap();
        match packet.payload {
            PacketPayload::HostPresence(presence) => {
                assert!(presence.online);
            }
            other => panic!("Expected HostPresence, got: {:?}", other),
        }
    } else {
        panic!("Expected binary WebSocket message for presence");
    }

    let sub_msg = sub_stream.next().await.unwrap().unwrap();
    if let Message::Binary(sub_bytes) = sub_msg {
        let packet = Packet::from_msgpack(&sub_bytes).unwrap();
        match packet.payload {
            PacketPayload::EncryptedBlob { nonce, ciphertext } => {
                let decrypted = subscriber_cipher
                    .decrypt(nonce, &ciphertext)
                    .expect("Subscriber decryption failed");
                assert_eq!(decrypted, raw_terminal_output);
            }
            other => panic!("Expected EncryptedBlob, got: {:?}", other),
        }
    } else {
        panic!("Expected binary WebSocket message");
    }

    // 5. Subscriber encrypts keystroke upstream and sends EncryptedBlob
    let raw_keystroke = b":wq\r";
    let seq_up = 5001;
    let ciphertext_up = subscriber_cipher
        .encrypt(seq_up, raw_keystroke)
        .expect("Subscriber encryption failed");

    let in_packet = Packet::new(
        session_id,
        seq_up,
        PacketPayload::EncryptedBlob {
            nonce: seq_up,
            ciphertext: ciphertext_up,
        },
    );
    sub_sink
        .send(Message::Binary(in_packet.to_msgpack().unwrap()))
        .await
        .unwrap();

    // 6. Host receives EncryptedBlob from Relay and decrypts keystroke
    let host_msg = host_stream.next().await.unwrap().unwrap();
    if let Message::Binary(host_bytes) = host_msg {
        let packet = Packet::from_msgpack(&host_bytes).unwrap();
        match packet.payload {
            PacketPayload::EncryptedBlob { nonce, ciphertext } => {
                let decrypted = host_cipher
                    .decrypt(nonce, &ciphertext)
                    .expect("Host decryption failed");
                assert_eq!(decrypted, raw_keystroke);
            }
            other => panic!("Expected EncryptedBlob upstream, got: {:?}", other),
        }
    } else {
        panic!("Expected binary WebSocket message on host stream");
    }
}

#[tokio::test]
async fn test_e2e_host_presence_lifecycle() {
    let (addr, token) = spawn_test_server(60).await;
    let session_id = "presence-lifecycle-session";

    // Scenario 1: Subscriber connects when host is offline
    // Subscriber must immediately receive HostPresence { online: false }
    let sub_url = format!("ws://{addr}/ws?token={token}&session_id={session_id}&role=client");
    let (ws_sub, _) = connect_async(&sub_url).await.expect("Subscriber connect");
    let (_sub_sink, mut sub_stream) = ws_sub.split();

    let msg1 = sub_stream.next().await.unwrap().unwrap();
    if let Message::Binary(bytes) = msg1 {
        let pkt = Packet::from_msgpack(&bytes).unwrap();
        match pkt.payload {
            PacketPayload::HostPresence(presence) => {
                assert!(!presence.online, "Host should be offline initially");
                assert_eq!(presence.session_id, session_id);
            }
            other => panic!("Expected initial offline HostPresence, got: {:?}", other),
        }
    } else {
        panic!("Expected binary frame");
    }

    // Scenario 2: Host connects with metadata
    // Active subscriber must receive broadcast HostPresence { online: true, host_name: ..., shell: ... }
    let host_url = format!(
        "ws://{addr}/ws?token={token}&session_id={session_id}&role=host&host_name=MacBook+Pro+Mas+Mufid&shell=%2Fbin%2Fzsh"
    );
    let (ws_host, _) = connect_async(&host_url).await.expect("Host connect");
    let (mut host_sink, _host_stream) = ws_host.split();

    let msg2 = sub_stream.next().await.unwrap().unwrap();
    if let Message::Binary(bytes) = msg2 {
        let pkt = Packet::from_msgpack(&bytes).unwrap();
        match pkt.payload {
            PacketPayload::HostPresence(presence) => {
                assert!(presence.online, "Host should be online after connection");
                assert_eq!(presence.host_name.as_deref(), Some("MacBook Pro Mas Mufid"));
                assert_eq!(presence.shell.as_deref(), Some("/bin/zsh"));
            }
            other => panic!("Expected online HostPresence broadcast, got: {:?}", other),
        }
    } else {
        panic!("Expected binary frame");
    }

    // Scenario 3: A NEW subscriber connects while host is active
    // New subscriber must receive direct initial HostPresence { online: true, ... }
    let (ws_sub2, _) = connect_async(&sub_url).await.expect("Sub2 connect");
    let (_sub2_sink, mut sub2_stream) = ws_sub2.split();

    let msg_sub2 = sub2_stream.next().await.unwrap().unwrap();
    if let Message::Binary(bytes) = msg_sub2 {
        let pkt = Packet::from_msgpack(&bytes).unwrap();
        match pkt.payload {
            PacketPayload::HostPresence(presence) => {
                assert!(presence.online, "New subscriber must see host as online");
                assert_eq!(presence.host_name.as_deref(), Some("MacBook Pro Mas Mufid"));
            }
            other => panic!("Expected initial online HostPresence, got: {:?}", other),
        }
    } else {
        panic!("Expected binary frame");
    }

    // Scenario 4: Host disconnects
    // Both active subscribers must receive broadcast HostPresence { online: false }
    host_sink.close().await.unwrap();

    let msg_offline1 = sub_stream.next().await.unwrap().unwrap();
    if let Message::Binary(bytes) = msg_offline1 {
        let pkt = Packet::from_msgpack(&bytes).unwrap();
        match pkt.payload {
            PacketPayload::HostPresence(presence) => {
                assert!(!presence.online, "Subscriber 1 must receive offline event");
            }
            other => panic!("Expected offline HostPresence, got: {:?}", other),
        }
    }

    let msg_offline2 = sub2_stream.next().await.unwrap().unwrap();
    if let Message::Binary(bytes) = msg_offline2 {
        let pkt = Packet::from_msgpack(&bytes).unwrap();
        match pkt.payload {
            PacketPayload::HostPresence(presence) => {
                assert!(!presence.online, "Subscriber 2 must receive offline event");
            }
            other => panic!("Expected offline HostPresence, got: {:?}", other),
        }
    }
}
