pub mod debug;
pub mod info;
pub mod lifecycle;
pub mod log;
pub mod monitor;
pub mod restart;
pub mod status;
pub mod tuning;

pub use debug::{
    handle_clear_inject, handle_get_rates, handle_inject, handle_list_packages, handle_set_log,
};
pub use info::handle_info;
pub use lifecycle::{handle_disable, handle_enable, handle_get_pid, handle_ping, handle_reload};
pub use log::handle_log;
pub use monitor::handle_monitor;
pub use restart::handle_restart;
pub use status::handle_status;
pub use tuning::{
    handle_add_game, handle_get_fps, handle_list_games, handle_remove_game, handle_set_fps,
    handle_set_profile,
};
