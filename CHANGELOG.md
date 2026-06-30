# Changelog

## v2.0.0

### New Features

*   **Native Jetpack Compose UI**: Replaced the Svelte WebUI with a native Android application built with Jetpack Compose and Material 3 `[3544041]`, `[5de50c2]`, `[2627857]`.
*   **Quick Settings Profile Switcher**: Added a Quick Settings tile to cycle performance profiles directly from the status bar, featuring dynamic icons (🎮/⚙️/🔋) and instant toast feedback `[71349df]`.
*   **Game Launch Toast**: Triggers an Android Toast notification whenever a registered game starts and tweaks are applied `[71349df]`.
*   **Draggable HUD Overlay**: Implemented drag gestures for the floating telemetry HUD and persisted its screen coordinates `[009368a]`.
*   **Customization Options**: Added RAM telemetry, split-circle color presets, custom hex color picker, minimalist HUD layout, Montserrat typography, and AMOLED dark mode `[7a537b2]`, `[ac72068]`, `[583fccc]`.
*   **Jetpack Glance App Widget**: Implemented a responsive Material 3 dashboard app widget displaying system metrics `[1bee126]`.
*   **Ceiling Power Caps**: Added a ceiling CPU power cap override per-game with Material 3 Expressive dropdown selectors `[d86f42b]`, `[de6e4d8]`.
*   **Heartbeat & Liveness Watcher**: Added an fcntl lock-based liveness watcher on `companion.lock` to instantly auto-restart the companion if it dies `[12841f6]`, `[f9c0407]`.
*   **Automated ADB Unit Tests**: Added a cargo adb runner (`adb-runner.sh`) to execute native tests directly on connected Android hardware `[ce078bf]`, `[d5ffe21]`.
*   **Magisk/KernelSU APK Installer**: Bundles and installs the companion user APK automatically during module flash `[7994024]`.
*   **Dynamic GPU/Mali Power Policies**: Added touchpanel game mode (Oppo/OnePlus), Mali power_policy configurations, and OEM battery saver disable tweaks `[9df5251]`.

### Performance Optimizations

*   **eBPF-Only Frame Timing (Toru)**: Replaced SurfaceFlinger dumpsys fallback with 100% eBPF-based frame stream probe to achieve zero-overhead monitoring `[0485b0c]`, `[9f11e5d]`, `[ccafd78]`.
*   **Bottleneck-Aware Dynamic Governor**: Implemented a CV-based bottleneck classifier (CPU vs GPU) for dynamic per-domain scaling via single eBPF probe `[969c13c]`, `[0ff45ea]`.
*   **Zero-Overhead Exit Detection**: Migrated to kernel `pidfd_open` + `poll` exit tracking for <1ms response latency `[5d65581]`, `[9731bf4]`.
*   **Zero-Allocation Tick Loop**: Replaced per-tick `GameList` deep-clones with an atomic `Arc` refcount swap `[3e64889]`.
*   **APK Size Shrunk 57%**: Shrunk release companion APK size from 9.4MB to 4.0MB with full R8 ProGuard code shrinking, obfuscation, and dependency cleanup `[61d43a6]`, `[5641e84]`.
*   **AppIconCache Scroll Jank Fix**: Pre-warms and caches launcher icons off-thread, rendering games list scroll at smooth 60-120fps `[d9e31da]`, `[5bc0bc6]`.
*   **Vendor-Lock Mounts**: Mount-binds sysfs nodes to disable background vendor CPU/GPU limits during game sessions `[9b1a2bf]`.

### Bug Fixes

*   **Android 16 Compatibility**:
    *   Replaced `FileObserver` in `CmdReader` with a 200ms main-looper Handler poll to avoid GKI 6.12 SIGSEGV crashes `[425e263]`.
    *   Wired Zen Mode interruption filters on Android 16 via reflection and Settings.Global fallback `[9554c8b]`.
    *   Removed `ActivityThread.systemMain()` to drop background service RSS memory overhead by ~16MB `[d7bda76]`.
*   **Stateful CmdWriter**: Fixed clobbering where DND and refresh rate commands dropped each other by batching fields sequentially `[aaa5b7d]`, `[a1f9e36]`.
*   **Multi-Tier CPU Classification**: Corrected cpufreq policy classification for complex CPU topologies (e.g., 4+3+1) to prevent capping little cores `[0e726c2]`, `[376b58b]`.
*   **Startup Self-Healing**: Automatically unmounts stale binds and resets freq nodes left read-only on daemon restart `[376b58b]`.
*   **Uninstall Auto-Cleanup**: Rebuilt `uninstall.sh` to properly kill processes, remove config dirs, and clean up APatch/KernelSU bin symlinks `[8e2e585]`.

