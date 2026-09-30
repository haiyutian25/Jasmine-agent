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
    /// A name for [session_id], or `None` when that id cannot be one.
    ///
    /// The id is the platform's, and it ends up inside a path — so it is checked here rather than
    /// trusted: a separator, a `..` or anything else with meaning to the filesystem would let a name
    /// point outside the conversation's own directory. Ids the platform hands over are UUIDs; this
    /// keeps that the only thing that can be written, whatever a stored file claims its id was.
    pub fn new(timestamp: NaiveDateTime, session_id: String) -> Option<Self> {
        is_usable_session_id(&session_id).then_some(Self {
            timestamp,
            session_id,
        })
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

/// Whether a session id may be part of a file name.
///
/// Everything the platform generates fits: letters, digits, `-` and `_`. Anything else — a path
/// separator, a dot that could pair up into `..`, a control character — is refused.
pub fn is_usable_session_id(session_id: &str) -> bool {
    !session_id.is_empty()
        && session_id.len() <= MAX_SESSION_ID_LENGTH
        && session_id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
}

/// Long enough for a UUID and then some; short enough that a name stays a name.
const MAX_SESSION_ID_LENGTH: usize = 128;

#[cfg(test)]
#[path = "rollout_file_name_tests.rs"]
mod tests;
