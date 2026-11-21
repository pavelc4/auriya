use auriya::core::config::settings::Settings;
use auriya::core::fas::buffer::{FrameBuffer, TargetFps};
use auriya::core::fas::{FasController, FasTuning};
use auriya::core::fps::meter::FpsMeter;
use auriya::core::fps::types::FpsSource;
use auriya::core::profile::ProfileMode;
use std::time::Duration;

#[test]
fn test_vulkan_sysfs_fallback_when_ebpf_yields_zero_frames() {
    let (_tx, rx) = tokio::sync::broadcast::channel(16);
    let settings = Settings::default();
    let tuning = FasTuning::for_mode(&settings, ProfileMode::Performance);
    let mut fas = FasController::new(rx, TargetFps::Single(60), tuning);

    // In Vulkan games, eBPF uprobe on eglSwapBuffers receives 0 frames.
    // When drain_frames_with_fallback is called with fallback_fps (from hardware sysfs):
    for _ in 0..150 {
        fas.drain_frames_with_fallback(Some(60.0));
    }

    // Controller must still ingest frames and compute valid telemetry!
    assert_eq!(fas.get_target_fps(), 60);

    let stats = fas.fps_stats();
    assert!(stats.avg >= 59.0 && stats.avg <= 61.0);
}

#[test]
fn test_jank_synthesis_on_prolonged_frame_stall() {
    let mut buf = FrameBuffer::new(TargetFps::Single(60));

    // Warm up buffer with 60 FPS frames (16.6ms)
    for _ in 0..200 {
        buf.push(Duration::from_millis(16));
    }
    assert_eq!(buf.target_fps, Some(60));
    let initial_long_fps = buf.current_fps_long;
    assert!(initial_long_fps >= 59.0);

    // Simulate game freezing / stalling for 300ms
    std::thread::sleep(Duration::from_millis(150));
    buf.additional_frametime();

    // After synthesizing the stall frametime, average FPS must drop to reflect the freeze
    let stalled_long_fps = buf.current_fps_long;
    assert!(
        stalled_long_fps < initial_long_fps,
        "Synthesized stall must pull down long-window FPS average"
    );
}

#[test]
fn test_fps_meter_ebpf_to_sysfs_seamless_fallback() {
    let (tx, rx) = tokio::sync::broadcast::channel(16);
    let mut meter = FpsMeter::new(Some(rx));

    // 1. While eBPF sends frames, source is Ebpf
    for _ in 0..35 {
        let _ = tx.send(Duration::from_millis(16));
    }
    let reading = meter.read();
    assert!(reading.is_some());
    let r = reading.unwrap();
    assert_eq!(r.source, FpsSource::Ebpf);

    // 2. When eBPF stream pauses/idle, reading falls back to sysfs without panicking
    // (on host/test without physical sysfs, it returns None or Sysfs reading cleanly)
    std::thread::sleep(Duration::from_millis(50));
    let fallback = meter.read();
    // Must execute cleanly without unwrap panic
    let _ = fallback;
}
