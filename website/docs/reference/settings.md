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
daemon (some keys live, most at startup — see
[Reload behavior](#reload-behavior)). The Rust and Kotlin schemas must stay in
sync — see [Schema sync](#schema-sync-rust--app).
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
   declare parses without error and is dropped. You get no warning.
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

:::note `[modes.*]` is required to start
`Settings.modes` has no `#[serde(default)]`, so **at least one** `[modes.X]`
table must exist or the daemon refuses to start. The mode named by
`fas.default_mode` is the one whose `margin`/`thermal_threshold` drive FAS; the
others are inactive until selected.
:::

## Key-by-key reference

Legend for the **Consumed** column:

- **Yes** — the daemon reads this value and it affects behavior.
- **No** — parsed into memory but never read by the daemon (no effect if changed).

### `[daemon]`

Defined by `DaemonConfig`, `settings.rs:20-30`.

| Key | Type | Default | Consumed | Meaning & evidence |
| --- | --- | --- | :---: | --- |
| `log_level` | string | `"info"` | Yes | `tracing` env-filter directive applied **at startup** (`main.rs:13-14`, `EnvFilter::new(level)`). Accepts anything `EnvFilter` accepts (`error`/`warn`/`info`/`debug`/`trace`, or per-target like `auriya::daemon=debug`). **Not** re-read on file reload — change the running level with the IPC `SETLOG` command instead (`src/daemon/run.rs:378-392`). |
| `check_interval_ms` | integer (ms) | `2000` | Yes | Idle/foreground tick cadence. Feeds `Daemon::normal_interval_ms` (clamped ≥100 ms), used in the event-loop sleep selection (`src/daemon/run.rs`). Re-read on reload. The in-game (500 ms) and screen-off (10 s) cadences stay fixed. |
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
| `default_enable` | bool | none (**required**) | Yes | Default `enable_dnd` for a game created via IPC `ADD_GAME` (`src/daemon/ipc/handlers.rs`, snapshotted into `IpcHandles.dnd_default`). Per-game DnD in `gamelist.toml` overrides it once a game has an explicit value. |

### `[fas]`

Frame-Aware Scheduling. Defined by `FasConfig`, `settings.rs:43-49`.

| Key | Type | Default | Consumed | Meaning & evidence |
| --- | --- | --- | :---: | --- |
| `enabled` | bool | none (**required**) | Yes | Master switch for FAS. When `true` **and** the eBPF frame stream initialized, the daemon builds a `FasController`; otherwise FAS is skipped (`src/daemon/run.rs`, `main.rs`). |
| `default_mode` | string | none (**required**) | Yes | Selects which `[modes.*]` entry is active, supplying the FAS `margin` and (preferentially) thermal ceiling (`FasTuning::from_settings`, `src/daemon/fas.rs`). Unknown name → default margin + `fas.thermal_threshold` fallback (logged). |
| `thermal_threshold` | float (°C) | none (**required**) | Yes | Fallback skin-temp ceiling for FAS `Reduce`, used when the active `[modes.*]` entry omits its own `thermal_threshold` (`FasTuning::from_settings`). |
| `poll_interval_ms` | integer (ms) | `100` | Yes | eBPF frame-poll deadline, clamped to `[1, 500]` ms (`EbpfFrameStream::new`, `src/core/ebpf.rs`). |
| `target_fps` | integer | `60` | Yes | Global FAS target when a game has no per-game `target_fps` (`FasConfig.target_fps` → `FasController` construction, `src/daemon/run.rs`). Per-game `target_fps` in `gamelist.toml` still overrides it at runtime. |

### `[dynamic_governor]`

Defined by `DynamicGovernorConfig`, `settings.rs:58-65`.

| Key | Type | Default | Consumed | Meaning & evidence |
| --- | --- | --- | :---: | --- |
| `enabled` | bool | `true` | Yes | When `false`, FAS skips bottleneck classification and treats every boost as `BoostBalanced` (full profile) instead of CPU/GPU-targeted (`FasController::tick`, `src/daemon/fas.rs`). |
| `cv_threshold` | float | `0.15` | Yes | Coefficient-of-variation split between GPU- and CPU-bound classification. Threaded into `BottleneckDetector::new` via `FasTuning` (`src/daemon/fas.rs`). |
| `debounce_frames` | integer | `3` | Yes | Frames a new bottleneck class must persist before it is accepted (`BottleneckDetector::new` via `FasTuning`). |

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
| `margin` | float (fps) | none (required per table) | Yes | FPS headroom subtracted from the target for the **active** mode (the one named by `fas.default_mode`). Higher margin biases FAS toward boosting. Fed to `FasController` via `FasTuning` (`src/daemon/fas.rs`). |
| `thermal_threshold` | float (°C) | none (required per table) | Yes | Skin-temp ceiling for the active mode; above it FAS forces `Reduce`. Overrides `fas.thermal_threshold` when the active mode defines it. |

The shipped file defines four modes (`powersave`, `balance`, `performance`,
`fast`). Only the one selected by `fas.default_mode` is active at a time; its
`margin`/`thermal_threshold` drive FAS. `fast` is a FAS **margin preset**
(`margin = 0.0`, push closest to the frame deadline) — it is *not* a separate
CPU-governor profile. This per-mode layout mirrors upstream
[fas-rs](https://github.com/shadow3aaa/fas-rs), which Auriya's FAS controller is
adapted from.

## Reload behavior

The settings watcher reacts to a runtime edit of `settings.toml`. A few keys are
re-read live; everything else is applied **once at startup** until the daemon
restarts. Verified in `Daemon::reload_settings` (`src/daemon/run.rs`):

| Key | Re-read on file change? | Effect |
| --- | :---: | --- |
| `cpu.default_governor` | Yes | Updates `balance_governor`; re-applies immediately only if the current profile is Balance. |
| `daemon.default_mode` | Yes | Updates the fallback profile for the next tick. |
| `daemon.check_interval_ms` | Yes | Updates the idle/foreground tick cadence for the next loop iteration. |
| `[fas]` / `[dynamic_governor]` / `[modes.*]` | No | Consumed at startup into the `FasController`; needs `auriyactl restart` to re-tune. |
| everything else | No | Applied at startup (including `log_level` — use `SETLOG` over IPC). |

## Schema sync (Rust ↔ app)

The manager app's `TomlParser.kt` parses **and re-serializes every key above**
(`TomlParser.kt` parse + serialize), so a settings save from the app rewrites the
full key set. The Rust `Settings` struct and the Kotlin model/parser must stay in
sync: adding or removing a key means editing **both** sides (plus the shipped
`settings.toml`), or the app will silently re-add what only Rust dropped. There is
no `#[serde(deny_unknown_fields)]`, so a key present in one schema but not the
other is ignored rather than erroring.

:::note FAS tuning is startup-only
`[fas]`, `[dynamic_governor]`, and the active `[modes.*]` entry are resolved into
the `FasController` once at construction (`FasTuning::from_settings`). Editing them
at runtime has no effect until `auriyactl restart`.
:::

## Likely to drift first

- Per-key **evidence** references — they point to functions (`FasTuning::from_settings`,
  `EbpfFrameStream::new`, `Daemon::reload_settings`) rather than line numbers, but
  re-verify if those move.
- The `[modes.*]` semantics and the `fast` preset, if FAS gains real per-mode
  profiles.

Re-verify against `src/core/config/settings.rs`, `src/daemon/run.rs`,
`src/daemon/fas.rs`, and `TomlParser.kt`.
