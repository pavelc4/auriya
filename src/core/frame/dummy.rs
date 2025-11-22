use super::FrameSource;

pub struct DummySource;

impl FrameSource for DummySource {
    fn get_metric(&mut self) -> anyhow::Result<Option<f32>> {
        Ok(Some(50.0))
    }
}
