//! The conversation identity the two sides of the boundary agree on.
//!
//! The id is the host's own persisted conversation id: the model's working context and the
//! durable transcript share it, so a resumed conversation is the same conversation.

use serde::Deserialize;
use serde::Serialize;
use std::fmt;

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(transparent)]
pub struct SessionId(String);

impl SessionId {
    pub fn new(id: impl Into<String>) -> Self {
        Self(id.into())
    }

    pub fn as_str(&self) -> &str {
        self.0.as_str()
    }
}

impl fmt::Display for SessionId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl From<String> for SessionId {
    fn from(id: String) -> Self {
        Self::new(id)
    }
}

impl From<&str> for SessionId {
    fn from(id: &str) -> Self {
        Self::new(id)
    }
}

#[cfg(test)]
mod tests {
    #![allow(clippy::unwrap_used, clippy::expect_used)]
    use super::SessionId;

    #[test]
    fn keeps_the_host_provided_id() {
        let id = SessionId::new("conversation-7");
        assert_eq!(id.as_str(), "conversation-7");
        assert_eq!(id.to_string(), "conversation-7");
    }

    #[test]
    fn survives_a_round_trip_through_json() {
        let id = SessionId::new("conversation-7");
        let encoded = serde_json::to_string(&id).expect("serialize");
        assert_eq!(encoded, "\"conversation-7\"");
        let decoded: SessionId = serde_json::from_str(&encoded).expect("deserialize");
        assert_eq!(decoded, id);
    }
}
