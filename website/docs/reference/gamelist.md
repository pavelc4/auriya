# gamelist.toml Reference

`gamelist.toml` is Auriya's **per-app** configuration: the whitelist of Android
packages that receive a managed performance profile, plus the per-package
overrides applied while that app is in the foreground. Global defaults live in
[`settings.toml`](settings) instead.

:::info Verified against source
Every claim is traced to Auriya commit
[`10fe7c6`](https://github.com/pavelc4/auriya/tree/10fe7c6b56474a00513fec34ebac1376b30e95e6).
Schema:
[`src/core/config/gamelist.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/core/config/gamelist.rs).
Runtime consumption:
[`src/daemon/tick.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/daemon/tick.rs).
:::

## Location and ownership

| Fact | Value | Source |
| --- | --- | --- |
| Installed path | `/data/adb/.config/auriya/gamelist.toml` | `src/core/config/path.rs:11-13` (`gamelist_path()`) |
| Passed to daemon as | `auriya --gamelist <path>` | `module/service.sh` (daemon launch line) |
| Format | TOML array of tables (`[[game]]`) | `GameList` = `Vec<GameProfile>`, `gamelist.rs:83-102` |
| Written by | manager app (`TomlParser.serializeGameList`) **and** the daemon itself on IPC mutation | `TomlParser.kt:198-219`; `gamelist.rs:118-130` |
| Read by | the daemon, cached as a package whitelist and consulted every tick | `src/daemon/run.rs:212-217`, `tick.rs:222` |

Unlike `settings.toml`, this file is **mutated at runtime by the daemon**. When a
client sends `ADD_GAME`, `REMOVE_GAME`, or `UPDATE_GAME` over IPC, the daemon
edits its in-memory list and writes the whole file back to disk (see
[Mutations](#how-entries-are-added-and-changed)).

## The shipped default file

Exact `gamelist.toml` bundled in the module ZIP (copied to
`/data/adb/.config/auriya/gamelist.toml` on first install when no user config
exists):

```toml
[[game]]
package = "com.mobile.legends"
cpu_governor = "performance"
enable_dnd = true
target_fps = 120

[[game]]
package = "com.supercell.clashroyale"
cpu_governor = "schedutil"
enable_dnd = false
target_fps = 60

[[game]]
package = "com.tencent.ig"
cpu_governor = "performance"
enable_dnd = true
target_fps = 120
```

Each `[[game]]` block is one entry. `package`, `cpu_governor`, and `enable_dnd`
are present on every shipped entry; `target_fps` is optional and the other
override fields (`refresh_rate`, `mode`, `ceiling`) are simply omitted here.

## Loading behavior

`GameList::load` (`gamelist.rs:104-117`) differs from settings loading in one
important way:

- **A missing file is not fatal.** If `gamelist.toml` does not exist, the daemon
  logs `Gamelist file not found, using empty list` and starts with zero managed
  packages (`gamelist.rs:108-111`). Compare `settings.toml`, whose absence
  aborts startup.
- **A malformed file *is* fatal.** If the file exists but fails to parse,
  `toml::from_str` errors and startup fails (`gamelist.rs:113-116`).
- Like settings, there is no `deny_unknown_fields`, so unknown keys inside a
  `[[game]]` block are silently ignored.

At startup the daemon builds a `HashSet` of package names (the "whitelist") from
this list (`src/daemon/run.rs:212-217`). On a gamelist file change the whitelist
is rebuilt and tracked package/PID state is cleared (`Daemon::rebuild_whitelist`,
`run.rs:320-327`).

## Field reference

Defined by `GameProfile`, `gamelist.rs:89-102`. The **Consumed** column uses the
same legend as the [settings reference](settings#key-by-key-reference): **Yes** (read
and effective), **No** (parsed but unused).

| Key | Type | Required | Default when omitted | Consumed | Meaning & evidence |
| --- | --- | :---: | --- | :---: | --- |
| `package` | string | **Yes** | — | Yes | Exact Android package name, e.g. `com.tencent.ig`. This is the whitelist key matched against the companion's focused package (`tick.rs:222`, `gamelist.find(pkg)`). No wildcards, no partial match. |
| `cpu_governor` | string | **Yes** | — | Yes | CPU governor applied for this game. Passed straight to the profile writer (`tick.rs:223-225`). An empty string falls back to the global `balance_governor` (`tick.rs:266-269`). Raw kernel governor name; not validated against the device. |
| `enable_dnd` | bool | **Yes** | — | Yes | `true` → request Priority Do-Not-Disturb while foreground; `false` → All/normal notifications (`tick.rs:296-300`). If a client omits it during mutation, the daemon treats it as `true` (`tick.rs:226`). |
| `target_fps` | integer **or** integer array | No | `None` (FAS keeps its current target) | Yes | FAS frame-rate target. Accepts **two shapes** — see [The `target_fps` field](#the-target_fps-field-single-value-or-array) below. Applied to the FAS controller when set (`tick.rs:159-170`). |
| `refresh_rate` | integer (Hz) | No | `None` (no display override) | Yes | Requested display refresh rate while foreground. Applied only when it differs from the currently applied rate (`tick.rs:287-293`); released back to automatic on exit by requesting `0` (`tick.rs:315-320`). |
| `mode` | string | No | `None` → **Performance** | Yes | Profile for this game. Parsed **case-insensitively**: `powersave` → Powersave, `balance` → Balance, **any other value _or_ missing → Performance** (`tick.rs:227-234`). So a typo like `mode = "perf"` silently resolves to Performance, not an error. |
| `ceiling` | string | No | `None` (no ceiling override) | Yes | Frequency-ceiling level for this game. Parsed to `CeilingLevel`; an **unparseable value is dropped to no-override**, not an error (`tick.rs:282-285`). |

:::note Where the enum values are defined
`mode` values map to `ProfileMode` (`src/common/types.rs:14-16`). `ceiling`
values map to `CeilingLevel` (`src/core/tweaks/ceiling.rs:20-24`). For what each
profile actually writes to the kernel, see
[Architecture overview → What each static profile changes](../architecture/overview#what-each-static-profile-changes).
:::

### The `target_fps` field: single value or array

`target_fps` has a **custom deserializer** (`TargetFpsConfig`,
`gamelist.rs:4-60`) that accepts either form:

```toml
# Single fixed target
target_fps = 120

# Array of candidate targets (adaptive)
target_fps = [60, 90, 120]
```

- A bare integer deserializes to `TargetFpsConfig::Single` (`gamelist.rs:32-44`).
- A TOML array deserializes to `TargetFpsConfig::Array` (`gamelist.rs:46-55`).
- If the key is absent the profile stores `None`; the default value of the type
  itself is `Single(60)` (`gamelist.rs:10-14`).

Both forms are passed to the FAS buffer via `to_buffer_config()`
(`gamelist.rs:74-81`). The array form is how a game exposes multiple acceptable
frame-rate steps to Frame-Aware Scheduling; the single form pins one target.

## How entries are added and changed

There is **no direct-edit CLI** for individual fields; entries are mutated over
the IPC socket (by the manager app, or by `auriyactl` for the subset it wraps —
see [Command reference](commands) and [IPC protocol](../internals/ipc-protocol)).
Each mutation rewrites the whole file.

### `ADD_GAME <package>` — injected defaults

Adding a package via IPC does **not** copy the shipped example values. It inserts
a fixed default profile (`src/daemon/ipc/handlers.rs:208-217`):

| Field | Value injected by `ADD_GAME` |
| --- | --- |
| `cpu_governor` | `"performance"` |
| `enable_dnd` | `true` |
| `mode` | `"performance"` |
| `target_fps`, `refresh_rate`, `ceiling` | unset (`None`) |

Adding a package that already exists returns an error (`ADD_GAME` → `add()`
bails, `gamelist.rs:136-143`).

### `UPDATE_GAME <package> [key=value ...]` — partial edit

`UPDATE_GAME` changes only the fields you name; unspecified fields are left as-is
(`GameList::update`, `gamelist.rs:155-182`). Recognized tokens
(`src/daemon/ipc/commands.rs`, `UpdateGame` parsing):

| Token | Sets field | Notes |
| --- | --- | --- |
| `gov=<name>` | `cpu_governor` | — |
| `dnd=<true\|false>` | `enable_dnd` | unparseable value falls back to `true` |
| `fps=<n>` | `target_fps` = `Single(n)` | ignored if `fps_array` is also given |
| `fps_array=<a,b,c>` | `target_fps` = `Array([...])` | takes precedence over `fps=`; empty list ignored |
| `rate=<hz>` | `refresh_rate` | — |
| `mode=<name>` | `mode` | — |
| `ceiling=<level>` | `ceiling` | — |

Updating a package that is not in the list returns an error (`gamelist.rs:178`).

### Persistence

Every successful `ADD_GAME` / `REMOVE_GAME` / `UPDATE_GAME` calls
`GameList::save`, which writes **atomically**: serialize to `gamelist.toml.tmp`,
then `rename` over the real file (`gamelist.rs:118-130`). A crash mid-write
cannot leave a half-written `gamelist.toml`. Note that a save re-serializes the
entire list, so any hand-added comments or unknown keys are lost on the next
mutation.

## Ordering and duplicates

- Entries are a `Vec`, searched linearly by `find()` (`gamelist.rs:132-134`), so
  file **order is preserved** on save.
- `ADD_GAME` refuses to insert a package that already exists.
- If you **hand-edit** the file to contain the same `package` twice, parsing
  succeeds and the daemon uses the **first** match; `REMOVE_GAME` then deletes
  **all** entries with that name (`retain`, `gamelist.rs:147`).

## Likely to drift first

- The `ADD_GAME` default profile (`handlers.rs:208-217`).
- The `UPDATE_GAME` token list (`commands.rs`).
- `mode` / `ceiling` accepted values, if new profiles or ceiling levels are
  added.

Re-verify against `src/core/config/gamelist.rs`, `src/daemon/tick.rs`, and
`src/daemon/ipc/`.
