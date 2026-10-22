use auriya::core::config::gamelist::{GameList, GameProfile, GameProfileUpdate, TargetFpsConfig};

#[test]
fn test_gamelist_add_find_and_update() {
    let mut gl = GameList { game: Vec::new() };

    let p = GameProfile {
        package: "com.example.game".to_string(),
        cpu_governor: "schedutil".to_string(),
        enable_dnd: false,
        target_fps: None,
        refresh_rate: None,
        mode: None,
        ceiling: None,
        thermal_preset: None,
    };

    assert!(gl.add(p).is_ok());
    assert_eq!(gl.game.len(), 1);

    // Duplicate add should fail
    let p_dup = GameProfile {
        package: "com.example.game".to_string(),
        cpu_governor: "performance".to_string(),
        enable_dnd: true,
        target_fps: None,
        refresh_rate: None,
        mode: None,
        ceiling: None,
        thermal_preset: None,
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
        ceiling: Some("high".to_string()),
    };
    assert!(gl.update("com.example.game", upd).is_ok());

    let found = gl.find("com.example.game").unwrap();
    assert_eq!(found.cpu_governor, "performance");
    assert!(found.enable_dnd);
    assert_eq!(found.refresh_rate, Some(120));
    assert_eq!(found.mode.as_deref(), Some("fast"));
    assert_eq!(found.ceiling.as_deref(), Some("high"));
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
            package: "com.test".to_string(),
            cpu_governor: "performance".to_string(),
            enable_dnd: true,
            target_fps: None,
            refresh_rate: None,
            mode: None,
            ceiling: None,
            thermal_preset: None,
        }],
    };

    assert!(gl.remove("com.test").is_ok());
    assert!(gl.game.is_empty());
    assert!(gl.remove("com.test").is_err());
}
