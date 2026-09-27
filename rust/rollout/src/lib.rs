//! 会话落盘：每个会话一个只追加的 JSONL，首行是会话自身的元信息。
//!
//! 文件就是转写本身——用户说了什么、模型答了什么、调了什么工具、得到了什么结果，按发生的
//! 顺序一行一条追加进去，进程中断也不会丢到上一行。会话的列表与恢复都从这些文件读，不需要
//! 另一份存储。

mod list;
mod model;
mod recorder;
mod rollout_file_name;

pub use list::SessionEntry;
pub use list::delete_session;
pub use list::find_session_path;
pub use list::interrupted_turn;
pub use list::list_sessions;
pub use list::read_items;
pub use list::read_response_items;
pub use list::read_session;
pub use model::RolloutItem;
pub use model::RolloutLine;
pub use model::SessionMeta;
pub use model::TurnAbortReason;
pub use model::timestamp_now;
pub use recorder::RolloutRecorder;

/// 会话文件所在的子目录，落在平台给的目录下面。
pub const SESSIONS_SUBDIR: &str = "sessions";
