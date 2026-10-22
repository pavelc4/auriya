use auriya::core::fas::{
    BOOST_THRESHOLD_KHZ, BottleneckType, FasTuning, KP_DEFAULT, MARGIN_FPS, REDUCE_THRESHOLD_KHZ,
    UtilSampler, compute_control_khz, decide_action,
};
use auriya::core::scaling::ScalingAction;
use std::time::Duration;

fn ft_ms(ms: f64) -> Duration {
    Duration::from_secs_f64(ms / 1000.0)
}

fn settings_with(
    default_mode: &str,
    modes: &[(&str, f64, f64)],
) -> auriya::core::config::settings::Settings {
    use auriya::core::config::settings::*;
    use std::collections::HashMap;
    let mut m = HashMap::new();
    for (name, margin, thermal) in modes {
        m.insert(
            (*name).to_string(),
            FasMode {
                margin: *margin,
                thermal_threshold: *thermal,
            },
        );
    }
    Settings {
        daemon: DaemonConfig::default(),
        cpu: CpuConfig {
            default_governor: "schedutil".into(),
        },
        dnd: DndConfig {
            default_enable: true,
        },
        fas: FasConfig {
            enabled: true,
            default_mode: default_mode.to_string(),
            thermal_threshold: 90.0,
            poll_interval_ms: 100,
            target_fps: 60,
        },
        dynamic_governor: DynamicGovernorConfig {
            enabled: true,
            cv_threshold: 0.22,
            debounce_frames: 7,
        },
        ceiling: CeilingConfig::default(),
        thermal: ThermalConfig::default(),
        modes: m,
    }
}

#[test]
fn tuning_uses_active_mode_margin_and_thermal() {
    let s = settings_with("fast", &[("balance", 2.0, 90.0), ("fast", 0.0, 95.0)]);
    let t = FasTuning::from_settings(&s);
    assert_eq!(t.margin_fps, 0.0);
    assert_eq!(t.thermal_threshold, 95.0);
    assert_eq!(t.cv_threshold, 0.22);
    assert_eq!(t.debounce_frames, 7);
    assert!(t.dynamic_governor_enabled);
}

#[test]
fn tuning_unknown_mode_falls_back_to_defaults_and_global_thermal() {
    let s = settings_with("nonexistent", &[("balance", 2.0, 88.0)]);
    let t = FasTuning::from_settings(&s);
    assert_eq!(t.margin_fps, FasTuning::default().margin_fps);
    assert_eq!(t.thermal_threshold, 90.0);
}

#[test]
fn tuning_disabled_dynamic_governor_is_propagated() {
    let mut s = settings_with("balance", &[("balance", 3.0, 85.0)]);
    s.dynamic_governor.enabled = false;
    let t = FasTuning::from_settings(&s);
    assert!(!t.dynamic_governor_enabled);
    assert_eq!(t.margin_fps, 3.0);
}

#[test]
fn perfect_frame_yields_zero_control() {
    let last = Duration::from_nanos(1_000_000_000 / 60);
    let ctl = compute_control_khz(last, 60.0, KP_DEFAULT);
    assert!(ctl.abs() < 1.0, "expected ~0 control, got {ctl}");
}

#[test]
fn slow_frame_yields_positive_control() {
    let ctl = compute_control_khz(ft_ms(30.0), 60.0, KP_DEFAULT);
    assert!(ctl > BOOST_THRESHOLD_KHZ, "expected boost, got {ctl}");
}

#[test]
fn fast_frame_yields_negative_control_below_reduce_threshold() {
    let ctl = compute_control_khz(ft_ms(10.0), 60.0, KP_DEFAULT);
    assert!(ctl < REDUCE_THRESHOLD_KHZ, "expected reduce, got {ctl}");
}

#[test]
fn marginal_frame_stays_in_maintain_band() {
    let ctl = compute_control_khz(ft_ms(17.0), 60.0, KP_DEFAULT);
    assert!(ctl.abs() < BOOST_THRESHOLD_KHZ);
}

#[test]
fn margin_fps_biases_target_lower_so_normal_frames_register_as_slow() {
    let last = Duration::from_nanos(1_000_000_000 / 60);
    let with_margin = compute_control_khz(last, 60.0 - MARGIN_FPS, KP_DEFAULT);
    let without_margin = compute_control_khz(last, 60.0, KP_DEFAULT);
    assert!(with_margin < without_margin);
}

#[test]
fn util_sampler_returns_value_within_bounds() {
    let mut s = UtilSampler::new();
    let first = s.sample(Duration::ZERO);
    assert!(first.is_some());
    if std::path::Path::new("/proc/stat").exists() {
        std::thread::sleep(Duration::from_millis(20));
        let second = s.sample(Duration::ZERO).unwrap();
        assert!((0.0..=1.0).contains(&second));
    }
}

#[test]
fn decide_action_jank_with_gpu_bottleneck_returns_boost_gpu() {
    let action = decide_action(100_000.0, true, BottleneckType::Gpu);
    assert_eq!(action, ScalingAction::BoostGpu);
}

#[test]
fn decide_action_jank_with_cpu_bottleneck_returns_boost_cpu() {
    let action = decide_action(100_000.0, true, BottleneckType::Cpu);
    assert_eq!(action, ScalingAction::BoostCpu);
}

#[test]
fn decide_action_jank_with_unknown_bottleneck_returns_boost_balanced() {
    let action = decide_action(100_000.0, true, BottleneckType::Unknown);
    assert_eq!(action, ScalingAction::BoostBalanced);
}

#[test]
fn decide_action_high_control_returns_boost_variant() {
    let action = decide_action(100_000.0, false, BottleneckType::Gpu);
    assert_eq!(action, ScalingAction::BoostGpu);
}

#[test]
fn decide_action_low_control_returns_reduce() {
    let action = decide_action(-100_000.0, false, BottleneckType::Balanced);
    assert_eq!(action, ScalingAction::Reduce);
}

#[test]
fn decide_action_neutral_control_returns_maintain() {
    let action = decide_action(0.0, false, BottleneckType::Balanced);
    assert_eq!(action, ScalingAction::Maintain);
}
