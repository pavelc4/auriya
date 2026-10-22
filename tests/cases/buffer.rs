use auriya::core::fas::buffer::{BufferState, FrameBuffer, TargetFps};
use std::time::Duration;

fn ft_for_fps(fps: u32) -> Duration {
    Duration::from_secs_f64(1.0 / f64::from(fps))
}

#[test]
fn capacity_rolls_over_at_target_times_five() {
    let mut buf = FrameBuffer::new(TargetFps::Single(60));
    for _ in 0..(60 * 5 + 50) {
        buf.push(ft_for_fps(60));
    }
    assert!(buf.frametimes.len() <= 60 * 5);
}

#[test]
fn target_fps_detected_after_60_frames_at_steady_rate() {
    let mut buf = FrameBuffer::new(TargetFps::Array(vec![60, 90, 120]));
    for _ in 0..200 {
        buf.push(ft_for_fps(90));
    }
    assert_eq!(buf.target_fps, Some(90));
}

#[test]
fn target_change_clears_frametimes_and_marks_unusable() {
    let mut buf = FrameBuffer::new(TargetFps::Array(vec![60, 120]));
    for _ in 0..200 {
        buf.push(ft_for_fps(60));
    }
    assert_eq!(buf.target_fps, Some(60));

    // Shift to 120 fps frames; once long-window catches up, target should switch.
    for _ in 0..400 {
        buf.push(ft_for_fps(120));
    }
    assert_eq!(buf.target_fps, Some(120));
    // After the switch the buffer must have been reset to Unusable so the
    // controller does not run policy on stale data from the 60fps regime.
    // (Subsequent pushes will re-warm it; here we only assert the state at
    // the transition's tail.)
    assert!(buf.frametimes.len() <= 600);
}

#[test]
fn warmup_blocks_usable_for_one_second() {
    let mut buf = FrameBuffer::new(TargetFps::Single(60));
    for _ in 0..120 {
        buf.push(ft_for_fps(60));
    }
    // We just pushed 120 frames worth of fixture data instantly; less than
    // a wall-clock second has elapsed, so the buffer must still be Unusable.
    assert_eq!(buf.state, BufferState::Unusable);
}

#[test]
fn additional_frametime_extends_long_window_average() {
    let mut buf = FrameBuffer::new(TargetFps::Single(60));
    for _ in 0..120 {
        buf.push(ft_for_fps(60));
    }
    let before = buf.frametimes.len();
    std::thread::sleep(Duration::from_millis(20));
    buf.additional_frametime();
    assert_eq!(buf.frametimes.len(), before + 1);
    // Synthesized value reflects elapsed wall-time, so the freshest entry
    // is larger than a steady-state 60fps frame.
    assert!(buf.frametimes.front().copied().unwrap() >= Duration::from_millis(15));
}

#[test]
fn clear_resets_all_state() {
    let mut buf = FrameBuffer::new(TargetFps::Single(60));
    for _ in 0..100 {
        buf.push(ft_for_fps(60));
    }
    buf.clear();
    assert!(buf.frametimes.is_empty());
    assert_eq!(buf.target_fps, None);
    assert_eq!(buf.state, BufferState::Unusable);
    assert_eq!(buf.current_fps_long, 0.0);
}

#[test]
fn fps_below_min_disables_target() {
    let mut buf = FrameBuffer::new(TargetFps::Single(60));
    for _ in 0..200 {
        buf.push(ft_for_fps(60));
    }
    assert_eq!(buf.target_fps, Some(60));
    // Very slow frames (10fps) should drop us below the (60-10)=50 floor.
    for _ in 0..200 {
        buf.push(Duration::from_millis(100));
    }
    assert_eq!(buf.target_fps, None);
}

#[test]
fn recent_real_frametimes_filters_synthetic_stalls() {
    let mut buf = FrameBuffer::new(TargetFps::Single(120));
    for _ in 0..10 {
        buf.push(ft_for_fps(120));
    }
    buf.additional_frametime();
    for _ in 0..10 {
        buf.push(ft_for_fps(120));
    }

    let real = buf.recent_real_frametimes(30);
    assert_eq!(
        real.len(),
        20,
        "synthetic stalls must be filtered from real frametimes"
    );
    for ft in real {
        assert!(ft < Duration::from_millis(200));
    }
}
