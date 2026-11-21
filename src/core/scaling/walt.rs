use serde::{Deserialize, Serialize};

/// Specification of a CPU cluster for WALT scheduler calculations.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ClusterSpec {
    pub cluster_id: usize,
    pub cpu_ids: Vec<usize>,
    pub capacity: u32,
    pub min_khz: u64,
    pub max_khz: u64,
}

impl ClusterSpec {
    pub fn new(cluster_id: usize, cpu_ids: Vec<usize>, capacity: u32) -> Self {
        Self {
            cluster_id,
            cpu_ids,
            capacity,
            min_khz: 0,
            max_khz: 0,
        }
    }
}

/// Profile mode for WALT calculation.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
pub enum WaltProfileMode {
    #[default]
    Balance,
    Performance,
    Powersave,
}

impl WaltProfileMode {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Balance => "balance",
            Self::Performance => "performance",
            Self::Powersave => "powersave",
        }
    }
}

/// Calculated WALT tunables for sysfs application.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct WaltCalculatedTuning {
    pub upmigrate: Vec<u32>,
    pub downmigrate: Vec<u32>,
    pub group_upmigrate: u32,
    pub group_downmigrate: u32,
    pub window_stats_policy: u32,
    pub ravg_window: u64,
    pub coloc_busy_hysteresis_enable_cpus: u32,
}

impl WaltCalculatedTuning {
    /// Formats upmigrate values as space-delimited string for sysfs.
    pub fn upmigrate_str(&self) -> String {
        self.upmigrate
            .iter()
            .map(|v| v.to_string())
            .collect::<Vec<_>>()
            .join(" ")
    }

    /// Formats downmigrate values as space-delimited string for sysfs.
    pub fn downmigrate_str(&self) -> String {
        self.downmigrate
            .iter()
            .map(|v| v.to_string())
            .collect::<Vec<_>>()
            .join(" ")
    }
}

/// Telemetry card representing current WALT status and tunables for APIs and UI.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct WaltCard {
    #[serde(default)]
    pub supported: bool,
    #[serde(default)]
    pub active: bool,
    #[serde(default)]
    pub auto_tune: bool,
    #[serde(default)]
    pub mode: std::borrow::Cow<'static, str>,
    #[serde(default)]
    pub upmigrate: Vec<u32>,
    #[serde(default)]
    pub downmigrate: Vec<u32>,
    #[serde(default)]
    pub group_upmigrate: Option<u32>,
    #[serde(default)]
    pub group_downmigrate: Option<u32>,
    #[serde(default)]
    pub window_policy: Option<u32>,
    #[serde(default)]
    pub ravg_window_ms: Option<u32>,
}

/// Mathematical calculation engine for Qualcomm WALT scheduler tunables.
pub struct WaltCalculator;

impl WaltCalculator {
    /// Formula 1: Capacity Ratio (R_ij = C_i / C_j)
    /// Relative computational throughput ratio between source cluster i and target cluster j.
    #[inline]
    pub fn capacity_ratio(c_source: u32, c_target: u32) -> f64 {
        match c_target {
            0 => 1.0,
            target => c_source as f64 / target as f64,
        }
    }

    /// Formula 2: Dynamic Headroom (H_i = 1.0 - Utilization_i)
    /// Available capacity fraction before the core reaches full saturation.
    #[inline]
    pub fn dynamic_headroom(utilization: f64) -> f64 {
        (1.0 - utilization).clamp(0.0, 1.0)
    }

    /// Formula 3: Normalized Load (L_norm = L_raw * 1024 / C_i)
    /// Converts cluster-relative raw utilization to the universal Linux 0..1024 scale.
    #[inline]
    pub fn normalized_load(raw_load: u32, capacity: u32) -> u32 {
        match capacity {
            0 => raw_load.min(1024),
            cap => ((raw_load as u64 * 1024) / cap as u64).min(1024) as u32,
        }
    }

    /// Formula 4: Exponential Moving Average (EMA_t = alpha * L_t + (1 - alpha) * EMA_{t-1})
    /// Smooth load filter reducing transient spikes while maintaining trend responsiveness.
    #[inline]
    pub fn ema(current: f64, previous: f64, alpha: f64) -> f64 {
        let a = alpha.clamp(0.0, 1.0);
        a * current + (1.0 - a) * previous
    }

