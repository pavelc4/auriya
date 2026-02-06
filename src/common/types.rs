use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ProfileMode {
    Performance,
    Balance,
    Powersave,
}

impl ProfileMode {
    pub fn from_str_ignore_case(s: &str) -> Option<Self> {
        match s.to_lowercase().as_str() {
            "performance" => Some(Self::Performance),
            "balance" => Some(Self::Balance),
            "powersave" => Some(Self::Powersave),
            _ => None,
        }
    }

    pub fn to_upper_str(&self) -> &'static str {
        match self {
            Self::Performance => "PERFORMANCE",
            Self::Balance => "BALANCE",
            Self::Powersave => "POWERSAVE",
        }
    }
}

impl std::fmt::Display for ProfileMode {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Performance => write!(f, "performance"),
            Self::Balance => write!(f, "balance"),
            Self::Powersave => write!(f, "powersave"),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum LogLevel {
    Debug,
    Info,
    Warn,
    Error,
}

impl LogLevel {
    pub fn to_upper_str(&self) -> &'static str {
        match self {
            Self::Debug => "DEBUG",
            Self::Info => "INFO",
            Self::Warn => "WARN",
            Self::Error => "ERROR",
        }
    }
}

impl std::fmt::Display for LogLevel {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Debug => write!(f, "debug"),
            Self::Info => write!(f, "info"),
            Self::Warn => write!(f, "warn"),
            Self::Error => write!(f, "error"),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DaemonStatus {
    pub enabled: bool,
    pub profile: ProfileMode,
    pub packages: usize,
    pub fps: Option<u32>,
}
