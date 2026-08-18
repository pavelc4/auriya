# IPC Protocol

The daemon exposes a local Unix socket. The manager app and `auriyactl` both use
it to send commands and read status. This page documents the wire format and
every command, request and response, exactly as implemented.

:::info Verified against source
Traced to Auriya commit
[`10fe7c6`](https://github.com/pavelc4/auriya/tree/10fe7c6b56474a00513fec34ebac1376b30e95e6).
Command grammar:
[`src/daemon/ipc/commands.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/daemon/ipc/commands.rs)
(`Command::from_str`). Handlers and responses:
[`src/daemon/ipc/handlers.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/daemon/ipc/handlers.rs).
:::

## Transport

| Property | Value | Source |
| --- | --- | --- |
| Socket path | `/dev/socket/auriya.sock` | `SOCKET_PATH`, `src/common/constants.rs:1` |
| Type | `AF_UNIX` stream | `handle_client(stream: UnixStream, …)`, `handlers.rs` |
| Framing | Newline-delimited UTF-8 text, one command per line | `reader.read_line`, `handlers.rs` |
| Greeting | Server sends `OK AURIYA IPC\n` immediately on connect | `handlers.rs` (`write_all(b"OK AURIYA IPC\n")`) |
| Max input | **256 bytes per line**; longer → `ERR input too long`, line skipped | `handlers.rs` (`if s.len() > 256`) |
| Whitespace | Each line is trimmed; commands split on whitespace | `commands.rs` (`s.split_whitespace()`) |
| Session | Multiple commands per connection until `QUIT` or EOF | `while reader.read_line(...) > 0` |

A minimal exchange with `nc`:

```console
$ nc -U /dev/socket/auriya.sock
OK AURIYA IPC          ← greeting (server → client)
PING                   ← you type this
PONG                   ← reply
QUIT
BYE
```

### Response conventions

- Success replies start with `OK ` (mutations) or return data directly (e.g.
  `PONG`, JSON, `STATUS` fields).
- Errors start with `ERR `. A parse failure yields `ERR <usage or reason>`; the
  unknown-command reply is `ERR unknown command (try HELP)` (`commands.rs`).
- `QUIT` replies `BYE` and closes; empty computed responses are not written.

## Command grammar and aliases

`Command::from_str` accepts a canonical token and, for many commands, a
no-underscore alias (`commands.rs`). Command tokens are matched
**case-sensitively** as shown (they are upper-case); their *arguments* may be
normalized (profile/log tokens are upper-cased, `mode=` for games is
lower-cased downstream).

| Canonical | Alias | Argument |
| --- | --- | --- |
| `HELP` | `?` | — |
| `STATUS` | — | — |
| `ENABLE` / `DISABLE` | — | — |
| `RELOAD` | — | — |
| `RESTART` | — | — |
| `SETLOG` | `SET_LOG` | `<DEBUG\|INFO\|WARN\|ERROR>` |
| `SET_FPS` | `SETFPS` | `<u32>` |
| `GET_FPS` | `GETFPS` | — |
| `GET_SUPPORTED_RATES` | `GETRATES` | — |
| `GET_STATS` | `GETSTATS` | — |
| `INJECT` | — | `<package>` |
| `CLEAR_INJECT` | `CLEARINJECT` | — |
| `GETPID` | `GET_PID` | — |
| `PING` | — | — |
| `QUIT` | — | — |
| `SET_PROFILE` | `SETPROFILE` | `<PERFORMANCE\|BALANCE\|POWERSAVE>` |
| `ADD_GAME` | `ADDGAME` | `<package>` |
| `REMOVE_GAME` | `REMOVEGAME` | `<package>` |
| `UPDATE_GAME` | `UPDATEGAME` | `<package> [gov= dnd= fps= fps_array= rate= mode= ceiling=]` |
| `GET_GAMELIST` | `GETGAMELIST` | — |
| `LIST_PACKAGES` | `LISTPACKAGES` | — |

:::warning The `HELP` reply is incomplete
The built-in `HELP` text (`handlers.rs`, `const HELP`) lists only a subset —
it omits `SET_FPS`, `GET_FPS`, `GET_SUPPORTED_RATES`, `SET_PROFILE`,
`GET_GAMELIST`, `UPDATE_GAME`, `LIST_PACKAGES`, `RESTART`, and `QUIT`. Trust this
page (derived from the parser), not the `HELP` output, for the full set.
:::

## Command reference

Each entry gives the exact response format string from `handlers.rs`. `{…}`
marks interpolated values.

### Introspection

| Command | Success response | Errors |
| --- | --- | --- |
| `PING` | `PONG` | — |
| `HELP` / `?` | Multi-line command list (partial — see warning) | — |
| `GETPID` / `GET_PID` | `PKG={pkg} PID={pid}`, or `PKG={pkg} PID=None`, or `PKG=None PID=None` | — |
| `GET_FPS` | `FPS={measured:.1} TARGET={target}` (measured `0` if none) | — |
| `GET_SUPPORTED_RATES` | JSON array of unique refresh rates, e.g. `[60,90,120]` (deduped/sorted from cached display modes) | `ERR JSON {e}` |
| `GET_STATS` | Single-line JSON perf snapshot (fps/thermal/battery/cpu/gpu/session) — full schema in [Stats API](../reference/stats-api) | `ERR JSON {e}` |
| `STATUS` | See [STATUS format](#status-response) below | — |

#### STATUS response

First line, always present:

```text
ENABLED={bool} PACKAGES={count} OVERRIDE={Option<pkg>} LOG_LEVEL={level}
```

Then zero or more telemetry lines, emitted only when the corresponding data is
present in `CurrentState` (`handlers.rs`, `Command::Status`):

```text
FPS={value:.1} SOURCE={ebpf|sysfs|?}
CPU_CORES={n} CPU_LOAD={pct}
CORE_{id}={id} online={bool} freq={khz} governor={name} cluster={Little|Big|Prime|…}
GPU_FREQ={mhz} GPU_LOAD={pct} GPU_VENDOR={vendor}
TEMP_CPU={c|N/A} TEMP_GPU={c|N/A}
```

One `CORE_{id}` line is emitted per online/known core. See
[FPS detection](fps-detection) for `SOURCE` semantics.

### State toggles

| Command | Success | Notes |
| --- | --- | --- |
| `ENABLE` | `OK ENABLED` | Sets the atomic enabled flag (`Ordering::Release`). |
| `DISABLE` | `OK DISABLED` | Clears it. |
| `SETLOG <LEVEL>` | `OK SET_LOG` | Live-reloads the `tracing` filter (`run.rs:378-392`). Bad level → `ERR usage: SETLOG <DEBUG\|INFO\|WARN\|ERROR>`. |
| `INJECT <pkg>` | `OK INJECT` | Forces a foreground package for debugging (overrides the companion). |
| `CLEAR_INJECT` | `OK CLEAR_INJECT` | Clears the override. |

### Configuration lifecycle

| Command | Success | Errors |
| --- | --- | --- |
| `RELOAD` | `OK RELOADED {n}` (`n` = reload result) | `ERR RELOAD {e}` |
| `RESTART` | *(no response — daemon re-execs)* | `ERR RESTART_FAILED` if the relaunch spawn fails |

`RELOAD` re-reads config; only `cpu.default_governor` and `daemon.default_mode`
actually take effect on reload (see [settings reference](../reference/settings#reload-behavior)).

`RESTART` clears `/data/adb/auriya/daemon.log`, spawns
`sh -c "sleep 2 && sh /data/adb/modules/auriya/service.sh"` in a new session
(`setsid`), then exits the current process after 500 ms (`handlers.rs`,
`Command::Restart`). Because it returns before replying, clients see the
connection close rather than an `OK`. This is distinct from `auriyactl restart`,
which does the kill/relaunch itself — see [Command reference](../reference/commands#restart-is-local-not-an-ipc-command).

### Profile control

| Command | Success | Errors |
| --- | --- | --- |
| `SET_PROFILE <MODE>` | `OK SET_PROFILE {Mode}` | `ERR SET_PROFILE {e}` on apply failure; `ERR usage: SETPROFILE <…>` on bad token |
| `SET_FPS <n>` | `OK SET_FPS {n}` | `ERR usage: SET_FPS <number>` on non-integer |

`SET_PROFILE` takes a **process-wide profile lock** before applying, so
concurrent profile writes cannot interleave (`handlers.rs`, `profile_lock`).
`MODE` ∈ `PERFORMANCE`/`BALANCE`/`POWERSAVE`. What each profile writes is
documented once in
[Architecture overview → What each static profile changes](../architecture/overview#what-each-static-profile-changes).

### Game-list mutations

All persist to `gamelist.toml` atomically on success (see
[gamelist reference](../reference/gamelist#persistence)). All can return
`ERR lock poisoned` if the shared lock is poisoned, or `ERR SAVE_GAMELIST {e}` if
the write fails after a successful in-memory change.

| Command | Success | Command-specific error |
| --- | --- | --- |
| `ADD_GAME <pkg>` | `OK ADD_GAME {pkg}` | `ERR ADD_GAME {e}` (e.g. already exists) |
| `REMOVE_GAME <pkg>` | `OK REMOVE_GAME {pkg}` | `ERR REMOVE_GAME {e}` (e.g. not found) |
| `UPDATE_GAME <pkg> [k=v…]` | `OK UPDATE_GAME {pkg}` | `ERR UPDATE_GAME {e}` (e.g. not found) |
| `GET_GAMELIST` | JSON array of game profiles | `ERR GET_GAMELIST {e}` |
| `LIST_PACKAGES` | Raw `pm list packages` output | `ERR LIST_PACKAGES {e}` |

`ADD_GAME` inserts a **fixed default profile** (governor `performance`, DnD on,
mode `performance`), not the shipped example values — see
[gamelist reference → ADD_GAME](../reference/gamelist#add_game-package--injected-defaults).
`UPDATE_GAME` token syntax (`gov=`, `dnd=`, `fps=`, `fps_array=`, `rate=`,
`mode=`, `ceiling=`) is documented in the same page.

### Session

| Command | Response | Notes |
| --- | --- | --- |
| `QUIT` | `BYE` | Closes the connection. |
| *(unknown)* | `ERR unknown command (try HELP)` | Any unrecognized token. |
| *(line > 256 bytes)* | `ERR input too long` | Line skipped; connection stays open. |

## Direction of data flow

Commands and status flow **into** the daemon over this socket. Companion-observed
state (focused app, screen/battery/zen) flows the **other** way — the companion
writes `/data/adb/.config/auriya/system_status`, which the daemon watches. See
[Data flow](../architecture/data-flow) and [Game detection](game-detection).

## Likely to drift first

The per-command response strings and the alias list — they are literal format
strings in `handlers.rs`. Re-verify against `src/daemon/ipc/commands.rs` and
`src/daemon/ipc/handlers.rs`. The stale `HELP` text is a known gap.
