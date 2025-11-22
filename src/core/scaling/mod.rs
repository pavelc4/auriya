#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScalingAction {
    Boost,
    Maintain,
    Reduce,
}

pub fn decide_from_gpu_util(gpu_util: f32, margin: f32, thermal_throttle: bool) -> ScalingAction {
    if thermal_throttle {
        return ScalingAction::Reduce;
    }

    let boost_threshold = 85.0 - margin;
    let reduce_threshold = 40.0 + margin;

    if gpu_util > boost_threshold {
        ScalingAction::Boost
    } else if gpu_util < reduce_threshold {
        ScalingAction::Reduce
    } else {
        ScalingAction::Maintain
    }
}
