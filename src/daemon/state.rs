#[derive(Debug, Default, Clone)]
pub struct CurrentState {
    pub pkg: Option<String>,
    pub pid: Option<i32>,
    pub screen_awake: bool,
    pub battery_saver: bool,
}

#[derive(Debug, Default, Clone)]
pub struct LastState {
    pub pkg: Option<String>,
    pub pid: Option<i32>,
    pub screen_awake: Option<bool>,
    pub battery_saver: Option<bool>,
    pub last_log_ms: Option<u128>,
    pub profile_mode: Option<crate::core::profile::ProfileMode>,
}
