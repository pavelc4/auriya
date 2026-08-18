# Game Detection

"Game detection" is how the daemon decides **which package is in the
foreground**, whether it is one Auriya manages, and whether its process is still
alive. Auriya does **not** scan the system itself — the Android companion service
supplies the focused package/PID, and the daemon validates and tracks it.

:::info Verified against source
Traced to Auriya commit
[`10fe7c6`](https://github.com/pavelc4/auriya/tree/10fe7c6b56474a00513fec34ebac1376b30e95e6).
Files:
[`src/core/dumpsys/activity.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/core/dumpsys/activity.rs)
(PID validity/verification),
[`src/core/pid_tracker.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/core/pid_tracker.rs)
(liveness + exit events),
[`src/daemon/tick.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/daemon/tick.rs)
(the decision path).
:::

## Where the foreground package comes from

The companion service observes the Android task stack and writes the focused
package and PID into `/data/adb/.config/auriya/system_status`. The daemon reads
that snapshot each tick — it never calls `dumpsys` for foreground detection
itself (`dumpsys/activity.rs` module comment: the old dumpsys-scanning path was
removed). See [Data flow](../architecture/data-flow) and
[IPC protocol → data flow direction](ipc-protocol#direction-of-data-flow).

An `INJECT <package>` IPC command overrides the companion's focused package for
debugging; `CLEAR_INJECT` removes the override (see
[IPC protocol](ipc-protocol#state-toggles)).

## The whitelist

At startup and on every `gamelist.toml` change, the daemon builds a `HashSet` of
package names from the game list — the "whitelist" (`src/daemon/run.rs:212-217`,
rebuilt by `rebuild_whitelist`, `run.rs:320-327`). A package is "a game" to
Auriya iff it is in this set. Matching is **exact** (no wildcards); see
[gamelist reference](../reference/gamelist).

## PID validity vs. package verification

Two cheap checks in `dumpsys/activity.rs`:

| Function | Check | Use |
| --- | --- | --- |
| `is_pid_valid(pid)` | `pid > 0` **and** `/proc/<pid>` exists | Drop a stale PID reference cheaply. |
| `verify_pid_package(pid, pkg)` | reads `/proc/<pid>/cmdline`, compares to `pkg` | Sanity-check that a PID really is the expected package. |

`verify_pid_package` matches the process name up to the first `\0` or `:`
separator, then also accepts a substring match so **isolated processes** named
`<pkg>:<suffix>` (a common Android pattern) still count as the package
(`activity.rs`, `verify_pid_package`).

## Liveness tracking and instant exit

Once a whitelisted game's PID is validated, the daemon spawns a `PidTracker`
(`pid_tracker.rs`). It serves two roles:

1. **Cheap poll** — `PidTracker::is_alive()` is a non-blocking probe the tick loop
   uses on the fast path.
2. **Instant exit event** — a background thread blocks until the process actually
   dies and then pushes a `DaemonEvent::PidExited`, so the daemon re-evaluates
   *immediately* instead of waiting for the next timer tick.

Two kernel paths, chosen at runtime:

- **`pidfd_open` (Linux ≥ 5.3)** — the tracker opens a pidfd via raw syscall
  (number `434` on aarch64, `439` on x86-64) and blocks in `poll()` on it. Zero
  wakeups until the process exits. `is_alive` is a non-blocking `poll` on the same
  fd (`pid_tracker.rs`, `open_pidfd`, `pidfd_is_alive`).
- **`/proc` fallback** — on older kernels, the watcher polls `/proc/<pid>` every
  **150 ms** (`wait_proc_poll`, `POLL_INTERVAL_MS = 150`), and `is_alive` falls
  back to a `/proc/<pid>` existence check.

An `eventfd` lets `Drop` interrupt the blocked watcher the instant the daemon
stops tracking (e.g. on game switch), so no thread lingers (`pid_tracker.rs`,
`Drop`, `make_eventfd`). The exit event is sent with `try_send`, not
`blocking_send`: if the channel is full the event is dropped and the next tick
catches the exit via `is_alive` — deliberately chosen to avoid a deadlock where
`Drop` runs on the same tokio worker that drains the channel (`track_loop`
comment).

:::note This is a real-device-tested path
`pid_tracker.rs` ships with tests that spawn a child, track it, kill it, and
assert the `PidExited` event arrives (`detects_child_process_exit`) and measures
exit→event latency (`pidfd_exit_latency`, target well under 100 ms). They
exercise the actual `pidfd_open`/`poll` path on the target kernel.
:::

## How detection drives the tick

Each tick resolves the package/PID, then (`process_tick_logic`,
`src/daemon/tick.rs`):

- **Same package, PID still alive** → fast path: the profile is *not* reapplied;
  only FAS may adjust within the session (see
  [Profile scheduler](profile-scheduler)).
- **New package, or previous PID exited** → full re-evaluation.
- **Whitelisted package with a valid PID** → enter/refresh the game session.
- **Not whitelisted, or PID invalid/missing, or no foreground package** → clear
  game state and apply the default profile.

The exact branch order and what each branch writes are documented in
[Profile scheduler](profile-scheduler).

## Likely to drift first

The `pidfd_open` syscall numbers, the 150 ms fallback interval, and the
isolated-process (`pkg:suffix`) matching rule. Re-verify against
`src/core/pid_tracker.rs` and `src/core/dumpsys/activity.rs`.
