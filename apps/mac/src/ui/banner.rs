use qrcode::render::unicode;
use qrcode::{EcLevel, QrCode};

/// Renders a compact Unicode QR code suitable for terminal displays.
/// Uses Low error correction level (EcLevel::L) and disabled quiet zone
/// to ensure the QR code fits within standard 80x24 terminal windows.
pub fn generate_terminal_qr(content: &str) -> Option<String> {
    QrCode::with_error_correction_level(content.as_bytes(), EcLevel::L)
        .ok()
        .map(|code| {
            code.render::<unicode::Dense1x2>()
                .dark_color(unicode::Dense1x2::Light)
                .light_color(unicode::Dense1x2::Dark)
                .quiet_zone(false)
                .build()
        })
}

pub fn render_startup_banner(session_id: &str, passphrase: &str, shell: &str, qr_content: Option<&str>) {
    if let Some(content) = qr_content {
        if let Some(qr_str) = generate_terminal_qr(content) {
            let qr_lines: Vec<&str> = qr_str.lines().collect();

            let side_info = [
                format!("\x1b[1;36m┌──────────────────────────────────────────────┐\x1b[0m"),
                format!("\x1b[1;36m│\x1b[0m \x1b[1;32m● Terminal Mirror - macOS Host Active\x1b[0m        \x1b[1;36m│\x1b[0m"),
                format!("\x1b[1;36m│\x1b[0m Shell:      \x1b[1m{:<33}\x1b[0m \x1b[1;36m│\x1b[0m", shell),
                format!("\x1b[1;36m│\x1b[0m Session:    \x1b[1m{:<33}\x1b[0m \x1b[1;36m│\x1b[0m", session_id),
                format!("\x1b[1;36m│\x1b[0m Passphrase: \x1b[1;33m{:<33}\x1b[0m \x1b[1;36m│\x1b[0m", passphrase),
                format!("\x1b[1;36m│\x1b[0m Kill Switch: \x1b[1;31mCtrl + Shift + Q\x1b[0m                 \x1b[1;36m│\x1b[0m"),
                format!("\x1b[1;36m└──────────────────────────────────────────────┘\x1b[0m"),
                format!("\x1b[1;33m← Scan QR code with Android app to pair\x1b[0m"),
                format!("\x1b[2m  Zero-Knowledge E2EE (ChaCha20-Poly1305)\x1b[0m"),
            ];

            let max_lines = qr_lines.len().max(side_info.len());
            for i in 0..max_lines {
                let qr_part = qr_lines.get(i).unwrap_or(&"");
                let info_part = side_info.get(i).map(|s| s.as_str()).unwrap_or("");
                println!("  {:<38}  {}", qr_part, info_part);
            }
            println!();
            return;
        }
    }

    // Fallback if no QR
    println!("\x1b[1;36m┌────────────────────────────────────────────────────────────────────────┐\x1b[0m");
    println!(
        "\x1b[1;36m│\x1b[0m \x1b[1;32m● Terminal Mirror - macOS Host Agent Active\x1b[0m                            \x1b[1;36m│\x1b[0m"
    );
    println!(
        "\x1b[1;36m│\x1b[0m Shell: \x1b[1m{:<15}\x1b[0m | Session: \x1b[1m{:<32}\x1b[0m \x1b[1;36m│\x1b[0m",
        shell, session_id
    );
    println!(
        "\x1b[1;36m│\x1b[0m 4-Word Passphrase : \x1b[1;33m{:<47}\x1b[0m \x1b[1;36m│\x1b[0m",
        passphrase
    );
    println!(
        "\x1b[1;36m│\x1b[0m Kill Switch Hotkey: \x1b[1;31mCtrl + Shift + Q\x1b[0m (Instant Revocation)              \x1b[1;36m│\x1b[0m"
    );
    println!("\x1b[1;36m└────────────────────────────────────────────────────────────────────────┘\x1b[0m");
    println!();
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_generate_terminal_qr_produces_non_empty_unicode_blocks() {
        let qr = generate_terminal_qr("https://relay.masmuf.cloud");
        assert!(qr.is_some());
        let s = qr.unwrap();
        assert!(!s.is_empty());
        assert!(s.contains('\n'));
    }

    #[test]
    fn test_compact_qr_fits_in_standard_terminal_height() {
        let payload = terminal_mirror_protocol::PairingPayload {
            relay_url: "ws://relay.example.internal:8888/ws".to_string(),
            session_id: "mac-live-session".to_string(),
            host_id: "macbook-pro".to_string(),
            pre_shared_key: "secret".to_string(),
            public_key: "".to_string(),
            pin_code: None,
            passphrase_words: Some(vec!["kilo".into(), "lima".into(), "sierra".into(), "tango".into()]),
            expires_at_ms: 1726532000000,
        };
        let qr_string = payload.to_qr_string().expect("to_qr_string failed");
        let qr = generate_terminal_qr(&qr_string).expect("Failed to render compact QR");
        let line_count = qr.lines().count();

        // Compact QR with EcLevel::L and quiet_zone(false) should be <= 20 terminal lines,
        // fitting cleanly within an 80x24 standard terminal without requiring resize!
        assert!(
            line_count <= 20,
            "Compact QR line count was {}, expected <= 20 lines to fit standard 80x24 terminal",
            line_count
        );
    }
}
