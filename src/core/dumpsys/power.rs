use crate::core::cmd::run_cmd_timeout_async;

#[derive(Debug, Default, Clone)]
pub struct PowerState {
    pub screen_awake: bool,
    pub battery_saver: bool,
}

impl PowerState {
    pub async fn fetch() -> anyhow::Result<PowerState> {
        let battery_saver_fast = get_battery_saver_fast().await;

        let out = match run_cmd_timeout_async("/system/bin/dumpsys", &["power"], 500).await {
            Ok(o) => o,
            Err(e) => {
                tracing::debug!(target: "auriya:power", "dumpsys power timeout: {:?}", e);
                return Ok(PowerState {
                    battery_saver: battery_saver_fast.unwrap_or(false),
                    ..Default::default()
                });
            }
        };

        let s = String::from_utf8_lossy(&out.stdout);

        let ps = PowerState {
            battery_saver: battery_saver_fast.unwrap_or_else(|| {
                s.contains("mBatterySaverEnabled=true")
                    || s.contains("mSettingBatterySaverEnabled=true")
                    || s.contains("Battery Saver: ON")
            }),
            screen_awake: s.contains("mWakefulness=Awake")
                || s.contains("mAwake=true")
                || s.contains("mInteractive=true")
                || s.contains("mScreenOn=true"),
        };

        Ok(ps)
    }
}
async fn get_battery_saver_fast() -> Option<bool> {
    let out = run_cmd_timeout_async(
        "/system/bin/cmd",
        &["settings", "get", "global", "low_power"],
        200,
    )
    .await
    .ok()?;

    let s = String::from_utf8_lossy(&out.stdout);
    let trimmed = s.trim();

    match trimmed {
        "1" => Some(true),
        "0" => Some(false),
        _ => None,
    }
}
