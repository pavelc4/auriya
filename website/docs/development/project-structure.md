# Project Structure

```text
auriya/
├── src/                         [Rust] daemon library and binaries
│   ├── main.rs                  [Rust] daemon entry point
│   ├── ctl.rs                   [Rust] auriyactl entry point
│   ├── cli/                     [Rust] CLI parser/client/output
│   ├── daemon/                  [Rust] tick loop, state, watchers, IPC
│   ├── core/                    [Rust] config, telemetry, FPS/FAS, tweaks
│   └── common/                  [Rust] constants and shared types
├── android/                     [Android/Gradle]
│   ├── app/                     [Android] Compose manager UI
│   ├── service/                 [Android] headless companion service
│   └── shared/                  [Android] models and TOML/status codecs
├── module/                      [Module] bundled root-module payload source
│   ├── customize.sh             [Module] install, verify, and copy payload
│   ├── service.sh               [Module] boot startup
│   ├── uninstall.sh             [Module] cleanup
│   ├── module.prop              [Module] module metadata
│   └── META-INF/                [Module] recovery installer entry points
├── website/                     [Docusaurus] documentation site
│   ├── docs/                    [Docusaurus] wiki content
│   ├── src/                     [Docusaurus] theme and CSS
│   ├── static/                  [Docusaurus] fonts and media
│   ├── sidebars.ts              [Docusaurus] navigation tree
│   └── docusaurus.config.ts     [Docusaurus] site configuration
├── .github/
│   ├── workflows/               [CI/CD] build and release pipelines
│   └── actions/                 [CI/CD] setup, package, notifications
├── Cargo.toml                   [Rust] dependencies and binary targets
├── settings.toml               [Config] bundled default settings
├── gamelist.toml               [Config] bundled default game profiles
└── update.json                 [Release] root-manager update metadata
```

Root files include `Cargo.toml`/`Cargo.lock`, `settings.toml`, `gamelist.toml`, `update.json`, `README.md`, and `CHANGELOG.md`. `.github/actions/` contains reusable setup, packaging, and Telegram notification actions; `.github/workflows/build.yml` is manual artifact CI and `release.yml` is tag/manual release CI. `module/` contains Magisk/KernelSU/APatch metadata and lifecycle scripts.

The Android tree has three Gradle modules: `app` (UI), `service` (background integration), and `shared` (models/parsers). Generated `android/shared/bin/` mirrors shared Kotlin declarations for tooling and is not the source of truth.

## Bundled ZIP preview

```text
auriya-<version>-<commit-count>-<sha>-<build-type>.zip
├── customize.sh
├── service.sh
├── uninstall.sh
├── module.prop
├── settings.toml
├── gamelist.toml
├── libs/
│   ├── aarch64/
│   │   ├── auriya                [Rust daemon]
│   │   ├── auriyactl             [Rust CLI]
│   │   └── checksums.sha256
│   └── companion/
│       ├── service.apk           [Android companion]
│       └── auriya-app.apk        [Android Compose app]
└── META-INF/com/google/android/
    ├── update-binary
    └── updater-script
```

During installation, `libs/` is only a staging area. `customize.sh` copies binaries/APKs to their runtime paths, moves the TOML defaults to `/data/adb/.config/auriya` when no user config exists, then removes `libs/`.
