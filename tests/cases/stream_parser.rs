use auriya::core::fas::buffer::{FrameBuffer, TargetFps};
use auriya::core::fas::source::FrameSource;
use auriya::core::stats::{fps_stats_compute_in_place, fps_stats_from_frametimes};
use std::mem::MaybeUninit;
use std::time::{Duration, Instant};
use tokio::sync::broadcast;

/// Sample system resource snapshot (CPU cycle time and Memory RSS footprint).
struct ResourceSnapshot {
    cpu_user_us: i64,
    cpu_sys_us: i64,
    peak_rss_kb: i64,
    resident_mem_bytes: u64,
}

impl ResourceSnapshot {
    fn capture() -> Self {
        let mut usage = MaybeUninit::<libc::rusage>::uninit();
        let (cpu_user_us, cpu_sys_us, peak_rss_kb) = unsafe {
            if libc::getrusage(libc::RUSAGE_SELF, usage.as_mut_ptr()) == 0 {
                let u = usage.assume_init();
                (
                    u.ru_utime.tv_sec * 1_000_000 + u.ru_utime.tv_usec,
                    u.ru_stime.tv_sec * 1_000_000 + u.ru_stime.tv_usec,
                    u.ru_maxrss,
                )
            } else {
                (0, 0, 0)
            }
        };

        let resident_mem_bytes = std::fs::read_to_string("/proc/self/statm")
            .ok()
            .and_then(|s| {
                let mut parts = s.split_whitespace();
                let _total_pages = parts.next()?;
                let resident_pages = parts.next()?.parse::<u64>().ok()?;
                Some(resident_pages * 4096)
            })
            .unwrap_or(0);

        Self {
            cpu_user_us,
            cpu_sys_us,
            peak_rss_kb,
            resident_mem_bytes,
        }
    }
}

/// Scenario A: Best-Case Simulation
/// A continuous, perfectly synchronized stream of 20,000 valid 120Hz frametime events (8.333ms)
/// arrives into the non-blocking parsing engine without any delayed packets.
#[test]
fn test_stream_parser_best_case_synchronized() {
    let (tx, rx) = broadcast::channel(1024);
    let mut source = FrameSource::new(rx);
    let mut buffer = FrameBuffer::new(TargetFps::Single(120));

    let snap_before = ResourceSnapshot::capture();
    let start = Instant::now();

    const TOTAL_FRAMES: usize = 20_000;
    const FRAME_DT: Duration = Duration::from_nanos(8_333_333); // 120 Hz

    // Stream frames through broadcast channel in batches to verify non-blocking drain
    let mut drained_total = 0;
    let batch_size = 500;
    for _ in 0..(TOTAL_FRAMES / batch_size) {
        for _ in 0..batch_size {
            let _ = tx.send(FRAME_DT);
        }
        let drained = source.drain_into_buffer(&mut buffer);
        drained_total += drained;
    }

    let elapsed = start.elapsed();
    let snap_after = ResourceSnapshot::capture();

    assert_eq!(drained_total, TOTAL_FRAMES);

    // Compute stats using stack buffer (100% zero-heap allocation)
    let mut scratch = [0.0f64; 240];
    let count = buffer.copy_recent_real_frametimes_secs(&mut scratch);
    assert!(count >= 120);

    let stats = fps_stats_compute_in_place(&mut scratch[..count], 120, Some(120.0));

    // Pacing and Cadence Assertions
    assert_eq!(stats.avg, 120.0, "harmonic mean locked to 120 VSync target");
    assert_eq!(stats.peak, 120.0, "peak clamped to 120Hz scanout ceiling");
    assert_eq!(stats.low_1pct, 120.0);
    assert_eq!(stats.low_01pct, 120.0);
    assert_eq!(stats.jank, 0, "zero jank on pristine stream");
    assert!(stats.std_dev_ms < 0.2, "standard deviation < 0.2ms");

    // Metrics computation
    let elapsed_secs = elapsed.as_secs_f64();
    let elements_per_us = (TOTAL_FRAMES as f64) / (elapsed.as_micros() as f64);
    let total_bytes = TOTAL_FRAMES * std::mem::size_of::<Duration>();
    let mb_per_sec = (total_bytes as f64 / (1024.0 * 1024.0)) / elapsed_secs;
    let ms_per_frame = (elapsed_secs * 1000.0) / (TOTAL_FRAMES as f64);
    let cpu_delta_us = (snap_after.cpu_user_us - snap_before.cpu_user_us)
        + (snap_after.cpu_sys_us - snap_before.cpu_sys_us);

    println!(
        "\n--- BEST-CASE BENCHMARK METRICS ---\n\
         Frames Ingested:       {}\n\
         Time Elapsed:          {:.3} ms\n\
         Processing Speed:      {:.2} elements/µs ({:.0} frames/sec)\n\
         Throughput:            {:.2} MB/s\n\
         Frame Processing Time: {:.4} ms/frame\n\
         CPU Consumption:       {} µs user+sys\n\
         RAM Resident Footprint:{:.2} MB (Peak RSS: {} KB)\n\
         -----------------------------------",
        TOTAL_FRAMES,
        elapsed_secs * 1000.0,
        elements_per_us,
        (TOTAL_FRAMES as f64) / elapsed_secs,
        mb_per_sec,
        ms_per_frame,
        cpu_delta_us,
        (snap_after.resident_mem_bytes as f64) / (1024.0 * 1024.0),
        snap_after.peak_rss_kb,
    );

    // Speed assertion: parser must process at least 50,000 frames/sec in debug mode
    assert!(
        elements_per_us > 0.05,
        "throughput too slow: {:.2} elements/µs",
        elements_per_us
    );
}

