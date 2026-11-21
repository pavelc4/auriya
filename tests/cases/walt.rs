use auriya::core::scaling::walt::{ClusterSpec, WaltCalculator, WaltProfileMode};

#[test]
fn test_walt_formulas() {
    // Formula 1: Capacity Ratio
    let r = WaltCalculator::capacity_ratio(271, 827);
    assert!((r - 0.32769).abs() < 1e-4);

    // Formula 2: Dynamic Headroom
    let h = WaltCalculator::dynamic_headroom(0.75);
    assert!((h - 0.25).abs() < 1e-6);

    // Formula 3: Normalized Load
    let norm = WaltCalculator::normalized_load(200, 271);
    assert_eq!(norm, (200 * 1024) / 271);

    // Formula 4: EMA
    let ema = WaltCalculator::ema(100.0, 50.0, 0.2);
    assert!((ema - 60.0).abs() < 1e-6);

    // Formula 5: Coefficient of Variation
    let samples = vec![16.6, 16.7, 16.5, 16.6, 16.8];
    let cv = WaltCalculator::coefficient_of_variation(&samples).unwrap();
    assert!(cv > 0.0 && cv < 0.05);

    // Formula 6: Hysteresis Gap
    assert_eq!(WaltCalculator::hysteresis_gap(65, 15), 50);
    assert_eq!(WaltCalculator::hysteresis_gap(80, 15), 65);

    // Formula 7: Co-location Bitmask (CPUs 4..7 => 240)
    let clusters = vec![
        ClusterSpec::new(0, vec![0, 1, 2, 3], 271),
        ClusterSpec::new(1, vec![4, 5, 6], 827),
        ClusterSpec::new(2, vec![7], 1024),
    ];
    let mask = WaltCalculator::coloc_cpus_mask(&clusters);
    assert_eq!(mask, 240);
}

#[test]
fn test_tri_cluster_tuning_sm7475() {
    let clusters = vec![
        ClusterSpec::new(0, vec![0, 1, 2, 3], 271),
        ClusterSpec::new(1, vec![4, 5, 6], 827),
        ClusterSpec::new(2, vec![7], 1024),
    ];

    let perf = WaltCalculator::calculate_tuning(&clusters, WaltProfileMode::Performance);
    assert_eq!(perf.upmigrate, vec![65, 80]);
    assert_eq!(perf.downmigrate, vec![50, 65]);
    assert_eq!(perf.group_upmigrate, 70);
    assert_eq!(perf.group_downmigrate, 55);
    assert_eq!(perf.window_stats_policy, 1);
    assert_eq!(perf.ravg_window, 10_000_000);
    assert_eq!(perf.coloc_busy_hysteresis_enable_cpus, 240);
    assert_eq!(perf.upmigrate_str(), "65 80");
    assert_eq!(perf.downmigrate_str(), "50 65");

    let bal = WaltCalculator::calculate_tuning(&clusters, WaltProfileMode::Balance);
    assert_eq!(bal.upmigrate, vec![80, 90]);
    assert_eq!(bal.downmigrate, vec![65, 75]);
    assert_eq!(bal.group_upmigrate, 85);
    assert_eq!(bal.group_downmigrate, 70);
    assert_eq!(bal.window_stats_policy, 2);
    assert_eq!(bal.ravg_window, 20_000_000);
    assert_eq!(bal.coloc_busy_hysteresis_enable_cpus, 0);

    let pwr = WaltCalculator::calculate_tuning(&clusters, WaltProfileMode::Powersave);
    assert_eq!(pwr.upmigrate, vec![90, 95]);
    assert_eq!(pwr.downmigrate, vec![75, 80]);
    assert_eq!(pwr.group_upmigrate, 95);
    assert_eq!(pwr.group_downmigrate, 80);
    assert_eq!(pwr.window_stats_policy, 0);
}

#[test]
fn test_dual_cluster_tuning_sm8250() {
    let clusters = vec![
        ClusterSpec::new(0, vec![0, 1, 2, 3], 384),
        ClusterSpec::new(1, vec![4, 5, 6, 7], 1024),
    ];

    let perf = WaltCalculator::calculate_tuning(&clusters, WaltProfileMode::Performance);
    assert_eq!(perf.upmigrate.len(), 1);
    assert_eq!(perf.downmigrate.len(), 1);
    assert_eq!(perf.upmigrate[0] - perf.downmigrate[0], 15);
    assert_eq!(perf.window_stats_policy, 1);
    assert_eq!(perf.ravg_window, 10_000_000);
    assert_eq!(perf.coloc_busy_hysteresis_enable_cpus, 240);

    let bal = WaltCalculator::calculate_tuning(&clusters, WaltProfileMode::Balance);
    assert_eq!(bal.upmigrate, vec![80]);
    assert_eq!(bal.downmigrate, vec![65]);
    assert_eq!(bal.window_stats_policy, 2);
}

