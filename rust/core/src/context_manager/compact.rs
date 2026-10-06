//! 上下文压缩（auto compact）—— 逐项移植自 codex `codex-rs`。
//!
//! 上游位置与本文的对应关系（每一处都在下面就地标注了行号）：
//!
//! | 本文 | codex 上游 |
//! |---|---|
//! | [`SUMMARIZATION_PROMPT`] | `prompts/templates/compact/prompt.md` |
//! | [`SUMMARY_PREFIX`] | `prompts/templates/compact/summary_prefix.md` |
//! | [`COMPACT_USER_MESSAGE_MAX_TOKENS`] | `core/src/compact.rs:55` |
//! | [`auto_compact_token_limit`] | `protocol/src/openai_models.rs:525-536` |
//! | [`token_limit_reached`] | `core/src/session/context_window.rs:106-110` |
//! | [`summary_with_prefix`] | `core/src/compact.rs:356` |
//! | [`is_summary_message`] | `core/src/compact.rs:577-579` |
//! | [`collect_user_messages`] | `core/src/compact.rs:550-557` |
//! | [`build_compacted_history`] | `core/src/compact.rs:662-740` |
//!
//! 上游把这一切分成触发（`session/context_window.rs`）、执行（`compact.rs`）、落盘
//! （`session/mod.rs: replace_compacted_history`）三块；这里把**纯逻辑**先收在一个文件里
//! （触发判定 + 历史重建），发请求与替换历史的部分在调用侧（`session/turn.rs`）。

use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::ResponseItem;
use jasmine_utils_output_truncation::TruncationPolicy;
use jasmine_utils_output_truncation::approx_token_count;
use jasmine_utils_output_truncation::truncate_text;

/// codex `prompts/templates/compact/prompt.md` —— **原文照录**。
///
/// 这是压缩时发给模型的唯一一条 user 消息（追加在完整历史末尾）。
pub const SUMMARIZATION_PROMPT: &str = "\
You are performing a CONTEXT CHECKPOINT COMPACTION. Create a handoff summary for another LLM that will resume the task.

Include:
- Current progress and key decisions made
- Important context, constraints, or user preferences
- What remains to be done (clear next steps)
- Any critical data, examples, or references needed to continue

Be concise, structured, and focused on helping the next LLM seamlessly continue the work.";

/// codex `prompts/templates/compact/summary_prefix.md` —— **原文照录**（单行）。
///
/// 压缩后用它与摘要正文拼成一条 user 消息（见 [`summary_with_prefix`]）。
pub const SUMMARY_PREFIX: &str = "Another language model started to solve this problem and produced a summary of its thinking process. You also have access to the state of the tools that were used by that language model. Use this to build on the work that has already been done and avoid duplicating work. Here is the summary produced by the other language model, use the information in this summary to assist with your own analysis:";

/// codex `core/src/compact.rs:55`。
///
/// 压缩后最多保留多少 token 的**用户消息**；超预算的那条会被截断成纯文本。
pub const COMPACT_USER_MESSAGE_MAX_TOKENS: usize = 20_000;

/// codex `protocol/src/openai_models.rs:525-536` —— 自动压缩的 token 上限。
///
/// 上游：`(context_window * 9) / 10`，可被 `model_auto_compact_token_limit` 覆盖，
/// 但覆盖值会被**钳到** 90% 那一档（`std::cmp::min`）。这里保留同一语义。
pub fn auto_compact_token_limit(context_window: i64, configured_limit: Option<i64>) -> Option<i64> {
    let context_limit = (context_window > 0).then(|| (context_window * 9) / 10);
    match (context_limit, configured_limit) {
        (Some(context), Some(configured)) => Some(configured.min(context)),
        (Some(context), None) => Some(context),
        (None, configured) => configured,
    }
}

