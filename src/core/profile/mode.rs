#[derive(Debug, PartialEq, Eq, Clone, Copy, Default)]
pub enum ProfileMode {
    Performance,
    #[default]
    Balance,
    Powersave,
    Fast,
}

impl std::fmt::Display for ProfileMode {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ProfileMode::Performance => write!(f, "Performance"),
            ProfileMode::Balance => write!(f, "Balance"),
            ProfileMode::Powersave => write!(f, "Powersave"),
            ProfileMode::Fast => write!(f, "Fast"),
        }
    }
}

impl std::str::FromStr for ProfileMode {
    type Err = ();
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.to_lowercase().as_str() {
            "performance" | "1" => Ok(ProfileMode::Performance),
            "balance" | "2" => Ok(ProfileMode::Balance),
            "powersave" | "3" => Ok(ProfileMode::Powersave),
            "fast" | "4" => Ok(ProfileMode::Fast),
            _ => Err(()),
        }
    }
}
