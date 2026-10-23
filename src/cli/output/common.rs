use std::time::Duration;

pub fn print_success(message: &str) {
    println!(" {message}");
}

pub fn print_error(message: &str) {
    eprintln!(" Error: {message}");
}

pub fn print_execution_time(elapsed: Duration) {
    let micros = elapsed.as_micros();
    if micros < 1000 {
        println!("\n[Execution time: {micros}µs]");
    } else {
        println!(
            "\n[Execution time: {:.2}ms]",
            elapsed.as_secs_f64() * 1000.0
        );
    }
}