#[test]
fn test_base_case_empty_clusters() {
    let empty_clusters: Vec<ClusterSpec> = Vec::new();
    let tuning = WaltCalculator::calculate_tuning(&empty_clusters, WaltProfileMode::Performance);
    assert_eq!(tuning.upmigrate, vec![65, 80]);
    assert_eq!(tuning.downmigrate, vec![50, 65]);
    assert_eq!(tuning.group_upmigrate, 70);
    assert_eq!(tuning.group_downmigrate, 55);
    assert_eq!(tuning.window_stats_policy, 1);
    assert_eq!(tuning.coloc_busy_hysteresis_enable_cpus, 240);
}

#[test]
fn test_base_case_single_cluster_all_modes() {
    // Single-cluster symmetric SoC (e.g. Snapdragon 625 MSM8953, 8x Cortex-A53)
    let clusters = vec![ClusterSpec::new(0, vec![0, 1, 2, 3, 4, 5, 6, 7], 1024)];

    let perf = WaltCalculator::calculate_tuning(&clusters, WaltProfileMode::Performance);
    assert_eq!(perf.upmigrate, vec![95]);
    assert_eq!(perf.downmigrate, vec![80]);
    assert_eq!(perf.window_stats_policy, 1);
    assert_eq!(perf.coloc_busy_hysteresis_enable_cpus, 0);

    let bal = WaltCalculator::calculate_tuning(&clusters, WaltProfileMode::Balance);
    assert_eq!(bal.upmigrate, vec![95]);
    assert_eq!(bal.downmigrate, vec![85]);
    assert_eq!(bal.window_stats_policy, 2);
    assert_eq!(bal.coloc_busy_hysteresis_enable_cpus, 0);

    let pwr = WaltCalculator::calculate_tuning(&clusters, WaltProfileMode::Powersave);
    assert_eq!(pwr.upmigrate, vec![98]);
    assert_eq!(pwr.downmigrate, vec![90]);
    assert_eq!(pwr.window_stats_policy, 0);
    assert_eq!(pwr.coloc_busy_hysteresis_enable_cpus, 0);
}

#[test]
fn test_base_case_boundary_utilization_and_headroom() {
    // Utilization exactly 0.0 -> full 1.0 headroom
    assert!((WaltCalculator::dynamic_headroom(0.0) - 1.0).abs() < 1e-6);

    // Utilization exactly 1.0 -> 0.0 headroom
    assert!((WaltCalculator::dynamic_headroom(1.0) - 0.0).abs() < 1e-6);

    // Utilization below 0.0 (anomalous negative load) -> clamped to 1.0
    assert!((WaltCalculator::dynamic_headroom(-0.5) - 1.0).abs() < 1e-6);

    // Utilization above 1.0 (overloaded core) -> clamped to 0.0
    assert!((WaltCalculator::dynamic_headroom(1.8) - 0.0).abs() < 1e-6);
}

#[test]
fn test_base_case_zero_capacity_division_safety() {
    // Target capacity 0 in capacity ratio -> safely returns 1.0
    let r = WaltCalculator::capacity_ratio(100, 0);
    assert_eq!(r, 1.0);

    // Capacity 0 in normalized load -> returns raw_load.min(1024)
    let norm = WaltCalculator::normalized_load(500, 0);
    assert_eq!(norm, 500);

    let norm_overflow = WaltCalculator::normalized_load(2000, 0);
    assert_eq!(norm_overflow, 1024);
}

#[test]
fn test_base_case_empty_and_constant_cv() {
    // Empty samples -> None
    assert_eq!(WaltCalculator::coefficient_of_variation(&[]), None);

    // Single sample -> None (no variance possible)
    assert_eq!(WaltCalculator::coefficient_of_variation(&[16.6]), None);

    // All zero values -> returns Some(0.0) without divide-by-zero panic
    assert_eq!(
        WaltCalculator::coefficient_of_variation(&[0.0, 0.0, 0.0]),
        Some(0.0)
    );

    // All identical non-zero values -> variance is 0.0, CV is 0.0
    let identical = vec![16.66, 16.66, 16.66, 16.66];
    let cv = WaltCalculator::coefficient_of_variation(&identical).unwrap();
    assert!(cv.abs() < 1e-6);
}

#[test]
fn test_base_case_hysteresis_underflow() {
    // Underflow case where delta_h > upmigrate
    let down = WaltCalculator::hysteresis_gap(10, 15);
    assert_eq!(down, 0);
}

