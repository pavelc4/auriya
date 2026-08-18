---
sidebar_position: 4
---

# Kala eBPF Frame Probe

This page describes the exact Kala revision consumed by Auriya: the git
dependency recorded in `Cargo.lock` as
`be1061bd032b4faf5e6ef1cf5eec19d924a5caf3` (`github.com/pavelc4/kala`, branch
`main`). The source was read from the Cargo checkout at that revision.

Source revision: [`pavelc4/kala@be1061b`](https://github.com/pavelc4/kala/tree/be1061bd032b4faf5e6ef1cf5eec19d924a5caf3). Primary implementation files: [`kala/src/lib.rs`](https://github.com/pavelc4/kala/blob/be1061bd032b4faf5e6ef1cf5eec19d924a5caf3/kala/src/lib.rs), [`kala/src/uprobe.rs`](https://github.com/pavelc4/kala/blob/be1061bd032b4faf5e6ef1cf5eec19d924a5caf3/kala/src/uprobe.rs), [`kala/src/tracker.rs`](https://github.com/pavelc4/kala/blob/be1061bd032b4faf5e6ef1cf5eec19d924a5caf3/kala/src/tracker.rs), [`kala/src/wire.rs`](https://github.com/pavelc4/kala/blob/be1061bd032b4faf5e6ef1cf5eec19d924a5caf3/kala/src/wire.rs), and [`kala-ebpf/src/main.rs`](https://github.com/pavelc4/kala/blob/be1061bd032b4faf5e6ef1cf5eec19d924a5caf3/kala-ebpf/src/main.rs).

## What Kala measures

Kala is a Rust library plus a `no_std` eBPF program. The probe attaches a
**uprobe** (a user-space function entry probe) to Android's
`/system/lib64/libgui.so`. Its target is one of several mangled
`android::Surface::queueBuffer` symbols (`kala/src/uprobe.rs`,
`QUEUE_BUFFER_SYMBOLS`). `queueBuffer` is called when an application hands a
buffer to SurfaceFlinger, so Kala observes frame submissions rather than GPU
work or display-scanout completion.

The eBPF entry point is `kala_frame_probe` in `kala-ebpf/src/main.rs`. It reads
`ctx.arg(1)` as the buffer pointer and obtains a kernel monotonic timestamp with
`bpf_ktime_get_ns()`. It writes this pair to the `RING_BUF` map:

```text
FrameRecord {
    ktime_ns: u64,  // monotonic nanoseconds
    buffer:   u64,  // queueBuffer buffer pointer
}
```

The ring buffer is 256 KiB (`RingBuf::with_byte_size(256 * 1024, 0)`). If
reservation or either argument lookup fails, `emit` returns `None` and the BPF
function returns `1`; there is no user-visible error record. The wire layout is
duplicated in `kala/src/wire.rs` and must remain byte-for-byte identical.

## Probe lifecycle

`FrameProbe::new` (`kala/src/lib.rs`) only initializes an empty target map and
does not load or attach an eBPF program. `FrameProbe::attach(pid)` is idempotent
for an already-attached PID. For a new PID it calls
`QueueBufferProbe::attach` (`kala/src/uprobe.rs`), which loads the embedded BPF
object, loads the `kala_frame_probe` program, and tries each symbol in order:

1. `Surface::hook_queueBuffer(ANativeWindow*, ANativeWindowBuffer*, int)`
2. `Surface::queueBufferInternal(...)`
3. legacy `Surface::queueBuffer(ANativeWindowBuffer*, int)`
4. legacy overload with `SurfaceQueueBufferOutput*`
5. modern `sp<GraphicBuffer>` overloads

The first successful `program.attach` wins. If all attempts fail, the last Aya
error is returned (or `SymbolNotFound` when no error is available). After a
successful attach, Kala creates a `FrameTracker` and rebuilds a `mio::Poll`
registry for all attached ring-buffer file descriptors.

`FrameProbe::detach(pid)` removes the PID, rebuilds the poll registry, and
returns `true` only when that PID was present. Dropping `QueueBufferProbe`
unloads the uprobe program (`Drop` in `kala/src/uprobe.rs`); dropping
`FrameProbe` therefore cleans up all attached PIDs. Poll/rebuild errors during
`recv_with_deadline` are ignored and retried on a later call.

## Receiving and frame-time reconstruction

`FrameProbe::recv_with_deadline(timeout)` polls all registered ring buffers with
`mio`. It returns the first available `(pid, Duration)`, or `None` when no event
arrives, no PID is attached, or a ring item cannot be decoded. A ring item
shorter than `size_of::<FrameRecord>()` is ignored. `FrameTracker::pump`
(`kala/src/tracker.rs`) reads one record at a time.

Because Android commonly has multiple buffers in flight, `FrameTracker::record`
keeps a separate timestamp history per buffer pointer. It computes a saturating
delta from the previous timestamp for that same pointer; the first observation
of a buffer produces no duration, and a zero delta is discarded. Each history
stores up to 144 durations. Among buffers tied for the longest history, the
buffer with the smallest cumulative duration is selected as the active buffer;
only a delta from that buffer is returned. This is a heuristic to avoid
triple-buffer interleaving and is not a direct display-present timestamp.

Errors while pumping a tracker are swallowed by `recv_with_deadline` (`Err(_) =>
continue`), so a broken ring read causes the current event to be skipped rather
than terminating the probe.

## Auriya integration

`src/core/ebpf.rs` wraps Kala in `EbpfFrameStream`. `EbpfFrameStream::new` calls
`FrameProbe::new`, creates a worker thread, and broadcasts returned frame
durations through a Tokio `broadcast` channel (capacity 4096). With no attached
PID the worker blocks on its command channel; with at least one PID it drains
commands and polls Kala on the `settings.fas.poll_interval_ms` deadline (clamped
to `[1, 500]` ms; `recv_with_deadline`). The PID from Kala's tuple is currently
ignored; only the `Duration` is sent to subscribers.

`attach` and `detach` send commands to that worker and wait up to one second for
the reply. They report explicit errors when the worker is gone, the timeout is
exceeded, or the reply channel disconnects. The daemon's tick path calls these
methods for the validated foreground game PID (`src/daemon/tick.rs`,
`ebpf_attach`/`ebpf_detach`). Attach failures are logged as warnings and do not
abort the daemon.

At startup, failure to create the stream is handled in `src/daemon/run.rs`:
Auriya continues with sysfs-only FPS telemetry and disables FAS. This covers
old kernels, missing BPF capabilities, SELinux denial, missing symbols, and
other Kala initialization errors. Kala's documented runtime requirements are a
Linux kernel with uprobe and ring-buffer support (5.8+, tested on 5.10), root or
`CAP_SYS_ADMIN` plus `CAP_BPF`, and a real Android image containing
`/system/lib64/libgui.so`. The embedded BPF object means downstream users do not
need `bpf-linker` at runtime; building Kala itself requires `bpf-linker` in
`PATH` when regenerating the object.

## Scope and limitations

Kala filters by PID through Aya's uprobe attachment; the eBPF program itself has
no package-name or surface-name filter. It records every matching
`queueBuffer` call in the attached process. The measured interval is the time
between queue calls for the selected buffer, not a guaranteed on-screen frame
interval. Ring overflow, failed reservations, malformed records, and tracker
read errors are silently dropped at the Kala layer. Auriya's FPS meter may use
the stream as a fallback, while FAS consumes the same broadcast independently;
the surrounding selection and timeout policy is implemented in Auriya, not in
Kala (`src/core/fps_meter/mod.rs`, `src/core/fas/source/mod.rs`).
