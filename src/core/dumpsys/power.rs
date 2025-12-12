use crate::core::cmd::run_cmd_timeout_async;

#[derive(Debug, Default, Clone)]
pub struct PowerState {
    pub screen_awake: bool,
    pub is_plugged_in: bool,
    pub battery_saver: bool,
    pub battery_saver_sticky: bool,
}

impl PowerState {
    pub async fn fetch() -> anyhow::Result<PowerState> {
        let out = match run_cmd_timeout_async("/system/bin/dumpsys", &["power"], 1500).await {
            Ok(o) => o,
            Err(e) => {
                tracing::debug!(target: "auriya:power", "dumpsys power timeout: {:?}", e);
                return Ok(PowerState::default());
            }
        };

        let s = String::from_utf8_lossy(&out.stdout);
        let mut ps = PowerState::default();

        if s.contains("mWakefulness=Awake")
            || s.contains("mAwake=true")
            || s.contains("mInteractive=true")
            || s.contains("mScreenOn=true")
        {
            ps.screen_awake = true;
        }

        if s.contains("mBatterySaverEnabled=true")
            || s.contains("mBatterySaverState=ON")
            || s.contains("Battery Saver: ON")
        {
            ps.battery_saver = true;
        }

        if s.contains("mBatterySaverSticky=true") || s.contains("mBatterySaverStickyEnabled=true") {
            ps.battery_saver_sticky = true;
        }

        if s.contains("mIsPowered=true")
            || s.contains("plugged=true")
            || (s.contains("Plug Type:") && !s.contains("Plug Type: NONE"))
        {
            ps.is_plugged_in = true;
        }

        Ok(ps)
    }
}
