# System Tweaks

The tweak layer is where the daemon actually touches the device: guarded writes
to `/proc` and `/sys`, plus a few actions that must be routed through Android via
the companion. Everything here is **best-effort and device-dependent** — a node
that does not exist is skipped, never fatal.

:::info Verified against source
Traced to Auriya commit
[`10fe7c6`](https://github.com/pavelc4/auriya/tree/10fe7c6b56474a00513fec34ebac1376b30e95e6),
[`src/core/tweaks/`](https://github.com/pavelc4/auriya/tree/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/core/tweaks)
and
[`src/core/cmd_writer/mod.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/core/cmd_writer/mod.rs).
:::

## Module map

`src/core/tweaks/` (`tweaks/mod.rs`) splits the surface by concern:

| Module | Responsibility |
| --- | --- |
| `paths.rs` | Scans and **caches** sysfs paths once (CPU governors, online, Snapdragon KGSL). |
| `cpu.rs` | Governor, boost, core onlining, per-core affinity. |
| `gpu.rs` | GPU performance/balanced mode. |
| `sched.rs` | Scheduler tunables. |
| `memory.rs` | Memory / swappiness / cache drop. |
| `storage.rs` | Block-I/O tunables. |
| `touchpanel.rs` | Touch "game mode". |
| `ceiling.rs` | Frequency-ceiling controller (Low/Balance). |
| `init.rs` | One-shot **general** tweaks applied with the Performance profile. |
| `vendor/` (`detect.rs`, `mtk.rs`, `snapdragon.rs`) | SoC detection + vendor-specific hooks. |
| `vendor_lock.rs` | Locks vendor perfmgr nodes so vendor services cannot fight Auriya. |

Which of these a given profile triggers is the single-source table in
[Architecture overview → What each static profile changes](../architecture/overview#what-each-static-profile-changes).

## Path detection and caching

Sysfs layout varies per device, so paths are discovered **once** and memoized in
a `OnceLock`. `CpuPaths::scan` probes `cpu0..15` and `policy0..7` governor nodes
plus `cpu1..15/online`, keeping only those that exist (`paths.rs`,
`cpu_paths()`). `set_governor_cached` then writes every cached governor node,
ignoring individual write errors:

```rust
pub fn set_governor_cached(governor: &str) {
    let paths = cpu_paths();
    for path in &paths.governors_cpu    { let _ = std::fs::write(path, governor); }
    for path in &paths.governors_policy { let _ = std::fs::write(path, governor); }
}
```

The same pattern caches Snapdragon KGSL/memlat paths **and their original values**
so they can be restored later (`SnapdragonPaths::scan`, `paths.rs`).

## Guarded, best-effort writes

The prevailing idiom (e.g. `init.rs`) is: check existence, write, ignore failure.
Missing nodes are silently skipped so a different kernel layout keeps running. A
concrete slice of the general tweaks (`apply_general_tweaks`, `init.rs`):

```rust
// disable kernel panics (only nodes that exist)
for (path, value) in [
    ("/proc/sys/kernel/panic", "0"),
    ("/proc/sys/kernel/panic_on_oops", "0"),
    ("/proc/sys/kernel/panic_on_warn", "0"),
    ("/proc/sys/kernel/softlockup_panic", "0"),
] {
    if Path::new(path).exists() { fs::write(path, value)?; }
}
```

`apply_general_tweaks` also: sets block-I/O tunables per `/sys/block/*/queue`
(`iostats=0`, `add_random=0`, `read_ahead_kb=32`, `nr_requests=32`), picks the
best available TCP congestion control from `bbr3 → bbr2 → bbrplus → bbr →
westwood → cubic`, applies VM/scheduler tunables, and disables several OEM
"assist"/bloat modules and the OEM battery-saver kernel module (`init.rs`). These
are OEM-node-dependent and mostly no-ops on a device that lacks them.

## Actions routed through Android — `CmdWriter`

Two decisions cannot be done from the root daemon because they require Android
framework APIs (`NotificationManager` for Do-Not-Disturb, the display manager for
refresh rate): **DnD** and **refresh-rate** changes. The daemon serializes these
to a small command file that the companion service watches and replays through
the proper APIs (`cmd_writer/mod.rs` module comment).

- File: `/data/adb/.config/auriya/auriya_cmd` (`CMD_FILE`).
- Wire format (mirrors the companion's `CmdFormat.kt`):

  ```text
  seq 42
  dnd 1              # 0 = All/off, 1 = Priority
  refresh_rate 90    # Hz; 0 means "restore previous"
  ```

- **Stateful re-emit**: the companion reads the *whole* file each time (it is one
  command, not a queue). Two quick single-field writes — `dnd` then
  `refresh_rate` on a game switch — would otherwise clobber each other, so the
  writer remembers the last value of every field and re-emits the **full** state
  on each write (`CmdWriter::write`).
- **Atomic delivery**: written to `.auriya_cmd.tmp` then `rename`d, so the
  companion's inotify watcher only sees a complete payload
  (`CmdWriter::write`). `seq` is a process-monotonic counter for dedup.

There is one process-wide writer (`shared()`); using more than one would reset the
`seq` counter and break the companion's dedup.

:::note There is a fallback for a dead companion
When the companion is considered dead, refresh-rate and DnD requests fall back to
Android `settings put` invocations from the daemon (see
[Architecture overview → Control and status paths](../architecture/overview#control-and-status-paths)).
:::

## SoC detection

Vendor hooks pick a SoC family once, cached in a `OnceLock` (`vendor/detect.rs`,
`detect_soc`). Detection tries, in order: `ro.board.platform` prefixes (`mt`/`k6`
→ MediaTek; `sm`/`sdm`/`msm`/`apq` → Snapdragon; `exynos`; `ud710`/`ums` → Unisoc;
`gs` → Tensor), then `ro.hardware` substrings, then filesystem probes (`/proc/ppm`
→ MediaTek, `/sys/class/kgsl/kgsl-3d0` → Snapdragon). Falls back to `Unknown`.
Only MediaTek and Snapdragon have implemented hook modules at this revision.

## Vendor lock — stopping vendor services from fighting back

On many devices a vendor "perfmgr"/game service continuously rewrites the same
CPU/GPU nodes Auriya sets, undoing Auriya's changes within milliseconds.
`VendorLock` neutralizes this by pinning a set of vendor toggles **read-only via a
bind mount** (`vendor_lock.rs`, `lock_all`):

For each existing path in `VENDOR_PATHS` it: saves the current value, writes the
desired value, `chmod`s the node to `0444`, then **bind-mounts** a Auriya-owned
file over it (`MS_BIND | MS_REC`) so even privileged writes hit the overlay, not
the real node. `unlock_all` unmounts, restores permissions, and writes the saved
value back. The locked set (`VENDOR_PATHS`):

```text
/sys/module/mtk_fpsgo/parameters/perfmgr_enable      → 0
/sys/module/perfmgr/parameters/perfmgr_enable        → 0
/sys/module/perfmgr_policy/parameters/perfmgr_enable → 0
/sys/module/perfmgr_mtk/parameters/perfmgr_enable    → 0
/sys/module/migt/parameters/glk_fbreak_enable        → 0
/sys/module/migt/parameters/glk_disable              → 1
/proc/game_opt/disable_cpufreq_limit                 → 1
```

Locking happens when entering a game session and unlocking when leaving it (see
[Profile scheduler](profile-scheduler#entering-a-whitelisted-game)). A failed
mount-bind reverts the node's permissions and logs a warning rather than aborting.

:::warning This performs mounts as root
`VendorLock` calls `mount`/`umount2` and `chmod`s kernel nodes — a genuine trust
boundary. It only touches the fixed `VENDOR_PATHS` list above, and only nodes that
already exist.
:::

## Likely to drift first

`VENDOR_PATHS`, the general-tweak node lists and their values in `init.rs`, and
the SoC-detection prefixes. These are hardware-specific and change most often.
Re-verify against `src/core/tweaks/init.rs`, `vendor_lock.rs`, and
`vendor/detect.rs`.
