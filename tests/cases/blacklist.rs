use auriya::core::config::blacklist::{
    BlacklistConfig, is_tamper_sensitive, load_custom_blacklist,
};

#[test]
fn test_blacklist_matching() {
    let toml_str = r#"
        packages = [
            "com.bcadigital.blu",
            "com.bca"
        ]
    "#;
    let temp_file = tempfile::NamedTempFile::new().unwrap();
    std::fs::write(temp_file.path(), toml_str).unwrap();

    load_custom_blacklist(temp_file.path());

    assert!(is_tamper_sensitive("com.bcadigital.blu"));
    assert!(is_tamper_sensitive("com.bcadigital.blu.core"));
    assert!(is_tamper_sensitive("com.bca"));
    assert!(!is_tamper_sensitive("com.dts.freefireth"));
    assert!(!is_tamper_sensitive("com.mobile.legends"));
}

#[test]
fn test_toml_parsing() {
    let toml_str = r#"
        packages = [
            "com.custom.bank",
            "org.secret.app"
        ]
    "#;
    let cfg: BlacklistConfig = toml::from_str(toml_str).unwrap();
    assert_eq!(cfg.packages, vec!["com.custom.bank", "org.secret.app"]);

    let toml_str_section = r#"
        [blacklist]
        packages = [
            "com.section.bank"
        ]
    "#;
    let cfg2: BlacklistConfig = toml::from_str(toml_str_section).unwrap();
    assert_eq!(cfg2.blacklist.unwrap().packages, vec!["com.section.bank"]);
}
