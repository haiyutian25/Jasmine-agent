//! Per-response usage metadata reported by the upstream service, without aggregation.

use serde::Deserialize;
use serde::Serialize;
use serde_json::Value;

/// Usage metadata reported for one upstream response.
#[derive(Debug, Clone, Default, Deserialize, Serialize, PartialEq, Eq)]
pub struct ResponseUsageMetadata {
    pub amount: Option<String>,
    pub metadata: Option<Value>,
}
