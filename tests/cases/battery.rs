use auriya::core::telemetry::battery::parse_temp_c;

#[test]
fn temp_deci_celsius_is_primary() {
    // Device's real reading: 402 deci-°C = 40.2 °C.
    assert_eq!(parse_temp_c(402), Some(40.2));
}

#[test]
fn temp_falls_back_to_milli_celsius() {
    // A milli-°C kernel: 40200 → deci gives 4020 (absurd) → retry milli.
    assert_eq!(parse_temp_c(40_200), Some(40.2));
}

#[test]
fn temp_rejects_garbage() {
    assert_eq!(parse_temp_c(999_999), None);
}
