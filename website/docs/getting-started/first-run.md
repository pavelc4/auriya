# First Run

After installing and rebooting, open **Auriya** from the launcher. This page
covers what happens the first time — and what is verifiable from the daemon
architecture versus the app UI.

:::note Scope of this page
The manager-app onboarding (theme, navigation style, "setup complete" flag) is
UI behavior in `android/app` and is not source-traced here. What *is* verifiable
is the runtime contract below: the app is a client of the daemon and needs root
to be useful.
:::

## What the first run establishes

1. **Root authorization.** The manager controls the daemon over the Unix socket
   `/dev/socket/auriya.sock` and reads/writes config under `/data/adb/.config/auriya`
   — all root-only paths. Without root, the app cannot query daemon status or
   change configuration, so onboarding cannot complete meaningfully.
2. **The daemon is already running.** Unlike many modules, Auriya's daemon is
   **not** started by the app — it is launched at boot by `module/service.sh`
   (see [Installation → After reboot](installation#after-reboot)). By the time you
   open the app, the daemon and companion should already be up.
3. **Appearance/onboarding preferences** are stored by the app for subsequent
   launches.

## Verifying it works

If you have the CLI installed, the fastest check is over IPC:

```console
$ auriyactl ping
 Daemon is alive (PONG)

$ auriyactl status
   	   Auriya Daemon Status
Daemon: Running

    Enabled:  true
    Games:    3 configured
    FPS:      59.8 SOURCE=ebpf
```

`Daemon: Not running` means the boot sequence failed — check
`/data/adb/auriya/daemon.log` and `companion.log`
([Debugging](../development/debugging)). See the full command set in
[Command reference](../reference/commands).

## Next

- [Configuration](configuration) — tune global and per-app behavior.
- [Architecture overview](../architecture/overview) — how the pieces fit.
