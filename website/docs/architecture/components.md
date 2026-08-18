# Components

## Android manager

Located under `android/app/`. It renders Compose screens, persists UI preferences, requests root access, and displays daemon status.

## Companion service

Located under `android/service/`. It maintains Android-side background integration and shares models with `android/shared/`.

## Rust daemon

Started from `src/main.rs`. Core subsystems live in `src/core/`, including process tracking, FPS sampling, scheduling, telemetry, thermal monitoring, and system tweaks.

## Control CLI

Started from `src/ctl.rs`. It communicates with the daemon for command-line status and control operations.
