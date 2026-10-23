pub mod bottleneck;
pub mod buffer;
pub mod compute;
pub mod controller;
pub mod sampler;
pub mod source;
pub mod tuning;

pub use bottleneck::{BottleneckDetector, BottleneckType};
pub use buffer::{BufferState, FrameBuffer, TargetFps};
pub use compute::{compute_control_khz, decide_action};
pub use controller::{FasController, FasState};
pub use sampler::UtilSampler;
pub use source::FrameSource;
pub use tuning::{
    BOOST_THRESHOLD_KHZ, FasTuning, JANK_DELTA_FPS, KP_DEFAULT, MARGIN_FPS, REDUCE_THRESHOLD_KHZ,
};
