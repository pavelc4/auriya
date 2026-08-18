# Model Data

Halaman ini memetakan entitas data, kepemilikan, dan arah sinkronisasi antara komponen Rust dan Kotlin di Auriya.

## Peta Entitas Data {#entity-map-who-owns-what-sync-direction}

```mermaid
classDiagram
    class Settings {
        +daemon: DaemonSettings
        +cpu: CpuSettings
        +gpu: GpuSettings
        +fas: FasSettings
        +modes: ModeSettings
    }

    class GameProfile {
        +package: String
        +mode: ProfileMode
        +target_fps: TargetFps
        +cpu_governor: Option~String~
        +refresh_rate: Option~u32~
        +enable_dnd: bool
    }

    class SystemStatus {
        +focused_app: Option~String~
        +is_screen_on: bool
        +is_battery_saver: bool
        +battery_level: u32
    }

    class DaemonStatus {
        +running: bool
        +active_profile: ProfileMode
        +tracked_game: Option~String~
        +fps: f32
        +fps_source: String
    }

    Settings "1" --> "1" DaemonStatus : Drives
    GameProfile "*" --> "1" DaemonStatus : Overrides
    SystemStatus "1" --> "1" DaemonStatus : Informs
```

## Entitas Konfigurasi (Rust ↔ Kotlin) {#config-entities-rust--kotlin--must-stay-in-sync}

Struktur data konfigurasi di Rust (`src/core/config/`) dan Kotlin (`android/shared/`) dirancang selalu sinkron:

1. **`Settings` (`settings.toml`)**: Mengatur parameter global seperti interval tick pemeriksaan, governor CPU default, profil default saat non-game, dan preset mode FAS (`powersave`, `balance`, `performance`, `fast`).
2. **`GameProfile` (`gamelist.toml`)**: Mengatur parameter khusus untuk aplikasi/game tertentu saat berada di foreground.
3. **`SystemStatus` (`system_status`)**: Snapshot data runtime yang ditulis oleh companion service dan dikonsumsi oleh scheduler daemon.
4. **`DaemonStatus` (IPC `GET_STATS` / `STATUS`)**: Snapshot data status daemon, telemetri hardware (frekuensi core CPU, GPU load, temperatur), dan telemetri FPS.