### Credits & Acknowledgments

Special thanks to the following projects for UI design inspiration:
*   [RvSystem-Monitor](https://github.com/Rve27/RvSystem-Monitor) — Inspired the modern bottom navigation layout.
*   [PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer) — Inspired the Glance App Widget layout references and typography styles.

---

## v1.0.4

### Bug Fixes

**Daemon (`activity.rs`):**
- Fixed PID overflow and wrong field parsing causing game detection failure
- Fixed `VisibleActivityProcess` parsing where MLBB showed "PID not found" despite being in foreground
- FPS now correctly reads 24–51fps with Performance profile applied

**Daemon (`tweaks/scheduled.rs`, `tweaks/snapdragon.rs`):**
- Fixed GPU `bus_split`/`force_clk_on` configuration
- Expanded `sched_lib_name` coverage for better scheduler hints
- Removed stale MTK tick call from Snapdragon module

**Daemon (`tweaks/mtk.rs`):**
- Fixed PPM policy dead code
- Moved `fix_mediatek_ppm` to daemon startup sequence for earlier initialization

---

### Performance Optimizations

**Daemon (`activity.rs`, `background.rs`, `power.rs`):**
- Optimized `dumpsys` parsing with batched output processing
- Reduced redundant field parsing on every poll cycle
- Tested on Snapdragon 7+ Gen 2 (Poco F5)

---

### New Features

**CLI (`auriyactl` Binary):**
- Replaced shell-based `auriya-ctl` script with compiled `auriyactl` binary
- Implemented full CLI functionality: `start`, `stop`, `restart`, `status`, `inject`, `clear-inject`
- Added `common` module for shared CLI logic and IPC handshake handling
- Added CLI infrastructure scaffolding

**WebUI (Theme):**
- Added system-aware `auto` theme option
- Theme now cycles through `auto → dark → light` modes via toggle

---

### Refactoring

**WebUI:**
- Replaced raw socket polling for restart with `auriyactl restart` IPC call
- Extracted CSS from Svelte components into dedicated `src/css/` files (`base.css`, `components.css`, `theme.css`, `utilities.css`)
- Fixed UI color inconsistencies across all components
- Simplified About page and removed stale content (–73 lines)

---

### Documentation

- Removed outdated documentation files (`docs/README.md`, `architecture.md`, `configuration.md`, `ipc-reference.md`)

---

### CI/CD & Dependencies

**Dependabot Auto-Updates:**
- Bumped `anyhow` from 1.0.101 to 1.0.102
- Bumped `clap` from 4.5.59 to 4.5.60
- Bumped `clap` from 4.5.60 to 4.6.0
- Bumped `libc` from 0.2.182 to 0.2.183
- Bumped `toml` from 1.0.3 to 1.0.6
- Bumped `toml` from 1.0.6 to 1.0.7
- Bumped `tokio` from 1.49.0 to 1.50.0
- Bumped `tracing-subscriber` from 0.3.22 to 0.3.23
- Bumped `vite` from 7.3.1 to 8.0.1 *(webui)*
- Bumped `@sveltejs/vite-plugin-svelte` from 6.2.4 to 7.0.0 *(webui)*
- Bumped `material-symbols` from 0.40.2 to 0.42.3 *(webui)*

**GitHub Actions:**
- Bumped `actions/upload-artifact` from 6 to 7

---

## v1.0.3

### Logging Overhaul

**Minimal INFO Logging:**
- INFO level now only shows startup message, restart, and shutdown events
- All runtime logs (profile changes, foreground detection, FAS decisions) moved to DEBUG
- Compact timestamp format: `HH:MM:SS` instead of full ISO format
- Single consolidated startup log: `Auriya v1.0.3 started (CPU=schedutil, FAS=on, games=3)`
- Removed log level label from output for cleaner logs

**Reduced Log Verbosity:**
- GPU, scheduler, storage, memory tweak logs → DEBUG
- Snapdragon/MediaTek vendor tweaks → DEBUG
- FAS controller and frame source logs → DEBUG
- IPC internal logs → DEBUG
- Config watcher logs → DEBUG

---

### New Features

**Snapdragon GPU & Memlat Tweaking:**
- Added `SnapdragonPaths` struct to cache kgsl and memlat_settings paths
- Use `OnceLock` for lazy initialization of Snapdragon path detection

**Display Improvements:**
- Simplified display mode parsing
- Use `BTreeSet` for auto-sorted unique refresh rates in IPC

**Module Enhancement:**
- Disable game default frame rate limiter

---

### Refactoring

**Logging System:**
- Added `time` crate for custom timestamp formatting
- Enabled `time` feature in `tracing-subscriber`
- Removed redundant startup logs (IPC, FAS, display modes, tick loop)

**Profile Module:**
- Extract helpers for cleaner code
- Cache sysfs paths to reduce hot path allocations

---

### Performance

- Adjust screen-off polling interval to 10s for better battery
- Detect screen wake instantly

---

### Dependencies

- Bump `tokio` from 1.48.0 to 1.49.0
- Bump `libc` from 0.2.178 to 0.2.180
- Bump `serde_json` from 1.0.148 to 1.0.149
- Bump `toml` from 0.9.10 to 0.9.11
- Bump `unplugin-icons` from 22.5.0 to 23.0.1 (WebUI)
- Added `time` v0.3 crate for log timestamp formatting

---

### Documentation

- Added initial project documentation
- Added `docs/README.md`, `architecture.md`, `configuration.md`, `ipc-reference.md`
- Updated README with Table of Contents removal

---

## v1.0.2

### New Features

**Configurable Default Mode:**
- Added `default_mode` field to `settings.toml` for global profile preset
- Supports Balance/Performance/Powersave as default when no game is running
- Auto-reload default mode when `settings.toml` changes
- Global Preset dropdown in WebUI Settings
- Handles missing `[daemon]` section with serde defaults

**Internationalization (i18n):**
- Full i18n support for WebUI with English and Indonesian translations
- Localized all UI strings, dashboard messages, and settings
- Dynamic language switching without restart

**Adaptive Polling:**
- Implemented adaptive polling mechanism for reduced CPU overhead
- CPU usage reduced from 0.3% to 0.1% (67% reduction) during idle
- Removed fixed `poll_interval` from `DaemonConfig`

---

### WebUI Improvements

- Fixed per-game settings persistence and display
- Fixed `target_fps` display using `Array.isArray()` for serde plain JSON
- Added reactive statement to update UI when store changes
- Backend now serializes target_fps as plain number/array

---

### Refactoring

**Module Reorganization:**
- Split `run.rs` into `tick.rs`, `watcher.rs`, and `config.rs` modules
  - `run.rs`: 770 → 330 lines
  - New: `tick.rs` (342 lines), `watcher.rs` (91 lines), `config.rs` (22 lines)
- Split `ipc.rs` into `ipc/commands.rs`, `ipc/handlers.rs`, `ipc/server.rs`
- Merged duplicate balance handling into `apply_balance_and_clear()`

**Code Quality:**
- Migrated `FasController` from `std::sync::Mutex` to `tokio::sync::Mutex` for async-aware locking
- Removed unused `log`/`android_logger` dependencies, use `tracing` only
- Simplified conditionals throughout codebase

---

### Bug Fixes

**Backend (`tick.rs`):**
- Use global governor fallback instead of hardcoded 'performance'
- Balance mode now respects per-game `cpu_governor` setting
- Clone governor string to avoid borrow checker conflict

**Frontend (`GameSettings.svelte`):**
- Fix `target_fps` display for serde plain JSON serialization
- Backend serializes as plain number/array instead of enum `{ Single, Array }`

---

### CI/CD & Dependencies

**Dependabot Auto-Updates:**
- Added Dependabot configuration for automated dependency updates
- Bumped `kernelsu-alt` from 2.1.2 to 3.0.0
- Bumped `serde_json` from 1.0.145 to 1.0.147
- Bumped `toml` from 0.9.8 to 0.9.10+spec-1.1.0
- Bumped `tracing` from 0.1.43 to 0.1.44
- Bumped `actions/checkout` from 4 to 6
- Bumped `actions/upload-artifact` from 4 to 6
- Bumped `softprops/action-gh-release` from 1 to 2

**GitHub Actions:**
- Use raw GitHub URL for changelog in `update.json` generation
- Switch changelog URL from rendered webpage to raw content

---

### Documentation

- Updated README.md with project details and features
- Added supported root managers, resources, and license information

---

## v1.0.1

### New Features

**Frame-Aware Scheduling (FAS) Lite:**
- Implemented FAS Lite with PID controller for adaptive frame rate management
- Added FrameMonitor for real-time frame detection via SurfaceFlinger
- Per-game target FPS configuration with auto-detect support (60/90/120)
- `target_fps` array support via IPC for comma-separated FPS targets

**Per-Game Refresh Rate:**
- Added per-game display refresh rate override
- Stateful refresh rate handling with automatic restoration on game exit
- Exposed supported refresh rates via IPC for WebUI

**Per-Game Profile Mode:**
- Configurable profile mode per game (Performance/Balance/Powersave)

**Daemon Self-Restart:**
- Implemented daemon restart via IPC with `setsid` orphan pattern
- WebUI integration for restart functionality

**Hot-Reload Settings:**
- Settings file watcher for dynamic governor updates without restart

---

###  WebUI Improvements

**Svelte Migration:**
- Complete migration from vanilla JS to Svelte framework
- Custom notification system with Ferris mascot icons
- `unplugin-icons` for declarative icon management

**UI/UX Enhancements:**
- Redesigned home section with device architecture display
- Overhauled game settings UI with dedicated view and enhanced controls
- Theme toggle button with dynamic icon (previously checkbox)
- GitHub-style pagination for game list
- Global CPU governor selection UI
- Debug mode toggle
- Updated social links and footer styling

---

### Performance Optimizations

**Daemon Loop:**
- Replaced shell commands with libc syscalls (`taskset`/`renice`)
- Single-pass foreground parsing with PID validation
- Cached whitelist as `HashSet` for O(1) lookup
- Staggered power/foreground checks to reduce system load
- Instant profile apply on gamelist change

**Memory & CPU:**
- Added swappiness fallback chain for HyperOS
- `chmod` fallback for HyperOS sysfs restrictions

---

### Refactoring

**Module Reorganization:**
- Extracted SoC detection to dedicated module
- Separated Snapdragon and MediaTek tweaks into vendor modules
- Consolidated system tweaks module
- Enhanced GPU tweaks for Adreno
- Removed regex dependency (manual display mode parsing)
- Flattened nested conditionals throughout codebase

**Code Quality:**
- Replaced `unwrap()` with safe lock handling
- Removed unused legacy FAS code
- Localized logs to English
- Use direct struct initialization in `PowerState`
- Replace modulo checks with `is_multiple_of()`
- Remove redundant closures
- Introduce `GameProfileUpdate` struct for cleaner API
- Remove unused `is_plugged_in` and `battery_saver_sticky` fields

---

###  Bug Fixes

- Fixed package truncation and taskset compatibility
- Fixed missing "Enable Optimization" toggle in Game Settings
- Fixed IPC JSON parsing robustness (strip handshake header)
- Fixed wildcard paths for Global Governor shell commands
- Removed unused `get_supported_refresh_rates` function

---

###  Build & CI

- Initial GitHub Actions build workflow
- Cargo-ndk with explicit `--platform` flag
- Centralized release profile configuration

---

## v1.0.0 (Initial Release)

**New Features:**
*   **WebUI:**
    *   Initial release of WebUI for module configuration and system monitoring.
    *   **Game Management:** Configure game packages and settings directly via WebUI.
    *   Redesigned main page and home view.
    *   Enhanced game list UI with GitHub-style badges and Material 3 design elements.
*   **Core Daemon Logic:**
    *   Implemented **Power Gating** & **Foreground Detection** via activities.
    *   Added **PID Priority** management based on `VisibleActivityProcess`.
    *   Implemented **Live Package Config Reloading** for dynamic updates.
    *   Added **Time-based Log Debounce** to reduce spam.
    *   Secure IPC socket permissions (0o660).
*   **Performance & Tweaks:**
    *   Introduced `tweaks` module for CPU, Memory, and **Adreno GPU** optimizations (auto-detect).
    *   Added **Adaptive LMK** (Low Memory Killer) tuning for low/mid-RAM devices.
    *   Added **Battery Saver Awareness**.
*   **Configuration:**
    *   Added **`gamelist.toml`** for managing game profiles.
    *   Added **`settings.toml`** for global daemon and FAS configuration.
    *   Initial support for Clash Royale in game list.

**Refactoring & Improvements:**
*   **Architecture:**
    *   Extracted daemon run logic and state into separate modules (`state.rs`, `run.rs`).
    *   Encapsulated daemon state in `Daemon` struct.
    *   Consolidated IPC server, commands, and types.
*   **Code Quality:**
    *   Simplified conditional logic and removed unused code.
    *   Applied consistent formatting and import ordering.

**Fixes & Build:**
*   Configured Cargo for **Android Release Builds**.
*   Improved module installation and daemon management.
*   Updated default config paths.
