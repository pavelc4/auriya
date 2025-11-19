use anyhow::{Context, Result};
use std::fs;
use std::path::Path;
use tracing::{debug, info};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum IoScheduler {
    Kyber,
    Deadline,
    Cfq,
    Noop,
    Bfq,
    MqDeadline,
}

#[allow(dead_code)]
pub fn revert_io_scheduler() -> Result<()> {
    info!(target: "auriya::io", "Reverting I/O scheduler to default");
    Ok(())
}

impl ToString for IoScheduler {
    fn to_string(&self) -> String {
        match self {
            IoScheduler::Kyber => "kyber".to_string(),
            IoScheduler::Deadline => "deadline".to_string(),
            IoScheduler::Cfq => "cfq".to_string(),
            IoScheduler::Noop => "noop".to_string(),
            IoScheduler::Bfq => "bfq".to_string(),
            IoScheduler::MqDeadline => "mq-deadline".to_string(),
        }
    }
}

pub fn get_available_schedulers(device: &str) -> Result<Vec<IoScheduler>> {
    let path = format!("/sys/block/{}/queue/scheduler", device);

    let content = fs::read_to_string(&path)
        .with_context(|| format!("Failed to read scheduler list for {}", device))?;

    let mut schedulers = Vec::new();

    for scheduler_name in content.split_whitespace() {
        let cleaned = scheduler_name
            .trim_start_matches('[')
            .trim_end_matches(']');

        match cleaned {
            "kyber" => schedulers.push(IoScheduler::Kyber),
            "deadline" => schedulers.push(IoScheduler::Deadline),
            "cfq" => schedulers.push(IoScheduler::Cfq),
            "noop" => schedulers.push(IoScheduler::Noop),
            "bfq" => schedulers.push(IoScheduler::Bfq),
            "mq-deadline" => schedulers.push(IoScheduler::MqDeadline),
            _ => {}
        }
    }

    Ok(schedulers)
}

pub fn get_current_scheduler(device: &str) -> Result<Option<IoScheduler>> {
    let path = format!("/sys/block/{}/queue/scheduler", device);

    let content = fs::read_to_string(&path)
        .with_context(|| format!("Failed to read current scheduler for {}", device))?;

    for part in content.split_whitespace() {
        if part.starts_with('[') && part.ends_with(']') {
            let scheduler_name = &part[1..part.len()-1];
            return Ok(match scheduler_name {
                "kyber" => Some(IoScheduler::Kyber),
                "deadline" => Some(IoScheduler::Deadline),
                "cfq" => Some(IoScheduler::Cfq),
                "noop" => Some(IoScheduler::Noop),
                "bfq" => Some(IoScheduler::Bfq),
                "mq-deadline" => Some(IoScheduler::MqDeadline),
                _ => None,
            });
        }
    }

    Ok(None)
}

pub fn select_best_gaming_scheduler(device: &str) -> Result<IoScheduler> {
    let available = get_available_schedulers(device)?;

    let preferred_order = [
        IoScheduler::Kyber,
        IoScheduler::Deadline,
        IoScheduler::Bfq,
        IoScheduler::Noop,
        IoScheduler::Cfq,
        IoScheduler::MqDeadline,
    ];

    for scheduler in &preferred_order {
        if available.contains(scheduler) {
            debug!(
                target: "auriya::io",
                "Selected {} for {} (available: {:?})",
                scheduler.to_string(),
                device,
                available
            );
            return Ok(scheduler.clone());
        }
    }

    available
        .first()
        .cloned()
        .context("No suitable I/O scheduler found")
}

pub fn set_io_scheduler(device: &str, scheduler: &IoScheduler) -> Result<()> {
    let path = format!("/sys/block/{}/queue/scheduler", device);

    if !Path::new(&path).exists() {
        debug!(target: "auriya::io", "Device {} does not exist, skipping", device);
        return Ok(());
    }

    let current = get_current_scheduler(device)?;
    if current == Some(scheduler.clone()) {
        debug!(
            target: "auriya::io",
            "Device {} already using {}",
            device,
            scheduler.to_string()
        );
        return Ok(());
    }

    fs::write(&path, scheduler.to_string())
        .with_context(|| format!(
            "Failed to set scheduler {} for device {}",
            scheduler.to_string(),
            device
        ))?;

    info!(
        target: "auriya::io",
        "Set I/O scheduler for {} to {}",
        device,
        scheduler.to_string()
    );

    Ok(())
}

pub fn discover_block_devices() -> Result<Vec<String>> {
    let sys_block_path = "/sys/block";

    let entries = fs::read_dir(sys_block_path)
        .with_context(|| format!("Failed to read {}", sys_block_path))?;

    let mut devices = Vec::new();

    for entry in entries {
        let entry = entry?;
        let name = entry.file_name().to_string_lossy().to_string();
        if name.starts_with("loop")
            || name.starts_with("ram")
            || name.starts_with("dm-")
            || name.starts_with("zram")
        {
            debug!(
                target: "auriya::io",
                "Skipping virtual device: {}",
                name
            );
            continue;
        }

        if name.starts_with("sd")
            || name.starts_with("nvme")
            || name.starts_with("mmcblk")
            || name.starts_with("vd")
        {
            debug!(
                target: "auriya::io",
                "Discovered block device: {}",
                name
            );
            devices.push(name);
        }
    }

    if devices.is_empty() {
        debug!(
            target: "auriya::io",
            "No block devices discovered, falling back to common names"
        );
        devices = vec![
            "sda".to_string(),
            "mmcblk0".to_string(),
        ];
    }

    Ok(devices)
}
pub fn apply_gaming_io() -> Result<()> {
    info!(target: "auriya::io", "Applying gaming I/O scheduler");

    let devices = match discover_block_devices() {
        Ok(devs) => devs,
        Err(e) => {
            debug!(
                target: "auriya::io",
                "Failed to discover devices: {}. Using fallback list.",
                e
            );
            vec![
                "sda".to_string(),
                "mmcblk0".to_string(),
                "nvme0n1".to_string(),
            ]
        }
    };

    debug!(
        target: "auriya::io",
        "Applying I/O scheduler to {} devices",
        devices.len()
    );

    let mut success_count = 0;
    let mut skip_count = 0;
    let mut error_count = 0;

    for device in &devices {
        let device_path = format!("/sys/block/{}", device);

        if !Path::new(&device_path).exists() {
            debug!(target: "auriya::io", "Device {} not found", device);
            skip_count += 1;
            continue;
        }

        match select_best_gaming_scheduler(device) {
            Ok(scheduler) => {
                match set_io_scheduler(device, &scheduler) {
                    Ok(_) => {
                        success_count += 1;
                    }
                    Err(e) => {
                        debug!(
                            target: "auriya::io",
                            "Failed to set {} for {}: {}",
                            scheduler.to_string(),
                            device,
                            e
                        );
                        error_count += 1;
                    }
                }
            }
            Err(e) => {
                debug!(
                    target: "auriya::io",
                    "Could not determine scheduler for {}: {}",
                    device,
                    e
                );
                skip_count += 1;
            }
        }
    }

    info!(
        target: "auriya::io",
        "I/O scheduler applied: {} success, {} errors, {} skipped",
        success_count,
        error_count,
        skip_count
    );

    Ok(())
}
