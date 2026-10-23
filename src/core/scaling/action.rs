#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScalingAction {
    BoostGpu,
    BoostCpu,
    BoostBalanced,
    Maintain,
    Reduce,
}
