use auriya::core::tweaks::cpu::classify_from_freqs;

/// Helper: turn a bitmask into a sorted Vec of core ids for assertions.
fn ids(mask: u64) -> Vec<usize> {
    (0..64usize).filter(|&i| (mask >> i) & 1 == 1).collect()
}

#[test]
fn classifies_4_3_1_topology() {
    // Snapdragon-style: cpu0-3 @1.8GHz, cpu4-6 @2.5GHz, cpu7 @2.9GHz.
    // Regression: the little tier used to tie the median and land in big.
    let freqs = [
        (0, 1_804_800),
        (1, 1_804_800),
        (2, 1_804_800),
        (3, 1_804_800),
        (4, 2_496_000),
        (5, 2_496_000),
        (6, 2_496_000),
        (7, 2_918_400),
    ];
    let (little, big, prime) = classify_from_freqs(&freqs);
    assert_eq!(ids(little), vec![0, 1, 2, 3], "little");
    assert_eq!(ids(big), vec![4, 5, 6], "big");
    assert_eq!(ids(prime), vec![7], "prime");
}

#[test]
fn classifies_4_4_two_tier_no_prime() {
    let freqs = [
        (0, 1_800_000),
        (1, 1_800_000),
        (2, 1_800_000),
        (3, 1_800_000),
        (4, 2_400_000),
        (5, 2_400_000),
        (6, 2_400_000),
        (7, 2_400_000),
    ];
    let (little, big, prime) = classify_from_freqs(&freqs);
    assert_eq!(ids(little), vec![0, 1, 2, 3]);
    assert_eq!(ids(big), vec![4, 5, 6, 7]);
    assert_eq!(ids(prime), Vec::<usize>::new());
}

#[test]
fn classifies_single_tier_all_big() {
    let freqs = [
        (0, 2_000_000),
        (1, 2_000_000),
        (2, 2_000_000),
        (3, 2_000_000),
    ];
    let (little, big, prime) = classify_from_freqs(&freqs);
    assert_eq!(ids(little), Vec::<usize>::new());
    assert_eq!(ids(big), vec![0, 1, 2, 3]);
    assert_eq!(ids(prime), Vec::<usize>::new());
}

#[test]
fn classifies_1_3_4_topology() {
    // 4 little, 3 big, 1 prime (different counts, same 3-tier shape).
    let freqs = [
        (0, 2_000_000),
        (1, 2_000_000),
        (2, 2_000_000),
        (3, 2_000_000),
        (4, 2_800_000),
        (5, 2_800_000),
        (6, 2_800_000),
        (7, 3_200_000),
    ];
    let (little, big, prime) = classify_from_freqs(&freqs);
    assert_eq!(ids(little), vec![0, 1, 2, 3]);
    assert_eq!(ids(big), vec![4, 5, 6]);
    assert_eq!(ids(prime), vec![7]);
}
