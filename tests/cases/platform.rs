use auriya::platform::cpu::classify_from_freqs;
use auriya::platform::procfs::is_pid_valid;
use auriya::platform::thermal::ThermalPreset;

#[test]
fn test_platform_facade_cpu() {
    let freqs = [(0, 1_800_000), (1, 2_400_000)];
    let (little, big, prime) = classify_from_freqs(&freqs);
    assert_eq!(little, 1);
    assert_eq!(big, 2);
    assert_eq!(prime, 0);
}

#[test]
fn test_platform_facade_procfs() {
    assert!(is_pid_valid(std::process::id() as i32));
}

#[test]
fn test_platform_facade_thermal_preset() {
    let p = "extreme".parse::<ThermalPreset>().unwrap();
    assert_eq!(p, ThermalPreset::Extreme);
}
