use crate::Result;
use crate::cli::output;
use crate::common::{BOOT_ERROR_FILE, CRASH_LOG_FILE, LOG_FILE};
use anyhow::Context;
use std::time::Duration;

pub fn handle_log(follow: bool, lines: usize, clear: bool, crash: bool) -> Result<()> {
    match (crash, clear) {
        (true, _) => {
            match std::fs::read_to_string(CRASH_LOG_FILE) {
                Ok(content) => println!("{content}"),
                Err(_) => println!("No crash tombstone found at {CRASH_LOG_FILE}"),
            }
            return Ok(());
        }
        (false, true) => {
            let _ = std::fs::write(LOG_FILE, "");
            let _ = std::fs::remove_file(CRASH_LOG_FILE);
            let _ = std::fs::remove_file(BOOT_ERROR_FILE);
            output::print_success("Daemon logs cleared");
            return Ok(());
        }
        (false, false) => {}
    }

    if !std::path::Path::new(LOG_FILE).exists() {
        println!("No log file found at {LOG_FILE}");
        return Ok(());
    }

    let content = std::fs::read_to_string(LOG_FILE).context("Failed to read log file")?;
    let all_lines: Vec<&str> = content.lines().collect();
    let start_idx = all_lines.len().saturating_sub(lines);
    for line in &all_lines[start_idx..] {
        println!("{line}");
    }

    if follow {
        use std::io::{BufRead, BufReader, Seek, SeekFrom};
        let file = std::fs::File::open(LOG_FILE)?;
        let mut reader = BufReader::new(file);
        let mut pos = reader.seek(SeekFrom::End(0))?;

        loop {
            let mut line = String::new();
            let n = reader.read_line(&mut line)?;
            if n > 0 {
                print!("{line}");
                pos += n as u64;
            } else {
                std::thread::sleep(Duration::from_millis(250));
                if let Ok(metadata) = std::fs::metadata(LOG_FILE)
                    && metadata.len() < pos
                {
                    let f = std::fs::File::open(LOG_FILE)?;
                    reader = BufReader::new(f);
                    pos = 0;
                }
            }
        }
    }

    Ok(())
}
