use auriya::common::{SOCKET_PATH, MODULE_PATH, ProfileMode, LogLevel};

fn main (){

	println!("Auriya CLI - Infrastructure");
	println!("Version: {}", env!("CARGO_PKG_VERSION"));

	let profile = ProfileMode::Balance;
    println!("Profile: {} (IPC: {})", profile, profile.to_upper_str());

    let log = LogLevel::Info;
    println!("Log level: {} (IPC: {})", log, log.to_upper_str());
}
