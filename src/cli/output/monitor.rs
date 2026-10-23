use crate::core::stats::StatsSnapshot;

pub fn print_monitor_frame(snap: &StatsSnapshot, oneline: bool) {
    let fps_str = if let Some(ref f) = snap.fps {
        if oneline {
            format!("{:.1} FPS", f.avg)
        } else {
            format!(
                "{:.1} FPS (1% low: {:.1}, peak: {:.0})",
                f.avg, f.low_1pct, f.peak
            )
        }
    } else if snap.session.active {
        "0.0 FPS".to_string()
    } else {
        "-- (Idle)".to_string()
    };

    let cpu_str = if let Some(ref c) = snap.cpu {
        format!("{:.0}%", c.load_pct)
    } else {
        "--".to_string()
    };

    let gpu_info = snap.gpu.as_ref().and_then(|g| {
        let load = g.load_pct?;
        let mhz = g.mhz.unwrap_or(0);
        if load > 0 || mhz > 0 {
            Some(format!("{load}% ({mhz} MHz)"))
        } else {
            None
        }
    });

    let temp_str = {
        let mut parts = Vec::new();
        if let Some(c) = snap.thermal.cpu_c {
            parts.push(format!("CPU: {c:.0}°C"));
        }
        if let Some(b) = snap.thermal.battery_c {
            parts.push(format!("Bat: {b:.0}°C"));
        }
        if parts.is_empty() {
            "--".to_string()
        } else {
            parts.join(", ")
        }
    };

    let bat_str = {
        let pct = snap
            .battery
            .pct
            .map(|p| format!("{p}%"))
            .unwrap_or_default();
        let cur = snap
            .battery
            .current_ma
            .map(|ma| format!("({ma} mA)"))
            .unwrap_or_default();
        if pct.is_empty() && cur.is_empty() {
            "--".to_string()
        } else {
            format!("{pct} {cur}").trim().to_string()
        }
    };

    let app_str = if let Some(ref pkg) = snap.session.pkg {
        format!("{pkg} ({})", snap.session.profile)
    } else {
        "(Idle)".to_string()
    };

    if oneline {
        let mut line = format!("FPS: {fps_str} | CPU: {cpu_str}");
        if let Some(ref g) = gpu_info {
            line.push_str(&format!(" | GPU: {g}"));
        }
        line.push_str(&format!(
            " | Temp: {temp_str} | Bat: {bat_str} | App: {app_str}"
        ));
        println!("{line}");
    } else {
        // Redraw in-place dashboard matching info style
        print!("\x1B[2J\x1B[H");
        println!("Auriya Live Telemetry (Ctrl+C to quit):");
        println!("  FPS:            {fps_str}");
        println!("  CPU Load:       {cpu_str}");
        if let Some(ref g) = gpu_info {
            println!("  GPU Load:       {g}");
        }
        println!("  Temperature:    {temp_str}");
        println!("  Battery:        {bat_str}");
        println!("  Active App:     {app_str}");
    }
}
