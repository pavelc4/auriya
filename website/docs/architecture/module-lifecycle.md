# Module Lifecycle

From CI build to boot to uninstall — the life of the module payload. Each stage
cites the script that implements it. For install *details* see
[Installation](../getting-started/installation); for the boot *execution* see
[Architecture overview](overview#binary-execution-workflow).

:::info Verified against source
Traced to commit `10fe7c6`: `module/customize.sh`, `module/service.sh`,
`module/uninstall.sh`, and `.github/actions/package-module`
([CI/CD workflows](../development/ci-cd)).
:::

## 1. Package (CI)

CI builds **one** self-contained ZIP: lifecycle scripts, `module.prop`, the
default `settings.toml` / `gamelist.toml`, the aarch64 `auriya` (+ optional
`auriyactl`) with a `checksums.sha256`, and both APKs under `libs/companion/`.
Nothing is fetched at boot. The exact packaging (APK search order, versioning,
7-zip invocation) is documented in
[CI/CD → package-module](../development/ci-cd#package-module). The ZIP name is
`auriya-<version>-<commit-count>-<sha>-<build_type>.zip`.

## 2. Extract (root manager)

Magisk / KernelSU / APatch extracts the ZIP into `/data/adb/modules/auriya`. The
repository's `module/` directory **is** the ZIP root, so there is no nested
`module/module/` on device.

## 3. Install (`customize.sh`)

Runs at flash time (see
[Installation → what the installer does](../getting-started/installation#what-the-installer-actually-does)):

1. Abort unless `$ARCH` is `arm64`.
2. Verify the daemon binary against `checksums.sha256` (mismatch aborts).
3. Copy the daemon → `system/bin/auriya`, CLI → `system/bin/auriyactl` (if
   present).
4. Copy the companion APK → `system/etc/auriya/service.apk` (**required**; missing
   aborts).
5. `pm install` the manager app `auriya-app.apk` (**best-effort**; failure warns
   and continues).
6. Delete the staging `libs/` directory.
7. Move `settings.toml` / `gamelist.toml` into `/data/adb/.config/auriya`
   **only if absent** (never overwrites user config).
8. Create KernelSU/APatch `bin` symlinks where those managers are present.

The runtime-vs-staging path distinction is in the
[Filesystem reference](../reference/filesystem#zip-staging-paths-inside-the-archive--during-install-only).

## 4. Boot (`service.sh`)

On every boot, via the root manager's `service.d` hook: wait for
`sys.boot_completed`, stop any stale companion/daemon, remove stale
socket/status/lock files, launch the companion with `app_process`, wait up to
10 s for its `system_status`, then start the daemon with explicit
`--settings` / `--gamelist` paths, teeing output to logcat and
`/data/adb/auriya/daemon.log`. The full boot sequence is in
[Architecture overview → Binary execution workflow](overview#binary-execution-workflow).

## 5. Run

The daemon's tick loop selects profiles and publishes status; clients connect
over the socket. See [Data flow](data-flow) and
[Profile scheduler](../internals/profile-scheduler).

## 6. Update

`update.json` (committed to `main` by the release workflow) advertises the latest
version, `versionCode`, release-asset URL, and changelog URL; `module.prop`'s
`updateJson` points root managers at it. See
[CI/CD → release.yml](../development/ci-cd#releaseyml).

## 7. Uninstall (`uninstall.sh`)

Stops `auriya` and `AuriyaSysMon`, `pm uninstall`s the packages, and deletes the
socket, `/data/adb/.config/auriya`, `/data/adb/auriya`, and the symlinks. Can also
be triggered at boot by a `remove` flag. Details:
[Uninstall](../getting-started/uninstall).
