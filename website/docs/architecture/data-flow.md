# Data Flow

Two independent flows cross the same three planes: **commands** (a client asks for
a state change) and **telemetry/state** (observed state flows back). They must be
read separately — a command is a request, telemetry is a report — and a failure at
either boundary must stay visible to the caller.

## Command / status round trip

```text
Manager UI          Companion Service       Rust Daemon       Kernel Interfaces
    |                       |                    |                    |
    | Request status/action |                    |                    |
    |---------------------->|                    |                    |
    |                       | Local command      |                    |
    |                       |------------------->|                    |
    |                       |                    | Read/write state   |
    |                       |                    |------------------->|
    |                       |                    | Current state      |
    |                       |                    |<-------------------|
    |                       | Structured status  |                    |
    |                       |<-------------------|                    |
    | Updated UI state      |                    |                    |
    |<----------------------|                    |                    |
```

Not every request goes through the companion: the app and `auriyactl` also talk to
the daemon **directly** over `/dev/socket/auriya.sock` for commands and status
([IPC protocol](../internals/ipc-protocol)). The companion path above is
specifically for Android-framework actions (DnD, refresh rate) the root daemon
cannot perform itself.

## The two channels concretely

| Direction | Mechanism | Payload | Reference |
| --- | --- | --- | --- |
| Client → daemon | Unix socket, newline text | commands like `STATUS`, `SET_PROFILE`, `ADD_GAME` | [IPC protocol](../internals/ipc-protocol) |
| Companion → daemon | `system_status` file (watched) | foreground app/PID, screen, battery-saver, Zen | below |
| Daemon → companion | `auriya_cmd` file (watched) | DnD filter, refresh rate | [System tweaks → CmdWriter](../internals/system-tweaks#actions-routed-through-android--cmdwriter) |
| Daemon → kernel | `/proc`, `/sys` writes | governors, ceilings, tweaks | [System tweaks](../internals/system-tweaks) |

## `system_status` — companion → daemon

The companion writes `/data/adb/.config/auriya/system_status` whenever the
foreground app, screen, battery-saver, or Zen state changes. The wire format is
line-oriented (`src/core/system_status/mod.rs:8-11`):

```text
focused_app <package> <pid> <uid>
screen_awake <0|1>
battery_saver <0|1>
zen_mode <0|1|2|3>
```

The daemon's watcher reloads this file and merges it into a `CurrentState`
snapshot that IPC clients read. Fields are optional — a partial write updates only
the lines present (`SystemStatus`, `mod.rs:27-56`). The daemon uses `focused_app`
+ `focused_pid` for [game detection](../internals/game-detection), and
`screen_awake` + `battery_saver` to force the power-save branch of the
[scheduler](../internals/profile-scheduler#decision-order).

## Tick flow

The daemon runs a variable-cadence tick (see
[Architecture overview → Event loop](../architecture/overview#event-loop-and-execution-cadence)
for the exact event table):

- **≈ 500 ms** while a validated game session is active,
- **5 s** in normal foreground operation,
- **10 s** when screen-off / battery-saver suspends normal work.

Each tick reads the cached companion snapshot, handles power-saving overrides
first, resolves the package/PID (or an `INJECT` override), then either runs FAS
for a known game or applies the appropriate profile
([Profile scheduler](../internals/profile-scheduler)). A copy-on-write game-list
snapshot avoids holding a lock across async work. A tick can also be triggered
early — outside the timer — by a companion update, a config change, or a tracked
PID exiting ([game detection](../internals/game-detection#liveness-tracking-and-instant-exit)).

## Failure visibility

- IPC errors are returned to the client as `ERR …` lines
  ([IPC protocol → response conventions](../internals/ipc-protocol#response-conventions)).
- Kernel-write failures are best-effort and logged, not fatal
  ([System tweaks](../internals/system-tweaks#guarded-best-effort-writes)).
- A dead companion is detected via `companion.lock`; display/DnD then fall back to
  Android `settings put`
  ([overview](../architecture/overview#control-and-status-paths)).
