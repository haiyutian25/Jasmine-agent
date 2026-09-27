//! Outcome of asking a provider's endpoint whether it can answer at all.
//!
//! The detail strings are the raw technical reason (HTTP status, transport error, provider
//! message), because the caller surfaces them verbatim and this layer has no UI.

use serde::Deserialize;
use serde::Serialize;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Enum))]
pub enum ProbeResult {
    /// The provider answered; `reply` is its trimmed text content.
    Success { reply: String },
    /// The round trip failed; `detail` explains why.
    Failure { detail: String },
}

/// What a probe reports when the round trip worked but carried no text.
pub const EMPTY_REPLY_DETAIL: &str = "the model returned an empty reply";

/// The cheapest prompt that still proves a model answers with content.
pub const PROBE_PROMPT: &str = "Reply with the single word: pong";

#[cfg(test)]
mod tests {
    use super::ProbeResult;

    #[test]
    fn carries_the_reply_or_the_reason() {
        let success = ProbeResult::Success {
            reply: "pong".to_string(),
        };
        let failure = ProbeResult::Failure {
            detail: "HTTP 401".to_string(),
        };
        assert_ne!(success, failure);
        assert!(matches!(success, ProbeResult::Success { .. }));
    }
}
