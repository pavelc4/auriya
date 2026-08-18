# Debugging

A boundary-first playbook. Auriya spans five boundaries — Android UI, companion
service, the Unix socket, daemon subsystems, and kernel nodes — and a symptom
usually points at one. Identify the boundary, then use the matching tool below.

## Logs first

| Log | Path | What it holds |
| --- | --- | --- |
| Daemon | `/data/adb/auriya/daemon.log` (+ `.1`, `.2`) | Daemon stdout/stderr, tee'd by `service.sh`. |
| Companion | `/data/adb/auriya/companion.log` (+ `.1`) | `AuriyaSysMon` output. |
| Restart | `/data/adb/auriya/restart.log` | Output of `service.sh` when relaunched by `auriyactl restart`. |
| logcat | `logcat -s auriya` | The daemon and scripts also log under the `auriya` tag. |

Paths are from the [Filesystem reference](../reference/filesystem#logs--dataadbauriya).

## Adjust log verbosity

The daemon's level comes from `settings.daemon.log_level` at startup
([settings](../reference/settings#daemon)). To change it **at runtime without a
restart**, use IPC (this is the only settings value with a live runtime toggle):

```bash
auriyactl set-log debug     # or info | warn | error
```

`log_level` is **not** re-read on config reload — `set-log` is the live path
([settings → reload behavior](../reference/settings#reload-behavior)).

## Boundary-by-boundary

### Is the daemon even up?

```bash
auriyactl ping        # → "Daemon is alive (PONG)"
auriyactl status      # → status block, or "Daemon: Not running"
```

`Not running` → the boot sequence failed. Check `daemon.log` and `companion.log`.
Common causes are enumerated by `service.sh`'s own error messages (missing
binary, missing companion APK, missing config, or the companion not producing
`system_status` within 10 s).

### Socket / IPC

Talk to the socket directly to see the **raw** protocol (the CLI shows only a
subset of `STATUS`):

```bash
printf 'STATUS\nQUIT\n' | nc -U /dev/socket/auriya.sock
```

Full command and response reference: [IPC protocol](../internals/ipc-protocol).
`ERR …` replies are documented there.

### Foreground / game detection

If a game is not being picked up, inspect the companion snapshot and the daemon's
resolved state:

```bash
cat /data/adb/.config/auriya/system_status   # focused_app/pid, screen, battery, zen
auriyactl get-pid                            # daemon's resolved PKG/PID
auriyactl inject com.your.game               # force a package (debug); clear-inject to undo
```

See [Game detection](../internals/game-detection). If `system_status` is stale or
empty, the problem is the **companion**, not the daemon.

### Profile not applying / wrong profile

- Confirm the package is whitelisted (`auriyactl list-games`).
- Remember the [decision order](../internals/profile-scheduler#decision-order):
  screen-off/battery-saver wins over everything; a per-game `mode` typo silently
  resolves to Performance.
- The daemon only writes when the target profile differs from the current one
  ([idempotence guard](../internals/profile-scheduler#the-idempotence-guard)) — so
  "nothing happened" can be correct.
- Check `/data/adb/.config/auriya/current_profile` (`1`/`2`/`3`) for the
  last-applied profile.

### Kernel node / tweak not taking effect

Tweaks are **best-effort**: a missing node is skipped silently
([System tweaks](../internals/system-tweaks#guarded-best-effort-writes)). Raise
the log level to `debug` to see which paths were found and written. If a vendor
service is fighting Auriya, that is what
[vendor lock](../internals/system-tweaks#vendor-lock--stopping-vendor-services-from-fighting-back)
addresses — verify the relevant `VENDOR_PATHS` node exists on your device.

## Restarting cleanly

```bash
auriyactl restart     # kills daemon+companion, clears socket/status/lock, re-runs service.sh
```

This is a **local** operation (not the IPC `RESTART`); it needs root and an
installed device. See [Command reference](../reference/commands#restart-is-local-not-an-ipc-command).

## Reporting an issue

Capture: the command and its response, the active package, root manager, ROM,
kernel version, and the relevant `settings.toml` / `gamelist.toml` — **without**
publishing private device identifiers. Root, `/proc`/`/sys` writes, the socket,
and shell commands are trust boundaries; scrub logs accordingly (`AGENTS.md`,
Security & Configuration).