#[test]
fn test_worst_case_inverted_cluster_capacities() {
    // Clusters delivered out of order: Prime (1024), Little (271), Gold (827)
    let clusters = vec![
        ClusterSpec::new(0, vec![7], 1024),
        ClusterSpec::new(1, vec![0, 1, 2, 3], 271),
        ClusterSpec::new(2, vec![4, 5, 6], 827),
    ];

    let perf = WaltCalculator::calculate_tuning(&clusters, WaltProfileMode::Performance);
    // Calculation should sort internally and produce the exact same tuning as ordered clusters
    assert_eq!(perf.upmigrate, vec![65, 80]);
    assert_eq!(perf.downmigrate, vec![50, 65]);
    assert_eq!(perf.coloc_busy_hysteresis_enable_cpus, 240);
}

#[test]
fn test_worst_case_identical_capacities() {
    // Dual cluster with identical capacities (e.g. 2 clusters of identical cores)
    let clusters = vec![
        ClusterSpec::new(0, vec![0, 1, 2, 3], 1024),
        ClusterSpec::new(1, vec![4, 5, 6, 7], 1024),
    ];

    let perf = WaltCalculator::calculate_tuning(&clusters, WaltProfileMode::Performance);
    assert_eq!(perf.upmigrate.len(), 1);
    assert_eq!(perf.downmigrate.len(), 1);
    assert!(perf.upmigrate[0] >= 60 && perf.upmigrate[0] <= 75);
    assert_eq!(perf.upmigrate[0] - perf.downmigrate[0], 15);
}

#[test]
fn test_worst_case_out_of_bounds_cpu_ids() {
    // CPU IDs greater than or equal to 32 should not cause bitshift overflow
    let clusters = vec![
        ClusterSpec::new(0, vec![0, 1], 200),
        ClusterSpec::new(1, vec![32, 64, 128], 1000),
    ];

    let mask = WaltCalculator::coloc_cpus_mask(&clusters);
    // Cores >= 32 are ignored in 32-bit CPU mask without panic
    assert_eq!(mask, 0);
}

#[test]
fn test_worst_case_extreme_loads() {
    // Huge raw load with large capacity -> clamped to 1024
    let norm = WaltCalculator::normalized_load(u32::MAX, 1024);
    assert_eq!(norm, 1024);

    // Micro capacity = 1
    let norm_micro = WaltCalculator::normalized_load(5, 1);
    assert_eq!(norm_micro, 1024);

    // Large capacity = u32::MAX
    let norm_giant = WaltCalculator::normalized_load(100, u32::MAX);
    assert_eq!(norm_giant, 0);
}

#[test]
fn test_worst_case_extreme_jitter_frametimes() {
    // Frame drop test: steady 60 FPS (16.6ms) suddenly freezes for 1000ms
    let frame_times = vec![16.6, 16.7, 16.5, 16.6, 1000.0];
    let cv = WaltCalculator::coefficient_of_variation(&frame_times).unwrap();
    // Huge standard deviation relative to mean -> CV spikes significantly above 1.0
    assert!(cv > 1.0);
}

#[test]
fn test_simulation_gaming_frame_burst_detection() {
    // Simulation Scenario:
    // Stage 1 (Stable 60 FPS gameplay, low load):
    let stage1_smooth = vec![16.66, 16.62, 16.70, 16.64, 16.68, 16.61, 16.65];
    let cv_smooth = WaltCalculator::coefficient_of_variation(&stage1_smooth).unwrap();
    // Smooth gameplay has minimal CV (< 0.05)
    assert!(cv_smooth < 0.05);
    assert!(
        cv_smooth < 0.22,
        "Smooth frames should not exceed calibrated threshold 0.22"
    );

    // Stage 2 (Combat burst with frame stutters: 16ms -> 35ms -> 50ms):
    let stage2_burst = vec![16.6, 35.2, 16.8, 52.1, 16.7, 44.0, 17.0];
    let cv_burst = WaltCalculator::coefficient_of_variation(&stage2_burst).unwrap();
    // Burst combat induces high variance exceeding 0.22 threshold
    assert!(
        cv_burst > 0.22,
        "Combat burst frame jitter should exceed 0.22"
    );
}

#[test]
fn test_simulation_walt_ema_window_tracking() {
    // Simulation: Task load steps from 20% to 80% across 10 scheduling windows
    let mut current_ema = 20.0;
    let target_load = 80.0;
    let alpha = 0.2; // 20% weight per window

    let mut history = Vec::new();
    for _ in 0..10 {
        current_ema = WaltCalculator::ema(target_load, current_ema, alpha);
        history.push(current_ema);
    }

    // After 1 window: 20 * 0.8 + 80 * 0.2 = 32.0
    assert!((history[0] - 32.0).abs() < 1e-6);

    // Monotonically increases towards target load
    for i in 1..history.len() {
        assert!(history[i] > history[i - 1]);
    }

    // By window 10, EMA has smoothly converged close to 80.0 (> 70.0)
    assert!(history[9] > 70.0 && history[9] < 80.0);
}

