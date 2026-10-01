//! 探测一家端点时用的提示词，以及"答了但没内容"的说法。
//!
//! 这里只留这两条常量：探测的**结果形状**就是核心其他边界调用的形状（`Result` + 一句能显示的
//! 原因），不再单立一个 `ProbeResult` 枚举 —— 同一层里两套失败风格只会让人不知道该照哪套写。

/// What a probe reports when the round trip worked but carried no text.
pub const EMPTY_REPLY_DETAIL: &str = "the model returned an empty reply";

/// The cheapest prompt that still proves a model answers with content.
pub const PROBE_PROMPT: &str = "Reply with the single word: pong";
