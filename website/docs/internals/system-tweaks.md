# System Tweaks

System changes are routed through code under `src/core/tweaks/` and the shared command writer. Targets include device-specific CPU, GPU, scheduler, memory, and networking nodes exposed by `/proc` or `/sys`.

Unsupported nodes must be detected rather than assumed to exist.
