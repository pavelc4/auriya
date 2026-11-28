use auriya::core::config::gamelist::{GameList, GameProfile, GameProfileUpdate, TargetFpsConfig};

#[test]
fn test_gamelist_add_find_and_update() {
    let mut gl = GameList { game: Vec::new() };

    let p = GameProfile {
        package: "com.mobile.legends".to_string(),
        cpu_governor: "schedutil".to_string(),
        enable_dnd: false,
        target_fps: None,
        refresh_rate: None,
        mode: None,
        thermal_preset: None,
        wifi_lock: true,
        statusbar_lock: true,
        touch_boost: true,
        power_hal: true,
        silence_vibrator: false,
        freeze_rotation: false,
    };

    assert!(gl.add(p).is_ok());
    assert_eq!(gl.game.len(), 1);

    // Duplicate add should fail
    let p_dup = GameProfile {
        package: "com.mobile.legends".to_string(),
        cpu_governor: "performance".to_string(),
        enable_dnd: true,
        target_fps: None,
        refresh_rate: None,
        mode: None,
        thermal_preset: None,
        wifi_lock: true,
        statusbar_lock: true,
        touch_boost: true,
        power_hal: true,
        silence_vibrator: false,
        freeze_rotation: false,
    };
    assert!(gl.add(p_dup).is_err());

    // Update existing game
    let upd = GameProfileUpdate {
        governor: Some("performance".to_string()),
        dnd: Some(true),
        target_fps: Some(90),
        refresh_rate: Some(120),
        mode: Some("fast".to_string()),
        fps_array: None,
        thermal_preset: None,
        wifi_lock: Some(false),
        statusbar_lock: Some(true),
        touch_boost: Some(true),
        power_hal: Some(false),
        silence_vibrator: Some(true),
        freeze_rotation: Some(true),
    };
    assert!(gl.update("com.mobile.legends", upd).is_ok());

    let found = gl.find("com.mobile.legends").unwrap();
    assert_eq!(found.cpu_governor, "performance");
    assert!(found.enable_dnd);
    assert_eq!(found.refresh_rate, Some(120));
    assert_eq!(found.mode.as_deref(), Some("fast"));
    assert!(!found.wifi_lock);
    assert!(found.silence_vibrator);
    assert!(found.freeze_rotation);
    match found.target_fps {
        Some(TargetFpsConfig::Single(fps)) => assert_eq!(fps, 90),
        _ => panic!("expected Single(90)"),
    }
}

#[test]
fn test_gamelist_update_nonexistent_returns_err() {
    let mut gl = GameList { game: Vec::new() };
    let upd = GameProfileUpdate::default();
    assert!(gl.update("com.nonexistent.app", upd).is_err());
}

#[test]
fn test_gamelist_remove() {
    let mut gl = GameList {
        game: vec![GameProfile {
            package: "com.tencent.ig".to_string(),
            cpu_governor: "performance".to_string(),
            enable_dnd: true,
            target_fps: None,
            refresh_rate: None,
            mode: None,
            thermal_preset: None,
            wifi_lock: true,
            statusbar_lock: true,
            touch_boost: true,
            power_hal: true,
            silence_vibrator: false,
            freeze_rotation: false,
        }],
    };

    assert!(gl.remove("com.tencent.ig").is_ok());
    assert!(gl.game.is_empty());
    assert!(gl.remove("com.tencent.ig").is_err());
}

#[test]
fn test_gamelist_load_strips_leading_nul_bytes_and_bom() {
    let tmp = tempfile::NamedTempFile::new().unwrap();
    let payload = format!(
        "{}\u{FEFF}[[game]]\npackage = \"com.mobile.legends\"\ncpu_governor = \"performance\"\nenable_dnd = true\ntarget_fps = 120\n",
        "\0".repeat(10),
    );
    std::fs::write(tmp.path(), payload.as_bytes()).unwrap();

    let loaded = GameList::load(tmp.path()).expect("NUL-padded gamelist.toml should recover");
    assert_eq!(loaded.game.len(), 1);
    assert_eq!(loaded.game[0].package, "com.mobile.legends");
    assert_eq!(loaded.game[0].cpu_governor, "performance");
}

#[test]
fn test_load_all_from_recovers_on_corrupted_gamelist() {
    let dir = tempfile::tempdir().unwrap();
    let settings_path = dir.path().join("settings.toml");
    let gamelist_path = dir.path().join("gamelist.toml");
    let blacklist_path = dir.path().join("blacklist.toml");

    std::fs::write(&settings_path, "[daemon]\ndefault_mode = \"fast\"\n").unwrap();
    std::fs::write(
        &gamelist_path,
        "[[game\nbroken syntax without closing bracket",
    )
    .unwrap();

    let (settings, gamelist) =
        auriya::core::config::load_all_from(&settings_path, &gamelist_path, &blacklist_path)
            .expect("daemon boot must recover with empty gamelist on broken gamelist.toml");
    assert_eq!(settings.daemon.default_mode, "fast");
    assert!(gamelist.game.is_empty());
}
