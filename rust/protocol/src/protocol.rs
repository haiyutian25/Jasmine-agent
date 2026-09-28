use serde::Deserialize;
use serde::Serialize;
use std::ops::Mul;

#[derive(Debug, Clone, Deserialize, Serialize, Default, PartialEq, Eq)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
pub struct TokenUsage {
    pub input_tokens: i64,
    pub cached_input_tokens: i64,
    #[serde(default)]
    pub cache_write_input_tokens: i64,
    pub output_tokens: i64,
    pub reasoning_output_tokens: i64,
    pub total_tokens: i64,
}

impl TokenUsage {
    /// What one request put in the model's context window.
    pub fn tokens_in_context_window(&self) -> i64 {
        self.total_tokens
    }

    /// In-place element-wise sum of token counts.
    pub fn add_assign(&mut self, other: &TokenUsage) {
        self.input_tokens += other.input_tokens;
        self.cached_input_tokens += other.cached_input_tokens;
        self.cache_write_input_tokens += other.cache_write_input_tokens;
        self.output_tokens += other.output_tokens;
        self.reasoning_output_tokens += other.reasoning_output_tokens;
        self.total_tokens += other.total_tokens;
    }
}

/// What a conversation cost: the running total, the last response's own count, and the window
/// both have to fit in.
#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
pub struct TokenUsageInfo {
    pub total_token_usage: TokenUsage,
    pub last_token_usage: TokenUsage,
    /// The model's context window, as the platform configured it. `None` when it never was.
    pub model_context_window: Option<i64>,
}

impl TokenUsageInfo {
    /// Folds one response's usage into the conversation's, starting one when there is none.
    pub fn new_or_append(
        info: Option<&TokenUsageInfo>,
        last: Option<&TokenUsage>,
        model_context_window: Option<i64>,
    ) -> Option<Self> {
        if info.is_none() && last.is_none() {
            return None;
        }

        let mut info = info.cloned().unwrap_or(Self {
            total_token_usage: TokenUsage::default(),
            last_token_usage: TokenUsage::default(),
            model_context_window,
        });
        if let Some(last) = last {
            info.append_last_usage(last);
        }
        if model_context_window.is_some() {
            info.model_context_window = model_context_window;
        }
        Some(info)
    }

    fn append_last_usage(&mut self, last: &TokenUsage) {
        self.total_token_usage.add_assign(last);
        self.last_token_usage = last.clone();
    }
}

/// Which part of a request the tokens went to.
#[derive(Debug, Clone, Copy, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
#[cfg_attr(feature = "uniffi", derive(uniffi::Enum))]
pub enum ContextUsageSource {
    /// The instruction the platform sends with every request.
    SystemPrompt,
    /// The schemas of the tools on offer.
    SystemToolSchemas,
    /// What skills contribute. Nothing fills this bucket yet.
    Skills,
    /// The schemas of the tools an MCP server contributes.
    McpToolSchemas,
    /// The conversation itself: what the user and the model have said.
    Messages,
}

/// One part of a request, counted in tokens.
#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
pub struct ContextUsageBreakdownItem {
    pub source: ContextUsageSource,
    pub tokens: i64,
}

#[derive(Debug, Clone, Copy, Deserialize, Serialize, PartialEq, Eq)]
#[serde(tag = "mode", content = "limit", rename_all = "snake_case")]
pub enum TruncationPolicy {
    Bytes(usize),
    Tokens(usize),
}

impl TruncationPolicy {
    pub fn token_budget(&self) -> usize {
        match self {
            TruncationPolicy::Bytes(bytes) => {
                usize::try_from(jasmine_utils_string::approx_tokens_from_byte_count(*bytes))
                    .unwrap_or(usize::MAX)
            }
            TruncationPolicy::Tokens(tokens) => *tokens,
        }
    }

    pub fn byte_budget(&self) -> usize {
        match self {
            TruncationPolicy::Bytes(bytes) => *bytes,
            TruncationPolicy::Tokens(tokens) => {
                jasmine_utils_string::approx_bytes_for_tokens(*tokens)
            }
        }
    }
}

impl Mul<f64> for TruncationPolicy {
    type Output = Self;

    fn mul(self, multiplier: f64) -> Self::Output {
        match self {
            TruncationPolicy::Bytes(bytes) => {
                TruncationPolicy::Bytes((bytes as f64 * multiplier).ceil() as usize)
            }
            TruncationPolicy::Tokens(tokens) => {
                TruncationPolicy::Tokens((tokens as f64 * multiplier).ceil() as usize)
            }
        }
    }
}
