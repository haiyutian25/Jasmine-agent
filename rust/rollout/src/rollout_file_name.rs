use chrono::NaiveDateTime;

/// Parsed canonical rollout basename.
///
/// The name carries the moment the conversation started and the session it belongs to, so
/// discovery can order conversations without opening a single file.
#[derive(Debug, PartialEq, Eq)]
pub struct RolloutFileName {
    timestamp: NaiveDateTime,
    session_id: String,
}

const TIMESTAMP_FORMAT: &str = "%Y-%m-%dT%H-%M-%S";

impl RolloutFileName {
    pub fn new(timestamp: NaiveDateTime, session_id: String) -> Self {
        Self {
            timestamp,
            session_id,
        }
    }

    pub fn parse(name: &str) -> Option<Self> {
        let core = name.strip_prefix("rollout-")?.strip_suffix(".jsonl")?;
        let timestamp = core.get(..19)?;
        if core.get(19..20)? != "-" {
            return None;
        }
        let session_id = core.get(20..)?.to_string();
        let timestamp = NaiveDateTime::parse_from_str(timestamp, TIMESTAMP_FORMAT).ok()?;
        Some(Self {
            timestamp,
            session_id,
        })
    }

    pub fn render(&self) -> String {
        format!(
            "rollout-{}-{}.jsonl",
            self.timestamp.format(TIMESTAMP_FORMAT),
            self.session_id
        )
    }

    pub fn session_id(&self) -> &str {
        &self.session_id
    }
}

#[cfg(test)]
#[path = "rollout_file_name_tests.rs"]
mod tests;
