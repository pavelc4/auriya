use crate::core::dumpsys::surfaceflinger::SurfaceFlinger;
use anyhow::Result;
use std::time::Duration;

pub struct FrameSource {
    package: String,
    layer_name: Option<String>,
}

impl FrameSource {
    pub fn new() -> Self {
        tracing::info!(target: "auriya::fas", "Using dumpsys SurfaceFlinger");
        Self {
            package: String::new(),
            layer_name: None,
        }
    }

    pub async fn attach(&mut self, package: &str, _pid: i32) -> Result<()> {
        self.package = package.to_string();
        self.layer_name = None;

        if let Ok(Some(layer)) = SurfaceFlinger::find_layer(package).await {
            tracing::debug!(target: "auriya::fas", "Found layer: {}", layer);
            self.layer_name = Some(layer);
        }

        Ok(())
    }

    pub async fn get_frame_time(&mut self) -> Result<Option<Duration>> {
        if self.layer_name.is_none()
            && !self.package.is_empty()
            && let Ok(Some(layer)) = SurfaceFlinger::find_layer(&self.package).await
        {
            self.layer_name = Some(layer);
        }

        let Some(layer) = &self.layer_name else {
            return Ok(None);
        };

        match SurfaceFlinger::get_frame_time(layer).await {
            Ok(ft) if ft > 0.0 => Ok(Some(Duration::from_secs_f32(ft / 1000.0))),
            Ok(_) => Ok(None),
            Err(e) => {
                tracing::debug!(target: "auriya::fas", "Frame error: {:?}", e);
                self.layer_name = None;
                Ok(None)
            }
        }
    }
}
