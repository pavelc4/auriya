<p align="center">
<img src="https://img.shields.io/badge/Rust-000000?style=for-the-badge&logo=rust&logoColor=white" alt="Rust">
<a href="https://auriya.pages.dev"><img src="https://img.shields.io/badge/Docs-auriya.pages.dev-FFB2B9?style=for-the-badge&logo=cloudflarepages&logoColor=black&labelColor=222" alt="Documentation"></a>
<a href="https://github.com/pavelc4/auriya/releases"><img src="https://img.shields.io/github/v/release/pavelc4/auriya?label=Release&style=for-the-badge&logo=github&logoColor=white&labelColor=222" alt="Latest Release"></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/GPLv3-white?style=for-the-badge&logo=gnu&logoColor=white&label=License&labelColor=222" alt="License"></a>
</p>

## About Auriya
**Auriya** is a rooted Android performance optimization daemon written in Rust, paired with a modern Jetpack Compose manager application and companion service.

Visit the [Official Documentation & Wiki](https://auriya.pages.dev) *(available in English and Bahasa Indonesia)* for detailed guides and configuration references.

## Features
- **Rust Native Daemon** — Runs in the background to handle system tweaks, performance tuning, and profile switching.
- **Dynamic Frame-Aware Scheduling** — Automatically boosts CPU performance when frames drop and saves battery when gameplay is smooth.
- **Per-Game Custom Profiles** — Set custom target FPS, screen refresh rate, CPU modes, and auto Do Not Disturb for each game.
- **Live Benchmark & FPS Recording** — Monitor real-time FPS, temperature, and battery drain, with session recording to review gaming stability.
- **Manager App & Floating Overlay** — Easy-to-use Android app to customize settings, plus a floating on-screen HUD to view live FPS while playing.
- **Command-Line Tools (`auriyactl`)** — Quick terminal commands to check status and switch profiles on the fly.

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
Comprehensive architectural deep-dives, configuration guides, and references are available on the documentation website:
- [Getting Started & Installation](https://auriya.pages.dev/getting-started/installation/)
- [Configuration & Profiles](https://auriya.pages.dev/getting-started/configuration/)
- [Telemetry Protocol & FPS Benchmark API](https://auriya.pages.dev/reference/stats-api/)
- [Architecture & Data Flow](https://auriya.pages.dev/architecture/overview/)
- [Dokumentasi Bahasa Indonesia](https://auriya.pages.dev/id/)

## Supported Root Managers
- [Magisk](https://github.com/topjohnwu/Magisk)
- [KernelSU](https://github.com/tiann/KernelSU)
- [APatch](https://github.com/bmax121/APatch)

## Resources
- [Documentation Website](https://auriya.pages.dev)
- [Releases](https://github.com/pavelc4/auriya/releases) - Download latest flashable ZIP
- [Changelog](CHANGELOG.md) - Version history
- [Issues](https://github.com/pavelc4/auriya/issues) - Report bugs

## Support 🌸
If you find Auriya useful for your device, please consider giving the repository a star ⭐ on GitHub to support the project.

## License
Auriya is open-sourced software licensed under the [GNU General Public License v3.0](LICENSE).
