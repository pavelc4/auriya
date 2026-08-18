# Data Flow

Two independent flows cross the same three planes: **commands** (a client asks for
a state change) and **telemetry/state** (observed state flows back). They must be
read separately — a command is a request, telemetry is a report — and a failure at
either boundary must stay visible to the caller.

## End-to-end: everything running

The full picture once the module is installed and a game is in the foreground —
who talks to whom, over which channel, with the real path/command. Every arrow is
one of the four channels in the [table below](#the-four-channels-concretely).

```mermaid
flowchart TD
    app["Manager App (Compose) / auriyactl<br/><code>dev.auriya.app</code>"]
    sock["Unix Domain Socket<br/><code>/dev/socket/auriya.sock</code>"]
    daemon["Rust Daemon (auriya)<br/>Tokio Async Event Loop"]
    comp["Companion Service (AuriyaSysMon)<br/><code>app_process (root uid)</code>"]
    kernel["Kernel Interfaces<br/><code>/proc</code> · <code>/sys</code>"]

    subgraph config_files ["Persisted Config & Status Files"]
        cfg[("settings.toml<br/>gamelist.toml")]
        status["system_status"]
        cmd["auriya_cmd"]
    end

    app -->|"writes config (root)"| cfg
    cfg -.->|"watched by"| daemon
    app -->|"IPC commands: STATUS, SET_PROFILE, GET_STATS"| sock
    sock <-->|"request / JSON reply"| daemon

    comp -->|"writes Android state"| status
    status -.->|"watched by"| daemon
    daemon -->|"writes DnD & refresh rate"| cmd
    cmd -.->|"watched & replayed via Android APIs"| comp

    daemon -->|"guarded writes: governors / ceiling / FAS"| kernel
    kernel -->|"best-effort telemetry reads: freq / load / temp"| daemon
```

The app and `auriyactl` talk to the daemon **directly** over the socket for
commands and status. The companion is a *separate* participant: it feeds the
daemon observed Android state (`system_status`) and executes the Android-framework
actions the root daemon cannot (`auriya_cmd`) — see
[System tweaks → CmdWriter](../internals/system-tweaks#actions-routed-through-android--cmdwriter).

## Boot sequence (cold start → first tick)

What happens from power-on until the daemon is serving requests, per
`module/service.sh` and `src/daemon/run.rs` (full detail:
[overview → binary execution](overview#binary-execution-workflow)):

```mermaid
flowchart TD
    boot([Android boot_completed]) --> svc["service.sh: stop stale procs,<br/>rm stale socket/status/lock"]
    svc --> comp["app_process → start Companion (AuriyaSysMon)"]
    comp --> cw["Companion writes first system_status"]
    cw --> wait{"system_status<br/>appears within 10s?"}
    wait -->|no| fail1["boot aborts — daemon not started"]
    wait -->|yes| exec["exec auriya --settings … --gamelist …"]
    exec --> load{"load settings.toml<br/>+ gamelist.toml"}
    load -->|parse error| fail2["main returns — no daemon"]
    load -->|ok| trace["init tracing (log_level)"]
    trace --> ebpf["init eBPF frame stream<br/>(or fall back: sysfs FPS, FAS off)"]
    ebpf --> build["build Daemon: whitelist,<br/>FasController(FasTuning), ceiling, telemetry"]
    build --> bind["bind /dev/socket/auriya.sock<br/>+ spawn IPC listener"]
    bind --> watch["start watchers: settings, gamelist,<br/>module-update, companion.lock"]
    watch --> tick0["run one immediate tick"]
    tick0 --> loop([enter adaptive event loop])
```

## Steady-state: one game session, tick by tick

The command/telemetry round trip while a whitelisted game runs and the app polls:

```mermaid
sequenceDiagram
    autonumber
    actor User as User / Game
    participant Comp as Companion (AuriyaSysMon)
    participant Daemon as Rust Daemon (tick loop)
    participant Kernel as Kernel (/proc, /sys)
    participant App as App (Manager UI ~1Hz)

    User->>Comp: Game enters foreground
    Comp->>Daemon: Write system_status (pkg, pid)
    Note over Daemon: Watcher fires → instant tick<br/>Lock vendor, apply profile, attach eBPF
    Daemon->>Comp: Write auriya_cmd (DnD, refresh rate)
    Comp->>User: Replay via Android Framework APIs

    loop Each Tick (~500ms active game session)
        Daemon->>Daemon: Drain frames → FAS scaling decision
        Daemon->>Kernel: Write ScalingAction (CPU/GPU frequencies)
        Daemon->>Daemon: Refresh CurrentState (FPS, telemetry)
        App->>Daemon: GET_STATS (Unix socket)
        Daemon-->>App: JSON (FpsStats, thermals, battery)
        App->>App: Render telemetry & benchmark cards
    end

    User->>Comp: Game leaves foreground
    Comp->>Daemon: Write system_status (home/launcher)
    Note over Daemon: Instant tick → clear game state,<br/>restore default profile, detach eBPF
    App->>Daemon: GET_STATS
    Daemon-->>App: JSON (fps: null, standby)
```

The eBPF worker only drains frames while a PID is attached, so it costs nothing
outside a game session. `GET_STATS` computes on request — see
[Stats API](../reference/stats-api).

## The four channels concretely

| Direction | Mechanism | Payload | Reference |
| --- | --- | --- | --- |
| Client → daemon | Unix socket, newline text | commands: `STATUS`, `SET_PROFILE`, `ADD_GAME`, `GET_STATS`, … | [IPC protocol](../internals/ipc-protocol) |
| Companion → daemon | `system_status` file (watched) | foreground app/PID/UID, screen, battery-saver, Zen | below |
| Daemon → companion | `auriya_cmd` file (watched) | DnD filter, refresh rate | [System tweaks → CmdWriter](../internals/system-tweaks#actions-routed-through-android--cmdwriter) |
| Daemon → kernel | `/proc`, `/sys` writes | governors, ceilings, tweaks | [System tweaks](../internals/system-tweaks) |

The exact struct/field shapes of these payloads are in the
[data model](data-model).

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
- **`daemon.check_interval_ms`** (default 2 s) in normal foreground operation,
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

