pub use super::gamelist::*;
pub use super::settings::*;
use std::path::PathBuf;

pub const CONFIG_DIR: &str = "/data/adb/.config/auriya";

pub fn settings_path() -> PathBuf {
    PathBuf::from(CONFIG_DIR).join("settings.toml")
}

pub fn gamelist_path() -> PathBuf {
    PathBuf::from(CONFIG_DIR).join("gamelist.toml")
}

pub fn blacklist_path() -> PathBuf {
    PathBuf::from(CONFIG_DIR).join("blacklist.toml")
}

/// Persisted stock thermal preset (the value the vendor shipped, captured
/// once at first daemon start). Restored when leaving a game session.
pub fn thermal_default_path() -> PathBuf {
    PathBuf::from(CONFIG_DIR).join("thermal_default")
}

/// Strips a leading UTF-8 BOM and stray NUL bytes (for example from F2FS
/// unclean-shutdown padding), logging a warning when NUL bytes were present.
pub fn sanitize_toml_content<'a>(
    path: &std::path::Path,
    raw: &'a str,
) -> std::borrow::Cow<'a, str> {
    let without_bom = raw.strip_prefix('\u{FEFF}').unwrap_or(raw);
    if without_bom.contains('\0') {
        tracing::warn!(
            target: "auriya::daemon",
            "{} contains NUL bytes; sanitizing before TOML parse",
            path.display()
        );
        std::borrow::Cow::Owned(without_bom.replace('\0', ""))
    } else {
        std::borrow::Cow::Borrowed(without_bom)
    }
}

pub fn load_all() -> anyhow::Result<(crate::core::config::Settings, crate::core::config::GameList)>
{
    load_all_from(&settings_path(), &gamelist_path(), &blacklist_path())
}

pub fn load_all_from(
    settings_file: &std::path::Path,
    gamelist_file: &std::path::Path,
    blacklist_file: &std::path::Path,
) -> anyhow::Result<(crate::core::config::Settings, crate::core::config::GameList)> {
    let settings = crate::core::config::Settings::load(settings_file)?;
    let gamelist = match crate::core::config::GameList::load(gamelist_file) {
        Ok(list) => list,
        Err(e) => {
            tracing::error!(
                "Failed to load {}: {e:#}. Recovering with empty gamelist.",
                gamelist_file.display()
            );
            crate::core::config::GameList::default()
        }
    };
    crate::core::config::load_custom_blacklist(blacklist_file);
    Ok((settings, gamelist))
}
