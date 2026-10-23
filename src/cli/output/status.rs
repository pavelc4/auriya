pub fn print_status(response: &str) {
    println!("       Auriya Daemon Status     ");

    if response.is_empty() {
        println!("  Daemon:      No response\n");
        return;
    }

    println!("  Daemon:      Running");
    let mut map = std::collections::HashMap::new();
    for token in response.split_whitespace() {
        if let Some((key, value)) = token.split_once('=') {
            map.insert(key, value);
        }
    }

    if let Some(&value) = map.get("ENABLED") {
        println!("  Enabled:     {}", value);
    }
    if let Some(&value) = map.get("PROFILE") {
        println!("  Profile:     {}", value);
    }
    if let Some(&value) = map.get("PACKAGES") {
        println!("  Games:       {} configured", value);
    }
    if let Some(&value) = map.get("FPS") {
        let src = map.get("SOURCE").copied().unwrap_or("?");
        println!("  FPS:         {} ({})", value, src);
    }
    if let Some(&value) = map.get("LOG_LEVEL") {
        println!("  Log Level:   {}", value);
    }

    println!();
}

pub fn print_daemon_stopped() {
    println!("       Auriya Daemon Status      ");
    println!("  Daemon: Not running\n");
}
