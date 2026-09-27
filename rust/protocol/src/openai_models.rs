use serde::Deserialize;
use serde::Deserializer;
use serde::Serialize;
use serde::Serializer;
use serde::de::Error;
use std::fmt;
use std::str::FromStr;

/// A content modality a model accepts on input.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Deserialize, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum InputModality {
    /// Plain text turns and tool payloads.
    Text,
    /// Image attachments included in user turns.
    Image,
    /// Audio attachments included in user turns.
    Audio,
}

/// Backward-compatible default when `input_modalities` is omitted on the wire.
///
/// Legacy payloads predate modality metadata, so we conservatively assume both text and images are
/// accepted unless a preset explicitly narrows support.
pub fn default_input_modalities() -> Vec<InputModality> {
    vec![InputModality::Text, InputModality::Image]
}

/// See https://platform.openai.com/docs/guides/reasoning?api-mode=responses#get-started-with-reasoning
#[derive(Debug, Default, Clone, PartialEq, Eq, Hash)]
pub enum ReasoningEffort {
    None,
    Minimal,
    Low,
    #[default]
    Medium,
    High,
    XHigh,
    Max,
    Ultra,
    Persistent,
    /// A model-defined effort value that this client does not know yet.
    Custom(String),
}

impl ReasoningEffort {
    /// Returns the exact value used on the wire.
    pub fn as_str(&self) -> &str {
        match self {
            Self::None => "none",
            Self::Minimal => "minimal",
            Self::Low => "low",
            Self::Medium => "medium",
            Self::High => "high",
            Self::XHigh => "xhigh",
            Self::Max => "max",
            Self::Ultra => "ultra",
            Self::Persistent => "persistent",
            Self::Custom(effort) => effort,
        }
    }
}

impl fmt::Display for ReasoningEffort {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl Serialize for ReasoningEffort {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(self.as_str())
    }
}

impl<'de> Deserialize<'de> for ReasoningEffort {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let effort = String::deserialize(deserializer)?;
        effort.parse().map_err(D::Error::custom)
    }
}

impl FromStr for ReasoningEffort {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "none" => Ok(Self::None),
            "minimal" => Ok(Self::Minimal),
            "low" => Ok(Self::Low),
            "medium" => Ok(Self::Medium),
            "high" => Ok(Self::High),
            "xhigh" => Ok(Self::XHigh),
            "max" => Ok(Self::Max),
            "ultra" => Ok(Self::Ultra),
            "persistent" => Ok(Self::Persistent),
            "" => Err("reasoning_effort must not be empty".to_string()),
            effort => Ok(Self::Custom(effort.to_string())),
        }
    }
}
