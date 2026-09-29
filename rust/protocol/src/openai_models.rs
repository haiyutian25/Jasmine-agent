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
/// 这份表**由上游两家的目录决定**：DeepSeek 照 `dsh` 的 `llm-deepseek`（off / low / high / max，
/// 默认 high），OpenAI 照 codex 的 `models-manager/models.json`（逐模型 low…max，其中五个还有 ultra）。
/// 上游不声明的档这里就没有 —— 所以这里**没有** `minimal`（两家都不声明它），多一个都算多余。
///
/// 「未设置」不在枚举里：那是"一个推理字段都不发"，由 `Option<ReasoningEffort>` 的 `None` 表达。
#[derive(Debug, Default, Clone, PartialEq, Eq, Hash)]
pub enum ReasoningEffort {
    /// 显式关掉思考，线上取值 `none`（codex 的 `ReasoningEffort::None` 就是这个；DeepSeek 那边叫 `off`）。
    ///
    /// 与「未设置」不是一回事：那是**不干预**，端点默认就思考的话照样思考；这个是明确要求不思考。
    /// 取值走的是 chat/completions 那一套通用写法（实测我们内置那家供应商的端点接受它，返回里就
    /// 没有 `reasoning_content` 了）；另有一个写法 `thinking: {type: "disabled"}` 是某些兼容端点
    /// 自己的方言、未必通用，所以不采用。
    None,
    Low,
    #[default]
    Medium,
    High,
    XHigh,
    Max,
    /// **界面档**，不是线上取值：codex 的 `models.json` 里 OpenAI 那几个模型声明了它，但发请求前
    /// 会被换成该模型支持的最强档（见它的 `resolve_reasoning_effort`，我们照做）。
    ///
    /// 解析在 `jasmine_model_provider_info::presets::wire_level` —— **别把它直接发出去**。
    Ultra,
}

impl ReasoningEffort {
    /// 这一档的**名字**（界面与配置里用的那个词）。`ultra` 是界面档，线上取值要过
    /// `presets::wire_level` 换一次才算数。
    pub fn as_str(&self) -> &str {
        match self {
            Self::None => "none",
            Self::Low => "low",
            Self::Medium => "medium",
            Self::High => "high",
            Self::XHigh => "xhigh",
            Self::Max => "max",
            Self::Ultra => "ultra",
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
            "low" => Ok(Self::Low),
            "medium" => Ok(Self::Medium),
            "high" => Ok(Self::High),
            "xhigh" => Ok(Self::XHigh),
            "max" => Ok(Self::Max),
            "ultra" => Ok(Self::Ultra),
            "" => Err("reasoning_effort must not be empty".to_string()),
            // 认不出的取值不当成"未知档位"发出去：调用方按"未设置"处理（一个字段都不发）。
            other => Err(format!("unknown reasoning_effort: {other}")),
        }
    }
}
