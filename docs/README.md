# Auriya

Android daemon for frame-aware scheduling and per-application configuration.

## Table of Contents

- [Purpose](#purpose)
- [Key Features](#key-features)
- [Use Cases](#use-cases)
- [Architecture](#architecture)
- [Installation](#installation)
- [Configuration](#configuration)
- [Usage](#usage)
- [License](#license)

## Purpose

Auriya monitors foreground applications and applies CPU/GPU settings based on a configurable game whitelist. The daemon switches between profiles when games are detected or when screen state changes.

## Features

- **Frame-Aware Scheduling (FAS)**: Adjusts CPU scaling based on frame rate
- **Per-App Profiles**: CPU governor, target FPS, and refresh rate per application
- **Game Detection**: Whitelist-based foreground monitoring via dumpsys
- **Screen State Handling**: Profile switching on screen on/off
- **Hot-Reload**: Configuration changes apply without restart
- **WebUI**: WebView-based configuration panel
- **IPC Socket**: Unix socket for external control

## Use Cases

- Per-game CPU governor and refresh rate configuration
- Automatic profile switching based on foreground app
- Do-Not-Disturb toggle during game
- Screen-off power saving

## Quick Start

1. Install the module via Magisk, KernelSU, or APatch
2. Reboot the device
3. Access WebUI at `Webui Apps or Kernelsu Manager`
4. Add games to the whitelist
5. Launch a whitelisted game to activate performance mode

## Documentation

| Document                            | Description                                        |
| ----------------------------------- | -------------------------------------------------- |
| [Architecture](./architecture.md)   | System design, component diagrams, and data flow   |
| [Configuration](./configuration.md) | Complete settings.toml and gamelist.toml reference |
| [IPC Reference](./ipc-reference.md) | IPC commands and protocol documentation            |

## File Locations

| Path                                       | Description                         |
| ------------------------------------------ | ----------------------------------- |
| `/system/bin/auriya`                       | Daemon binary                       |
| `/data/adb/.config/auriya/settings.toml`   | Global configuration                |
| `/data/adb/.config/auriya/gamelist.toml`   | Game whitelist and per-app settings |
| `/dev/socket/auriya.sock`                  | IPC Unix socket                     |
| `/data/adb/.config/auriya/current_profile` | Active profile indicator            |

## Requirements

- Android 9.0 or higher
- Root access via Magisk, KernelSU, or APatch
- ARM64 architecture

## License

MIT License - See [LICENSE](../LICENSE) for details.
