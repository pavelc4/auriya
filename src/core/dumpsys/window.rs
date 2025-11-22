use std::process::Command;

#[derive(Debug, Default, Clone)]
#[allow(dead_code)]
pub struct RecentApp {
    pub package_name: String,
    pub visible: bool,
}
#[derive(Debug, Default, Clone)]
pub struct WindowDisplays {
    pub screen_awake: bool,
    pub recent_apps: Vec<RecentApp>,
}

impl WindowDisplays {
    #[allow(dead_code)]
    pub fn fetch() -> anyhow::Result<Self> {
        let out = Command::new("/system/bin/dumpsys")
            .args(["window", "displays"])
            .output()?;
        let s = String::from_utf8_lossy(&out.stdout);

        let mut res = WindowDisplays::default();
        if s.contains("mAwake=true")
            || s.contains("mWakefulness=Awake")
            || s.contains("mScreenOn=true")
        {
            res.screen_awake = true;
        }

        for line in s.lines() {
            if let Some(idx) = line.find("ActivityRecord") {
                let seg = &line[idx..];
                if let Some(start) = seg.find(' ') {
                    let token = seg[start + 1..].split_whitespace().next().unwrap_or("");
                    let pkg = token.split('/').next().unwrap_or("");
                    if pkg.contains('.') {
                        res.recent_apps.push(RecentApp {
                            package_name: pkg.to_string(),
                            visible: true,
                        });
                    }
                }
            }
        }
        Ok(res)
    }
}
