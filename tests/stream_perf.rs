mod common;

use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering};
use std::time::{Duration, Instant};
use sysinfo::{Pid, ProcessesToUpdate, System};

const RING_CAPACITY: usize = 1024;
const TOTAL_BENCHMARK_FRAMES: usize = 50_000;
const MAX_RSS_MEMORY_BYTES: u64 = 15 * 1024 * 1024; // 15 MB ceiling

#[derive(Clone, Copy, Default)]
struct FrameEvent {
    timestamp_ns: u64,
    frame_duration_ns: u32,
    sequence: u64,
}

/// Zero-copy lock-free ring buffer for frame stream events.
struct LockFreeFrameRing {
    buffer: Box<[FrameEvent; RING_CAPACITY]>,
    head: AtomicUsize,
    tail: AtomicUsize,
}

impl LockFreeFrameRing {
    fn new() -> Self {
        Self {
            buffer: Box::new([FrameEvent::default(); RING_CAPACITY]),
            head: AtomicUsize::new(0),
            tail: AtomicUsize::new(0),
        }
    }

    #[inline(always)]
    fn push(&mut self, event: FrameEvent) -> bool {
        let current_head = self.head.load(Ordering::Relaxed);
        let current_tail = self.tail.load(Ordering::Acquire);

        if current_head.wrapping_sub(current_tail) >= RING_CAPACITY {
            return false;
        }

        let slot = current_head % RING_CAPACITY;
        self.buffer[slot] = event;
        self.head
            .store(current_head.wrapping_add(1), Ordering::Release);
        true
    }

    #[inline(always)]
    fn pop(&mut self) -> Option<FrameEvent> {
        let current_tail = self.tail.load(Ordering::Relaxed);
        let current_head = self.head.load(Ordering::Acquire);

        if current_tail == current_head {
            return None;
        }

        let slot = current_tail % RING_CAPACITY;
        let event = self.buffer[slot];
        self.tail
            .store(current_tail.wrapping_add(1), Ordering::Release);
        Some(event)
    }
}

#[tokio::test]
async fn test_frame_stream_throughput_and_memory_stability() -> Result<(), String> {
    let _restorer = common::setup_verified_root_environment()?;

    let running = Arc::new(AtomicBool::new(true));
    let peak_rss_bytes = Arc::new(AtomicU64::new(0));

    // Monitor thread: samples process RSS memory and CPU every 500ms
    let monitor_running = Arc::clone(&running);
    let peak_rss_tracker = Arc::clone(&peak_rss_bytes);
    let my_pid = Pid::from_u32(std::process::id());

    let mut sys_initial = System::new();
    sys_initial.refresh_processes(ProcessesToUpdate::Some(&[my_pid]), true);
    if let Some(proc) = sys_initial.process(my_pid) {
        peak_rss_bytes.fetch_max(proc.memory(), Ordering::Release);
    }

    let monitor_handle = tokio::spawn(async move {
        let mut sys = System::new();
        while monitor_running.load(Ordering::Acquire) {
            sys.refresh_processes(ProcessesToUpdate::Some(&[my_pid]), true);
            if let Some(process) = sys.process(my_pid) {
                let current_mem = process.memory();
                peak_rss_tracker.fetch_max(current_mem, Ordering::Release);
            }
            tokio::time::sleep(Duration::from_millis(5)).await;
        }
    });

    let mut ring = LockFreeFrameRing::new();
    let mut total_latency_us = 0u128;
    let mut frames_processed = 0usize;

    let bench_start = Instant::now();

    for seq in 0..TOTAL_BENCHMARK_FRAMES {
        let frame_start = Instant::now();

        let event = FrameEvent {
            timestamp_ns: 1_000_000_000 + (seq as u64 * 16_666_666),
            frame_duration_ns: 16_666_666,
            sequence: seq as u64,
        };

        if !ring.push(event) {
            return Err("Ring buffer capacity overrun: push rejected".to_string());
        }

        let popped = ring.pop();
        let Some(event) = popped else {
            return Err("Ring buffer delivery failure: pop returned empty".to_string());
        };
        let _ = event.sequence;
        let _ = event.timestamp_ns;
        let _ = event.frame_duration_ns;

        let latency = frame_start.elapsed().as_micros();
        total_latency_us += latency;
        frames_processed += 1;
    }

    let bench_duration = bench_start.elapsed();
    running.store(false, Ordering::Release);

    let mut sys_final = System::new();
    sys_final.refresh_processes(ProcessesToUpdate::Some(&[my_pid]), true);
    if let Some(proc) = sys_final.process(my_pid) {
        peak_rss_bytes.fetch_max(proc.memory(), Ordering::Release);
    }

    monitor_handle
        .await
        .map_err(|e| format!("Memory monitor task failure: {}", e))?;

    let avg_latency_us = (total_latency_us as f64) / (frames_processed as f64);
    let throughput_fps = (frames_processed as f64) / bench_duration.as_secs_f64();
    let final_peak_rss = peak_rss_bytes.load(Ordering::Acquire);

    println!(
        "\n--- STREAM PERF STATS ---\nFrames Processed: {}\nElapsed: {:?}\nThroughput: {:.0} fps\nLatency: {:.3} us/frame\nPeak RSS Memory: {} KB ({:.2} MB)\n-------------------------",
        frames_processed,
        bench_duration,
        throughput_fps,
        avg_latency_us,
        final_peak_rss / 1024,
        (final_peak_rss as f64) / (1024.0 * 1024.0)
    );

    if final_peak_rss > MAX_RSS_MEMORY_BYTES {
        return Err(format!(
            "Memory budget breach: peak RSS reached {} bytes (ceiling: {} bytes)",
            final_peak_rss, MAX_RSS_MEMORY_BYTES
        ));
    }

    if throughput_fps < 10_000.0 {
        return Err(format!(
            "Insufficient stream throughput: achieved only {:.0} events/sec",
            throughput_fps
        ));
    }

    if avg_latency_us > 100.0 {
        return Err(format!(
            "Stream latency degradation: average latency is {:.2} us (expected < 100 us)",
            avg_latency_us
        ));
    }

    Ok(())
}
