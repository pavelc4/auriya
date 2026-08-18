# settings.toml Reference

`settings.toml` is Auriya's **global** configuration: daemon-wide defaults that
apply regardless of which app is in the foreground. Per-app behavior lives in
[`gamelist.toml`](gamelist) instead.

:::info Verified against source
Every claim on this page is traced to Auriya commit
[`10fe7c6`](https://github.com/pavelc4/auriya/tree/10fe7c6b56474a00513fec34ebac1376b30e95e6).
The Rust type that defines the schema is
[`src/core/config/settings.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/core/config/settings.rs).
Re-verify this page if that file, `settings.toml`, or
`android/shared/src/main/kotlin/dev/auriya/shared/config/TomlParser.kt` changes.
:::

## Location and ownership

| Fact | Value | Source |
| --- | --- | --- |
| Installed path | `/data/adb/.config/auriya/settings.toml` | `src/core/config/path.rs:5-9` (`CONFIG_DIR` + `settings_path()`) |
| Passed to daemon as | `auriya --settings <path>` | `module/service.sh` (daemon launch line) |
| Format | TOML | parsed by `toml::from_str` in `Settings::load`, `settings.rs:96-103` |
| Written by | the **manager app** (Kotlin `TomlParser.serializeSettings`), `TomlParser.kt:109-134` | — |
| Read by | the **Rust daemon** at startup and on file change | `main.rs:11`, `src/daemon/run.rs:288-317` |

:::note The app is the config authority
As of this revision the CLI (`auriyactl`) has **no** command that edits
`settings.toml`. The file is written by the manager app and re-read by the
daemon through a filesystem watcher. That is why some keys below are written by
the app but not yet consumed by the daemon — see
[Written by the app, not yet used by the daemon](#written-by-the-app-not-yet-used-by-the-daemon).
:::

## The shipped default file

This is the exact `settings.toml` bundled in the module ZIP (repository root,
copied to `/data/adb/.config/auriya/settings.toml` on first install by
`module/customize.sh` only when no user config exists):

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

[dynamic_governor]
enabled = true
cv_threshold = 0.15
debounce_frames = 3

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

## How the file is loaded

`Settings::load` reads the file and calls `toml::from_str` with **no**
`#[serde(deny_unknown_fields)]` (`settings.rs:6`, `96-103`). Two consequences,
both verified:

1. **Unknown keys are silently discarded.** A key the `Settings` struct does not
   declare (for example `[fas] target_fps`) parses without error and is dropped.
   You get no warning.
2. **Sections without a serde default are mandatory.** If a required section is
   missing, `toml::from_str` returns an error, `main` returns before the daemon
   starts, and startup fails.

### Which sections are required to start

| Section | Required at startup? | Why | Source |
| --- | --- | --- | --- |
| `[daemon]` | Optional | field-level `#[serde(default)]` on every key | `settings.rs:8-9`, `20-30` |
| `[cpu]` | **Required** | no serde default on the field or struct | `settings.rs:11`, `33-35` |
| `[dnd]` | **Required** | no serde default | `settings.rs:11`, `38-40` |
| `[fas]` | **Required** | no serde default | `settings.rs:12`, `43-49` |
| `[dynamic_governor]` | Optional | `#[serde(default)]` + `impl Default` | `settings.rs:13-14`, `67-75` |
| `[ceiling]` | Optional | `#[serde(default)]` + `impl Default` | `settings.rs:15-16`, `85-93` |
| `[modes.*]` | **Required (≥1 table)** | `modes: HashMap` has no serde default | `settings.rs:17` |

:::warning `[modes.*]` is required to start but its values are ignored
`Settings.modes` has no `#[serde(default)]`, so **at least one** `[modes.X]`
table must exist or the daemon refuses to start. Yet nothing in the daemon ever
reads the map — see the dead-config note below. Removing every `[modes.*]`
block breaks startup; changing the numbers inside them changes nothing.
:::

## Key-by-key reference

Legend for the **Consumed** column:

- **Yes** — the daemon reads this value and it affects behavior.
- **No** — parsed into memory but never read by the daemon (no effect if changed).
- **Dropped** — not a struct field; dropped during parsing.

### `[daemon]`

Defined by `DaemonConfig`, `settings.rs:20-30`.

| Key | Type | Default | Consumed | Meaning & evidence |
| --- | --- | --- | :---: | --- |
| `log_level` | string | `"info"` | Yes | `tracing` env-filter directive applied **at startup** (`main.rs:13-14`, `EnvFilter::new(level)`). Accepts anything `EnvFilter` accepts (`error`/`warn`/`info`/`debug`/`trace`, or per-target like `auriya::daemon=debug`). **Not** re-read on file reload — change the running level with the IPC `SETLOG` command instead (`src/daemon/run.rs:378-392`). |
| `check_interval_ms` | integer (ms) | `2000` | No | **Dead key.** No reader exists outside the struct. The tick cadence is hardcoded (≈500 ms in-game, 5 s foreground, 10 s screen-off) in the event loop (`src/daemon/run.rs:644`), not derived from this value. |
| `default_mode` | string | `"balance"` | Yes | The profile applied when no whitelisted game is foreground. Parsed via `ProfileMode::from_str`; unrecognized values fall back to `Balance` (`src/daemon/run.rs:164-170`). Re-read on reload (`run.rs:303-312`). Valid: `performance`, `balance`, `powersave` (`src/common/types.rs:14-16`). |

### `[cpu]`

Defined by `CpuConfig`, `settings.rs:33-35`.

| Key | Type | Default | Consumed | Meaning & evidence |
| --- | --- | --- | :---: | --- |
| `default_governor` | string | none (**required**) | Yes | The CPU governor written when the Balance profile is applied (the daemon's `balance_governor`, `src/daemon/run.rs:163`). On reload, if it changed **and** the current profile is Balance, it is re-applied immediately (`run.rs:290-300`). Value is a raw governor name written to the kernel (e.g. `schedutil`, `walt`); Auriya does not validate it against the device's available governors. |

### `[dnd]`

Defined by `DndConfig`, `settings.rs:38-40`.

| Key | Type | Default | Consumed | Meaning & evidence |
| --- | --- | --- | :---: | --- |
| `default_enable` | bool | none (**required**) | No | **Not consumed by the daemon.** Do-Not-Disturb is driven per-game: each tick uses the game's own `enable_dnd` (or a hardcoded `true` when a game has none) — `src/daemon/tick.rs:156,226,296`. The global `settings.dnd.default_enable` is never read. The app still writes it (`TomlParser.kt:75-76,120`). |

### `[fas]`

Frame-Aware Scheduling. Defined by `FasConfig`, `settings.rs:43-49`.

| Key | Type | Default | Consumed | Meaning & evidence |
| --- | --- | --- | :---: | --- |
| `enabled` | bool | none (**required**) | Yes | Master switch for FAS. When `true` **and** the eBPF frame stream initialized, the daemon builds a `FasController`; otherwise FAS is skipped (`src/daemon/run.rs:190-207`, `main.rs:36`). |
| `default_mode` | string | none (**required**) | No | Parsed, never read. FAS's fallback target is the daemon's `default_mode`, not this field. |
| `thermal_threshold` | float (°C) | none (**required**) | No | Parsed, never read. |
| `poll_interval_ms` | integer (ms) | `100` | No | Parsed, never read. The eBPF worker polls the frame stream on a hardcoded 50 ms deadline (`src/core/ebpf.rs`; see [Kala eBPF frame probe](../internals/kala-research)). |
| `target_fps` | integer | — | Dropped | **Not a field of `FasConfig`** — dropped during parsing. The FAS controller is constructed with a hardcoded target of `60` (`src/daemon/run.rs:195`, `FasController::with_target_fps(rx, 60)`). Per-game `target_fps` in `gamelist.toml` *does* override this at runtime (`tick.rs:159-170`); the global one does not. |

### `[dynamic_governor]`

Defined by `DynamicGovernorConfig`, `settings.rs:58-65`.

| Key | Type | Default | Consumed | Meaning & evidence |
| --- | --- | --- | :---: | --- |
| `enabled` | bool | `true` | No | Parsed, never read. |
| `cv_threshold` | float | `0.15` | No | Parsed, never read. The bottleneck detector is constructed with hardcoded literals — `BottleneckDetector::new(0.15, 3)` (`src/daemon/fas.rs:83`) — not from this value. The default matching `0.15` is a coincidence, not a wiring. |
| `debounce_frames` | integer | `3` | No | Parsed, never read (same hardcoded `3` above). |

### `[ceiling]`

Frequency-ceiling override applied outside game sessions / in power-save.
Defined by `CeilingConfig`, `settings.rs:78-83`. **Absent from the shipped
file**, so it currently runs entirely on defaults.

| Key | Type | Default | Consumed | Meaning & evidence |
| --- | --- | --- | :---: | --- |
| `default` | string | `"balance"` | Yes | Ceiling level parsed to `CeilingLevel`; unrecognized → `Balance` (`src/daemon/run.rs:220-225`). |
| `low_freq_little_khz` | integer (kHz) or absent | `None` | Yes | Little-cluster max frequency used by the Low ceiling (`run.rs:226`, consumed in `src/core/tweaks/ceiling.rs:283`). |
| `low_freq_big_khz` | integer (kHz) or absent | `None` | Yes | Big-cluster equivalent (`run.rs:227`, `ceiling.rs:286`). |

### `[modes.*]`

A TOML table per mode name, deserialized into `HashMap<String, FasMode>`
(`FasMode`, `settings.rs:52-55`).

| Key | Type | Default | Consumed | Meaning & evidence |
| --- | --- | --- | :---: | --- |
| `margin` | float (fps) | none (required per table) | No | Parsed, never read. FAS uses a hardcoded `MARGIN_FPS = 1.5` (`src/daemon/fas.rs:30`). |
| `thermal_threshold` | float (°C) | none (required per table) | No | Parsed, never read. |

The shipped file defines four modes (`powersave`, `balance`, `performance`,
`fast`). The map is required to exist (see the startup warning above) but no
code path looks up a mode by name.

## Reload behavior

The settings watcher reacts to a runtime edit of `settings.toml`. Only two keys
are re-read — everything else is applied **once at startup and never again**
until the daemon restarts. Verified in `Daemon::reload_settings`
(`src/daemon/run.rs:288-317`):

| Key | Re-read on file change? | Effect |
| --- | :---: | --- |
| `cpu.default_governor` | Yes | Updates `balance_governor`; re-applies immediately only if the current profile is Balance. |
| `daemon.default_mode` | Yes | Updates the fallback profile for the next tick. |
| everything else | No | Ignored on reload (including `log_level` — use `SETLOG` over IPC). |

## Written by the app, not yet used by the daemon

The manager app's `TomlParser.kt` parses **and re-serializes every key above**,
including the ones marked No / Dropped (`TomlParser.kt:59-96` parse,
`109-134` serialize). So each time settings are saved from the app, the full set
of keys is rewritten to disk even though the daemon ignores most of them.

This is **half-wired scaffolding, not accidental junk**. The hardcoded values in
the daemon (`BottleneckDetector::new(0.15, 3)`, `with_target_fps(rx, 60)`,
`MARGIN_FPS = 1.5`) sit exactly where these keys would plug in, matching Auriya's
in-progress Frame-Aware Scheduling work. Do not "clean up" a No key by editing
only `settings.toml` or only the Rust struct: the app will rewrite it, and the
Rust and Kotlin schemas must stay in sync.

:::warning Effective vs. configured
For any key marked No or Dropped, the number in your `settings.toml` is **not** the
value in effect. The effective value is the hardcoded one cited in the table.
:::

## Likely to drift first

If this page falls out of date, these parts go stale soonest:

- The **Consumed** columns — as FAS wiring lands, No/Dropped keys become Yes.
- `check_interval_ms` and the hardcoded tick cadence.
- `[modes.*]` and the hardcoded `MARGIN_FPS`.

Re-verify against `src/core/config/settings.rs`, `src/daemon/run.rs`,
`src/daemon/fas.rs`, and `TomlParser.kt`.
