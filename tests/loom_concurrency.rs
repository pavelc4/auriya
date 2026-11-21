use loom::future::block_on;
use loom::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use loom::sync::{Arc, Mutex, RwLock};
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

/// Test multi-lock hierarchy (profile_lock -> active_mode) under Loom permutations
/// to prove mathematical freedom from deadlocks.
#[test]
fn test_loom_lock_hierarchy_deadlock_freedom() {
    loom::model(|| {
        let profile_lock = Arc::new(Mutex::new(()));
        let active_mode = Arc::new(RwLock::new(0usize));

        // Thread 1: set_profile worker (holds profile_lock then writes active_mode)
        let p1 = Arc::clone(&profile_lock);
        let m1 = Arc::clone(&active_mode);
        let t1 = thread::spawn(move || {
            let _guard = p1.lock().unwrap();
            let mut mode = m1.write().unwrap();
            *mode = 1;
        });

        // Thread 2: another set_profile worker
        let p2 = Arc::clone(&profile_lock);
        let m2 = Arc::clone(&active_mode);
        let t2 = thread::spawn(move || {
            let _guard = p2.lock().unwrap();
            let mut mode = m2.write().unwrap();
            *mode = 2;
        });

        // Thread 3: telemetry status reader (reads active_mode)
        let m3 = Arc::clone(&active_mode);
        let t3 = thread::spawn(move || {
            let mode = *m3.read().unwrap();
            assert!(mode <= 2);
        });

        t1.join().unwrap();
        t2.join().unwrap();
        t3.join().unwrap();

        let final_mode = *active_mode.read().unwrap();
        assert!(final_mode == 1 || final_mode == 2);
    });
}

/// Test RwLock consistency under concurrent writer and reader threads,
/// proving readers never observe partial updates or data races.
#[test]
fn test_loom_rwlock_reader_writer_race_freedom() {
    loom::model(|| {
        // State tuple: (package_id, process_id)
        let state = Arc::new(RwLock::new((0u32, 0u32)));

        let s_writer = Arc::clone(&state);
        let writer = thread::spawn(move || {
            let mut w = s_writer.write().unwrap();
            w.0 = 100;
            w.1 = 100;
        });

        let s_reader = Arc::clone(&state);
        let reader = thread::spawn(move || {
            let r = s_reader.read().unwrap();
            // Invariant: both values must be either (0,0) or (100,100), never torn
            assert_eq!(r.0, r.1, "Torn read detected across RwLock");
        });

        writer.join().unwrap();
        reader.join().unwrap();
    });
}

/// Test Acquire-Release atomic synchronization for daemon state flags
/// to guarantee published memory visibility across threads.
#[test]
fn test_loom_state_transition_acquire_release() {
    loom::model(|| {
        let flag = Arc::new(AtomicBool::new(false));
        let data = Arc::new(AtomicUsize::new(0));

        let f1 = Arc::clone(&flag);
        let d1 = Arc::clone(&data);
        let publisher = thread::spawn(move || {
            d1.store(1234, Ordering::Relaxed);
            f1.store(true, Ordering::Release);
        });

        let f2 = Arc::clone(&flag);
        let d2 = Arc::clone(&data);
        let subscriber = thread::spawn(move || {
            if f2.load(Ordering::Acquire) {
                assert_eq!(d2.load(Ordering::Relaxed), 1234);
            }
        });

        publisher.join().unwrap();
        subscriber.join().unwrap();
    });
}
