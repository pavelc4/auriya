use auriya::core::config::settings::{Settings, ThermalConfig, WindowConfig};

#[test]
fn settings_load_strips_nul_bytes_and_preserves_values() {
    let tmp = tempfile::NamedTempFile::new().unwrap();
    let payload = format!(
        "{}[daemon]\nlog_level = \"debug\"\ncheck_interval_ms = 1500\ndefault_mode = \"fast\"\n",
        "\0".repeat(683),
    );
    std::fs::write(tmp.path(), payload.as_bytes()).unwrap();

    let loaded = Settings::load(tmp.path()).unwrap();
    assert_eq!(loaded.daemon.log_level, "debug");
    assert_eq!(loaded.daemon.check_interval_ms, 1500);
    assert_eq!(loaded.daemon.default_mode, "fast");
}

#[test]
fn thermal_defaults_to_enabled() {
    assert!(ThermalConfig::default().enabled);
    let parsed: ThermalConfig = toml::from_str("").unwrap();
    assert!(parsed.enabled);
    assert_eq!(parsed.default, None);
}

#[test]
fn thermal_parses_explicit_default() {
    let parsed: ThermalConfig = toml::from_str("default = 20").unwrap();
    assert_eq!(parsed.default, Some(20));
}

#[test]
fn window_defaults_to_disabled_auto_rotation() {
    assert!(!WindowConfig::default().auto_rotation);
    let parsed: WindowConfig = toml::from_str("").unwrap();
    assert!(!parsed.auto_rotation);
    let parsed_enabled: WindowConfig = toml::from_str("auto_rotation = true").unwrap();
    assert!(parsed_enabled.auto_rotation);
}
