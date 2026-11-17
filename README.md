<p align="center">
  <img src="doc/asset/logo.png" width="160" height="160" alt="Auriya Logo">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Rust-000000?style=for-the-badge&logo=rust&logoColor=white" alt="Rust">
  <a href="https://auriya.pages.dev"><img src="https://img.shields.io/badge/Docs-auriya-FFB2B9?style=for-the-badge&logo=cloudflarepages&logoColor=black&labelColor=222" alt="Documentation"></a>
  <a href="https://github.com/pavelc4/auriya/releases"><img src="https://img.shields.io/github/v/release/pavelc4/auriya?label=Release&style=for-the-badge&logo=github&logoColor=white&labelColor=222" alt="Latest Release"></a>
  <a href="https://crowdin.com/project/auriya"><img src="https://img.shields.io/badge/Crowdin-Translations-white?style=for-the-badge&logo=crowdin&logoColor=white&labelColor=222" alt="Crowdin Translations"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/GPLv3-white?style=for-the-badge&logo=gnu&logoColor=white&label=License&labelColor=222" alt="License"></a>
</p>

## About Auriya
Auriya is an Android performance optimization daemon written in Rust, with a Jetpack Compose manager app and native Binder IPC.

Documentation is available at [auriya.pages.dev](https://auriya.pages.dev).

> [!NOTE]
> This module is an experimental project for rooted devices. Features and tuning behaviors may change between releases.

## Features
- Native Rust daemon for system tuning and profile management
- Dynamic frame-aware scheduling based on real-time frametimes
- Per-game profiles for target FPS, refresh rates, and automated Do Not Disturb
- Telemetry and session recording for FPS, temperature, and battery metrics
- Compose app with an optional floating HUD overlay
- `auriyactl` CLI for terminal monitoring and profile switching

## Screenshots
| Home | Games | Tuning |
| :---: | :---: | :---: |
| ![Home Dashboard](doc/asset/home.png) | ![Games List](doc/asset/game.png) | ![Game Tuning](doc/asset/tuing.png) |

| Telemetry | Benchmark | Floating HUD |
| :---: | :---: | :---: |
| ![Live Telemetry](doc/asset/fps.png) | ![Benchmark Results](doc/asset/fps-result.png) | ![Floating HUD](doc/asset/floating.png) |

| Domain Config | Customization | Settings |
| :---: | :---: | :---: |
| ![Domain Config](doc/asset/config.png) | ![Customization](doc/asset/cusztomi.png) | ![Settings](doc/asset/settings.png) |

## Documentation
- [auriyactl CLI Reference](doc/auriyactl.md)
- [Installation Guide](https://auriya.pages.dev/getting-started/installation/)
- [Configuration and Profiles](https://auriya.pages.dev/getting-started/configuration/)
- [Telemetry Protocol and FPS Benchmark API](https://auriya.pages.dev/reference/stats-api/)
- [Architecture Overview](https://auriya.pages.dev/architecture/overview/)

## Resources
- [Releases](https://github.com/pavelc4/auriya/releases)
- [Issue Tracker](https://github.com/pavelc4/auriya/issues)

## Translation
Auriya translations are managed on [Crowdin](https://crowdin.com/project/auriya). Contributions for existing and new languages are welcome.


## Support
If you find Auriya useful, consider starring the repository on GitHub.

## License
Auriya is open-source software licensed under [GPLv3](LICENSE).
