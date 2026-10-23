use std::sync::atomic::{AtomicUsize, Ordering};

/// Filters repeated log events, emitting output only when the observed state actually changes.
pub struct StateChangeLogger<T: PartialEq + Clone> {
    last_state: Option<T>,
}

impl<T: PartialEq + Clone> Default for StateChangeLogger<T> {
    fn default() -> Self {
        Self::new()
    }
}

impl<T: PartialEq + Clone> StateChangeLogger<T> {
    pub const fn new() -> Self {
        Self { last_state: None }
    }

    /// Run the given logging closure only if `new_state` differs from the previous observed state.
    pub fn log_if_changed<F>(&mut self, new_state: T, mut log_action: F)
    where
        F: FnMut(&T),
    {
        if self.last_state.as_ref() != Some(&new_state) {
            log_action(&new_state);
            self.last_state = Some(new_state);
        }
    }

    pub fn current(&self) -> Option<&T> {
        self.last_state.as_ref()
    }
}

/// Throttles high-frequency stream events by aggregating individual ticks into summary windows.
pub struct WindowThrottle {
    window_size: usize,
    counter: AtomicUsize,
}

impl WindowThrottle {
    pub const fn new(window_size: usize) -> Self {
        Self {
            window_size,
            counter: AtomicUsize::new(0),
        }
    }

    /// Increments tick counter and returns `Some(window_index)` whenever window boundary is crossed.
    #[inline]
    pub fn tick(&self) -> Option<usize> {
        let count = self.counter.fetch_add(1, Ordering::Relaxed) + 1;
        if count.is_multiple_of(self.window_size) {
            Some(count / self.window_size)
        } else {
            None
        }
    }

    #[inline]
    pub fn total_ticks(&self) -> usize {
        self.counter.load(Ordering::Relaxed)
    }
}
