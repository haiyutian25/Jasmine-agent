use crate::protocol::ContextUsageBreakdownItem;
use crate::protocol::TokenUsageInfo;

/// How much of a tool's arguments/result is worth putting on screen.
const TOOL_DETAIL_MAX_LENGTH: usize = 200;

/// What the UI sees for one assistant turn.
#[derive(Debug, Clone, PartialEq, Eq)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Enum))]
pub enum ChatEvent {
    /// A chunk of assistant text, emitted as it streams.
    Text(String),
    /// A chunk of the model's **thinking**, emitted as it streams.
    ///
    /// It is not part of the answer: the platform shows it in its own collapsible block beside the
    /// reply, and the transcript keeps it as a reasoning item of its own so the provider can be
    /// shown the thinking again.
    Reasoning(String),
    /// A tool the model asked for, with its arguments abbreviated for a status line.
    ToolCall { name: String, arguments: String },
    /// A tool's result, abbreviated the same way.
    ToolResult { name: String, result: String },
    /// A call that stops the turn and waits for the user.
    UserPromptRequested {
        prompt: String,
        options: Vec<String>,
    },
    /// The turn failed; the detail is the raw technical reason.
    Failed(String),
    /// The turn finished.
    Completed,
    /// The turn stopped because the platform asked it to; whatever it had produced so far is
    /// already in the transcript.
    Aborted { duration_ms: u64 },
    /// 上下文自动压缩**开始时**报一次：历史已经超过窗口的阈值，正在让模型把它压成摘要。
    ///
    /// 压缩要**额外打一次模型请求**（在已经跑着的这一轮里），所以这里是个真实可见的停顿 ——
    /// 界面上得有个提示，否则用户只看到"卡住了"。
    ///
    /// 它不是回合的一步，也不结束回合：压缩完成后这一轮照常往下跑（下一次请求带的是压缩后的历史）。
    Compacting {
        /// 触发压缩时的活跃 token 估算（也就是"压之前有多大"）。
        tokens: i64,
        /// 该会话的上下文窗口。
        context_window: i64,
    },
    /// What the conversation's context window looks like after one request: what it cost, how
    /// big the window is, and which part of the request the tokens went to.
    ///
    /// Metadata about the request rather than a step of the reply, but it arrives like one: the
    /// number only exists once the model has answered.
    ///
    /// The breakdown's shares come from the core's own byte-based estimate, while its total is what
    /// the model reported for its input — a tokenizer only exists on the provider's side.
    Usage {
        info: TokenUsageInfo,
        breakdown: Vec<ContextUsageBreakdownItem>,
    },
}

impl ChatEvent {
    pub fn tool_call(name: impl Into<String>, arguments: &str) -> Self {
        Self::ToolCall {
            name: name.into(),
            arguments: abbreviate(arguments),
        }
    }

    pub fn tool_result(name: impl Into<String>, result: &str) -> Self {
        Self::ToolResult {
            name: name.into(),
            result: abbreviate(result),
        }
    }
}

/// Renders model-supplied arguments / tool responses compactly for a status line.
pub fn abbreviate(text: &str) -> String {
    if text.chars().count() <= TOOL_DETAIL_MAX_LENGTH {
        return text.to_string();
    }
    let mut truncated: String = text.chars().take(TOOL_DETAIL_MAX_LENGTH - 1).collect();
    truncated.push('…');
    truncated
}
