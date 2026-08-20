// Battery telemetry via sysfs.
//
// Unlike DnD / refresh-rate (which must go through the companion because they
// need Android framework APIs), battery data lives in sysfs, so the root daemon
// reads it directly — same as CPU/GPU/thermal. Stateless: called on demand by
// the IPC `GET_STATS` handler, not on the per-tick hot path.
//
// Standard node: /sys/class/power_supply/battery/{capacity,temp,current_now,
// voltage_now,status,health}. Every field is best-effort — a missing or
// unparsable node degrades that field to `None`, never fails the snapshot.

use std::fs;

const BASE: &str = "/sys/class/power_supply/battery";

#[derive(Debug, Clone, Default)]
pub struct BatterySnapshot {
    /// Charge level, 0–100 %.
    pub pct: Option<u8>,
    /// Battery temperature in °C. Sits in the UI's *thermal* card, not battery.
    pub temp_c: Option<f32>,
    /// Instantaneous current in mA. Sign is **device-specific** (this SoC
    /// reports negative while charging); callers must use `status`, not the
    /// sign, to decide charge direction. Reported faithfully (signed) so no
    /// information is discarded.
    pub current_ma: Option<i32>,
    /// Terminal voltage in V (2 dp).
    pub voltage_v: Option<f32>,
    /// e.g. "Charging" / "Discharging" / "Full". Authoritative for direction.
    pub status: Option<String>,
    /// e.g. "Good" / "Overheat".
    pub health: Option<String>,
}

fn read_i32(node: &str) -> Option<i32> {
    fs::read_to_string(format!("{BASE}/{node}"))
        .ok()?
        .trim()
        .parse::<i32>()
        .ok()
}

fn read_str(node: &str) -> Option<String> {
    let s = fs::read_to_string(format!("{BASE}/{node}")).ok()?;
    let t = s.trim();
    if t.is_empty() {
        None
    } else {
        Some(t.to_string())
    }
}

/// Parse the raw `temp` node to °C.
///
/// The Linux power_supply ABI defines `temp` in tenths of a degree (deci-°C),
/// which this device confirms (402 → 40.2 °C). Some vendor kernels instead
/// report milli-°C, so if the deci-°C reading is out of a sane battery range we
/// retry as milli-°C.
// ponytail: deci-°C is the ABI standard; the milli-°C retry covers the common
// deviation. Non-conformant "plain integer °C" kernels are not handled — add a
// third branch only if a real device needs it.
fn parse_temp_c(raw: i32) -> Option<f32> {
    let deci = raw as f32 / 10.0;
    if (-20.0..=100.0).contains(&deci) {
        return Some(deci);
    }
    let milli = raw as f32 / 1000.0;
    if (-20.0..=100.0).contains(&milli) {
        return Some(milli);
    }
    None
}

pub fn snapshot() -> BatterySnapshot {
    let pct = read_i32("capacity").and_then(|v| u8::try_from(v.clamp(0, 100)).ok());
    let temp_c = read_i32("temp").and_then(parse_temp_c);
    // current_now / voltage_now are in µA / µV.
    let current_ma = read_i32("current_now").map(|ua| ua / 1000);
    let voltage_v =
        read_i32("voltage_now").map(|uv| (uv as f32 / 1_000_000.0 * 100.0).round() / 100.0);
    let status = read_str("status");
    let health = read_str("health");

    BatterySnapshot {
        pct,
        temp_c,
        current_ma,
        voltage_v,
        status,
        health,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

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
}
