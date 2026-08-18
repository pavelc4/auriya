# Configuration

Auriya reads two TOML files, both under `/data/adb/.config/auriya/`:

| File | Scope | Full reference |
| --- | --- | --- |
| `settings.toml` | **Global** daemon and scheduler defaults | [settings.toml reference](../reference/settings) |
| `gamelist.toml` | **Per-app** whitelist and overrides | [gamelist.toml reference](../reference/gamelist) |

This page is the orientation; the reference pages are the source of truth for
every key (type, default, whether the daemon actually consumes it, and evidence).

## How edits reach the daemon

- **From the manager app** — the app writes both files (and, for the game list,
  the daemon also writes it in response to app/CLI commands). This is the primary,
  supported path.
- **From the CLI** — `auriyactl` mutates the game list over IPC (`add-game`,
  `remove-game`, and raw `UPDATE_GAME`) and can trigger a settings reload with
  `auriyactl reload`. It has **no** command to edit individual `settings.toml`
  keys. See [Command reference](../reference/commands).
- **By hand** — you can edit the files directly, then `auriyactl reload` (or let
  the file watcher pick up the change).

## Two things to know before editing

1. **Some keys apply live, most at startup.** `cpu.default_governor`,
   `daemon.default_mode`, and `daemon.check_interval_ms` are re-read when you edit
   `settings.toml`; the FAS block (`[fas]`, `[dynamic_governor]`, `[modes.*]`) is
   read once at construction and needs a daemon restart to re-tune. Each key's
   behavior is in the [settings reference](../reference/settings#key-by-key-reference).
2. **`fas.default_mode` picks the active `[modes.*]`.** Only the mode it names
   drives FAS margin/thermal; the other `[modes.*]` blocks are inactive until
   selected. See [settings → `[modes.*]`](../reference/settings#modes).

## Invalid values

There is no `deny_unknown_fields`, so unknown keys are **silently ignored** and
some fields fall back to defaults rather than erroring (e.g. an unknown game
`mode` resolves to Performance, an unparseable `ceiling` is dropped). A malformed
`settings.toml` **aborts daemon startup**; a malformed `gamelist.toml` does too,
but a *missing* game list is treated as empty. Details in the reference pages.

## Next

[settings.toml reference](../reference/settings) ·
[gamelist.toml reference](../reference/gamelist) ·
[Profile scheduler](../internals/profile-scheduler).