/// codex `core/src/session/context_window.rs:106-110` —— 是否该触发自动压缩。
///
/// 上游判据是**绝对值**而不是百分比：
/// ```text
/// token_limit_reached = auto_compact_scope_tokens >= auto_compact_scope_limit + fallback_buffer
///                    || active_context_tokens >= full_context_window_limit
/// ```
/// 其中 `full_context_window_limit = context_window * effective_context_window_percent / 100`
/// （`openai_models.rs:389-391`，默认 95）。这里把 `fallback_buffer` 与 `percent` 作为参数传入，
/// 保持与上游同样的两个条件。
pub fn token_limit_reached(
    active_context_tokens: i64,
    context_window: i64,
    configured_limit: Option<i64>,
    effective_context_window_percent: i64,
    fallback_buffer_tokens: i64,
) -> bool {
    let scope_reached = auto_compact_token_limit(context_window, configured_limit)
        .is_some_and(|limit| active_context_tokens >= limit + fallback_buffer_tokens);
    let full_limit = (context_window > 0).then(|| context_window * effective_context_window_percent / 100);
    let full_reached = full_limit.is_some_and(|limit| active_context_tokens >= limit);
    scope_reached || full_reached
}

/// 一次压缩的结果，供调用侧落盘（`RolloutItem::Compacted`）。
///
/// 上游把同样这些字段装进 `CompactedItem`（`protocol/src/protocol.rs`）追加进 rollout；
/// 这里多带了几项诊断数据（触发时的 token / 窗口 / 前后条目数），因为我们的 rollout 是给人看的
/// 追加日志，多留几个数不花什么钱，事后调阈值时却省事。
#[derive(Debug, Clone)]
pub struct CompactRecord {
    /// 压缩后的历史，替换掉此前的全部条目。
    pub replacement_history: Vec<ResponseItem>,
    /// 摘要正文（不含前缀）。
    pub summary: String,
    /// 触发时的活跃 token 估算。
    pub active_context_tokens: i64,
    /// 触发时会话的上下文窗口。
    pub context_window: i64,
    /// 压缩前的条目数。
    pub items_before: usize,
    /// 压缩后保留的条目数。
    pub items_after: usize,
}

/// codex `core/src/compact.rs:356` —— 摘要正文拼上前缀。
pub fn summary_with_prefix(summary_suffix: &str) -> String {
    format!("{SUMMARY_PREFIX}\n{summary_suffix}")
}

/// codex `core/src/compact.rs:577-579` —— 判断一条消息是不是（上一次压缩留下的）摘要。
///
/// 上游用前缀识别，因为摘要就是一条 `role="user"` 的普通消息，**没有 XML 标记包裹**
/// （见 codex `core/src/context/compaction_summary.rs`，它的 `type_markers()` 返回两个空串）。
pub fn is_summary_message(message: &str) -> bool {
    message.starts_with(&format!("{SUMMARY_PREFIX}\n"))
}

/// 一条消息里可参与 token 计数的文本（`InputText` + `OutputText`）。
fn message_text(item: &ResponseItem) -> String {
    let ResponseItem::Message { content, .. } = item else {
        return String::new();
    };
    let mut out = String::new();
    for part in content {
        match part {
            ContentItem::InputText { text } | ContentItem::OutputText { text } => out.push_str(text),
            _ => {}
        }
    }
    out
}

/// 整条消息的 token 估算。
///
/// 上游有 `estimate_item_token_count`（`core/src/context_manager/history.rs`）做同样的事；
/// 我们这里只有 `approx_token_count(&str)`（`utils/string`，`len / 4`），所以先把该条的
/// 文本拼起来再估 —— 计的是同一批字符。
pub fn approx_item_token_count(item: &ResponseItem) -> usize {
    approx_token_count(&message_text(item))
}

/// codex `core/src/compact.rs:550-557` —— 收集历史里的用户消息（排除已有的摘要）。
///
/// 上游只保留 user 消息：工具调用 / 工具输出 / assistant 文本 / reasoning 一律丢弃，
/// 它们的"交接"职责由摘要承担。
pub fn collect_user_messages(items: &[ResponseItem]) -> Vec<ResponseItem> {
    items
        .iter()
        .filter(|item| item.is_user_message())
        .filter(|item| !is_summary_message(&message_text(item)))
        .cloned()
        .collect()
}

