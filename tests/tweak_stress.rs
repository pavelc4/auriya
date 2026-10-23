mod common;

use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::thread;
use std::time::{Duration, Instant};

#[tokio::test]
async fn test_concurrency_and_tweak_application_stress() -> Result<(), String> {
    let _restorer = common::setup_verified_root_environment()?;
    let governor_paths = common::find_cpu_governor_paths()?;

    let running = Arc::new(AtomicBool::new(true));
    let fps_read_count = Arc::new(AtomicUsize::new(0));
    let tweak_apply_count = Arc::new(AtomicUsize::new(0));
    let tweak_error_count = Arc::new(AtomicUsize::new(0));

    // Task 1: Asynchronous FPS monitoring loop (reads every 16ms / ~60 FPS)
    let fps_running = Arc::clone(&running);
    let fps_counter = Arc::clone(&fps_read_count);
    let fps_task = tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_millis(16));
        while fps_running.load(Ordering::Acquire) {
            interval.tick().await;
            fps_counter.fetch_add(1, Ordering::Relaxed);
        }
    });

    // Task 2: Parallel OS thread pool aggressively modifying kernel CPU governor paths
    // Mutex protects hardware bus synchronization, preventing kernel EBUSY contention
    // while actively stressing data synchronization bottlenecks across competing threads.
    let tweak_lock = Arc::new(std::sync::Mutex::new(()));
    let test_governors = ["performance", "schedutil", "powersave"];
    let mut thread_handles = Vec::with_capacity(3);

    for thread_idx in 0..3 {
        let t_running = Arc::clone(&running);
        let t_counter = Arc::clone(&tweak_apply_count);
        let t_errors = Arc::clone(&tweak_error_count);
        let t_lock = Arc::clone(&tweak_lock);
        let paths = governor_paths.clone();

        let handle = thread::spawn(move || {
            let mut gov_idx = thread_idx;
            while t_running.load(Ordering::Acquire) {
                let target_gov = test_governors[gov_idx % test_governors.len()];
                if let Ok(_guard) = t_lock.lock() {
                    for path in &paths {
                        let mut success = false;
                        for _ in 0..3 {
                            if std::fs::write(path, target_gov.as_bytes()).is_ok() {
                                t_counter.fetch_add(1, Ordering::Relaxed);
                                success = true;
                                break;
                            }
                            thread::sleep(Duration::from_millis(1));
                        }
                        if !success {
                            t_errors.fetch_add(1, Ordering::Relaxed);
                        }
                    }
                }
                gov_idx = gov_idx.wrapping_add(1);
                thread::sleep(Duration::from_millis(25));
            }
        });
        thread_handles.push(handle);
    }

    // Stress duration: 2 seconds of concurrent kernel modifications
    let start = Instant::now();
    tokio::time::sleep(Duration::from_millis(2000)).await;

    // Gracefully stop all workers
    running.store(false, Ordering::Release);

    fps_task
        .await
        .map_err(|e| format!("FPS background monitor failed: {}", e))?;

    for handle in thread_handles {
        handle
            .join()
            .map_err(|_| "Governor modification worker thread panicked".to_string())?;
    }

    let total_fps_ticks = fps_read_count.load(Ordering::Acquire);
    let total_tweaks = tweak_apply_count.load(Ordering::Acquire);
    let total_errors = tweak_error_count.load(Ordering::Acquire);
    let elapsed = start.elapsed();

    if total_errors > 0 {
        return Err(format!(
            "Kernel tweak contention detected: {} write failures encountered",
            total_errors
        ));
    }

    if total_fps_ticks < 50 {
        return Err(format!(
            "FPS monitoring loop starved: only {} frames recorded over {:?}",
            total_fps_ticks, elapsed
        ));
    }

    if total_tweaks < 20 {
        return Err(format!(
            "Kernel tweak loop insufficient throughput: only {} modifications over {:?}",
            total_tweaks, elapsed
        ));
    }

    Ok(())
}
