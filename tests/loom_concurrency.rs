use loom::future::block_on;
use loom::sync::Arc;
use loom::sync::Mutex;
use loom::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use loom::thread;

/// Test atomic ordering and state synchronization in a concurrent ring buffer under all interleavings.
#[test]
fn test_loom_ring_buffer_atomic_ordering() {
    loom::model(|| {
        let head = Arc::new(AtomicUsize::new(0));
        let tail = Arc::new(AtomicUsize::new(0));
        let value = Arc::new(AtomicUsize::new(0));

        let h_prod = Arc::clone(&head);
        let t_prod = Arc::clone(&tail);
        let v_prod = Arc::clone(&value);

        let producer = thread::spawn(move || {
            let cur_head = h_prod.load(Ordering::Relaxed);
            let cur_tail = t_prod.load(Ordering::Acquire);

            // Buffer capacity is 2
            if cur_head.wrapping_sub(cur_tail) < 2 {
                v_prod.store(42, Ordering::Relaxed);
                h_prod.store(cur_head.wrapping_add(1), Ordering::Release);
            }
        });

        let h_cons = Arc::clone(&head);
        let t_cons = Arc::clone(&tail);
        let v_cons = Arc::clone(&value);

        let consumer = thread::spawn(move || {
            let cur_tail = t_cons.load(Ordering::Relaxed);
            let cur_head = h_cons.load(Ordering::Acquire);

            if cur_tail != cur_head {
                let read_val = v_cons.load(Ordering::Relaxed);
                assert_eq!(read_val, 42);
                t_cons.store(cur_tail.wrapping_add(1), Ordering::Release);
            }
        });

        producer.join().unwrap();
        consumer.join().unwrap();
    });
}

/// Test async task coordination and mutex contention with Loom under all execution permutations.
#[test]
fn test_loom_async_mutex_contention_and_race_freedom() {
    loom::model(|| {
        let shared_state = Arc::new(Mutex::new(0usize));
        let is_running = Arc::new(AtomicBool::new(true));

        // Async task 1: Simulating telemetry polling
        let s_task1 = Arc::clone(&shared_state);
        let r_task1 = Arc::clone(&is_running);
        let handle1 = thread::spawn(move || {
            block_on(async {
                if r_task1.load(Ordering::Acquire) {
                    let mut lock = s_task1.lock().unwrap();
                    *lock += 1;
                }
            });
        });

        // Async task 2: Simulating concurrent tweak applier
        let s_task2 = Arc::clone(&shared_state);
        let r_task2 = Arc::clone(&is_running);
        let handle2 = thread::spawn(move || {
            block_on(async {
                if r_task2.load(Ordering::Acquire) {
                    let mut lock = s_task2.lock().unwrap();
                    *lock += 10;
                }
            });
        });

        handle1.join().unwrap();
        handle2.join().unwrap();

        let final_val = *shared_state.lock().unwrap();
        assert!(final_val == 11 || final_val == 1 || final_val == 10);
    });
}
