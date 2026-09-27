use jasmine_protocol::models::ResponseItem;
use serde::Deserialize;
use serde::Serialize;

/// What a conversation needs to be listed and resumed.
///
/// It is the first line of the rollout and the only part a list has to read.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SessionMeta {
    pub session_id: String,
    /// When the conversation was created, in RFC 3339.
    pub timestamp: String,
    pub title: String,
    pub provider_id: String,
    pub model_id: String,
}

/// One entry of a conversation.
///
/// The payload rides under its own key: an item carries its own `type` of its own, and two tags
/// in one object would overwrite each other.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", content = "payload", rename_all = "snake_case")]
pub enum RolloutItem {
    SessionMeta(SessionMeta),
    ResponseItem(ResponseItem),
    /// A turn's sampling began. The file carries the id, so a turn that never finishes can be
    /// resumed under it.
    TurnStarted {
        turn_id: String,
    },
    /// The turn finished.
    TurnComplete {
        turn_id: String,
    },
    /// The turn stopped before finishing.
    TurnAborted {
        turn_id: String,
        reason: TurnAbortReason,
    },
}

/// Why a turn stopped before it finished.
///
/// Only the reason the platform itself produces is defined so far; the set grows with its callers.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TurnAbortReason {
    /// The platform asked the turn to stop.
    Interrupted,
}

/// One JSONL line: an item with the moment it was recorded.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct RolloutLine {
    pub timestamp: String,
    pub item: RolloutItem,
}

impl RolloutLine {
    /// Wraps an item with the current time.
    pub fn now(item: RolloutItem) -> Self {
        Self {
            timestamp: timestamp_now(),
            item,
        }
    }
}

/// The current time, in the shape every line carries.
pub fn timestamp_now() -> String {
    chrono::Local::now().to_rfc3339_opts(chrono::SecondsFormat::Millis, true)
}