/// Scenario B: Worst-Case Simulation (Empty / Zero-Data Payload)
/// The stream buffer contains absolute zero data, yet the execution trigger is fired.
/// The parser must handle TryRecvError::Empty gracefully via match pattern matching
/// without crashing, panicking, hanging, or throwing unhandled unwrap().
#[test]
fn test_stream_parser_worst_case_empty_payload() {
    let (_tx, rx) = broadcast::channel(16);
    let mut source = FrameSource::new(rx);
    let mut buffer = FrameBuffer::new(TargetFps::Single(60));

    let start = Instant::now();
    let snap_before = ResourceSnapshot::capture();

    // Fire 1,000 drain triggers on empty channel
    for _ in 0..1000 {
        let count = source.drain_into_buffer(&mut buffer);
        assert_eq!(count, 0, "empty stream returns 0 drained elements");
    }

    let elapsed = start.elapsed();
    let snap_after = ResourceSnapshot::capture();

    // Verify empty buffer computation yields total empty stats
    let mut scratch = [0.0f64; 240];
    let count = buffer.copy_recent_real_frametimes_secs(&mut scratch);
    assert_eq!(count, 0);

    let stats = fps_stats_compute_in_place(&mut scratch[..count], 60, Some(60.0));
    assert_eq!(stats.frames, 0);
    assert_eq!(stats.avg, 0.0);
    assert_eq!(stats.peak, 0.0);
    assert_eq!(stats.low_1pct, 0.0);
    assert_eq!(stats.low_01pct, 0.0);
    assert_eq!(stats.std_dev_ms, 0.0);
    assert_eq!(stats.jank, 0);

    // Also verify pure empty slice wrapper
    let empty_stats = fps_stats_from_frametimes(&[], 60);
    assert_eq!(empty_stats.frames, 0);
    assert_eq!(empty_stats.avg, 0.0);

    let cpu_delta_us = (snap_after.cpu_user_us - snap_before.cpu_user_us)
        + (snap_after.cpu_sys_us - snap_before.cpu_sys_us);

    println!(
        "\n--- EMPTY WORST-CASE BENCHMARK METRICS ---\n\
         Empty Triggers Executed: 1,000\n\
         Total Elapsed Time:      {:.3} ms\n\
         Latency per Trigger:     {:.3} µs\n\
         CPU Consumption:         {} µs\n\
         Panics / Exceptions:     0\n\
         ------------------------------------------",
        elapsed.as_secs_f64() * 1000.0,
        (elapsed.as_micros() as f64) / 1000.0,
        cpu_delta_us,
    );

    // Non-blocking requirement: 1,000 empty checks must complete in under 10ms
    assert!(
        elapsed < Duration::from_millis(10),
        "empty recv blocked or stalled: {:?}",
        elapsed
    );
}

