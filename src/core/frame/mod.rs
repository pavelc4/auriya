mod gpubusy;
mod dummy;

pub trait FrameSource: Send + Sync {
    fn get_metric(&mut self) -> anyhow::Result<Option<f32>>;
}


pub fn create_frame_source() -> Box<dyn FrameSource> {
    if gpubusy::is_available() {
        Box::new(gpubusy::GpuBusySource::new())
    } else {
        Box::new(dummy::DummySource)
    }
}
