use clap::ValueEnum;

#[derive(Clone, Copy, Debug, PartialEq, Eq, ValueEnum)]
pub enum ProfileMode {
    Performance,
    Balance,
    Powersave,
    Fast,
}

impl ProfileMode {
    pub fn to_upper_str(&self) -> &'static str {
        match self {
            Self::Performance => "PERFORMANCE",
            Self::Balance => "BALANCE",
            Self::Powersave => "POWERSAVE",
            Self::Fast => "FAST",
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, ValueEnum)]
pub enum LogLevel {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
}

impl LogLevel {
    pub fn to_upper_str(&self) -> &'static str {
        match self {
            Self::Trace => "TRACE",
            Self::Debug => "DEBUG",
            Self::Info => "INFO",
            Self::Warn => "WARN",
            Self::Error => "ERROR",
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, ValueEnum)]
pub enum CliDndMode {
    All,
    Priority,
}

impl From<CliDndMode> for crate::platform::binder::DndMode {
    fn from(mode: CliDndMode) -> Self {
        match mode {
            CliDndMode::All => Self::All,
            CliDndMode::Priority => Self::Priority,
        }
    }
}
