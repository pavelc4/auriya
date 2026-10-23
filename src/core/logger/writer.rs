use std::fs::{File, OpenOptions};
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::Mutex;

const DEFAULT_LOG_FILE: &str = "/data/adb/auriya/daemon.log";
const ROTATE_LOG_FILE: &str = "/data/adb/auriya/daemon.log.old";
const MAX_LOG_SIZE_BYTES: u64 = 2 * 1024 * 1024; // 2 MB ceiling

pub struct LogWriter {
    path: PathBuf,
    file: Mutex<Option<File>>,
    bytes_written: Mutex<u64>,
}

impl LogWriter {
    pub fn new<P: AsRef<Path>>(path: P) -> Self {
        let p = path.as_ref().to_path_buf();
        let (file, initial_len) = Self::open_or_fallback(&p);
        Self {
            path: p,
            file: Mutex::new(file),
            bytes_written: Mutex::new(initial_len),
        }
    }

    pub fn default_daemon() -> Self {
        Self::new(DEFAULT_LOG_FILE)
    }

    pub fn stdout_fallback() -> Self {
        Self {
            path: PathBuf::new(),
            file: Mutex::new(None),
            bytes_written: Mutex::new(0),
        }
    }

    fn open_or_fallback(path: &Path) -> (Option<File>, u64) {
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }

        match OpenOptions::new().create(true).append(true).open(path) {
            Ok(f) => {
                let len = f.metadata().map(|m| m.len()).unwrap_or(0);
                (Some(f), len)
            }
            Err(_) => (None, 0),
        }
    }

    pub fn write_bytes(&self, bytes: &[u8]) {
        let mut file_guard = match self.file.lock() {
            Ok(g) => g,
            Err(e) => e.into_inner(),
        };

        if let Some(ref mut file) = *file_guard {
            let mut written_guard = match self.bytes_written.lock() {
                Ok(g) => g,
                Err(e) => e.into_inner(),
            };

            let _ = file.write_all(bytes);
            *written_guard += bytes.len() as u64;

            // Rotate file if size exceeds threshold
            if *written_guard >= MAX_LOG_SIZE_BYTES {
                let _ = file.flush();
                drop(file_guard);

                // Rotate file atomically
                let _ = std::fs::rename(&self.path, ROTATE_LOG_FILE);

                let mut file_guard = match self.file.lock() {
                    Ok(g) => g,
                    Err(e) => e.into_inner(),
                };
                let (new_file, new_len) = Self::open_or_fallback(&self.path);
                *file_guard = new_file;
                *written_guard = new_len;
            }
        } else {
            // Fallback directly to standard output
            let mut stdout = io::stdout().lock();
            let _ = stdout.write_all(bytes);
            let _ = stdout.flush();
        }
    }
}
