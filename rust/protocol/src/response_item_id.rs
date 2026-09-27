use std::fmt;
use std::ops::Deref;

use serde::Deserialize;
use serde::Serialize;

/// A Responses API item ID. Deserialization remains permissive so legacy
/// rollouts can still be read.
#[derive(Debug, Clone, PartialEq, Eq, Hash, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(transparent)]
pub struct ResponseItemId(String);

impl ResponseItemId {
    pub fn from_server(value: String) -> Self {
        Self(value)
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }

    pub fn is_prefixed(&self) -> bool {
        self.split_once('_')
            .is_some_and(|(prefix, suffix)| !prefix.is_empty() && !suffix.is_empty())
    }
}

impl Deref for ResponseItemId {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        self.as_str()
    }
}

impl AsRef<str> for ResponseItemId {
    fn as_ref(&self) -> &str {
        self.as_str()
    }
}

impl fmt::Display for ResponseItemId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(self.as_str())
    }
}

impl From<ResponseItemId> for String {
    fn from(value: ResponseItemId) -> Self {
        value.0
    }
}
