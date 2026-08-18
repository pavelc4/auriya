# Data Flow

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
    |                       |                    |                    |
```

Commands should be treated separately from telemetry: commands request a state change, while telemetry reports observed state. Failure at either boundary must remain visible to the caller.
