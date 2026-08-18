# Performance Tuning

How to pick Auriya's settings and what values to use — the "which knob, which
value, and why" guide. For the exhaustive per-key spec (types, defaults, what
the daemon consumes) see the [settings reference](../reference/settings) and
[gamelist reference](../reference/gamelist); this page is the practical layer on
top.

:::tip Use the manager app — you do not need to edit files
**Every setting on this page is configurable from the Auriya manager app.** You
do **not** need to open a terminal or hand-edit `settings.toml` / `gamelist.toml`.
The app writes both files for you and the daemon picks the changes up. Manual
file editing exists (see [Configuration](configuration)) but is a fallback for
power users, not the intended workflow.
:::

## The two levels of configuration

| Level | Sets | Edit in the app under |
| --- | --- | --- |
| **Global** (`settings.toml`) | daemon defaults, FAS behavior, thermal ceilings | Settings / Config screen |
| **Per-game** (`gamelist.toml`) | a game's governor, target FPS, refresh rate, mode, ceiling | the game's entry on the Games screen |

Per-game overrides win while that game is foreground; global values apply
everywhere else.

## FAS: what it is and how to set it

Frame-Aware Scheduling (FAS) watches real frame timing and nudges CPU/GPU up or
down to hold your target FPS with the least power. It only runs for **whitelisted
games** and only when the eBPF frame probe is available (see
[FPS detection](../internals/fps-detection) and
[Kala eBPF frame probe](../internals/kala-research)).

### Enabling FAS

`[fas] enabled = true` (default). If the device kernel can't load the eBPF probe,
the daemon automatically falls back to sysfs-only FPS and disables FAS — nothing
to configure.

### FAS modes (the `margin` knob)

FAS behavior is chosen by **modes**. Each mode is a `margin` (FPS headroom) plus a
`thermal_threshold`. `fas.default_mode` selects which mode is active. **Smaller
margin = more aggressive** (pushes clocks harder to stay glued to the target);
**larger margin = calmer** (tolerates dropping a little below target to save
power/heat).

| Mode | `margin` | `thermal_threshold` | Feel |
| --- | --- | --- | --- |
| `powersave` | 5.0 | 80 °C | Coolest/most battery; lets FPS sag furthest below target |
| `balance` | 2.0 | 90 °C | **Default** — good FPS, sensible heat |
| `performance` | 1.0 | 95 °C | Chases the target tightly |
| `fast` | 0.0 | 95 °C | Zero headroom — hugs the frame deadline hardest |

These are the shipped values and match the upstream
[fas-rs](https://github.com/shadow3aaa/fas-rs) presets Auriya's controller is
adapted from. `fast` is a **FAS margin preset**, not a separate CPU profile —
don't confuse it with the three profile modes below.

### Recommended settings by goal

| Your goal | `fas.default_mode` | Notes |
| --- | --- | --- |
| Balanced daily driver | `balance` | Leave everything default. |
| Max smoothness (comp games) | `performance` | Tighter frame pacing; more heat/battery. |
| Absolute lowest latency | `fast` | Only if your device stays cool enough (95 °C ceiling). |
| Long sessions / hot device / battery | `powersave` | Accepts minor FPS dips to run cool. |

`[dynamic_governor]` (defaults `cv_threshold = 0.15`, `debounce_frames = 3`) tunes
how FAS decides CPU-bound vs GPU-bound. **Leave these at defaults** unless you are
diagnosing a specific bottleneck-misclassification — they are advanced knobs, not
everyday settings.

:::note FAS tuning applies at restart
Changes to `[fas]`, `[dynamic_governor]`, and `[modes.*]` are read when the daemon
starts. After changing them, restart the daemon (the app does this for you; from a
shell it's `auriyactl restart`). `cpu.default_governor` and `daemon.default_mode`
apply live. See [settings → reload behavior](../reference/settings#reload-behavior).
:::

## Per-game tuning

On the Games screen, each whitelisted game can override:

| Field | What it does | Typical value |
| --- | --- | --- |
| `target_fps` | FAS target; single (`120`) or steps (`[60,90,120]`) | your game's cap, e.g. `120` |
| `cpu_governor` | governor while this game runs | `performance` or `walt` |
| `mode` | static profile: `performance` / `balance` / `powersave` | `performance` for demanding games |
| `refresh_rate` | requested display Hz | match `target_fps` |
| `ceiling` | frequency-ceiling level | leave default unless throttling |
| `enable_dnd` | Do-Not-Disturb while playing | `true` for focus |

**Recommended per-game starting point for a demanding game:** `mode =
performance`, `target_fps` = the game's real cap, `refresh_rate` = same,
`cpu_governor = performance`. Tune down toward `balance` if the device runs hot.

`target_fps` as an **array** (`[60, 90, 120]`) lets FAS match whichever rate the
game actually renders at — useful for games with in-menu vs in-match rate changes.

## Profile modes vs FAS modes (don't mix them up)

- **Profile modes** — `performance` / `balance` / `powersave` (3). These set CPU
  governor, GPU mode, and tweaks. Chosen per-game via `mode`, or globally via
  `daemon.default_mode`. What each writes:
  [overview → static profiles](../architecture/overview#what-each-static-profile-changes).
- **FAS modes** — `powersave` / `balance` / `performance` / `fast` (4). These are
  just `margin` + `thermal_threshold` presets for the FAS controller, chosen via
  `fas.default_mode`. `fast` exists only here.

Same words, different layers: profile modes decide the baseline; FAS modes decide
how aggressively FAS chases the frame target on top of it.

## See also

- [settings.toml reference](../reference/settings) — every global key.
- [gamelist.toml reference](../reference/gamelist) — every per-game field.
- [Profile scheduler](../internals/profile-scheduler) — how a profile is chosen each tick.