    /// Formula 5: Coefficient of Variation (CV = sigma / mu)
    /// Statistical dispersion used to identify CPU bound frame variance vs GPU or display pacing.
    pub fn coefficient_of_variation(samples: &[f64]) -> Option<f64> {
        match samples.len() {
            0..=1 => None,
            count => {
                let n = count as f64;
                let mean = samples.iter().sum::<f64>() / n;
                match mean.abs() < 1e-6 {
                    true => Some(0.0),
                    false => {
                        let variance = samples
                            .iter()
                            .map(|&x| {
                                let diff = x - mean;
                                diff * diff
                            })
                            .sum::<f64>()
                            / n;
                        Some(variance.sqrt() / mean)
                    }
                }
            }
        }
    }

    /// Formula 6: Hysteresis Gap (Downmigrate = Upmigrate - Delta_H)
    /// Enforces migration band gap to prevent task ping-ponging across cluster boundaries.
    #[inline]
    pub fn hysteresis_gap(upmigrate: u32, delta_h: u32) -> u32 {
        upmigrate.saturating_sub(delta_h)
    }

    /// Formula 7: Co-location Bitmask (Sum of 2^c for Gold and Prime cores)
    /// Bitmask specifying which CPU cores participate in colocated task placement.
    pub fn coloc_cpus_mask(clusters: &[ClusterSpec]) -> u32 {
        match clusters.len() {
            0..=1 => 0,
            _ => {
                let mut sorted = clusters.to_vec();
                sorted.sort_by_key(|c| c.capacity);

                // Little cluster is sorted[0]; Gold and Prime are sorted[1..]
                let mut mask: u32 = 0;
                for cluster in &sorted[1..] {
                    for &cpu_id in &cluster.cpu_ids {
                        if cpu_id < 32 {
                            mask |= 1 << cpu_id;
                        }
                    }
                }
                mask
            }
        }
    }

