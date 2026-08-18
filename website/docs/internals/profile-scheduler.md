# Profile Scheduler

The scheduler combines configuration, foreground application state, FPS telemetry, and device status to select runtime behavior. It should remain the single owner of profile transitions so competing observers do not write conflicting settings.
