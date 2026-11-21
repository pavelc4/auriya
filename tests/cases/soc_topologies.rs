use auriya::core::fas::buffer::{FrameBuffer, TargetFps};
use auriya::platform::cpu::topology::classify_from_freqs;
use std::time::Duration;

#[test]
fn test_heterogeneous_soc_cluster_classifications() {
    // 1. Snapdragon 7+ Gen 2 / SM7475 (4 Little + 3 Mid + 1 Prime)
    let sm7475 = [
        (0, 1804800),
        (1, 1804800),
        (2, 1804800),
        (3, 1804800),
        (4, 2496000),
        (5, 2496000),
        (6, 2496000),
        (7, 2918400),
    ];
    let (little, big, prime) = classify_from_freqs(&sm7475);
    assert_eq!(little, 0b0000_1111); // cores 0..3
    assert_eq!(big, 0b0111_0000); // cores 4..6
    assert_eq!(prime, 0b1000_0000); // core 7

    // 2. MediaTek Dimensity 8100/9000 (4 Little + 4 Big, 2 Tiers)
    let dimensity_dual = [
        (0, 2000000),
        (1, 2000000),
        (2, 2000000),
        (3, 2000000),
        (4, 2850000),
        (5, 2850000),
        (6, 2850000),
        (7, 2850000),
    ];
    let (little, big, prime) = classify_from_freqs(&dimensity_dual);
    assert_eq!(little, 0b0000_1111); // cores 0..3
    assert_eq!(big, 0b1111_0000); // cores 4..7 (treated as big in 2-tier layout)
    assert_eq!(prime, 0); // no dedicated prime tier

    // 3. Snapdragon 680 / Helio G99 (6 Little + 2 Big)
    let six_plus_two = [
        (0, 1900000),
        (1, 1900000),
        (2, 1900000),
        (3, 1900000),
        (4, 1900000),
        (5, 1900000),
        (6, 2400000),
        (7, 2400000),
    ];
    let (little, big, prime) = classify_from_freqs(&six_plus_two);
    assert_eq!(little, 0b0011_1111); // cores 0..5
    assert_eq!(big, 0b1100_0000); // cores 6..7
    assert_eq!(prime, 0);

    // 4. Google Tensor G2 / G3 (4 Little + 2 Mid + 2 Big/Prime, 3 Tiers)
    let tensor = [
        (0, 1800000),
        (1, 1800000),
        (2, 1800000),
        (3, 1800000),
        (4, 2350000),
        (5, 2350000),
        (6, 2850000),
        (7, 2850000),
    ];
    let (little, big, prime) = classify_from_freqs(&tensor);
    assert_eq!(little, 0b0000_1111); // cores 0..3
    assert_eq!(big, 0b0011_0000); // cores 4..5
    assert_eq!(prime, 0b1100_0000); // cores 6..7

    // 5. Symmetric / Single-Cluster (4 cores identical, e.g. entry-level/IoT)
    let single_cluster = [(0, 1500000), (1, 1500000), (2, 1500000), (3, 1500000)];
    let (little, big, prime) = classify_from_freqs(&single_cluster);
    assert_eq!(little, 0);
    assert_eq!(big, 0b1111); // all cores in single cluster treated as big
    assert_eq!(prime, 0);
}

#[test]
fn test_display_rates_144hz_and_165hz_tolerance() {
    let mut buf = FrameBuffer::new(TargetFps::Array(vec![60, 90, 120, 144, 165]));

    // Feed 142 FPS frames (dt ≈ 7.04ms)
    for _ in 0..200 {
        buf.push(Duration::from_nanos(7_042_253));
    }
    assert_eq!(
        buf.target_fps,
        Some(144),
        "142 FPS must snap to 144Hz target"
    );

    // Feed 163 FPS frames (dt ≈ 6.13ms)
    for _ in 0..300 {
        buf.push(Duration::from_nanos(6_134_969));
    }
    assert_eq!(
        buf.target_fps,
        Some(165),
        "163 FPS must snap to 165Hz target"
    );
}

#[test]
fn test_missing_sysfs_graceful_defaults() {
    // Non-existent cpufreq node returns None cleanly
    let freq = auriya::platform::cpu::topology::read_core_max_freq(999);
    assert_eq!(freq, None);

    // Online cores with empty or malformed input
    let cores = auriya::platform::cpu::topology::parse_online_cores("");
    assert!(cores.is_empty());

    let cores_invalid = auriya::platform::cpu::topology::parse_online_cores("abc,xyz-foo");
    assert!(cores_invalid.is_empty());
}
