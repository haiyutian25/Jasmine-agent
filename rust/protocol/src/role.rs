//! Who produced one piece of a conversation, as the host sees it.

use serde::Deserialize;
use serde::Serialize;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Enum))]
#[serde(rename_all = "lowercase")]
pub enum Role {
    /// The user's own message.
    User,
    /// The model's reply.
    Model,
}

impl Role {
    /// The role name the wire uses for this side of the conversation.
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::User => "user",
            Self::Model => "assistant",
        }
    }
}

#[cfg(test)]
mod tests {
    use super::Role;

    #[test]
    fn maps_onto_the_wire_roles() {
        assert_eq!(Role::User.as_str(), "user");
        assert_eq!(Role::Model.as_str(), "assistant");
    }
}
