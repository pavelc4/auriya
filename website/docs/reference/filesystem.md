# Filesystem Reference

Every path Auriya reads, writes, or installs, grouped by **when it exists**.
This matters because the module archive's layout is *not* the installed layout:
`customize.sh` copies files to their runtime locations and then deletes the
staging directory.

:::info Verified against source
Traced to Auriya commit
[`10fe7c6`](https://github.com/pavelc4/auriya/tree/10fe7c6b56474a00513fec34ebac1376b30e95e6).
Path constants:
[`src/common/constants.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/common/constants.rs)
and
[`src/core/config/path.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/core/config/path.rs).
Install/boot layout: `module/customize.sh`, `module/service.sh`,
`module/uninstall.sh`.
:::

## Runtime paths (on an installed, running device)

These exist after installation and boot.

### Configuration and runtime state — `/data/adb/.config/auriya/`

`CONFIG_DIR`, defined once in `src/common/constants.rs:3` and
`src/core/config/path.rs:5`.

| Path | Written by | Read by | Purpose |
| --- | --- | --- | --- |
| `settings.toml` | manager app | daemon (startup + watcher) | Global config. See [settings reference](settings). `SETTINGS_FILE`, `constants.rs:4`. |
| `gamelist.toml` | manager app **and** daemon (IPC mutations) | daemon (startup + watcher) | Per-app whitelist/profiles. See [gamelist reference](gamelist). `GAMELIST_FILE`, `constants.rs:5`. |
| `system_status` | companion service | daemon (`system_status` watcher) | Companion→daemon snapshot: focused app, screen/battery/zen state. Deleted at each boot by `service.sh` so the daemon only proceeds on fresh data. `STATUS_FILE`, `src/core/system_status`. |
| `companion.lock` | companion service (flock) | daemon (`companion_lock` watcher) | Liveness lock; the daemon watches its release to detect a dead companion. `src/daemon/companion_lock.rs:31-32`. |
| `current_profile` | daemon | external/legacy readers | Legacy status file holding `1`/`2`/`3` for Performance/Balance/Powersave. Best-effort compatibility output — **not** the authoritative UI state. `src/daemon/run.rs:49-64`. |
| `gpu_type` | `customize.sh` (install-time) | — | Detected GPU (`adreno`/`mali`/`unknown`), written once at install. `module/customize.sh` (`make_node`). |
| `arch` | `customize.sh` (install-time) | — | Detected device ABI, written once at install. |

### Daemon socket

| Path | Purpose |
| --- | --- |
| `/dev/socket/auriya.sock` | Local Unix socket for all IPC (app + `auriyactl`). Created by the daemon on startup, removed by `service.sh` before restart and by `uninstall.sh`. `SOCKET_PATH`, `constants.rs:1`; bound at `src/daemon/run.rs:417`. See [IPC protocol](../internals/ipc-protocol). |

### Logs — `/data/adb/auriya/`

| Path | Purpose |
| --- | --- |
| `daemon.log` | Daemon stdout/stderr, tee'd here by `service.sh`. `LOG_FILE`, `constants.rs:6`. |
| `daemon.log.1`, `daemon.log.2` | Rotated daemon logs (rotate when `daemon.log` exceeds 1 MB). `service.sh`. |
| `companion.log`, `companion.log.1` | Companion stdout/stderr and its single rotation. `service.sh`. |
| `restart.log` | Output of `service.sh` when relaunched by `auriyactl restart`. `src/cli/executor.rs:155-159`. |
| `daemon.log.old` | Previous boot's `daemon.log`, renamed once at install. `customize.sh`. |

### Installed module tree — `/data/adb/modules/auriya/`

The module root after extraction. Runtime binaries live under `system/`, which
the root manager mounts into the system image.

| Path | Purpose |
| --- | --- |
| `system/bin/auriya` | **The daemon binary that actually runs.** Copied here from staging, `0755`. `customize.sh` (`cp "$DAEMON_BINARY" "$MODPATH/system/bin/auriya"`). |
| `system/bin/auriyactl` | Control CLI, if bundled (optional). `customize.sh`. |
| `system/etc/auriya/service.apk` | Installed companion APK; launched at boot via `app_process`. Required — the daemon refuses to start without the companion. `customize.sh`, `service.sh` (`COMPANION_APK`). |
| `service.sh` | Boot script: starts companion, waits for its status file, starts the daemon. Runs on every boot via the root manager's `service.d` hook. |
| `uninstall.sh` | Cleanup script (also invoked mid-run when a `remove` flag appears). |
| `module.prop` | Module metadata (id, name, version, `updateJson` URL). `module/module.prop`. |

### Root-manager symlinks (conditional)

`customize.sh` symlinks the binaries into the active root manager's `bin`
directory **only if that directory exists**, so the binary is on `PATH`:

| Path | Condition |
| --- | --- |
| `/data/adb/ksu/bin/auriya`, `/data/adb/ksu/bin/auriyactl` | KernelSU present (`/data/adb/ksu/bin` exists) |
| `/data/adb/ap/bin/auriya`, `/data/adb/ap/bin/auriyactl` | APatch present (`/data/adb/ap/bin` exists) |

Magisk does not get symlinks; `system/bin` is already on `PATH` via the mounted
overlay. All four are removed by `uninstall.sh`.

## Kernel interfaces (read/written by tweaks)

Device-dependent; probed before use and skipped when absent. See
[System tweaks](../internals/system-tweaks).

| Path root | Purpose |
| --- | --- |
| `/proc` | Kernel/process telemetry and a few control nodes. |
| `/sys` | CPU/GPU/scheduler/memory/thermal control and telemetry nodes. |

## ZIP-staging paths (inside the archive / during install only)

These exist **only** inside the flashable ZIP and during `customize.sh`. The
staging `libs/` directory is deleted after install (`rm -rf "$MODPATH/libs"`),
and the root TOMLs are *moved* into `CONFIG_DIR` on first install.

| Path (relative to ZIP root) | Fate on install |
| --- | --- |
| `libs/aarch64/auriya` | Verified (SHA256), copied to `system/bin/auriya`, then `libs/` removed. |
| `libs/aarch64/auriyactl` | Copied to `system/bin/auriyactl` if present, then removed. |
| `libs/aarch64/checksums.sha256` | Used for integrity check, then removed. |
| `libs/companion/service.apk` | Copied to `system/etc/auriya/service.apk`, then removed. |
| `libs/companion/auriya-app.apk` | `pm install`ed (manager app `dev.auriya.app`), then removed. Not required for the daemon. |
| `settings.toml` (ZIP root) | Moved to `CONFIG_DIR/settings.toml` **only if none exists**; otherwise the user's copy is kept. |
| `gamelist.toml` (ZIP root) | Same move-if-absent behavior. |

:::warning Do not point tools at `libs/…` on a running device
A common mistake: the release ZIP and older docs show `libs/aarch64/auriya`, but
that path **does not exist after installation**. The running daemon is
`/data/adb/modules/auriya/system/bin/auriya` (or the root-manager symlink).
:::

## Installed packages

| Package | Role | Removed by |
| --- | --- | --- |
| `dev.auriya.app` | Manager UI (Compose). `pm install`ed from staging. | `uninstall.sh` (`pm uninstall`) |
| `dev.auriya.service` | Companion service package identity. | `uninstall.sh` |
| `dev.auriya.app.debug` | Debug-variant manager, if installed. | `uninstall.sh` |

## What uninstall removes

`uninstall.sh` stops `auriya` and the `AuriyaSysMon` companion, `pm uninstall`s
the packages above, then deletes: `/dev/socket/auriya.sock`,
`/data/adb/.config/auriya` (all config + runtime state), `/data/adb/auriya` (all
logs), and the ksu/ap symlinks. The module directory itself is removed by the
root manager.

## Likely to drift first

Log rotation filenames, the conditional symlink paths, and the staging layout —
they live in shell scripts (`module/*.sh`) that change independently of the Rust
constants. Re-verify against `module/customize.sh`, `module/service.sh`,
`module/uninstall.sh`, and `src/common/constants.rs`.
