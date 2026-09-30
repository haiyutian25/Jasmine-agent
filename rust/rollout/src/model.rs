use jasmine_protocol::models::ResponseItem;
use jasmine_protocol::protocol::ContextUsageBreakdownItem;
use jasmine_protocol::protocol::TokenUsageInfo;
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
    /// resumed under it, and the model the turn ran on, so a conversation that switched models
    /// still shows each answer's own — and so the usage of that turn can be placed under the model
    /// that spent it.
    TurnStarted {
        turn_id: String,
        /// The model this turn ran on.
        model_id: String,
        /// 这一轮实际用的推理档位（codex 那套值：`minimal` / `low` / `medium` / `high`，或 provider 自
        /// 己的词；空串表示没设置 —— 请求里一个推理字段都不发）。
        ///
        /// 逐轮记下来，往回翻历史时就能看到"这一轮用的什么档位"，而不是只有一个会话级的当前值。
        reasoning_effort: String,
    },
    /// The turn finished.
    TurnComplete {
        turn_id: String,
    },
    /// The context window this conversation runs against, in tokens.
    ///
    /// Written once — when the conversation is first attached, from the model the platform had
    /// picked then — and again whenever the platform changes it, so the last one in the file is the
    /// one that counts.
    ContextWindow {
        tokens: u64,
    },
    /// 这个会话当前的推理档位（codex 那套值；空串 = 未设置，请求里不发任何推理字段）。
    ///
    /// **每次改动都追加一条，最后一条生效** —— 新建会话时写的第一条来自模型配置里的档位（"新建对话时
    /// 读一次模型级设置"），之后用户在对话里每改一次就再多一条，所以历史里能看到「未设置 → 高」这样的
    /// 完整变化，而这些记录一条都不删。
    ReasoningEffort {
        value: String,
    },
    /// What a finished turn cost, and the makeup of the request that produced it.
    ///
    /// Written once per turn that reported a cost. Upstream records the same thing under the same
    /// name; a conversation reopened later reads the last one back, so the usage it shows is not
    /// lost with the process.
    TokenUsageRecord {
        info: TokenUsageInfo,
        breakdown: Vec<ContextUsageBreakdownItem>,
    },
    /// The turn stopped before finishing.
    TurnAborted {
        turn_id: String,
        reason: TurnAbortReason,
        /// How long the turn had been running, which the platform shows as "stopped after N s".
        duration_ms: u64,
    },
    /// The part of an answer a stopped turn had already written.
    ///
    /// A presentation record, not a model-visible one: the conversation is made of the items the
    /// provider marked done, and a round cut off in the middle never got that far. `read_response_items`
    /// reads `ResponseItem` only, so the next request does not carry this, and the transcript shows it
    /// as the text the turn had reached.
    InterruptedReply {
        turn_id: String,
        text: String,
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