    /// Calculate optimal WALT tunables based on cluster specs and target profile mode.
    pub fn calculate_tuning(
        clusters: &[ClusterSpec],
        mode: WaltProfileMode,
    ) -> WaltCalculatedTuning {
        match clusters.len() {
            0 => Self::fallback_tuning(mode),
            _ => {
                let mut sorted = clusters.to_vec();
                sorted.sort_by_key(|c| c.capacity);

                let coloc_mask = Self::coloc_cpus_mask(clusters);

                match sorted.len() {
                    1 => {
                        // Single cluster SoC: no inter-cluster migrations exist
                        match mode {
                            WaltProfileMode::Performance => WaltCalculatedTuning {
                                upmigrate: vec![95],
                                downmigrate: vec![80],
                                group_upmigrate: 95,
                                group_downmigrate: 80,
                                window_stats_policy: 1, // Max
                                ravg_window: 10_000_000,
                                coloc_busy_hysteresis_enable_cpus: 0,
                            },
                            WaltProfileMode::Balance => WaltCalculatedTuning {
                                upmigrate: vec![95],
                                downmigrate: vec![85],
                                group_upmigrate: 100,
                                group_downmigrate: 85,
                                window_stats_policy: 2, // Recent-Avg
                                ravg_window: 20_000_000,
                                coloc_busy_hysteresis_enable_cpus: 0,
                            },
                            WaltProfileMode::Powersave => WaltCalculatedTuning {
                                upmigrate: vec![98],
                                downmigrate: vec![90],
                                group_upmigrate: 100,
                                group_downmigrate: 90,
                                window_stats_policy: 0, // Window-Avg
                                ravg_window: 20_000_000,
                                coloc_busy_hysteresis_enable_cpus: 0,
                            },
                        }
                    }
                    2 => {
                        // Dual cluster SoC: Little -> Big
                        let r01 = Self::capacity_ratio(sorted[0].capacity, sorted[1].capacity);
                        match mode {
                            WaltProfileMode::Performance => {
                                // Formula 1 + Formula 6 with 15% hysteresis gap
                                let up1 = ((r01 * 50.0 + 45.0).round() as u32).clamp(60, 75);
                                let down1 = Self::hysteresis_gap(up1, 15);
                                let group_up = up1 + 5;
                                let group_down = Self::hysteresis_gap(group_up, 15);
                                WaltCalculatedTuning {
                                    upmigrate: vec![up1],
                                    downmigrate: vec![down1],
                                    group_upmigrate: group_up,
                                    group_downmigrate: group_down,
                                    window_stats_policy: 1,  // Max
                                    ravg_window: 10_000_000, // 10ms window
                                    coloc_busy_hysteresis_enable_cpus: coloc_mask,
                                }
                            }
                            WaltProfileMode::Balance => {
                                let up1 = 80;
                                let down1 = Self::hysteresis_gap(up1, 15);
                                WaltCalculatedTuning {
                                    upmigrate: vec![up1],
                                    downmigrate: vec![down1],
                                    group_upmigrate: 85,
                                    group_downmigrate: 70,
                                    window_stats_policy: 2, // Recent-Avg
                                    ravg_window: 20_000_000,
                                    coloc_busy_hysteresis_enable_cpus: 0,
                                }
                            }
                            WaltProfileMode::Powersave => {
                                let up1 = 90;
                                let down1 = Self::hysteresis_gap(up1, 15);
                                WaltCalculatedTuning {
                                    upmigrate: vec![up1],
                                    downmigrate: vec![down1],
                                    group_upmigrate: 95,
                                    group_downmigrate: 80,
                                    window_stats_policy: 0, // Window-Avg
                                    ravg_window: 20_000_000,
                                    coloc_busy_hysteresis_enable_cpus: 0,
                                }
                            }
                        }
                    }
                    _ => {
                        // Tri-cluster SoC: Little -> Gold -> Prime
                        let r01 = Self::capacity_ratio(sorted[0].capacity, sorted[1].capacity);
                        let r12 = Self::capacity_ratio(sorted[1].capacity, sorted[2].capacity);

                        match mode {
                            WaltProfileMode::Performance => {
                                // Little -> Gold threshold based on capacity ratio
                                let up1 = ((r01 * 60.0 + 45.0).round() as u32).clamp(60, 75);
                                let down1 = Self::hysteresis_gap(up1, 15);

                                // Gold -> Prime threshold based on capacity ratio
                                let up2 = ((r12 * 50.0 + 40.0).round() as u32).clamp(75, 85);
                                let down2 = Self::hysteresis_gap(up2, 15);

                                let group_up = up1 + 5;
                                let group_down = Self::hysteresis_gap(group_up, 15);

                                WaltCalculatedTuning {
                                    upmigrate: vec![up1, up2],
                                    downmigrate: vec![down1, down2],
                                    group_upmigrate: group_up,
                                    group_downmigrate: group_down,
                                    window_stats_policy: 1,  // Max
                                    ravg_window: 10_000_000, // 10ms
                                    coloc_busy_hysteresis_enable_cpus: coloc_mask,
                                }
                            }
                            WaltProfileMode::Balance => {
                                let up1 = 80;
                                let up2 = 90;
                                let down1 = Self::hysteresis_gap(up1, 15);
                                let down2 = Self::hysteresis_gap(up2, 15);

                                WaltCalculatedTuning {
                                    upmigrate: vec![up1, up2],
                                    downmigrate: vec![down1, down2],
                                    group_upmigrate: 85,
                                    group_downmigrate: 70,
                                    window_stats_policy: 2, // Recent-Avg
                                    ravg_window: 20_000_000,
                                    coloc_busy_hysteresis_enable_cpus: 0,
                                }
                            }
                            WaltProfileMode::Powersave => {
                                let up1 = 90;
                                let up2 = 95;
                                let down1 = Self::hysteresis_gap(up1, 15);
                                let down2 = Self::hysteresis_gap(up2, 15);

                                WaltCalculatedTuning {
                                    upmigrate: vec![up1, up2],
                                    downmigrate: vec![down1, down2],
                                    group_upmigrate: 95,
                                    group_downmigrate: 80,
                                    window_stats_policy: 0, // Window-Avg
                                    ravg_window: 20_000_000,
                                    coloc_busy_hysteresis_enable_cpus: 0,
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fn fallback_tuning(mode: WaltProfileMode) -> WaltCalculatedTuning {
        match mode {
            WaltProfileMode::Performance => WaltCalculatedTuning {
                upmigrate: vec![65, 80],
                downmigrate: vec![50, 65],
                group_upmigrate: 70,
                group_downmigrate: 55,
                window_stats_policy: 1,
                ravg_window: 10_000_000,
                coloc_busy_hysteresis_enable_cpus: 240,
            },
            WaltProfileMode::Balance => WaltCalculatedTuning {
                upmigrate: vec![80, 90],
                downmigrate: vec![65, 75],
                group_upmigrate: 85,
                group_downmigrate: 70,
                window_stats_policy: 2,
                ravg_window: 20_000_000,
                coloc_busy_hysteresis_enable_cpus: 0,
            },
            WaltProfileMode::Powersave => WaltCalculatedTuning {
                upmigrate: vec![90, 95],
                downmigrate: vec![75, 80],
                group_upmigrate: 95,
                group_downmigrate: 80,
                window_stats_policy: 0,
                ravg_window: 20_000_000,
                coloc_busy_hysteresis_enable_cpus: 0,
            },
        }
    }
}
