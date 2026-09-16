pub fn render_startup_banner(session_id: &str, passphrase: &str, shell: &str) {
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
    println!("\x1b[1;36m└────────────────────────────────────────────────────────────────────────┘\x1b[0m\r\n");
}
