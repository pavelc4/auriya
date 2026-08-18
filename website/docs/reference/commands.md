# Command Reference (`auriyactl`)

`auriyactl` is the command-line client for the Auriya daemon. It is a thin
wrapper over the [IPC protocol](../internals/ipc-protocol): almost every
subcommand opens the daemon's Unix socket, sends one text command, and prints
the reply.

:::info Verified against source
Traced to Auriya commit
[`10fe7c6`](https://github.com/pavelc4/auriya/tree/10fe7c6b56474a00513fec34ebac1376b30e95e6).
CLI definitions:
[`src/cli/app.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/cli/app.rs)
(clap subcommands),
[`src/cli/executor.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/cli/executor.rs)
(what each subcommand sends),
[`src/cli/output.rs`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/src/cli/output.rs)
(formatting).
:::

:::note Status of the CLI
The CLI is not the primary control surface — the manager app is. `auriyactl`
wraps a **subset** of IPC commands and has no equivalent for `UPDATE_GAME`,
`GET_FPS`-target editing, or per-field game edits. Use it for status checks,
scripting, and debugging.
:::

## Synopsis

```text
auriyactl [--socket <path>] <subcommand> [args]
```

- Running `auriyactl` with **no subcommand prints help and exits** non-zero
  (`arg_required_else_help = true`, `app.rs:5`).
- `--socket <path>` / `-s <path>` overrides the daemon socket. It is a **global**
  option — valid before or after the subcommand. Default:
  `/dev/socket/auriya.sock` (`SOCKET_PATH`, `src/common/constants.rs:1`;
  `executor.rs:14`).

### Liveness precondition

Before running any subcommand **except `status` and `restart`**, the CLI checks
that the socket is connectable; if not, it prints `Error: Daemon is not running`
and exits (`executor.rs:17-19`). `status` degrades to a "Not running" banner
instead of erroring; `restart` is a local operation that does not require a live
daemon (see below).

## Subcommands

Each subcommand maps to a raw IPC command (or, for `restart`, to local shell
actions). "Prints" describes stdout on success.

| Subcommand | Arguments | Sends (IPC) | Prints | Source |
| --- | --- | --- | --- | --- |
| `status` | — | `STATUS` | Formatted status block (subset — see below) | `executor.rs:140-148` |
| `enable` | — | `ENABLE` | raw reply (`OK ENABLED`) | `executor.rs:34-37` |
| `disable` | — | `DISABLE` | raw reply (`OK DISABLED`) | `executor.rs:39-42` |
| `reload` | — | `RELOAD` | `Configuration reloaded: OK RELOADED <n>` | `executor.rs:44-47` |
| `restart` | — | *(none — local)* | `Restarting daemon + companion...` then a tail hint | `executor.rs:49`, `115-159` |
| `set-profile` | `<performance\|balance\|powersave>` | `SET_PROFILE <MODE>` | `Profile set: OK SET_PROFILE <Mode>` | `executor.rs:51-56` |
| `set-fps` | `<fps>` (u32) | `SET_FPS <fps>` | `FPS set: OK SET_FPS <fps>` | `executor.rs:58-61` |
| `get-fps` | — | `GET_FPS` | `Current FPS: FPS=<measured> TARGET=<target>` | `executor.rs:63-66` |
| `add-game` | `<package>` | `ADD_GAME <package>` | `Game added: OK ADD_GAME <package>` | `executor.rs:68-71` |
| `remove-game` | `<package>` | `REMOVE_GAME <package>` | `Game removed: OK REMOVE_GAME <package>` | `executor.rs:73-76` |
| `list-games` | — | `GET_GAMELIST` | `Configured games:` + JSON array | `executor.rs:78-81` |
| `list-packages` | — | `LIST_PACKAGES` | `Installed packages:` + `pm list packages` output | `executor.rs:83-86` |
| `get-rates` | — | `GET_SUPPORTED_RATES` | `Supported refresh rates:` + JSON array | `executor.rs:88-91` |
| `set-log` | `<debug\|info\|warn\|error>` | `SETLOG <LEVEL>` | `Log level set: OK SET_LOG` | `executor.rs:93-98` |
| `get-pid` | — | `GET_PID` | `Daemon PID: PKG=<pkg> PID=<pid>` | `executor.rs:100-103` |
| `ping` | — | `PING` | `Daemon is alive (PONG)` or `Error: Daemon not responding` | `executor.rs:105-111` |
| `inject` | `<package>` | `INJECT <package>` | `Injected: OK INJECT` | `executor.rs:113-116` |
| `clear-inject` | — | `CLEAR_INJECT` | `Inject cleared: OK CLEAR_INJECT` | `executor.rs:118-121` |

Subcommand names use kebab-case (clap derives them from the enum variants in
`app.rs:14-52`); the raw socket protocol uses `SCREAMING_SNAKE_CASE`.

### `restart` is local, not an IPC command

`auriyactl restart` does **not** send the IPC `RESTART` command. It runs entirely
in the CLI process (`handle_restart`, `executor.rs:115-159`):

1. `killall -TERM auriya AuriyaSysMon`, wait 3 s, then `killall -KILL` the same
   (`stop_processes`, `executor.rs:127-136`).
2. Remove `/data/adb/.config/auriya/system_status` and `companion.lock`, and
   truncate `/data/adb/auriya/daemon.log` (`clear_runtime_state`,
   `executor.rs:138-153`).
3. Spawn `sh /data/adb/modules/auriya/service.sh`, redirecting output to
   `/data/adb/auriya/restart.log` (`launch_service`, `executor.rs:155-159`).

Because it shells out to `killall` and the module's `service.sh`, `restart`
needs root and only works on an installed device — not in a bare build tree. The
separate IPC `RESTART` command (used by the app) makes the *daemon* re-exec
itself instead; see [IPC protocol](../internals/ipc-protocol).

### `status` prints a subset of the daemon reply

The daemon's raw `STATUS` reply contains `ENABLED`, `PACKAGES`, `OVERRIDE`,
`LOG_LEVEL`, and multi-line telemetry (`FPS`, per-core CPU, GPU, thermal) —
documented in [IPC protocol](../internals/ipc-protocol). The CLI's pretty-printer
(`print_status`, `output.rs:1-40`) only renders four keys — `ENABLED`,
`PROFILE`, `PACKAGES`, `FPS` — and silently drops the rest via its catch-all
arm. Note `PROFILE` is matched by the printer but **not currently emitted** by
the daemon's `STATUS` response, so that line does not appear. To see everything
the daemon returns, talk to the socket directly (see the raw example below).

## Examples

Real invocations and their output shape (values illustrative; format strings are
exact, from `executor.rs`/`output.rs`):

```console
$ auriyactl ping
 Daemon is alive (PONG)

$ auriyactl set-profile performance
 Profile set: OK SET_PROFILE Performance

$ auriyactl get-fps
Current FPS: FPS=59.8 TARGET=60

$ auriyactl --socket /tmp/test.sock status
   	   Auriya Daemon Status
Daemon: Running

    Enabled:  true
    Games:    3 configured
    FPS:      59.8 SOURCE=ebpf
```

Talking to the socket directly (bypasses the CLI's subset view). The daemon
sends a greeting line first, then the reply:

```console
$ printf 'STATUS\nQUIT\n' | nc -U /dev/socket/auriya.sock
OK AURIYA IPC
ENABLED=true PACKAGES=3 OVERRIDE=None LOG_LEVEL=Info
FPS=59.8 SOURCE=ebpf
CPU_CORES=8 CPU_LOAD=42
...
BYE
```

## Error output

- Daemon not running (non-`status`/`restart` command): `Error: Daemon is not
  running`, non-zero exit (`executor.rs:18`).
- Any daemon-side failure is returned as an `ERR ...` line and printed verbatim
  inside the success wrapper (e.g. `Game added: ERR ADD_GAME "Game X already
  exists"`). The CLI does not currently translate `ERR` replies into a non-zero
  exit code for the send-and-print commands.

## Likely to drift first

The subcommand-to-IPC mapping table and the `status` field subset. Re-verify
against `src/cli/app.rs`, `src/cli/executor.rs`, and `src/cli/output.rs`.
