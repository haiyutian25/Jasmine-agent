/// How much of a tool's arguments/result is worth putting on screen.
const TOOL_DETAIL_MAX_LENGTH: usize = 200;

/// What the UI sees for one assistant turn.
#[derive(Debug, Clone, PartialEq, Eq)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Enum))]
pub enum ChatEvent {
    /// A chunk of assistant text, emitted as it streams.
    Text(String),
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
