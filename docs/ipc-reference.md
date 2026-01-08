# IPC Reference

Complete reference for the Auriya IPC protocol.

## Connection

**Socket:** `/dev/socket/auriya.sock`

**Type:** Unix domain socket

**Connect via shell:**

```bash
adb shell
su
nc -U /dev/socket/auriya.sock
# or
toybox nc -U /dev/socket/auriya.sock
```

**Send single command:**

```bash
echo "STATUS" | nc -U /dev/socket/auriya.sock
```

---

## Commands

### Health

| Command | Response     | Description             |
| ------- | ------------ | ----------------------- |
| `PING`  | `PONG`       | Test connectivity       |
| `HELP`  | Command list | Show available commands |

### Status

| Command   | Response          | Description             |
| --------- | ----------------- | ----------------------- |
| `STATUS`  | Key=Value         | Current daemon state    |
| `GET_PID` | `PKG=... PID=...` | Current package and PID |

**STATUS Response:**

```
ENABLED=true PACKAGES=3 OVERRIDE=None LOG_LEVEL=Info
```

**GET_PID Response:**

```
PKG=com.app PID=1234
```

### Control

| Command   | Response        | Description      |
| --------- | --------------- | ---------------- |
| `ENABLE`  | `OK enabled`    | Enable daemon    |
| `DISABLE` | `OK disabled`   | Disable daemon   |
| `RELOAD`  | `OK reloaded`   | Reload config    |
| `RESTART` | `OK restarting` | Restart daemon   |
| `QUIT`    | `BYE`           | Close connection |

### Profile

| Command                   | Response         | Description       |
| ------------------------- | ---------------- | ----------------- |
| `SET_PROFILE PERFORMANCE` | `OK profile set` | Force performance |
| `SET_PROFILE BALANCE`     | `OK profile set` | Force balance     |
| `SET_PROFILE POWERSAVE`   | `OK profile set` | Force powersave   |

### Game Management

| Command             | Response     | Description            |
| ------------------- | ------------ | ---------------------- |
| `LIST_PACKAGES`     | Package list | List whitelisted games |
| `GET_GAMELIST`      | JSON         | Full game config       |
| `ADD_GAME <pkg>`    | `OK added`   | Add to whitelist       |
| `REMOVE_GAME <pkg>` | `OK removed` | Remove from whitelist  |

**UPDATE_GAME:**

```
UPDATE_GAME <package> [gov=<gov>] [dnd=<bool>] [fps=<n>] [fps_array=<n,n,n>] [rate=<hz>] [mode=<mode>]
```

Example:

```bash
echo "UPDATE_GAME com.game gov=performance fps=120 dnd=true" | nc -U /dev/socket/auriya.sock
```

### FAS

| Command               | Response        | Description           |
| --------------------- | --------------- | --------------------- |
| `GET_FPS`             | `FPS=60`        | Current target FPS    |
| `SET_FPS <n>`         | `OK SET_FPS 60` | Set target FPS        |
| `GET_SUPPORTED_RATES` | `[60,90,120]`   | Display refresh rates |

### Debug

| Command         | Response           | Description         |
| --------------- | ------------------ | ------------------- |
| `SET_LOG DEBUG` | `OK log level set` | Verbose logging     |
| `SET_LOG INFO`  | `OK log level set` | Normal logging      |
| `INJECT <pkg>`  | `OK injected`      | Fake foreground app |
| `CLEAR_INJECT`  | `OK cleared`       | Clear injection     |

---

## Quick Test

```bash
adb shell su -c 'echo "PING" | nc -U /dev/socket/auriya.sock'
# Expected: PONG

adb shell su -c 'echo "STATUS" | nc -U /dev/socket/auriya.sock'
# Expected: ENABLED=... PACKAGES=... OVERRIDE=... LOG_LEVEL=...

adb shell su -c 'echo "GET_GAMELIST" | nc -U /dev/socket/auriya.sock'
# Expected: JSON array of games
```
