use crate::core::dumpsys::surfaceflinger::SurfaceFlinger;
use crate::core::fas::sysfs;
use anyhow::Result;
use std::time::Duration;

pub struct FrameSource {
    package: String,
    layer_name: Option<String>,
    sysfs_path: Option<String>,
    use_sysfs: bool,
}

impl Default for FrameSource {
    fn default() -> Self {
        Self::new()
    }
}

impl FrameSource {
    pub fn new() -> Self {
        let sysfs_path = sysfs::detect_fps_path();
        let use_sysfs = sysfs_path.is_some();

        if use_sysfs {
            tracing::info!(
                target: "auriya::fas",
                "FAS    | Using sysfs (fast): {}",
                sysfs_path.as_ref().unwrap()
            );
        } else {
            tracing::info!(
                target: "auriya::fas",
                "FAS    | Using dumpsys SurfaceFlinger (fallback)"
            );
        }

        Self {
            package: String::new(),
            layer_name: None,
            sysfs_path,
            use_sysfs,
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
        if self.use_sysfs
            && let Some(ref path) = self.sysfs_path
        {
            match sysfs::read_sysfs_fps(path) {
                Ok(fps) if fps > 0.0 => {
                    tracing::debug!(
                        target: "auriya::fas",
                        "FPS: {:.1} (source: sysfs kernel - fast path)",
                        fps
                    );
                    return Ok(Some(Duration::from_secs_f32(1.0 / fps)));
                }
                Ok(fps) => {
                    tracing::debug!(
                        target: "auriya::fas",
                        "sysfs returned invalid FPS: {}, trying fallback",
                        fps
                    );
                }
                Err(e) => {
                    tracing::warn!(
                        target: "auriya::fas",
                        "sysfs read error ({}), switching to dumpsys permanently",
                        e
                    );
                    self.use_sysfs = false;
                }
            }
        }
        tracing::debug!(
            target: "auriya::fas",
            "Using dumpsys SurfaceFlinger fallback (slow path)"
        );

        if self.layer_name.is_none()
            && !self.package.is_empty()
            && let Ok(Some(layer)) = SurfaceFlinger::find_layer(&self.package).await
        {
            tracing::debug!(
                target: "auriya::fas",
                "Found layer: {} for {}",
                layer.chars().take(30).collect::<String>(),
                self.package
            );
            self.layer_name = Some(layer);
        }

        let Some(layer) = &self.layer_name else {
            return Ok(None);
        };

        match SurfaceFlinger::get_frame_time(layer).await {
            Ok(ft) if ft > 0.0 => {
                tracing::debug!(
                    target: "auriya::fas",
                    "Frame time: {:.1}ms (source: dumpsys - slow path)",
                    ft
                );
                Ok(Some(Duration::from_secs_f32(ft / 1000.0)))
            }
            Ok(_) => Ok(None),
            Err(e) => {
                tracing::debug!(target: "auriya::fas", "dumpsys error: {:?}", e);
                self.layer_name = None;
                Ok(None)
            }
        }
    }
}
