# Installation

Auriya ships as a single flashable module ZIP. The ZIP contains **everything** —
the daemon, the CLI, both APKs, and default config — so nothing is downloaded at
boot. See [Module lifecycle](../architecture/module-lifecycle) for the full
packaging story.

:::info Verified against source
Install behavior traced to
[`module/customize.sh`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/module/customize.sh)
at commit `10fe7c6`.
:::

## Steps

1. **Download** the current `auriya-*.zip` from the
   [releases page](https://github.com/pavelc4/auriya/releases). You do **not**
   need a separate APK download — the manager app is bundled inside the ZIP.
2. **Flash** the ZIP through your root manager (Magisk / KernelSU / APatch).
3. **Watch the installer output.** `customize.sh` prints device info and a
   step-by-step log (see below). If it aborts, the message says why.
4. **Reboot** when prompted.
5. **Open Auriya** from the launcher and grant root when asked (see
   [First run](first-run)).

## What the installer actually does

`customize.sh` runs these checks and actions, in order:

1. **Architecture gate** — if `$ARCH` is not `arm64`, it prints
   "Unsupported architecture" and aborts. Only aarch64 is shipped.
2. **Extract** the ZIP into `/data/adb/modules/auriya`.
3. **Integrity check** — verifies the daemon binary's SHA-256 against the bundled
   `checksums.sha256`; a mismatch **aborts** the install. The CLI checksum is
   checked too, but a CLI mismatch only downgrades to daemon-only mode.
4. **Install binaries** — copies the daemon to
   `/data/adb/modules/auriya/system/bin/auriya` (`0755`), and `auriyactl` if
   present.
5. **Install the companion** — copies `service.apk` to
   `system/etc/auriya/service.apk`. This is **required**; a missing companion APK
   aborts the install.
6. **Install the manager app** — `pm install -r -g` the bundled
   `auriya-app.apk` (`dev.auriya.app`). This is **best-effort**: if `pm install`
   fails or the APK is not bundled, it prints a manual `adb install` hint and
   continues (the daemon does not depend on the UI app).
7. **Seed config** — moves `settings.toml` / `gamelist.toml` into
   `/data/adb/.config/auriya/` **only if you have no existing config** there, so a
   reinstall never overwrites your settings.
8. **Root-manager symlinks** — links the binaries into `/data/adb/ksu/bin`
   (KernelSU) or `/data/adb/ap/bin` (APatch) when those directories exist, so
   `auriya`/`auriyactl` are on `PATH`. Magisk needs no symlink.

Exact runtime and staging paths are in the
[Filesystem reference](../reference/filesystem).

## After reboot

The module does **not** launch the daemon from the app. At boot,
`module/service.sh` (via the root manager's `service.d` hook) waits for
`sys.boot_completed`, launches the companion with `app_process`, waits for its
status file, then starts the daemon with explicit `--settings` / `--gamelist`
paths. See
[Architecture overview → Binary execution workflow](../architecture/overview#binary-execution-workflow).

If something is wrong, the daemon and companion logs are under
`/data/adb/auriya/` (`daemon.log`, `companion.log`) — see
[Debugging](../development/debugging).

## Next

[First run](first-run) · [Configuration](configuration).