/// codex `core/src/compact.rs:662-740` —— 重建压缩后的历史。
///
/// 上游算法（逐字对应）：
/// 1. 从**最新往回**遍历 user 消息，累计到 `max_tokens` 为止；
/// 2. 单条超预算时把它截断成纯文本（`TruncationPolicy::Tokens(remaining)`）；
/// 3. 恢复时间序；
/// 4. **把摘要 push 到最后** —— 摘要永远在末尾，且没有 XML 标记包裹。
///
/// `max_tokens == 0` 时只留摘要。
pub fn build_compacted_history(
    user_messages: &[ResponseItem],
    summary_text: &str,
    max_tokens: usize,
) -> Vec<ResponseItem> {
    let mut selected: Vec<ResponseItem> = Vec::new();
    if max_tokens > 0 {
        let mut remaining = max_tokens;
        for message in user_messages.iter().rev() {
            if remaining == 0 {
                break;
            }
            let tokens = approx_item_token_count(message);
            let ResponseItem::Message { id, role, content } = message else {
                continue;
            };
            // 只有"整条都在预算内、且全是 InputText"才原样保留；否则截断成一条纯文本。
            let all_input_text = !content.is_empty()
                && content
                    .iter()
                    .all(|part| matches!(part, ContentItem::InputText { .. }));
            let new_content = if tokens <= remaining && all_input_text {
                content.clone()
            } else {
                vec![ContentItem::InputText {
                    text: truncate_text(&message_text(message), TruncationPolicy::Tokens(remaining)),
                }]
            };
            selected.push(ResponseItem::Message {
                id: id.clone(),
                role: role.clone(),
                content: new_content,
            });
            if tokens > remaining {
                break;
            }
            remaining = remaining.saturating_sub(tokens);
        }
        selected.reverse();
    }

    let summary_text = if summary_text.is_empty() {
        "(no summary available)".to_string()
    } else {
        summary_text.to_string()
    };
    selected.push(ResponseItem::Message {
        id: None,
        role: "user".to_string(),
        content: vec![ContentItem::InputText { text: summary_text }],
    });
    selected
}

#[cfg(test)]
mod tests {
    use super::*;
    use pretty_assertions::assert_eq;

    fn user(text: &str) -> ResponseItem {
        ResponseItem::Message {
            id: None,
            role: "user".to_string(),
            content: vec![ContentItem::InputText {
                text: text.to_string(),
            }],
        }
    }

    fn assistant(text: &str) -> ResponseItem {
        ResponseItem::Message {
            id: None,
            role: "assistant".to_string(),
            content: vec![ContentItem::OutputText {
                text: text.to_string(),
            }],
        }
    }

    fn summary(text: &str) -> ResponseItem {
        ResponseItem::Message {
            id: None,
            role: "user".to_string(),
            content: vec![ContentItem::InputText {
                text: summary_with_prefix(text),
            }],
        }
    }

    #[test]
    fn auto_compact_limit_is_ninety_percent_and_clamped() {
        // codex openai_models.rs:528 —— 默认取 context_window 的 90%。
        assert_eq!(Some(180_000), auto_compact_token_limit(200_000, None));
        // 配的比 90% 大 -> 钳到 90%。
        assert_eq!(Some(180_000), auto_compact_token_limit(200_000, Some(999_999)));
        // 配的比 90% 小 -> 用配的。
        assert_eq!(Some(100_000), auto_compact_token_limit(200_000, Some(100_000)));
        // 没有 context_window 时只能用配的。
        assert_eq!(Some(12_345), auto_compact_token_limit(0, Some(12_345)));
        assert_eq!(None, auto_compact_token_limit(0, None));
    }

    #[test]
    fn token_limit_reached_uses_absolute_thresholds() {
        // 90% 线：180k。
        assert!(!token_limit_reached(179_999, 200_000, None, 95, 0));
        assert!(token_limit_reached(180_000, 200_000, None, 95, 0));
        // 全窗口 95% 线：190k（即便 scope 限额更大，这条也能触发）。
        assert!(token_limit_reached(190_000, 200_000, Some(i64::MAX), 95, 0));
        // fallback buffer 会把 scope 线往后推（180_000 + 10）。
        assert!(!token_limit_reached(180_009, 200_000, None, 100, 10));
        assert!(token_limit_reached(180_010, 200_000, None, 100, 10));
    }

