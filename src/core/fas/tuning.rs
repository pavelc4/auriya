pub const KP_DEFAULT: f64 = 1.5e-4;
pub const MARGIN_FPS: f64 = 1.5;
pub const JANK_DELTA_FPS: f64 = 2.0;
pub const BOOST_THRESHOLD_KHZ: f64 = 50_000.0;
pub const REDUCE_THRESHOLD_KHZ: f64 = -50_000.0;

/// Runtime-tunable FAS parameters, resolved from `settings.toml` at daemon
/// construction (`[fas]`, `[dynamic_governor]`, and the active `[modes.*]`
/// entry selected by `fas.default_mode`). `Default` reproduces the historical
/// hardcoded constants so the unit tests stay behaviour-identical without a
/// settings file.
#[derive(Debug, Clone, Copy)]
pub struct FasTuning {
    /// FPS headroom subtracted from the target (per-mode `margin`). Higher =
    /// the controller treats "exactly target" as slower, biasing toward boosts.
    pub margin_fps: f64,
    /// Skin-temperature ceiling (°C); above it FAS forces `Reduce`.
    pub thermal_threshold: f32,
    /// Bottleneck-detector coefficient-of-variation split
    pub cv_threshold: f64,
    /// Frames a new bottleneck class must persist before it is accepted.
    pub debounce_frames: u32,
    /// When false, bottleneck classification is skipped and every boost is
    /// `BoostBalanced` (full profile) instead
    pub dynamic_governor_enabled: bool,
}

impl Default for FasTuning {
    fn default() -> Self {
        Self {
            margin_fps: MARGIN_FPS,
            thermal_threshold: 90.0,
            cv_threshold: 0.15,
            debounce_frames: 3,
            dynamic_governor_enabled: true,
        }
    }
}

impl FasTuning {
    /// Resolve tuning from `settings.toml`.
    ///
    /// `fas.default_mode` names which `[modes.*]` entry is active; that entry
    /// supplies `margin` and (preferentially) the thermal ceiling, with
    /// `fas.thermal_threshold` as the fallback when the mode omits it or is
    /// unknown. `[dynamic_governor]` supplies the bottleneck-detector knobs.
    /// This is what makes `fas.default_mode` and every `[modes.*]` block
    /// (including `fast`) actually affect FAS behaviour.
    pub fn from_settings(settings: &crate::core::config::Settings) -> Self {
        let mode = settings.modes.get(&settings.fas.default_mode);
        if mode.is_none() {
            tracing::warn!(
                target: "auriya::fas",
                "fas.default_mode = {:?} has no matching [modes.*] entry; using default margin",
                settings.fas.default_mode,
            );
        }
        let default = Self::default();
        Self {
            margin_fps: mode.map(|m| m.margin).unwrap_or(default.margin_fps),
            thermal_threshold: mode
                .map(|m| m.thermal_threshold)
                .unwrap_or(settings.fas.thermal_threshold) as f32,
            cv_threshold: settings.dynamic_governor.cv_threshold,
            debounce_frames: settings.dynamic_governor.debounce_frames,
            dynamic_governor_enabled: settings.dynamic_governor.enabled,
        }
    }
}
