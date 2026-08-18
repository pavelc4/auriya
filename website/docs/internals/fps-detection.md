# FPS Detection

FPS handling is implemented under `src/core/fps_meter/` and the frame-analysis subsystem under `src/core/fas/`. Samples feed the scheduler as observed telemetry rather than a performance claim.

The final reference should document:

- frame data source and availability checks;
- sampling interval and calculation window;
- dropped, duplicated, or stale sample handling;
- fallback behavior when the frame source is unavailable;
- how the scheduler consumes the resulting value.

Implementation details must match the current `FpsMeter::read` and frame buffer code.
