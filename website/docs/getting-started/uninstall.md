# Uninstall

Uninstall Auriya through your root manager's module list (Magisk / KernelSU /
APatch) — flag the module for removal and reboot. The module's `uninstall.sh`
does the cleanup.

:::info Verified against source
Traced to
[`module/uninstall.sh`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/module/uninstall.sh)
at commit `10fe7c6`.
:::

## What `uninstall.sh` does, in order

1. **Stops the daemon** — `SIGTERM` `auriya`, wait up to 5 s, then `SIGKILL`.
2. **Stops the companion** — the same TERM→KILL sequence for the `AuriyaSysMon`
   process.
3. **Force-stops and uninstalls the packages** — `am force-stop` then
   `pm uninstall` for `dev.auriya.app`, `dev.auriya.app.debug`, and
   `dev.auriya.service`. Each uninstall is retried up to 3 times with a 15 s
   timeout, because `pm uninstall` can hang.
4. **Deletes runtime data**:
   - `/dev/socket/auriya.sock`
   - `/data/adb/.config/auriya` — **all config**, including your `settings.toml`
     and `gamelist.toml`
   - `/data/adb/auriya` — all logs
   - the KernelSU/APatch symlinks (`/data/adb/ksu/bin/*`, `/data/adb/ap/bin/*`)
5. **Countdown** — a short "Do not reboot" countdown so Android can finish the
   package removals, then "Safe to reboot".

The module directory itself (`/data/adb/modules/auriya`) is removed by the root
manager after reboot.

:::warning Your config is deleted
Step 4 removes `/data/adb/.config/auriya` entirely. If you want to keep your
`settings.toml` / `gamelist.toml`, back them up **before** uninstalling. A fresh
install re-seeds the shipped defaults (see
[Installation](installation#what-the-installer-actually-does)).
:::

## Do not reboot early

`pm uninstall` runs asynchronously and is retried. Rebooting during the countdown
can leave a package half-removed. Wait for **"Auriya uninstall complete. Safe to
reboot."** before rebooting.

## Uninstall triggered at boot

`uninstall.sh` is also invoked automatically by `service.sh` if it finds a
`remove` flag file for the module at boot (`module/service.sh`, `_cleanup_all`) —
this is how a root-manager "remove on next boot" request is honored.
