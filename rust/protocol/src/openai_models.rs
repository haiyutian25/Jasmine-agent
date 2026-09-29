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

/// 这个 app 提供的推理档位。见
/// https://platform.openai.com/docs/guides/reasoning?api-mode=responses#get-started-with-reasoning
///
/// 七个档与两个界面（聊天输入行的选择面板、供应商页的模型编辑）里那张列表**一一对应**；
/// 「未设置」不在枚举里 —— 它是"一个推理字段都不发"，由 `Option<ReasoningEffort>` 的 `None` 表达。
/// 加档位要三处一起改。
#[derive(Debug, Default, Clone, PartialEq, Eq, Hash)]
pub enum ReasoningEffort {
    /// 显式关掉思考，线上取值 `none`（codex 的 `ReasoningEffort::None` 就是这个）。
    ///
    /// 与「未设置」不是一回事：那是**不干预**，端点默认就思考的话照样思考；这个是明确要求不思考。
    /// 取值走的是 chat/completions 那一套通用写法（实测我们内置那家供应商的端点接受它，返回里就
    /// 没有 `reasoning_content` 了）；另有一个写法 `thinking: {type: "disabled"}` 是某些兼容端点
    /// 自己的方言、未必通用，所以不采用。
    None,
    Minimal,
    Low,
    #[default]
    Medium,
    High,
    XHigh,
    Max,
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
            "" => Err("reasoning_effort must not be empty".to_string()),
            // 认不出的取值不当成"未知档位"发出去：调用方按"未设置"处理（一个字段都不发）。
            other => Err(format!("unknown reasoning_effort: {other}")),
        }
    }
}
