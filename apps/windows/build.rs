fn main() {
    // Only compile resources when target OS is Windows
    if std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default() == "windows" {
        let mut res = winres::WindowsResource::new();
        res.set_icon("assets/terminal_remote_icon.ico");
        if let Err(e) = res.compile() {
            eprintln!("Failed to compile windows resources: {}", e);
            std::process::exit(1);
        }
    }
}
