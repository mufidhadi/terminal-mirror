use qrcode::render::unicode;
use qrcode::QrCode;

/// Renders a dense Unicode QR code suitable for terminal displays.
pub fn generate_terminal_qr(content: &str) -> Option<String> {
    QrCode::new(content.as_bytes()).ok().map(|code| {
        code.render::<unicode::Dense1x2>()
            .dark_color(unicode::Dense1x2::Dark)
            .light_color(unicode::Dense1x2::Light)
            .build()
    })
}

pub fn render_startup_banner(session_id: &str, passphrase: &str, shell: &str, qr_content: Option<&str>) {
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

    if let Some(content) = qr_content {
        if let Some(qr_str) = generate_terminal_qr(content) {
            println!("\x1b[1;33mScan QR Code below with Terminal Mirror Android app to pair:\x1b[0m");
            println!("{}", qr_str);
        }
    }
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
}