#[test]
fn test_simulation_task_migration_hysteresis_prevention() {
    // Simulation Scenario:
    // Upmigrate threshold = 65, Downmigrate threshold = 50 (Delta_H = 15).
    let upmigrate = 65;
    let downmigrate = 50;

    // Task load oscillates near the boundary:
    let load_sequence = vec![60, 68, 62, 70, 58, 52, 48, 45, 66];
    let mut on_big_core = false;
    let mut migration_count = 0;

    for &load in &load_sequence {
        match (on_big_core, load >= upmigrate, load <= downmigrate) {
            (false, true, _) => {
                // Task migrates up to Big core
                on_big_core = true;
                migration_count += 1;
            }
            (true, _, true) => {
                // Task migrates down to Little core
                on_big_core = false;
                migration_count += 1;
            }
            _ => {
                // Stays in hysteresis deadband (51..64), no migration ping-pong
            }
        }
    }

    // Step-by-step:
    // load 60: on Little
    // load 68: migrates to Big (count = 1)
    // load 62: stays on Big (inside deadband, 62 > 50)
    // load 70: stays on Big
    // load 58: stays on Big (inside deadband, 58 > 50)
    // load 52: stays on Big (inside deadband, 52 > 50)
    // load 48: migrates down to Little (count = 2)
    // load 45: stays on Little
    // load 66: migrates to Big (count = 3)
    assert_eq!(
        migration_count, 3,
        "Hysteresis gap successfully prevented ping-ponging in the 50..65 deadband"
    );
}

#[test]
fn test_simulation_multi_soc_architecture_matrix() {
    // Test SoC Matrix from real DTSI topologies:

    // 1. Qualcomm SM8450 / Snapdragon 8 Gen 1 (1+3+4)
    let sm8450 = vec![
        ClusterSpec::new(0, vec![0, 1, 2, 3], 220), // A510
        ClusterSpec::new(1, vec![4, 5, 6], 780),    // A710
        ClusterSpec::new(2, vec![7], 1024),         // X2
    ];
    let sm8450_tuning = WaltCalculator::calculate_tuning(&sm8450, WaltProfileMode::Performance);
    assert_eq!(sm8450_tuning.upmigrate.len(), 2);
    assert!(sm8450_tuning.upmigrate[0] < sm8450_tuning.upmigrate[1]);
    assert_eq!(
        sm8450_tuning.upmigrate[0] - sm8450_tuning.downmigrate[0],
        15
    );
    assert_eq!(
        sm8450_tuning.upmigrate[1] - sm8450_tuning.downmigrate[1],
        15
    );
    assert_eq!(sm8450_tuning.coloc_busy_hysteresis_enable_cpus, 240);

    // 2. Qualcomm SM8550 / Snapdragon 8 Gen 2 (1+4+3)
    let sm8550 = vec![
        ClusterSpec::new(0, vec![0, 1, 2], 250),    // A520
        ClusterSpec::new(1, vec![3, 4, 5, 6], 850), // A720 / A715
        ClusterSpec::new(2, vec![7], 1024),         // X3
    ];
    let sm8550_tuning = WaltCalculator::calculate_tuning(&sm8550, WaltProfileMode::Performance);
    assert_eq!(sm8550_tuning.upmigrate.len(), 2);
    assert!(sm8550_tuning.upmigrate[0] < sm8550_tuning.upmigrate[1]);
    assert_eq!(sm8550_tuning.coloc_busy_hysteresis_enable_cpus, 248); // CPUs 3..7 => bitmask 248

    // 3. Qualcomm SM7125 / Snapdragon 720G (2+6)
    let sm7125 = vec![
        ClusterSpec::new(0, vec![0, 1, 2, 3, 4, 5], 350), // 6x A55
        ClusterSpec::new(1, vec![6, 7], 1024),            // 2x A76
    ];
    let sm7125_tuning = WaltCalculator::calculate_tuning(&sm7125, WaltProfileMode::Performance);
    assert_eq!(sm7125_tuning.upmigrate.len(), 1);
    assert_eq!(
        sm7125_tuning.upmigrate[0] - sm7125_tuning.downmigrate[0],
        15
    );
    assert_eq!(sm7125_tuning.coloc_busy_hysteresis_enable_cpus, 192); // CPUs 6,7 => 64 + 128 = 192
}
