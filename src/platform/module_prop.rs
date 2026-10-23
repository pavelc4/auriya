use std::fs;
use std::path::Path;

pub const MODULE_PROP_PATH: &str = "/data/adb/modules/auriya/module.prop";
pub const BASE_DESCRIPTION: &str = "Ferris, let's goo!";

/// Update the module description in `/data/adb/modules/auriya/module.prop` with operational status.
pub fn set_module_status(status: &str) {
    update_module_prop_at(MODULE_PROP_PATH, status);
}

/// Update the `description=` entry of a specified `module.prop` file.
pub fn update_module_prop_at(path_str: &str, status: &str) {
    let path = Path::new(path_str);
    if !path.exists() {
        return;
    }

    let Ok(content) = fs::read_to_string(path) else {
        return;
    };

    let target_prefix = "description=";
    let mut updated = String::with_capacity(content.len() + 32);
    let mut found = false;

    for line in content.lines() {
        if line.starts_with(target_prefix) {
            updated.push_str(target_prefix);
            updated.push_str(BASE_DESCRIPTION);
            updated.push_str("\\nstatus: ");
            updated.push_str(status);
            updated.push('\n');
            found = true;
        } else {
            updated.push_str(line);
            updated.push('\n');
        }
    }

    if !found {
        updated.push_str(target_prefix);
        updated.push_str(BASE_DESCRIPTION);
        updated.push_str("\\nstatus: ");
        updated.push_str(status);
        updated.push('\n');
    }

    let _ = fs::write(path, updated);
}
