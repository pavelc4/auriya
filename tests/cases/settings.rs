use auriya::core::config::settings::{ThermalConfig, WindowConfig};

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