/// Scenario C: Volatile / Delayed & Lagged Stream Simulation
/// Stream packets arrive out of order, with delayed pauses and broadcast lag drops (TryRecvError::Lagged).
/// Verifies that non-blocking state machine handles lagged channel drops and recovers seamlessly.
#[test]
fn test_stream_parser_volatile_delayed_and_lagged() {
    let (tx, rx) = broadcast::channel(16); // Small buffer to intentionally trigger Lagged
    let mut source = FrameSource::new(rx);
    let mut buffer = FrameBuffer::new(TargetFps::Single(60));

    let snap_before = ResourceSnapshot::capture();
    let start = Instant::now();

    // 1. Send 100 frames to overflow the small 16-slot ring, causing Lagged error
    for i in 0..100 {
        let dt = if i % 10 == 0 {
            Duration::from_millis(33) // Stall / jank frame
        } else {
            Duration::from_nanos(16_666_666) // Normal 60Hz frame
        };
        let _ = tx.send(dt);
    }

    // 2. Drain stream — must encounter TryRecvError::Lagged, skip dropped frames, and consume remainder
    let count = source.drain_into_buffer(&mut buffer);
    assert!(count > 0, "recovered and drained frames after lag");
    assert!(count <= 16, "drained available unlagged frames");

    // 3. Send additional clean frames in chunks <= capacity to verify continuous streaming
    let mut additional = 0;
    for _ in 0..6 {
        for _ in 0..10 {
            let _ = tx.send(Duration::from_nanos(16_666_666));
        }
        additional += source.drain_into_buffer(&mut buffer);
    }
    assert_eq!(additional, 60);

    let elapsed = start.elapsed();
    let snap_after = ResourceSnapshot::capture();

    // Compute stats
    let mut scratch = [0.0f64; 240];
    let sample_count = buffer.copy_recent_real_frametimes_secs(&mut scratch);
    let stats = fps_stats_compute_in_place(&mut scratch[..sample_count], 60, Some(60.0));

    assert!(stats.frames > 60);
    assert!(
        (stats.avg - 60.0).abs() <= 1.5,
        "average accurately reflects jitter: {}",
        stats.avg
    );
    assert_eq!(stats.peak, 60.0, "peak clamped to 60Hz ceiling");

    let cpu_delta_us = (snap_after.cpu_user_us - snap_before.cpu_user_us)
        + (snap_after.cpu_sys_us - snap_before.cpu_sys_us);

    println!(
        "\n--- VOLATILE / LAGGED BENCHMARK METRICS ---\n\
         Total Frames Recovered: {}\n\
         Total Elapsed:          {:.3} ms\n\
         CPU Consumption:        {} µs\n\
         Peak RSS Memory:        {} KB\n\
         Final Calculated Avg:   {} FPS\n\
         Final Calculated P1:    {} FPS\n\
         -------------------------------------------",
        count + additional,
        elapsed.as_secs_f64() * 1000.0,
        cpu_delta_us,
        snap_after.peak_rss_kb,
        stats.avg,
        stats.low_1pct,
    );

    // RAM allocation audit: verify no memory leak or growth after volatile recovery
    assert!(
        snap_after.peak_rss_kb <= snap_before.peak_rss_kb + 2048,
        "memory grew abnormally: before={}KB after={}KB",
        snap_before.peak_rss_kb,
        snap_after.peak_rss_kb
    );
}
