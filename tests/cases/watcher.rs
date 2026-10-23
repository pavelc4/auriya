use auriya::core::config::GameList;
use auriya::daemon::watcher::{is_ignorable_file, reload_gamelist_with_retry};
use std::fs;
use std::sync::{Arc, RwLock};
use tempfile::NamedTempFile;

#[test]
fn test_is_ignorable_file() {
    assert!(is_ignorable_file("/path/to/gamelist.toml.tmp"));
    assert!(is_ignorable_file("/path/to/gamelist.toml~"));
    assert!(is_ignorable_file("/path/to/.gamelist.toml.swp"));
    assert!(!is_ignorable_file("/path/to/gamelist.toml"));
    assert!(!is_ignorable_file("/path/to/settings.toml"));
}

#[test]
fn test_reload_gamelist_with_retry_success() {
    let tmp = NamedTempFile::new().unwrap();
    let toml_content = r#"
[[game]]
package = "com.mobile.legends"
cpu_governor = "performance"
enable_dnd = true
"#;
    fs::write(tmp.path(), toml_content).unwrap();

    let shared = Arc::new(RwLock::new(Arc::new(GameList { game: Vec::new() })));
    let ok = reload_gamelist_with_retry(tmp.path(), &shared);
    assert!(ok);

    let g = shared.read().unwrap();
    assert_eq!(g.game.len(), 1);
    assert_eq!(g.game[0].package, "com.mobile.legends");
}
