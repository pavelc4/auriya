use std::str::FromStr;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LogLevelCmd {
    Debug,
    Info,
    Warn,
    Error,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProfileMode {
    Performance,
    Balance,
    Powersave,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Command {
    Help,
    Status,
    Enable,
    Disable,
    Reload,
    Restart,
    SetLog(LogLevelCmd),
    Inject(String),
    ClearInject,
    GetPid,
    Ping,
    Quit,
    SetProfile(ProfileMode),
    AddGame(String),
    RemoveGame(String),
    ListPackages,
    GetGameList,
    UpdateGame(
        String,
        Option<String>,
        Option<bool>,
        Option<u32>,
        Option<u32>,
        Option<String>,
        Option<Vec<u32>>,
    ),
    SetFps(u32),
    GetFps,
    GetSupportedRates,
}

impl FromStr for Command {
    type Err = &'static str;
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let parts: Vec<&str> = s.split_whitespace().collect();
        match parts.as_slice() {
            ["HELP"] | ["?"] => Ok(Command::Help),
            ["STATUS"] => Ok(Command::Status),
            ["ENABLE"] => Ok(Command::Enable),
            ["DISABLE"] => Ok(Command::Disable),
            ["RELOAD"] => Ok(Command::Reload),
            ["GETPID"] | ["GET_PID"] => Ok(Command::GetPid),
            ["PING"] => Ok(Command::Ping),
            ["QUIT"] => Ok(Command::Quit),
            ["RESTART"] => Ok(Command::Restart),
            ["LIST_PACKAGES"] | ["LISTPACKAGES"] => Ok(Command::ListPackages),
            ["GET_GAMELIST"] | ["GETGAMELIST"] => Ok(Command::GetGameList),

            ["SETLOG", level] | ["SET_LOG", level] => match level.to_uppercase().as_str() {
                "DEBUG" => Ok(Command::SetLog(LogLevelCmd::Debug)),
                "INFO" => Ok(Command::SetLog(LogLevelCmd::Info)),
                "WARN" => Ok(Command::SetLog(LogLevelCmd::Warn)),
                "ERROR" => Ok(Command::SetLog(LogLevelCmd::Error)),
                _ => Err("usage: SETLOG <DEBUG|INFO|WARN|ERROR>"),
            },

            ["SET_FPS", fps] | ["SETFPS", fps] => match fps.parse::<u32>() {
                Ok(val) => Ok(Command::SetFps(val)),
                Err(_) => Err("usage: SET_FPS <number>"),
            },

            ["GET_FPS"] | ["GETFPS"] => Ok(Command::GetFps),
            ["GET_SUPPORTED_RATES"] | ["GETRATES"] => Ok(Command::GetSupportedRates),

            ["INJECT", pkg] => Ok(Command::Inject(pkg.to_string())),
            ["CLEAR_INJECT"] | ["CLEARINJECT"] => Ok(Command::ClearInject),

            ["SET_PROFILE", mode] | ["SETPROFILE", mode] => match mode.to_uppercase().as_str() {
                "PERFORMANCE" => Ok(Command::SetProfile(ProfileMode::Performance)),
                "BALANCE" => Ok(Command::SetProfile(ProfileMode::Balance)),
                "POWERSAVE" => Ok(Command::SetProfile(ProfileMode::Powersave)),
                _ => Err("usage: SETPROFILE <PERFORMANCE|BALANCE|POWERSAVE>"),
            },

            ["ADD_GAME", pkg] | ["ADDGAME", pkg] => Ok(Command::AddGame(pkg.to_string())),
            ["REMOVE_GAME", pkg] | ["REMOVEGAME", pkg] => Ok(Command::RemoveGame(pkg.to_string())),
            ["UPDATE_GAME", pkg, rest @ ..] | ["UPDATEGAME", pkg, rest @ ..] => {
                let mut governor = None;
                let mut dnd = None;
                let mut target_fps = None;
                let mut refresh_rate = None;
                let mut mode = None;
                let mut fps_array = None;

                for arg in rest {
                    if let Some(gov) = arg.strip_prefix("gov=") {
                        governor = Some(gov.to_string());
                    } else if let Some(dnd_val) = arg.strip_prefix("dnd=") {
                        dnd = Some(dnd_val.parse::<bool>().unwrap_or(true));
                    } else if let Some(fps_val) = arg.strip_prefix("fps=") {
                        target_fps = fps_val.parse::<u32>().ok();
                    } else if let Some(arr_val) = arg.strip_prefix("fps_array=") {
                        let arr: Vec<u32> = arr_val
                            .split(',')
                            .filter_map(|s| s.trim().parse().ok())
                            .collect();
                        if !arr.is_empty() {
                            fps_array = Some(arr);
                        }
                    } else if let Some(rate_val) = arg.strip_prefix("rate=") {
                        refresh_rate = rate_val.parse::<u32>().ok();
                    } else if let Some(mode_val) = arg.strip_prefix("mode=") {
                        mode = Some(mode_val.to_string());
                    }
                }

                Ok(Command::UpdateGame(
                    pkg.to_string(),
                    governor,
                    dnd,
                    target_fps,
                    refresh_rate,
                    mode,
                    fps_array,
                ))
            }

            _ => Err("unknown command (try HELP)"),
        }
    }
}
