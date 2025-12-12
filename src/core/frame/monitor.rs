use crate::core::dumpsys::surfaceflinger::SurfaceFlinger;
use anyhow::Result;

pub struct FrameMonitor {
    pub package: String,
    layer_name: Option<String>,
}

impl FrameMonitor {
    pub fn new(package: String) -> Self {
        Self {
            package,
            layer_name: None,
        }
    }

    pub async fn get_frame_time(&mut self) -> Result<f32> {
        if self.layer_name.is_none()
         && let Ok(Some(layer)) = SurfaceFlinger::find_layer(&self.package).await {
	            tracing::info!(target: "auriya::fas", "Found SurfaceFlinger layer: {}", layer);
	            self.layer_name = Some(layer);
	        }

        if let Some(layer) = &self.layer_name {
            match SurfaceFlinger::get_frame_time(layer).await {
                Ok(ft) => Ok(ft),
                Err(e) => {
                    tracing::warn!(target: "auriya::fas", "Failed to get frame time, resetting layer: {:?}", e);
                    self.layer_name = None;
                    Ok(0.0)
                }
            }
        } else {
            Ok(0.0)
        }
    }

    pub fn update_package(&mut self, package: String) {
        if self.package != package {
            self.package = package;
            self.layer_name = None;
        }
    }
}
