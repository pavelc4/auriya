#[global_allocator]
static ALLOC: dhat::Alloc = dhat::Alloc;

use auriya::core::fas::buffer::{FrameBuffer, TargetFps};
use std::time::Duration;

#[test]
fn test_frame_buffer_hot_path_zero_heap_allocations() {
    let _profiler = dhat::Profiler::builder().testing().build();

    let mut buf = FrameBuffer::new(TargetFps::Single(60));

    // Warm up the ring buffer to its steady state (300 frames)
    for _ in 0..300 {
        buf.push(Duration::from_micros(16666));
    }

    // Capture memory allocation baseline after warmup
    let baseline = dhat::HeapStats::get();

    // Now push 1,000 more frames in the steady state
    for _ in 0..1000 {
        buf.push(Duration::from_micros(16666));
    }

    // Check allocations occurred during the 1,000 frames
    let stats = dhat::HeapStats::get();
    println!(
        "\n--- DHAT ZERO-ALLOCATION REPORT ---\nBaseline curr_bytes: {} bytes (blocks: {})\nAfter 1000 frames curr_bytes: {} bytes (blocks: {})\nDelta: {} bytes (0 bytes heap growth)\n-----------------------------------",
        baseline.curr_bytes,
        baseline.curr_blocks,
        stats.curr_bytes,
        stats.curr_blocks,
        stats.curr_bytes as i64 - baseline.curr_bytes as i64
    );
    assert_eq!(
        stats.curr_bytes, baseline.curr_bytes,
        "Steady-state FrameBuffer::push must perform zero heap growth"
    );
}
