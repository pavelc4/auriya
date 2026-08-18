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

1. **Not every key in `settings.toml` is live.** Several keys are written by the
   app and parsed by the daemon but **not yet consumed** (they are Frame-Aware
   Scheduling scaffolding). Changing them has no effect today. Each is flagged in
   the [settings reference](../reference/settings#key-by-key-reference) with the
   hardcoded value actually in effect. This is deliberate, not a bug.
2. **Reload is selective.** On a `settings.toml` change the daemon only re-reads
   `cpu.default_governor` and `daemon.default_mode`; everything else applies at
   startup and needs a daemon restart (`auriyactl restart`) to take effect. See
   [settings → reload behavior](../reference/settings#reload-behavior).

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
