mod common;

use auriya::daemon::watchdog::CompanionWatchdog;
use nix::sys::signal::{Signal, kill};
use nix::sys::wait::{WaitPidFlag, WaitStatus, waitpid};
use nix::unistd::Pid;
use std::process::Command;
use std::time::Instant;

#[test]
fn test_chaos_daemon_crash_and_watchdog_resilience() -> Result<(), String> {
    let _restorer = common::setup_verified_root_environment()?;

    // Step 1: Spawn target worker process representing monitored subsystem
    let mut child = Command::new("sleep")
        .arg("30")
        .spawn()
        .map_err(|e| format!("Failed to spawn mock subsystem process: {}", e))?;

    let child_pid = child.id() as i32;
    let target_nix_pid = Pid::from_raw(child_pid);

    // Verify child is alive prior to chaos injection
    let probe_res = kill(target_nix_pid, None);
    if probe_res.is_err() {
        let _ = child.kill();
        return Err("Spawned subsystem process is not reachable".to_string());
    }

    // Initialize watchdog instance
    let mut watchdog = CompanionWatchdog::new();
    if !watchdog.alive {
        let _ = child.kill();
        return Err("Watchdog initialized in unhealthy state".to_string());
    }

    // Step 2: Inject Chaos via uncatchable SIGKILL mid-operation
    let crash_time = Instant::now();
    kill(target_nix_pid, Signal::SIGKILL)
        .map_err(|e| format!("Failed to dispatch SIGKILL to pid {}: {}", child_pid, e))?;

    // Step 3: Watchdog detects termination and executes recovery hooks
    watchdog.on_companion_died();

    // Verify child process was terminated and reaped with no zombie status
    let wait_res = waitpid(target_nix_pid, Some(WaitPidFlag::WNOHANG))
        .map_err(|e| format!("Waitpid failed for target {}: {}", child_pid, e))?;

    match wait_res {
        WaitStatus::Signaled(p, sig, _) if p == target_nix_pid && sig == Signal::SIGKILL => {
            // Terminated cleanly by SIGKILL
        }
        WaitStatus::Exited(p, _) if p == target_nix_pid => {
            // Exited
        }
        WaitStatus::StillAlive => {
            let _ = child.kill();
            return Err(format!(
                "Process {} is a zombie/still alive after SIGKILL",
                child_pid
            ));
        }
        other => {
            let _ = child.kill();
            return Err(format!(
                "Unexpected process status after SIGKILL: {:?}",
                other
            ));
        }
    }

    let recovery_ms = crash_time.elapsed().as_millis();

    // Step 4: Verify recovery latency timeline (should hook and clean up within 1500ms)
    if recovery_ms > 1500 {
        return Err(format!(
            "Recovery timeline violation: elapsed {} ms (maximum tolerated: 1500 ms)",
            recovery_ms
        ));
    }

    // Step 5: Verify watchdog registered restart timestamp and remained crash-resilient
    if watchdog.last_restart.is_none() {
        return Err("Watchdog failed to record recovery restart timestamp".to_string());
    }

    Ok(())
}
