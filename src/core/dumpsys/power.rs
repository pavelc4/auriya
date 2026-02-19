use crate::core::cmd::run_cmd_timeout_async;
use memchr::memmem;

#[derive(Debug, Default, Clone)]
pub struct PowerState {
    pub screen_awake: bool,
    pub battery_saver: bool,
}

impl PowerState {
    pub async fn fetch() -> anyhow::Result<PowerState> {
        let out = match run_cmd_timeout_async("/system/bin/dumpsys", &["power"], 500).await {
            Ok(o) => o,
            Err(e) => {
                tracing::debug!(target: "auriya:power", "dumpsys power timeout: {:?}", e);
                return Ok(PowerState::default());
            }
        };
        Ok(Self::parse_zerocopy(&out.stdout))
    }

    fn parse_zerocopy(data: &[u8]) -> PowerState {
        let awake_finder = memmem::Finder::new(b"mWakefulness=Awake");
        let batt_finder = memmem::Finder::new(b"mBatterySaverEnabled=true");

        PowerState {
            screen_awake: awake_finder.find(data).is_some(),
            battery_saver: batt_finder.find(data).is_some(),
        }
    }
}
