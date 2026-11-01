# auriyactl Command-Line Interface Reference

`auriyactl` is the native command-line control and diagnostics utility for the Auriya performance daemon on Android. It communicates with the background daemon through a high-performance Unix domain socket (`/dev/socket/auriya.sock`) using an atomic request-response protocol.

---

## Table of Contents

- [Overview](#overview)
- [Binary Location and Permissions](#binary-location-and-permissions)
- [Global Options](#global-options)
- [Command Reference](#command-reference)
  - [Daemon Lifecycle](#daemon-lifecycle)
  - [Tuning and Profiles](#tuning-and-profiles)
  - [Diagnostics and Telemetry](#diagnostics-and-telemetry)
  - [Developer and Debugging](#developer-and-debugging)
- [Usage Examples](#usage-examples)
- [Exit Codes and Pipeline Safety](#exit-codes-and-pipeline-safety)

---

## Overview

`auriyactl` provides a zero-overhead CLI to query hardware state, switch power profiles, configure target framerates, manage the game whitelist, and stream telemetry in real time. Because it is compiled as a static native binary (ARM64), execution latency is typically sub-millisecond to a few milliseconds.

---

## Binary Location and Permissions

When installed as a Magisk, KernelSU, or APatch module, `auriyactl` is located at:

```
/system/bin/auriyactl
```

### Requirements:
- Root access (`su`) is required to interact with the daemon's Unix socket (`/dev/socket/auriya.sock`).
- From a non-root shell (such as standard ADB or Termux), switch to root before executing:
  ```bash
  su
  auriyactl <command>
  ```

---

## Global Options

The utility supports the following global options across all subcommands:

| Option | Shorthand | Description | Default |
| :--- | :--- | :--- | :--- |
| `--socket <PATH>` | `-s` | Path to the daemon Unix domain socket | `/dev/socket/auriya.sock` |
| `--help` | `-h` | Display help information | |
| `--version` | `-V` | Display version information | |

---

## Command Reference

### Daemon Lifecycle

Commands to inspect, enable, disable, or restart the background daemon.

#### `status`
Displays daemon status, active game, configured game count, and current profile.
```bash
auriyactl status
```

#### `ping`
Sends a heartbeat ping to verify IPC socket responsiveness and roundtrip latency.
```bash
auriyactl ping
```

#### `enable`
Enables all Auriya kernel and scheduler optimizations.
```bash
auriyactl enable
```

#### `disable`
Disables optimizations and restores baseline system governor and frequency settings.
```bash
auriyactl disable
```

#### `reload`
Forces the daemon to reload configuration files (`settings.toml` and `gamelist.toml`) from disk.
```bash
auriyactl reload
```

#### `restart`
Gracefully stops and restarts the native Rust daemon.
```bash
auriyactl restart
```

#### `get-pid`
Returns the Process ID (PID) of the active background daemon and currently focused game.
```bash
auriyactl get-pid
```

---

### Tuning and Profiles

Commands to change execution modes, framerate targets, and game profiles.

#### `set-profile <mode>`
Switches the global performance profile dynamically.
```bash
auriyactl set-profile <mode>
```
Supported modes:
- `powersave`: Minimizes power draw and CPU frequencies during idle or non-gaming tasks.
- `balance`: Default balanced mode providing responsive UI with power efficiency.
- `performance`: High-clock bias for demanding applications and responsive scheduling.
- `fast`: Maximum throughput mode with aggressive frequency scaling and low latency.

#### `set-fps <fps>`
Overrides the target framerate dynamically for the active foreground application.
```bash
auriyactl set-fps <fps>
```
Example:
```bash
auriyactl set-fps 120
```

#### `get-fps`
Queries the real-time measured FPS and current target FPS configuration.
```bash
auriyactl get-fps
```

#### `add-game <package>`
Adds an Android package to the game whitelist in `gamelist.toml`.
```bash
auriyactl add-game <package>
```
Example:
```bash
auriyactl add-game com.mobile.legends
```

#### `remove-game <package>`
Removes an Android package from the game whitelist.
```bash
auriyactl remove-game <package>
```

#### `list-games`
Prints all configured games in `gamelist.toml` along with their target FPS, governor, and DND settings.
```bash
auriyactl list-games
```

---

### Diagnostics and Telemetry

Commands for performance monitoring, log viewing, and hardware inspection.

#### `info`
Queries system and kernel diagnostic information directly via `/proc` and `/sys`.
```bash
auriyactl info
```
Output includes:
- Hardware brand, codename, and board platform.
- CPU topology (clusters, core distribution).
- GPU model, driver version, and active renderer.
- Vulkan support status.
- Kernel version, SELinux enforcement mode, and root manager.
- eBPF subsystem diagnostics (bpf syscall support, JIT compiler status).
- Daemon runtime resource usage (PID, RSS memory, CPU usage, thread count).

Sample output:
```text
Hardware & Platform:
  Brand:          Xiaomi
  Codename:       marble
  Android:        17 (API 37)
  SoC Board:      SM7475 (qcom)
  CPU Topology:   8 cores (4 Little, 3 Mid, 1 Prime)
  GPU:            Adreno 725
  GPU Driver:     v@0863.1 (kgsl)
  Vulkan:         Supported (v1.1)
  Active Render:  OpenGL ES (GLES)
  Display:        120Hz (1080x2400) [Supported: 60Hz, 90Hz, 120Hz]
  Root Manager:   KernelSU
  SELinux:        Enforcing
  Kernel:         5.10.269-Chandelier-prjkt+ (aarch64)

Kernel eBPF Diagnostics:
  BPF Mount:      Mounted (/sys/fs/bpf)
  BPF Syscall:    Available (SYS_bpf)
  JIT Compiler:   Enabled (bpf_jit_enable = 1)
  Kala Probe:     Supported & Ready
  Active Engine:  sysfs DRM Fallback

Daemon Runtime:
  PID:            11783 (Running)
  Memory (RSS):   5.4 MB
  CPU Load:       19.0%
  Workers:        7
```

#### `stats`
Queries a point-in-time telemetry snapshot or continuous stream from the daemon.
```bash
auriyactl stats [OPTIONS]
```
Options:
- `--json`: Outputs raw JSON API envelope (`ApiResponse<StatsSnapshot>`).
- `--stream`: Streams telemetry snapshots continuously.
- `--interval-ms <MS>`: Refresh rate in milliseconds (default: `1000`).

Sample output:
```text
Auriya Live Telemetry (Ctrl+C to quit):
  FPS:            59.8 FPS (1% low: 56.4, 0.1% low: 52.1, peak: 60, σ: 1.24ms)
  CPU Load:       42%
  GPU Load:       68% (540 MHz)
  Temperature:    CPU: 42°C, Bat: 37°C
  Battery:        78% (-612 mA)
  Active App:     com.miHoYo.GenshinImpact (performance)
```

#### `monitor`
Streams real-time performance telemetry including FPS, CPU usage, GPU load, temperature, battery drain, and active package.
```bash
auriyactl monitor [OPTIONS]
```
Options:
- `-1`, `--oneline`: Outputs a compact, single-line rolling stream instead of clearing the screen. Ideal for log recording or split-screen terminal sessions.
- `-i`, `--interval-ms <MS>`: Refresh rate in milliseconds (default: `1000`).

Example:
```bash
auriyactl monitor --oneline --interval-ms 500
```

#### `log`
Views or follows log messages generated by the daemon.
```bash
auriyactl log [OPTIONS]
```
Options:
- `-f`, `--follow`: Streams logs continuously in real-time (similar to `tail -f`).
- `-n`, `--lines <NUM>`: Number of trailing log lines to display (default: `30`).
- `--clear`: Truncates and clears the log file.

Example:
```bash
auriyactl log --lines 50
auriyactl log --follow
```

#### `get-rates`
Queries the hardware display driver for supported panel refresh rates (e.g. 60, 90, 120, 144 Hz).
```bash
auriyactl get-rates
```

#### `list-packages`
Lists all installed application packages on the device as reported by the Android Package Manager.
```bash
auriyactl list-packages
```

---

### Developer and Debugging

Commands reserved for development, tuning validation, and automated testing.

#### `set-log <level>`
Changes the daemon's runtime log filter level dynamically without restarting.
```bash
auriyactl set-log <level>
```
Supported levels: `trace`, `debug`, `info`, `warn`, `error`.

#### `inject <package>`
Injects a mock foreground application package into the daemon's pipeline. Used to test game detection, DND activation, and profile transitions without launching the actual application.
```bash
auriyactl inject com.tencent.ig
```

#### `clear-inject`
Clears any active package injection and restores automatic detection via Native Binder `ProcessObserver`.
```bash
auriyactl clear-inject
```

#### `binder`
Native Android Binder IPC diagnostics, connection probe, automated self-test, and direct hardware actuator commands.

##### Connectivity & Diagnostics
```bash
# Audit servicemanager registration and 13 core framework services
auriyactl binder probe

# Run automated end-to-end self-test across all actuators
auriyactl binder test
```

##### Actuators & Framework Controls
```bash
# Do Not Disturb (Priority or All notifications)
auriyactl binder dnd priority
auriyactl binder dnd all

# Display refresh rate override (0 to restore dynamic system default)
auriyactl binder rate 120
auriyactl binder rate 0

# Wi-Fi Low-Latency lock (WIFI_MODE_FULL_LOW_LATENCY)
auriyactl binder wifi on
auriyactl binder wifi off
auriyactl binder wifi status

# High Touch Polling Rate (sampling boost)
auriyactl binder touch on
auriyactl binder touch off

# Status bar lock (lock notification panel & heads-up alerts)
auriyactl binder statusbar lock
auriyactl binder statusbar unlock

# PowerHAL hardware modes (expensive-rendering, sustained, interaction boost)
auriyactl binder power mode expensive on
auriyactl binder power boost interaction 100

# Hardware vibrator haptics
auriyactl binder vibrator pulse 30
auriyactl binder vibrator off

# Display orientation and window animation scale
auriyactl binder window rotation freeze
auriyactl binder window rotation thaw
auriyactl binder window anim 1.0

# Android OS Battery Saver mode
auriyactl binder powersave on
auriyactl binder powersave off

# Display native on-screen Toast
auriyactl binder toast "Auriya: Performance Profile Applied"

# Dispatch explicit broadcast Intent via ActivityManager
auriyactl binder broadcast dev.auriya.action.PROFILE_CHANGED -e mode=performance
```

---

## Usage Examples

### 1. Monitor Gaming Performance Live
Open a split terminal window (via Termux or ADB) and start the one-line monitor while playing:
```bash
auriyactl monitor --oneline
```

### 2. Configure a Game with 120 FPS Target
Add the game package and set the desired framerate:
```bash
auriyactl add-game com.mobile.legends
auriyactl set-fps 120
```

### 3. Check Thermal and Hardware Diagnostics
Inspect kernel and device diagnostic indicators:
```bash
auriyactl info
```

### 4. Tail Live Daemon Logs
Observe state machine decisions, FAS frametime calculations, and governor switches:
```bash
auriyactl log -f
```

---

## Exit Codes and Pipeline Safety

`auriyactl` incorporates standard POSIX pipeline handling. When piped to stream consumers that terminate early (such as `head`, `grep -q`, or pagers), `auriyactl` intercepts `SIGPIPE` / `EPIPE` (`os error 32`) and exits cleanly with exit code `0` rather than generating unhandled aborts or system crash tombstones.

Standard exit status convention:
- `0`: Success or clean pipeline termination.
- `1`: Execution error (e.g., daemon offline, invalid argument, or permission denied).
