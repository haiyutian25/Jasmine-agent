//! Shares immutable output schema storage and materializes private JSON for consumers.

use serde_json::Value;
use std::sync::Arc;

/// Immutable output schema that a consumer materializes only when it needs JSON.
#[derive(Debug, Clone)]
pub struct ToolOutputSchema(Arc<Value>);

impl ToolOutputSchema {
    /// Materialize the schema for consumers that traverse JSON values.
    pub fn to_value(&self) -> Value {
        self.0.as_ref().clone()
    }

    /// Consume the schema, reusing uniquely owned JSON and cloning shared storage.
    pub fn into_value(self) -> Value {
        Arc::unwrap_or_clone(self.0)
    }
}

impl From<Value> for ToolOutputSchema {
    fn from(value: Value) -> Self {
        Self(Arc::new(value))
    }
}

impl PartialEq for ToolOutputSchema {
    fn eq(&self, other: &Self) -> bool {
        self.0 == other.0
    }
}
