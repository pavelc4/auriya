# Configuration

This document provides complete reference for Auriya configuration files.

## Table of Contents

- [Overview](#overview)
- [settings.toml](#settingstoml)
- [gamelist.toml](#gamelisttoml)
- [Hot Reload](#hot-reload)
- [Examples](#examples)

## Overview

Auriya uses two TOML configuration files:

| File            | Location                                 | Purpose                             |
| --------------- | ---------------------------------------- | ----------------------------------- |
| `settings.toml` | `/data/adb/.config/auriya/settings.toml` | Global daemon settings              |
| `gamelist.toml` | `/data/adb/.config/auriya/gamelist.toml` | Game whitelist and per-app profiles |

Both files support hot-reload. Changes apply within one second without daemon restart.

---

## settings.toml

### Complete Reference

```toml
[daemon]
log_level = "info"
check_interval_ms = 2000
default_mode = "balance"

[cpu]
default_governor = "schedutil"

[dnd]
default_enable = true

[fas]
enabled = true
default_mode = "balance"
thermal_threshold = 90.0
poll_interval_ms = 300
target_fps = 60

[modes.powersave]
margin = 5.0
thermal_threshold = 80.0

[modes.balance]
margin = 2.0
thermal_threshold = 90.0

[modes.performance]
margin = 1.0
thermal_threshold = 95.0

[modes.fast]
margin = 0.0
thermal_threshold = 95.0
```

### Section: [daemon]

Controls daemon runtime behavior.

| Key                 | Type    | Required | Default     | Description                                                      |
| ------------------- | ------- | -------- | ----------- | ---------------------------------------------------------------- |
| `log_level`         | string  | No       | `"info"`    | Logging verbosity: debug, info, warn, error                      |
| `check_interval_ms` | integer | No       | `2000`      | Legacy field, unused with adaptive polling                       |
| `default_mode`      | string  | No       | `"balance"` | Profile when no game is running: balance, performance, powersave |

**Example:**

```toml
[daemon]
log_level = "debug"      # Enable verbose logging
default_mode = "balance" # Use Balance profile by default
```

### Section: [cpu]

Controls CPU governor settings.

| Key                | Type   | Required | Default       | Description               |
| ------------------ | ------ | -------- | ------------- | ------------------------- |
| `default_governor` | string | No       | `"schedutil"` | Governor for Balance mode |

**Supported Governors:**

| Governor      | Description                    | Use Case             |
| ------------- | ------------------------------ | -------------------- |
| `schedutil`   | Energy-aware frequency scaling | Recommended default  |
| `performance` | Maximum frequency              | Game, benchmarks     |
| `powersave`   | Minimum frequency              | Battery conservation |
| `interactive` | Touch-responsive scaling       | Legacy devices       |
| `ondemand`    | Load-based scaling             | Fallback option      |

**Example:**

```toml
[cpu]
default_governor = "schedutil"
```

### Section: [dnd]

Controls Do-Not-Disturb behavior during game.

| Key              | Type    | Required | Default | Description                                 |
| ---------------- | ------- | -------- | ------- | ------------------------------------------- |
| `default_enable` | boolean | No       | `true`  | Suppress heads-up notifications during game |

**Example:**

```toml
[dnd]
default_enable = true
```

### Section: [fas]

Controls Frame-Aware Scheduling system.

| Key                 | Type    | Required | Default     | Description                       |
| ------------------- | ------- | -------- | ----------- | --------------------------------- |
| `enabled`           | boolean | No       | `true`      | Enable FAS controller             |
| `default_mode`      | string  | No       | `"balance"` | FAS operating mode                |
| `thermal_threshold` | float   | No       | `90.0`      | Temperature limit in Celsius      |
| `poll_interval_ms`  | integer | No       | `300`       | FAS tick interval in milliseconds |
| `target_fps`        | integer | No       | `60`        | Default target frame rate         |

**Example:**

```toml
[fas]
enabled = true
thermal_threshold = 85.0  # More conservative thermal limit
target_fps = 90           # Target 90 FPS by default
```

### Section: [modes.*]

Per-mode FAS tuning parameters.

| Key                 | Type  | Required | Default | Description                         |
| ------------------- | ----- | -------- | ------- | ----------------------------------- |
| `margin`            | float | No       | varies  | FPS deviation before scaling action |
| `thermal_threshold` | float | No       | varies  | Per-mode thermal limit              |

**Mode Defaults:**

| Mode        | Margin | Thermal Threshold |
| ----------- | ------ | ----------------- |
| powersave   | 5.0    | 80.0              |
| balance     | 2.0    | 90.0              |
| performance | 1.0    | 95.0              |
| fast        | 0.0    | 95.0              |

**Example:**

```toml
[modes.performance]
margin = 0.5              # Tighter FPS tolerance
thermal_threshold = 92.0  # Allow higher temperature
```

---

## gamelist.toml

### Format

Each game entry uses TOML array-of-tables syntax:

```toml
[[game]]
package = "com.example.game"
cpu_governor = "performance"
enable_dnd = true
target_fps = 60
mode = "performance"
refresh_rate = 90
```

### Field Reference

| Field          | Type             | Required | Description                                    |
| -------------- | ---------------- | -------- | ---------------------------------------------- |
| `package`      | string           | Yes      | Android package name                           |
| `cpu_governor` | string           | No       | Override governor for this game                |
| `enable_dnd`   | boolean          | No       | Override DND setting                           |
| `target_fps`   | integer or array | No       | Target FPS for FAS                             |
| `mode`         | string           | No       | Force profile: performance, balance, powersave |
| `refresh_rate` | integer          | No       | Override display refresh rate in Hz            |

### Field: package

**Description:** Android application package name. This field is required.

**Format:** Reverse domain notation (e.g., `com.company.appname`)

**Finding Package Names:**

```bash
# List all packages
adb shell pm list packages

# Filter by keyword
adb shell pm list packages | grep game

# Get package for running app
adb shell dumpsys window | grep mCurrentFocus
```

### Field: cpu_governor

**Description:** Overrides CPU governor when this game is in foreground.

**Type:** string

**Valid Values:** schedutil, performance, powersave, interactive, ondemand

**Default Behavior:** Uses value from `settings.toml [cpu].default_governor`

### Field: enable_dnd

**Description:** Controls notification suppression for this game.

**Type:** boolean

**Default Behavior:** Uses value from `settings.toml [dnd].default_enable`

### Field: target_fps

**Description:** Target frame rate for FAS controller.

**Type:** integer or array of integers

**Single Value:**

```toml
target_fps = 60
```

**Multiple Values (FAS auto-detect):**

```toml
target_fps = [60, 90, 120]
```

### Field: mode

**Description:** Forces specific profile mode regardless of default.

**Type:** string

**Valid Values:** performance, balance, powersave

**Example:**

```toml
mode = "balance"  # Use Balance profile even for whitelisted game
```

### Field: refresh_rate

**Description:** Overrides display refresh rate when game is in foreground.

**Type:** integer (Hz)

**Requirements:** Value must match device-supported refresh rate.

**Example:**

```toml
refresh_rate = 120
```

---

## Hot Reload

Both configuration files support hot reload:

1. Edit the file using any method (adb push, text editor, WebUI)
2. Daemon detects change via inotify within ~1 second
3. New configuration applies immediately
4. No daemon restart required

**Verification:**

```bash
# Monitor log for reload confirmation
adb logcat | grep auriya | grep reload
```

---

## Examples

### Minimal Configuration

**settings.toml:**

```toml
[cpu]
default_governor = "schedutil"

[fas]
enabled = true
```

**gamelist.toml:**

```toml
[[game]]
package = "com.mobile.legends"
```

### Optimized Game Setup

**settings.toml:**

```toml
[daemon]
log_level = "info"
default_mode = "balance"

[cpu]
default_governor = "schedutil"

[dnd]
default_enable = true

[fas]
enabled = true
thermal_threshold = 90.0
target_fps = 60

[modes.performance]
margin = 0.5
thermal_threshold = 95.0
```

**gamelist.toml:**

```toml
#  game - maximum performance
[[game]]
package = "com.tencent.ig"
cpu_governor = "performance"
enable_dnd = true
target_fps = [60, 90, 120]
mode = "performance"
refresh_rate = 120

#  game - stable frame rate
[[game]]
package = "com.mobile.legends"
cpu_governor = "performance"
enable_dnd = true
target_fps = 60
mode = "performance"
refresh_rate = 60

# game - balanced approach
[[game]]
package = "com.supercell.clashroyale"
cpu_governor = "schedutil"
enable_dnd = false
target_fps = 60
mode = "balance"

#  - custom configuration
[[game]]
package = "org.ppsspp.ppsspp"
cpu_governor = "performance"
target_fps = [30, 60]
mode = "performance"
```

### Battery-Focused Configuration

**settings.toml:**

```toml
[daemon]
default_mode = "powersave"

[cpu]
default_governor = "schedutil"

[fas]
enabled = true
thermal_threshold = 80.0

[modes.performance]
margin = 2.0
thermal_threshold = 85.0
```

---

## Troubleshooting

### Configuration Not Loading

1. Verify file exists:

   ```bash
   ls -la /data/adb/.config/auriya/
   ```

2. Check file permissions:

   ```bash
   chmod 644 /data/adb/.config/auriya/*.toml
   ```

3. Validate TOML syntax:

   ```bash
   cat /data/adb/.config/auriya/settings.toml
   ```

4. Check daemon logs:
   ```bash
   adb logcat | grep auriya | grep -i error
   ```

### Game Not Detected

1. Verify package name:

   ```bash
   adb shell pm list packages | grep <keyword>
   ```

2. Check whitelist:

   ```bash
   echo "GET_GAMELIST" | nc -U /dev/socket/auriya.sock
   ```

3. Force reload:
   ```bash
   echo "RELOAD" | nc -U /dev/socket/auriya.sock
   ```
