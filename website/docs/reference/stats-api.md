# Stats API & FPS Recorder (`GET_STATS`)

Auriya exposes a live performance snapshot the manager app polls to render its
telemetry cards (FPS, temps, battery, CPU/GPU clocks) and to drive per-game FPS
recording. This page is the stable contract for that data.

:::info Status
The **`GET_STATS` API is implemented and verified on-device.** The **auto-record
feature is app-side** — the daemon supplies the live data and the session
trigger; the recording/accumulation and its UI live in the manager app (see
[Auto-record](#fps-auto-record-app-side)). Verified against Auriya commit range
adding `src/core/stats/mod.rs` + `src/core/telemetry/battery.rs`.
:::

## Transport

| Fact | Value |
| --- | --- |
| Command | `GET_STATS` (alias `GETSTATS`) |
| Channel | Unix socket `/dev/socket/auriya.sock` (see [IPC protocol](../internals/ipc-protocol)) |
| Response | one line of JSON, then the connection closes on `QUIT` |
| Cost | computed **on request** — the daemon accumulates nothing between polls |

From a root shell:

```console
$ printf 'GET_STATS\nQUIT\n' | nc -U /dev/socket/auriya.sock
OK AURIYA IPC
{"fps":{"avg":118.0,...},"thermal":{...},...}
BYE
```

## JSON schema (one group = one UI card)

```json
{
  "fps":     { "avg": 118.0, "peak": 258.9, "low_1pct": 78.5, "jank": 2, "frames": 600 },
  "thermal": { "cpu_c": 64.7, "gpu_c": null, "battery_c": 41.5 },
  "battery": { "pct": 100, "current_ma": 573, "voltage_v": 4.23, "status": "Charging", "health": "Good" },
  "cpu":     { "load_pct": 60.0, "cores": [ { "id": 0, "khz": 1804800, "gov": "walt", "cluster": "Little", "online": true } ] },
  "gpu":     { "mhz": 580, "load_pct": null, "vendor": "kgsl" },
  "session": { "pkg": "com.mobile.legends", "profile": "performance", "active": true }
}
```

### Field reference

| Group | Field | Meaning |
| --- | --- | --- |
| `fps` | `avg` | Mean FPS over the window (`1 / mean frametime`). |
| | `peak` | Fastest single frame (`1 / min frametime`). |
| | `low_1pct` | Mean FPS of the worst 1% of frames — the stutter metric. |
| | `jank` | Frames slower than `target × 1.5`. |
| | `frames` | Sample count the window was computed from. |
| `thermal` | `cpu_c` / `gpu_c` / `battery_c` | °C. `battery_c` lives here (it is a temperature), not in `battery`. |
| `battery` | `pct` | Charge 0–100 %. |
| | `current_ma` | Instantaneous current. **Sign is device-specific** — use `status`, not the sign, for direction. |
| | `voltage_v` | Terminal voltage. |
| | `status` / `health` | e.g. `Charging` / `Good`. |
| `cpu` | `load_pct` | Overall CPU load. |
| | `cores[]` | Per-core `id`, `khz` (clock), `gov` (governor), `cluster` (`Little`/`Big`/`Prime`), `online`. |
| `gpu` | `mhz` / `load_pct` / `vendor` | GPU clock, busy %, driver. |
| `session` | `pkg` | Foreground package. |
| | `profile` | Active profile (`performance`/`balance`/`powersave`). |
| | `active` | **`true` only when a whitelisted game with a live PID is running.** When `true`, `pkg` is that game. This is the record trigger. |

### Null rules (must handle)

- **`fps` is `null`** when no game is running (idle) — render the FPS card as
  "inactive", not "0".
- **Any field can be `null`** when the device does not expose that node (e.g.
  `gpu_c`, `gpu.load_pct` on some SoCs). Each card renders independently and skips
  null fields — never crash on a null.
- **`session.active == false`** means no managed game (could be another app in the
  foreground). Only `active == true` is a game session.

## Recommended access method (for the UI)

The socket is root-only (app-uid cannot open it under SELinux), so poll it through
the app's existing libsu root shell — the same pattern `OverlayService` already
uses. **Do not write a raw `LocalSocket` client.**

```kotlin
// Persistent root shell (libsu). Poll on a coroutine loop.
fun fetchStats(): Stats? {
    val raw = RootShell.run("printf 'GET_STATS\\nQUIT\\n' | timeout 2 nc -U /dev/socket/auriya.sock")
    val json = raw?.lineSequence()?.firstOrNull { it.startsWith("{") } ?: return null
    return Json { ignoreUnknownKeys = true }.decodeFromString<Stats>(json)  // kotlinx.serialization
}
```

- Poll cadence: reuse the existing `update_interval_ms` pref (default 1000 ms).
  ~1 Hz is the design point; higher is unnecessary and the daemon computes
  on-request anyway.
- Parse with `ignoreUnknownKeys = true` so future daemon fields never break the UI.

## FPS auto-record (app-side)

Auto-record is orchestrated **in the manager app**, not the daemon — `GET_STATS`
already provides everything needed:

- **Enable/disable per whitelisted game** — store as an app preference keyed by
  package. Do **not** add a field to `gamelist.toml`; the daemon does not consume
  it and it would be dead config. The whitelist itself *is* `gamelist.toml`.
- **Trigger** — watch `session.active`. On `false → true` (and the game has
  auto-record enabled), start a recording buffer for `session.pkg`; while `true`,
  append each poll's `fps` (plus any telemetry you want) with a timestamp; on
  `true → false`, finalize a session summary (avg, min `low_1pct`, max `cpu_c`,
  total `jank`, duration).
- **Run it in a foreground service** (like `OverlayService`) so recording
  continues while the game — not the app UI — is in the foreground.
- **Store recordings in the app's own sandbox** (`filesDir` / Room / DataStore).
  Never write under `/data/adb`.

### Resolution ceiling

Poll-based recording is ~1 sample/second (coarse) — right for a session FPS graph
and summary. **Per-frame traces are not available this way**; that would require a
new daemon streaming API (out of scope today). Flag it if the product needs
frame-level detail.

## See also

- [IPC protocol](../internals/ipc-protocol) — the socket and every command.
- [FPS detection](../internals/fps-detection) — where the FPS numbers come from.
- [Performance tuning](../getting-started/performance-tuning) — FAS modes & values.
