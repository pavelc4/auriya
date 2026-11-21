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
    if let Some(&value) = map.get("BINDER") {
        println!("  Binder IPC:  {}", value);
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
    println!("  Daemon:      Not running");

    let crash_opt = std::fs::read_to_string(crate::common::CRASH_LOG_FILE).ok();
    let boot_err_opt = std::fs::read_to_string(crate::common::BOOT_ERROR_FILE).ok();

    match (crash_opt, boot_err_opt) {
        (Some(crash), _) if crash.contains("CRASH TOMBSTONE") || crash.contains("panic") => {
            if let Some(first_line) = crash
                .lines()
                .find(|l| l.contains("CRASH TOMBSTONE") || l.contains("panic"))
            {
                println!("  Fatal Crash: {}", first_line.trim());
                println!(
                    "  (See '{}' for full backtrace)",
                    crate::common::CRASH_LOG_FILE
                );
            }
        }
        (_, Some(boot_err)) if boot_err.contains("BOOT FAILURE") => {
            if let Some(first_line) = boot_err.lines().find(|l| l.contains("BOOT FAILURE")) {
                println!("  Boot Error:  {}", first_line.trim());
                println!("  (See '{}' for details)", crate::common::BOOT_ERROR_FILE);
            }
        }
        _ => {}
    }

    println!();
}
