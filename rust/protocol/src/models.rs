use crate::response_item_id::ResponseItemId;
use serde::Deserialize;
use serde::Serialize;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ContentItem {
    InputText {
        text: String,
    },
    InputImage {
        #[serde(flatten)]
        image: ImageReference,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        detail: Option<ImageDetail>,
    },
    InputAudio {
        audio_url: String,
    },
    OutputText {
        text: String,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(untagged)]
pub enum ImageReference {
    Inline { image_url: String },
    File { file_id: String },
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum ImageDetail {
    Auto,
    Low,
    High,
    Original,
}

pub const DEFAULT_IMAGE_DETAIL: ImageDetail = ImageDetail::High;

/// Parameters of a `tool_search` call.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct SearchToolCallParams {
    pub query: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub limit: Option<usize>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ResponseItem {
    Message {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        id: Option<ResponseItemId>,
        role: String,
        content: Vec<ContentItem>,
    },
    Reasoning {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        id: Option<ResponseItemId>,
        summary: Vec<ReasoningItemReasoningSummary>,
        /// The model's own words ride back with the turn that continues them: a provider in
        /// thinking mode refuses the follow-up that drops them, and asks for exactly this
        /// field. An absent one is left out rather than sent as null.
        #[serde(default, skip_serializing_if = "Option::is_none")]
        content: Option<Vec<ReasoningItemContent>>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        encrypted_content: Option<String>,
    },
    FunctionCall {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        id: Option<ResponseItemId>,
        name: String,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        namespace: Option<String>,
        // The Responses API returns the function call arguments as a *string* that contains
        // JSON, not as an already-parsed object.
        arguments: String,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        encrypted_function_args: Option<Vec<String>>,
        call_id: String,
    },
    // The `output` field for `function_call_output` is either a plain string (`content`)
    // or an array of structured content items (`content_items`); the encoding lives in
    // `FunctionCallOutputPayload`.
    FunctionCallOutput {
        #[serde(default, skip_serializing_if = "Option::is_none")]
        id: Option<ResponseItemId>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        call_id: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        name: Option<String>,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        namespace: Option<String>,
        output: FunctionCallOutputPayload,
    },
    #[serde(other)]
    Other,
}

impl ResponseItem {
    /// Returns whether this item is an ordinary user-role message.
    pub fn is_user_message(&self) -> bool {
        matches!(self, Self::Message { role, .. } if role == "user")
    }

    /// Returns the Responses API item ID, if present.
    pub fn id(&self) -> Option<&ResponseItemId> {
        match self {
            Self::Message { id, .. }
            | Self::Reasoning { id, .. }
            | Self::FunctionCall { id, .. }
            | Self::FunctionCallOutput { id, .. } => id.as_ref(),
            Self::Other => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ReasoningItemReasoningSummary {
    SummaryText { text: String },
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ReasoningItemContent {
    ReasoningText { text: String },
    Text { text: String },
}

/// The payload we send back to OpenAI when reporting a tool call result.
///
/// `body` serializes directly as the wire value for `function_call_output.output`.
/// `success` remains internal metadata for downstream handling.
#[derive(Debug, Default, Clone, PartialEq)]
pub struct FunctionCallOutputPayload {
    pub body: FunctionCallOutputBody,
    pub success: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(untagged)]
pub enum FunctionCallOutputBody {
    Text(String),
    ContentItems(Vec<FunctionCallOutputContentItem>),
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum FunctionCallOutputContentItem {
    InputText {
        text: String,
    },
    InputImage {
        #[serde(flatten)]
        image: ImageReference,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        detail: Option<ImageDetail>,
    },
    InputAudio {
        audio_url: String,
    },
    EncryptedContent {
        encrypted_content: String,
    },
}

impl Default for FunctionCallOutputBody {
    fn default() -> Self {
        Self::Text(String::new())
    }
}

impl FunctionCallOutputBody {
    /// Best-effort conversion of a function-call output body to plain text for
    /// human-readable surfaces.
    ///
    /// This conversion is intentionally lossy when the body contains content
    /// items: image entries are dropped and text entries are joined with
    /// newlines.
    pub fn to_text(&self) -> Option<String> {
        match self {
            Self::Text(content) => Some(content.clone()),
            Self::ContentItems(items) => {
                let text_segments: Vec<&str> = items
                    .iter()
                    .filter_map(|item| match item {
                        FunctionCallOutputContentItem::InputText { text } => Some(text.as_str()),
                        FunctionCallOutputContentItem::InputImage { .. }
                        | FunctionCallOutputContentItem::InputAudio { .. }
                        | FunctionCallOutputContentItem::EncryptedContent { .. } => None,
                    })
                    .collect();
                if text_segments.is_empty() {
                    return None;
                }
                Some(text_segments.join("\n"))
            }
        }
    }
}

impl FunctionCallOutputPayload {
    pub fn from_text(content: String) -> Self {
        Self {
            body: FunctionCallOutputBody::Text(content),
            success: None,
        }
    }

    pub fn text_content(&self) -> Option<&str> {
        match &self.body {
            FunctionCallOutputBody::Text(content) => Some(content.as_str()),
            FunctionCallOutputBody::ContentItems(_) => None,
        }
    }
}

impl Serialize for FunctionCallOutputPayload {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::Serializer,
    {
        self.body.serialize(serializer)
    }
}

impl<'de> Deserialize<'de> for FunctionCallOutputPayload {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: serde::Deserializer<'de>,
    {
        FunctionCallOutputBody::deserialize(deserializer).map(|body| Self {
            body,
            success: None,
        })
    }
}
