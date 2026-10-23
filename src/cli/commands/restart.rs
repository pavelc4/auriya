use crate::Result;
use anyhow::Context;
use std::time::Duration;

const DAEMON_BIN: &str = "auriya";
const COMPANION_PROC: &str = "AuriyaSysMon";
const CONFIG_DIR: &str = "/data/adb/.config/auriya";
const LOG_DIR: &str = "/data/adb/auriya";
const SERVICE_SCRIPT: &str = "/data/adb/modules/auriya/service.sh";

pub fn handle_restart() -> Result<()> {
    println!("Restarting daemon + companion...");
    stop_processes();
    clear_runtime_state();
    launch_service()?;
    println!("Restart initiated — tail {LOG_DIR}/restart.log for progress");
    Ok(())
}

fn stop_processes() {
    crate::platform::module_prop::set_module_status("sleep 💤");
    use std::process::{Command, Stdio};
    for proc in [DAEMON_BIN, COMPANION_PROC] {
        let _ = Command::new("killall")
            .args(["-q", "-TERM", proc])
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
    }
    std::thread::sleep(Duration::from_secs(2));
    for proc in [DAEMON_BIN, COMPANION_PROC] {
        let _ = Command::new("killall")
            .args(["-q", "-KILL", proc])
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
    }
}

fn clear_runtime_state() {
    let _ = std::fs::remove_file(format!("{CONFIG_DIR}/system_status"));
    let _ = std::fs::remove_file(format!("{CONFIG_DIR}/companion.lock"));
    let _ = std::fs::write(format!("{LOG_DIR}/daemon.log"), "");
}

fn launch_service() -> Result<()> {
    use std::process::{Command, Stdio};
    Command::new("sh")
        .args([
            "-c",
            &format!("sh {SERVICE_SCRIPT} >> {LOG_DIR}/restart.log 2>&1 &"),
        ])
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .context("failed to spawn service.sh")?;
    Ok(())
}
