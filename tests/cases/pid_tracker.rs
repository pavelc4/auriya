use auriya::core::pid_tracker::PidTracker;
use auriya::daemon::event::DaemonEvent;
use std::process::Command;
use std::time::{Duration, Instant};

/// Real-device test: spawn a child, track it, kill it, and assert the
/// tracker reports the exit. Exercises the actual `pidfd_open`/`poll`
/// path on the target kernel.
#[test]
fn detects_child_process_exit() {
    let (tx, mut rx) = tokio::sync::mpsc::channel(4);

    let mut child = Command::new("sleep")
        .arg("30")
        .spawn()
        .expect("spawn sleep child");
    let pid = child.id() as i32;

    let tracker = PidTracker::spawn(pid, tx);
    assert!(
        tracker.is_alive(),
        "child should be alive immediately after spawn"
    );

    child.kill().expect("kill child");
    let _ = child.wait();

    let deadline = Instant::now() + Duration::from_secs(5);
    let mut got = None;
    while Instant::now() < deadline {
        if let Ok(ev) = rx.try_recv() {
            got = Some(ev);
            break;
        }
        std::thread::sleep(Duration::from_millis(50));
    }

    assert_eq!(got, Some(DaemonEvent::PidExited(pid)));
    assert!(
        !tracker.is_alive(),
        "tracker should report the child as dead"
    );
}

/// Real-device latency: time from process death to the PidExited
/// event landing on the channel. The daemon's headline goal is
/// event-to-action < 20 ms; this measures the tracker's slice of it.
#[test]
fn pidfd_exit_latency() {
    let (tx, mut rx) = tokio::sync::mpsc::channel(4);
    let mut child = Command::new("sleep")
        .arg("30")
        .spawn()
        .expect("spawn sleep child");
    let pid = child.id() as i32;
    let _tracker = PidTracker::spawn(pid, tx);

    // Give the watcher thread a moment to arm its blocking poll.
    std::thread::sleep(Duration::from_millis(50));

    let killed_at = Instant::now();
    child.kill().expect("kill child");
    let _ = child.wait();

    let deadline = killed_at + Duration::from_secs(5);
    let mut latency = None;
    while Instant::now() < deadline {
        if let Ok(DaemonEvent::PidExited(p)) = rx.try_recv() {
            assert_eq!(p, pid);
            latency = Some(killed_at.elapsed());
            break;
        }
        std::thread::sleep(Duration::from_micros(200));
    }

    let latency = latency.expect("no PidExited event within 5s");
    eprintln!(
        "pidfd exit→event latency: {:.3} ms",
        latency.as_secs_f64() * 1000.0
    );
    // Generous bound to stay non-flaky on a busy device; the typical
    // value is well under 5 ms.
    assert!(
        latency < Duration::from_millis(100),
        "exit latency too high: {latency:?}"
    );
}

#[test]
fn dead_pid_is_not_alive() {
    // PID 0 is never a valid tracked target; open_pidfd returns None
    // and the /proc fallback reports not-alive for a nonexistent PID.
    let (tx, _rx) = tokio::sync::mpsc::channel(1);
    let tracker = PidTracker::spawn(999_999_999, tx);
    assert!(!tracker.is_alive());
}
