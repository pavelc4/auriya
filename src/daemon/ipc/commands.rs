use super::types::{Command, LogLevelCmd, ProfileMode};
use crate::core::config::gamelist::GameProfileUpdate;
use std::str::FromStr;

pub const HELP: &str = "CMDS:
        - HELP | ?
        - STATUS
        - ENABLE | DISABLE
        - RELOAD
        - SETLOG <DEBUG|INFO|WARN|ERROR>
        - INJECT <pkg>
        - CLEAR_INJECT
        - GETPID
        - PING
        - GET_STATS
        - STREAM_STATS [interval_ms]
        - QUIT
        - SET_PROFILE <PERFORMANCE|BALANCE|POWERSAVE|FAST>
        - SET_GOVERNOR <governor>
        - ADD_GAME <pkg>
        - REMOVE_GAME <pkg>
        - UPDATE_GAME <pkg> [gov=..] [dnd=..] [fps=..] [rate=..] [mode=..] [ceiling=..] [fps_array=..]
";

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
                "TRACE" => Ok(Command::SetLog(LogLevelCmd::Trace)),
                "DEBUG" => Ok(Command::SetLog(LogLevelCmd::Debug)),
                "INFO" => Ok(Command::SetLog(LogLevelCmd::Info)),
                "WARN" => Ok(Command::SetLog(LogLevelCmd::Warn)),
                "ERROR" => Ok(Command::SetLog(LogLevelCmd::Error)),
                _ => Err("usage: SETLOG <TRACE|DEBUG|INFO|WARN|ERROR>"),
            },

            ["SET_FPS", fps] | ["SETFPS", fps] => fps
                .parse::<u32>()
                .map(Command::SetFps)
                .map_err(|_| "usage: SET_FPS <number>"),

            ["GET_FPS"] | ["GETFPS"] => Ok(Command::GetFps),
            ["GET_SUPPORTED_RATES"] | ["GETRATES"] => Ok(Command::GetSupportedRates),
            ["GET_STATS"] | ["GETSTATS"] => Ok(Command::GetStats),
            ["STREAM_STATS", interval] | ["STREAMSTATS", interval] => {
                let ms = interval.parse::<u64>().unwrap_or(1000).clamp(200, 5000);
                Ok(Command::StreamStats(ms))
            }
            ["STREAM_STATS"] | ["STREAMSTATS"] => Ok(Command::StreamStats(1000)),

            ["INJECT", pkg] => Ok(Command::Inject(pkg.to_string())),
            ["CLEAR_INJECT"] | ["CLEARINJECT"] => Ok(Command::ClearInject),

            ["SET_PROFILE", mode] | ["SETPROFILE", mode] => match mode.to_uppercase().as_str() {
                "PERFORMANCE" | "1" => Ok(Command::SetProfile(ProfileMode::Performance)),
                "BALANCE" | "2" => Ok(Command::SetProfile(ProfileMode::Balance)),
                "POWERSAVE" | "3" => Ok(Command::SetProfile(ProfileMode::Powersave)),
                "FAST" | "4" => Ok(Command::SetProfile(ProfileMode::Fast)),
                _ => Err("usage: SETPROFILE <PERFORMANCE|BALANCE|POWERSAVE|FAST>"),
            },

            ["SET_GOVERNOR", gov] | ["SETGOVERNOR", gov] => {
                Ok(Command::SetGovernor(gov.to_string()))
            }

            ["ADD_GAME", pkg] | ["ADDGAME", pkg] => Ok(Command::AddGame(pkg.to_string())),
            ["REMOVE_GAME", pkg] | ["REMOVEGAME", pkg] => Ok(Command::RemoveGame(pkg.to_string())),
            ["UPDATE_GAME", pkg, rest @ ..] | ["UPDATEGAME", pkg, rest @ ..] => {
                let mut update = GameProfileUpdate::default();
                for arg in rest {
                    match arg.split_once('=') {
                        Some(("gov", v)) => update.governor = Some(v.to_string()),
                        Some(("dnd", v)) => update.dnd = v.parse::<bool>().ok(),
                        Some(("fps", v)) => update.target_fps = v.parse::<u32>().ok(),
                        Some(("fps_array", v)) => {
                            let arr: Vec<u32> =
                                v.split(',').filter_map(|s| s.trim().parse().ok()).collect();
                            if !arr.is_empty() {
                                update.fps_array = Some(arr);
                            }
                        }
                        Some(("rate", v)) => update.refresh_rate = v.parse::<u32>().ok(),
                        Some(("mode", v)) => update.mode = Some(v.to_string()),
                        Some(("ceiling", v)) => update.ceiling = Some(v.to_string()),
                        _ => {}
                    }
                }

                Ok(Command::UpdateGame {
                    pkg: pkg.to_string(),
                    update: Box::new(update),
                })
            }

            _ => Err("unknown command (try HELP)"),
        }
    }
}
