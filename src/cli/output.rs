pub fn print_status(response: &str) {
    println!("   	   Auriya Daemon Status     ");


    if response.is_empty() {
        println!("No response from daemon\n");
        return;
    }

    println!("Daemon: Running\n");
    for line in response.lines() {
        if let Some((key, value)) = line.split_once('=') {
            let key = key.trim();
            let value = value.trim();

            match key {
                "ENABLED" => {
                    let icon = if value == "true" { "✓" } else { "✗" };
                    println!("  {} Enabled:  {}", icon, value);
                }
                "PROFILE" => {
                    println!("    Profile:  {}", value);
                }
                "PACKAGES" => {
                    println!("    Games:    {} configured", value);
                }
                "FPS" => {
                    println!("    FPS:      {}", value);
                }
                _ => {}
            }
        }
    }

    println!();
}

pub fn print_daemon_stopped() {
    println!("       Auriya Daemon Status      ");
    println!(" Daemon: Not running\n");
}

pub fn print_success(message: &str) {
    println!(" {}", message);
}

pub fn print_error(message: &str) {
    eprintln!(" Error: {}", message);
}