    #[test]
    fn token_limit_reached_percent_line_scales_with_the_window() {
        // 百分比线乘的是**当时的**窗口 —— 窗口是可变的（界面能改），所以同一个百分比
        // 在缩小后的窗口上要跟着缩。这条正是把模型配置接进来之后要守住的性质。
        assert!(!token_limit_reached(99_999, 200_000, None, 50, 0));
        assert!(token_limit_reached(100_000, 200_000, None, 50, 0));
        // 窗口缩到四分之一，50% 的线也缩到四分之一。
        assert!(token_limit_reached(25_000, 50_000, None, 50, 0));
        assert!(!token_limit_reached(24_999, 50_000, None, 50, 0));

        // 与绝对线是「或」的关系：绝对线先到就按绝对线触发，哪怕离百分比线还很远
        //（窗口 200k、百分比线 190k，而这次在 100k 上就该触发）。
        assert!(token_limit_reached(100_000, 200_000, Some(100_000), 95, 0));
        // 两条线都没到就不触发。
        assert!(!token_limit_reached(179_999, 200_000, Some(i64::MAX), 95, 0));
    }

    #[test]
    fn summary_text_is_prefix_plus_body() {
        let text = summary_with_prefix("did a thing");
        assert_eq!(format!("{SUMMARY_PREFIX}\ndid a thing"), text);
        assert!(is_summary_message(&text));
        assert!(!is_summary_message("just a normal user message"));
    }

    #[test]
    fn collect_user_messages_drops_tools_assistant_and_old_summaries() {
        let items = vec![
            user("first"),
            assistant("answer"),
            ResponseItem::FunctionCall {
                id: None,
                name: "shell".to_string(),
                namespace: None,
                arguments: "{}".to_string(),
                encrypted_function_args: None,
                call_id: "c1".to_string(),
            },
            summary("old summary"),
            user("second"),
        ];
        assert_eq!(vec![user("first"), user("second")], collect_user_messages(&items));
    }

    #[test]
    fn build_compacted_history_keeps_recent_user_messages_then_summary_last() {
        let messages = vec![user("old"), user("new")];
        let text = summary_with_prefix("SUM");
        let history = build_compacted_history(&messages, &text, 1_000_000);
        assert_eq!(3, history.len());
        assert_eq!(user("old"), history[0]);
        assert_eq!(user("new"), history[1]);
        // 摘要永远最后。
        assert_eq!(summary("SUM"), history[2]);
        assert!(history.last().is_some_and(ResponseItem::is_user_message));
    }

    #[test]
    fn build_compacted_history_respects_token_budget_from_the_tail() {
        // 每条 1 个字符 -> approx_token_count = len/4 = 0；用长文本把预算吃出差异。
        let long = "x".repeat(4_000); // ≈1000 tokens
        let messages = vec![user(&long), user(&long), user(&long)];
        // 预算只够最近一条多一点。
        let text = summary_with_prefix("SUM");
        let history = build_compacted_history(&messages, &text, 1_500);
        assert_eq!(3, history.len(), "最近一条 + 截断的第二条 + 摘要");
        assert_eq!(user(&long), history[1], "最近一条原样保留");
        // 最老的那条被预算挡在外面。
        assert_ne!(user(&long), history[0]);
    }

    #[test]
    fn build_compacted_history_with_zero_budget_keeps_only_summary() {
        let text = summary_with_prefix("SUM");
        let history = build_compacted_history(&[user("a"), user("b")], &text, 0);
        assert_eq!(vec![summary("SUM")], history);
    }

    #[test]
    fn build_compacted_history_falls_back_when_summary_is_empty() {
        // codex compact.rs:730 —— 空摘要兜底文案 "(no summary available)"。
        // 注意上游这个兜底**没有**加 SUMMARY_PREFIX（前缀是调用方在 compact.rs:356 拼的），
        // 所以它其实不会被 is_summary_message 认出来 —— 这是上游原样，这里如实照搬。
        let history = build_compacted_history(&[], "", 0);
        assert_eq!(1, history.len());
        assert_eq!(user("(no summary available)"), history[0]);
        assert!(!is_summary_message("(no summary available)"));
    }
}
