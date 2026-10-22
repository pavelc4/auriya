use auriya::core::config::settings::ThermalConfig;

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
