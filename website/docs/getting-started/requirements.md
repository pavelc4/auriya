# Requirements

What a device needs before installing Auriya. Where a requirement comes from
source, it is cited.

## Hardware and OS

| Requirement | Detail | Source |
| --- | --- | --- |
| Root manager | Magisk, KernelSU, or APatch | `module/customize.sh` detects `KSU`/APatch and links binaries accordingly |
| CPU architecture | **`arm64-v8a` (aarch64) only** | `customize.sh` aborts on any other `$ARCH`; the module ships only aarch64 binaries |
| Android version | **11 or newer** (`minSdk = 30`) | `android/app/build.gradle.kts:54`, `android/service/build.gradle.kts:54` |
| Root permission | The manager and daemon require root to read/write `/proc` and `/sys` and to bind the daemon socket | see [System tweaks](../internals/system-tweaks), [IPC protocol](../internals/ipc-protocol) |

## Kernel features (for Frame-Aware Scheduling)

The base daemon runs on any supported device, but **FAS is optional and
capability-gated**:

- FAS uses an eBPF uprobe (Kala) that needs a kernel with **uprobe + ring-buffer
  support (5.8+, tested on 5.10)**, root or `CAP_SYS_ADMIN` + `CAP_BPF`, and a
  real Android image containing `/system/lib64/libgui.so`
  ([Kala eBPF frame probe → Auriya integration](../internals/kala-research#auriya-integration)).
- If any of that is missing, the daemon **continues with sysfs-only FPS and FAS
  disabled** — it does not fail to start
  ([FPS detection](../internals/fps-detection)).

So: an older-kernel device still runs Auriya's static profiles and tweaks; only
the adaptive frame-aware layer is unavailable.

## Trust boundary

Auriya runs as root and writes kernel nodes, binds a Unix socket, mounts over
vendor nodes ([vendor lock](../internals/system-tweaks#vendor-lock--stopping-vendor-services-from-fighting-back)),
and installs packages. Review the module source and your
[`settings.toml`](../reference/settings) / [`gamelist.toml`](../reference/gamelist)
before installing. Device-specific `/proc` and `/sys` writes are best-effort and
skipped when a node is absent, but they are still privileged operations.

## Next

[Installation](installation).
