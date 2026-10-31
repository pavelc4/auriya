//! Native Android Binder IPC adapter for Auriya.
//!
//! Provides zero-disk in-memory streaming communication with Android OS system services:
//! - NotificationManager (Do Not Disturb)
//! - DisplayManager / Settings (Display refresh rate)
//! - PowerManager (Screen interactive state)
//! - ActivityManager (Process & foreground activity lifecycle)

pub mod actuators;
pub mod client;
pub mod observer;

pub use actuators::{DndMode, apply_dnd, apply_refresh_rate};
pub use client::BinderClient;
pub use observer::{ProcessEvent, ProcessEventType, ProcessObserver};
