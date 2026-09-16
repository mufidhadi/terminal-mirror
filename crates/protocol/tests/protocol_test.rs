use terminal_mirror_protocol::{
    OsType, Packet, PacketPayload, PairingPayload, SessionDescriptor, SessionStatus, PROTOCOL_VERSION,
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
fn test_packet_msgpack_roundtrip_terminal_output() {
    // Simulating ANSI colored terminal stream
    let raw_terminal_bytes = b"\x1b[32muser@host:~$ \x1b[0mls -la\r\ntotal 12\r\n".to_vec();
    let payload = PacketPayload::TerminalOutput {
        bytes: raw_terminal_bytes.clone(),
    };
    let packet = Packet::new("mac-session-001", 105, payload);

    let encoded = packet.to_msgpack().expect("Failed to encode to msgpack");
    assert!(!encoded.is_empty());

    let decoded = Packet::from_msgpack(&encoded).expect("Failed to decode from msgpack");
    assert_eq!(decoded.session_id, "mac-session-001");
    assert_eq!(decoded.sequence, 105);

    if let PacketPayload::TerminalOutput { bytes } = decoded.payload {
        assert_eq!(bytes, raw_terminal_bytes);
    } else {
        panic!("Decoded payload variant mismatch!");
    }
}

#[test]
fn test_packet_msgpack_roundtrip_resize() {
    let payload = PacketPayload::TerminalResize { cols: 120, rows: 45 };
    let packet = Packet::new("win-session-002", 2, payload);

    let encoded = packet.to_msgpack().expect("Failed to encode resize packet");
    let decoded = Packet::from_msgpack(&encoded).expect("Failed to decode resize packet");

    if let PacketPayload::TerminalResize { cols, rows } = decoded.payload {
        assert_eq!(cols, 120);
        assert_eq!(rows, 45);
    } else {
        panic!("Decoded payload variant mismatch!");
    }
}

#[test]
fn test_pairing_payload_qr_serialization() {
    let pairing = PairingPayload {
        relay_url: "wss://relay.example.internal:8443/ws".to_string(),
        session_id: "mac-term-alpha".to_string(),
        host_id: "macbook-pro".to_string(),
        pre_shared_key: "k3y_pr3_sh4r3d_s3cur3".to_string(),
        public_key: "pub_k3y_x25519_3x4mpl3".to_string(),
        expires_at_ms: 1726530000000,
    };

    let qr_string = pairing.to_qr_string().expect("Failed to convert to QR string");
    assert!(qr_string.contains("mac-term-alpha"));

    let parsed = PairingPayload::from_qr_string(&qr_string).expect("Failed to parse from QR string");
    assert_eq!(parsed, pairing);
}

#[test]
fn test_session_descriptor_initialization() {
    let session = SessionDescriptor::new(
        "sess-123",
        "mac-laptop",
        "Mufid-MacBook.local",
        OsType::MacOS,
        "/bin/zsh",
        80,
        24,
    );

    assert_eq!(session.session_id, "sess-123");
    assert_eq!(session.host_id, "mac-laptop");
    assert_eq!(session.os_type, OsType::MacOS);
    assert_eq!(session.status, SessionStatus::Starting);
    assert_eq!(session.cols, 80);
    assert_eq!(session.rows, 24);
}
