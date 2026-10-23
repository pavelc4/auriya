use auriya::platform::common::init::optimize_io_paths;
use std::fs;
use tempfile::tempdir;

#[test]
fn test_optimize_io_paths_writes_tunings() {
    let block_dir = tempdir().unwrap();
    let thermal_dir = tempdir().unwrap();

    let dev_queue = block_dir.path().join("sda/queue");
    fs::create_dir_all(&dev_queue).unwrap();
    fs::write(dev_queue.join("iostats"), "1").unwrap();
    fs::write(dev_queue.join("add_random"), "1").unwrap();
    fs::write(dev_queue.join("read_ahead_kb"), "128").unwrap();
    fs::write(dev_queue.join("nr_requests"), "128").unwrap();

    let tz = thermal_dir.path().join("thermal_zone0");
    fs::create_dir_all(&tz).unwrap();
    fs::write(tz.join("policy"), "user_space").unwrap();

    optimize_io_paths(block_dir.path(), thermal_dir.path()).unwrap();

    assert_eq!(fs::read_to_string(dev_queue.join("iostats")).unwrap(), "0");
    assert_eq!(
        fs::read_to_string(dev_queue.join("add_random")).unwrap(),
        "0"
    );
    assert_eq!(
        fs::read_to_string(dev_queue.join("read_ahead_kb")).unwrap(),
        "32"
    );
    assert_eq!(
        fs::read_to_string(dev_queue.join("nr_requests")).unwrap(),
        "32"
    );
    assert_eq!(fs::read_to_string(tz.join("policy")).unwrap(), "step_wise");
}

#[test]
fn test_optimize_io_paths_handles_empty_or_missing_gracefully() {
    let block_dir = tempdir().unwrap();
    let thermal_dir = tempdir().unwrap();

    // Empty directories shouldn't fail
    assert!(optimize_io_paths(block_dir.path(), thermal_dir.path()).is_ok());
}
