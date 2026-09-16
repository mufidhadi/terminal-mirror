pub fn format_startup_banner(session_id: &str, passphrase: &str, shell: &str) -> String {
    let mut banner = String::new();
    banner.push_str("\x1b[1;34m┌────────────────────────────────────────────────────────────────────────┐\x1b[0m\r\n");
    banner.push_str("\x1b[1;34m│\x1b[0m \x1b[1;32m● Terminal Mirror - Windows Host Agent (ConPTY Active)\x1b[0m                 \x1b[1;34m│\x1b[0m\r\n");
    banner.push_str(&format!(
        "\x1b[1;34m│\x1b[0m Shell: \x1b[1m{:<15}\x1b[0m | Session: \x1b[1m{:<32}\x1b[0m \x1b[1;34m│\x1b[0m\r\n",
        shell, session_id
    ));
    banner.push_str(&format!(
        "\x1b[1;34m│\x1b[0m Passphrase : [ \x1b[1;33m{:<43}\x1b[0m ] \x1b[1;34m│\x1b[0m\r\n",
        passphrase
    ));
    banner.push_str("\x1b[1;34m│\x1b[0m Kill Switch: \x1b[1;31mCtrl + Shift + Q\x1b[0m (Instant Session Revocation)             \x1b[1;34m│\x1b[0m\r\n");
    banner.push_str("\x1b[1;34m└────────────────────────────────────────────────────────────────────────┘\x1b[0m\r\n");
    banner
}

pub fn render_startup_banner(session_id: &str, passphrase: &str, shell: &str) {
    print!("{}", format_startup_banner(session_id, passphrase, shell));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_format_startup_banner_contains_required_fields() {
        let banner = format_startup_banner("win-test-1234", "batu-merah-kuda-terbang", "pwsh.exe");
        assert!(banner.contains("win-test-1234"));
        assert!(banner.contains("batu-merah-kuda-terbang"));
        assert!(banner.contains("pwsh.exe"));
        assert!(banner.contains("ConPTY Active"));
        assert!(banner.contains("Ctrl + Shift + Q"));
    }
}
