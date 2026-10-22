use auriya::core::tweaks::thermal::{ThermalPreset, capture_from};
use std::fs;

#[test]
fn parses_preset_names() {
    for (name, preset) in [
        ("class0", ThermalPreset::Class0),
        ("extreme", ThermalPreset::Extreme),
        ("dynamic", ThermalPreset::Dynamic),
        ("incalls", ThermalPreset::Incalls),
        ("thermal20", ThermalPreset::Thermal20),
    ] {
        assert_eq!(name.parse::<ThermalPreset>().unwrap(), preset);
    }
}

#[test]
fn rejects_unknown_preset() {
    assert!("turbo".parse::<ThermalPreset>().is_err());
    assert!("".parse::<ThermalPreset>().is_err());
}

#[test]
fn maps_sconfig_values() {
    for (value, preset) in [
        ("11", ThermalPreset::Class0),
        ("0", ThermalPreset::Class0),
        ("2", ThermalPreset::Extreme),
        ("10", ThermalPreset::Dynamic),
        ("8", ThermalPreset::Incalls),
        ("20", ThermalPreset::Thermal20),
    ] {
        assert_eq!(value.parse::<ThermalPreset>().unwrap(), preset);
    }
}

#[test]
fn display_roundtrips_index() {
    for preset in [
        ThermalPreset::Class0,
        ThermalPreset::Extreme,
        ThermalPreset::Dynamic,
        ThermalPreset::Incalls,
        ThermalPreset::Thermal20,
    ] {
        assert_eq!(preset.to_string().parse::<ThermalPreset>().unwrap(), preset);
    }
}

#[test]
fn captures_actual_rom_value_not_hardcoded() {
    let dir = std::env::temp_dir().join(format!("auriya_thermal_test_{}", std::process::id()));
    let file = dir.join("thermal_default");
    let sconfig = dir.join("sconfig_test");
    let _ = fs::remove_dir_all(&dir);
    fs::create_dir_all(&dir).unwrap();

    // Pretend the stock ROM ships Thermal20 (20), never touched by Auriya.
    fs::write(&sconfig, "20\n").unwrap();

    let captured = capture_from(&file, &sconfig);
    assert_eq!(captured, Some(20));
    let persisted: i32 = fs::read_to_string(&file).unwrap().trim().parse().unwrap();
    assert_eq!(
        persisted, 20,
        "stock value must be stored as-is, not assumed 11"
    );

    // Second call must never overwrite an existing default.
    fs::write(&sconfig, "2\n").unwrap();
    capture_from(&file, &sconfig);
    assert_eq!(
        fs::read_to_string(&file).unwrap().trim(),
        "20",
        "default file must never be overwritten once persisted"
    );

    let _ = fs::remove_dir_all(&dir);
}
