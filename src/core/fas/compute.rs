use super::bottleneck::BottleneckType;
use super::tuning::{BOOST_THRESHOLD_KHZ, REDUCE_THRESHOLD_KHZ};
use crate::core::scaling::ScalingAction;
use std::time::Duration;

/// Pure P-controller core. Extracted for testability.
///
/// `last_frame * adjusted_target_fps` lands on exactly 1 second when the GPU
/// hits target. Anything slower (heavier frame) makes the product overshoot
/// and produces a positive control signal; anything faster undershoots.
pub fn compute_control_khz(last_frame: Duration, adjusted_target_fps: f64, kp: f64) -> f64 {
    let normalized_ns = last_frame.as_nanos() as f64 * adjusted_target_fps;
    let error_ns = normalized_ns - 1_000_000_000.0;
    error_ns * kp
}

/// Map a control signal + jank flag + bottleneck type to a discrete ScalingAction.
/// Pulled out of `tick` so replay tests can exercise the bucket boundaries
/// without spinning up a full controller.
pub fn decide_action(
    control_khz: f64,
    is_janked: bool,
    bottleneck: BottleneckType,
) -> ScalingAction {
    if is_janked || control_khz > BOOST_THRESHOLD_KHZ {
        match bottleneck {
            BottleneckType::Gpu => ScalingAction::BoostGpu,
            BottleneckType::Cpu => ScalingAction::BoostCpu,
            BottleneckType::Balanced => ScalingAction::BoostBalanced,
            BottleneckType::Unknown => ScalingAction::BoostBalanced,
        }
    } else if control_khz < REDUCE_THRESHOLD_KHZ {
        ScalingAction::Reduce
    } else {
        ScalingAction::Maintain
    }
}
