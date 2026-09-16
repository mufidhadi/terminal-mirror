use terminal_mirror_protocol::{
    CompressionAlgorithm, Packet, PacketPayload, PairingGuard, PairingPayload, ScreenSnapshot,
    SessionRole, Utf8StreamChunker, PROTOCOL_VERSION,
};

#[test]
fn test_packet_creation_and_version() {
    let payload = PacketPayload::Ping { nonce: 42 };
    let packet = Packet::new("session-01", 1, payload);

    assert_eq!(packet.version, PROTOCOL_VERSION);
    assert_eq!(packet.session_id, "session-01");
    assert_eq!(packet.sequence, 1);
    assert!(!packet.trace_id.is_empty());
    assert!(packet.timestamp_ms > 0);
}

#[test]
fn test_packet_msgpack_roundtrip_terminal_output_compressed() {
    let raw_terminal_bytes = b"\x1b[32muser@host:~$ \x1b[0mls -la\r\ntotal 12\r\n".to_vec();
    let payload = PacketPayload::TerminalOutput {
        bytes: raw_terminal_bytes.clone(),
        compression: CompressionAlgorithm::Zstd,
    };
    let packet = Packet::new("mac-session-001", 105, payload);

    let encoded = packet.to_msgpack().expect("Failed to encode to msgpack");
    assert!(!encoded.is_empty());

    let decoded = Packet::from_msgpack(&encoded).expect("Failed to decode from msgpack");
    assert_eq!(decoded.session_id, "mac-session-001");
    assert_eq!(decoded.sequence, 105);

    if let PacketPayload::TerminalOutput { bytes, compression } = decoded.payload {
        assert_eq!(bytes, raw_terminal_bytes);
        assert_eq!(compression, CompressionAlgorithm::Zstd);
    } else {
        panic!("Decoded payload variant mismatch!");
    }
}

#[test]
fn test_screen_snapshot_roundtrip() {
    let snapshot = ScreenSnapshot {
        cols: 80,
        rows: 24,
        cursor_x: 10,
        cursor_y: 5,
        in_alternate_screen: true,
        lines: vec![
            "top - 12:00:00 up 10 days".to_string(),
            "Tasks: 350 total, 1 running".to_string(),
        ],
    };

    let payload = PacketPayload::ScreenStateSync {
        snapshot: snapshot.clone(),
    };
    let packet = Packet::new("sess-tui-01", 1, payload);

    let encoded = packet.to_msgpack().expect("Failed to encode snapshot packet");
    let decoded = Packet::from_msgpack(&encoded).expect("Failed to decode snapshot packet");

    if let PacketPayload::ScreenStateSync { snapshot: decoded_snap } = decoded.payload {
        assert_eq!(decoded_snap.cols, 80);
        assert_eq!(decoded_snap.rows, 24);
        assert!(decoded_snap.in_alternate_screen);
        assert_eq!(decoded_snap.lines.len(), 2);
    } else {
        panic!("Decoded payload variant mismatch!");
    }
}

#[test]
fn test_pairing_payload_with_pin_and_trusted_device() {
    let pairing = PairingPayload {
        relay_url: "wss://relay.example.internal:8443/ws".to_string(),
        session_id: "mac-term-alpha".to_string(),
        host_id: "macbook-pro".to_string(),
        pre_shared_key: "k3y_pr3_sh4r3d_s3cur3".to_string(),
        public_key: "pub_k3y_x25519_3x4mpl3".to_string(),
        pin_code: Some("849201".to_string()),
        passphrase_words: Some(vec!["kuda".into(), "terbang".into(), "batu".into(), "merah".into()]),
        expires_at_ms: 1726530000000,
    };

    assert_eq!(pairing.formatted_passphrase().unwrap(), "kuda-terbang-batu-merah");

    let qr_string = pairing.to_qr_string().expect("Failed to convert to QR string");
    let parsed = PairingPayload::from_qr_string(&qr_string).expect("Failed to parse from QR string");
    assert_eq!(parsed, pairing);
}

#[test]
fn test_session_role_subscription() {
    let payload = PacketPayload::SubscribeSession {
        client_id: "phone-client-1".to_string(),
        session_id: "mac-sess".to_string(),
        auth_token: "token123".to_string(),
        requested_role: SessionRole::Spectator,
    };

    let packet = Packet::new("mac-sess", 1, payload);
    let encoded = packet.to_msgpack().expect("Failed encode");
    let decoded = Packet::from_msgpack(&encoded).expect("Failed decode");

    if let PacketPayload::SubscribeSession { requested_role, .. } = decoded.payload {
        assert_eq!(requested_role, SessionRole::Spectator);
    } else {
        panic!("Role mismatch!");
    }
}

#[test]
fn test_utf8_stream_chunker_multibyte_slicing() {
    let mut chunker = Utf8StreamChunker::new();

    let rocket_bytes = "🚀".as_bytes();
    assert_eq!(rocket_bytes.len(), 4);

    let chunk_1 = vec![b'A', b'B', rocket_bytes[0], rocket_bytes[1]];
    let chunk_2 = vec![rocket_bytes[2], rocket_bytes[3], b'C'];

    let out_1 = chunker.process_chunk(&chunk_1);
    assert_eq!(out_1, b"AB");
    assert_eq!(chunker.pending_len(), 2);

    let out_2 = chunker.process_chunk(&chunk_2);
    assert_eq!(out_2, "🚀C".as_bytes());
    assert_eq!(chunker.pending_len(), 0);
}

#[test]
fn test_pairing_guard_three_strikes_auto_burn() {
    let mut guard = PairingGuard::new("sess-123", "kuda-terbang-batu-merah");

    let res1 = guard.verify_attempt("wrong-password-1");
    assert_eq!(res1, Err(2));
    assert!(!guard.is_burned);

    let res2 = guard.verify_attempt("wrong-password-2");
    assert_eq!(res2, Err(1));
    assert!(!guard.is_burned);

    let res3 = guard.verify_attempt("wrong-password-3");
    assert_eq!(res3, Err(0));
    assert!(guard.is_burned);

    let res4 = guard.verify_attempt("kuda-terbang-batu-merah");
    assert_eq!(res4, Err(0));
}
