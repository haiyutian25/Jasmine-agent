use crate::event_mapping::PendingPrompt;
use crate::event_mapping::map_response_event;
use jasmine_api::ResponseEvent;
use jasmine_protocol::ChatEvent;
use jasmine_protocol::protocol::ContextUsageBreakdownItem;
use jasmine_protocol::protocol::TokenUsageInfo;
use std::collections::VecDeque;

/// Live state of one conversation: the session it runs on, the turn in flight, and the
/// calls that are waiting for the user.
#[derive(Debug, Default)]
pub struct ChatThread {
    /// The id of the session the thread is attached to.
    attached_session_id: Option<String>,

    /// Id of the current turn, recorded from the events as they arrive. The interrupted
    /// reply is appended under it, so the turn's pieces share one id.
    active_invocation_id: Option<String>,

    /// Calls that were issued and not yet answered, in the order they were asked.
    ///
    /// Queued rather than held in a single slot: a model that asks two interactive questions
    /// in one turn would otherwise overwrite the first one, leaving a history entry with a
    /// tool call and no result.
    pending_prompts: VecDeque<PendingPrompt>,

    /// Whether this turn has already stopped for a prompt.
    ///
    /// Once a prompt is out, the turn must not continue: the framework would otherwise call
    /// the model again while the interactive call still has no result, and the provider
    /// rejects an assistant message whose tool calls are not all answered.
    paused_for_prompt: bool,

    /// What the conversation has cost so far: the running total, the last response's own count,
    /// and the window both have to fit in.
    ///
    /// A response's usage is metadata about that response — the caller reads it after the
    /// turn, it is not a step in the reply.
    token_usage_info: Option<TokenUsageInfo>,

    /// The context window the conversation runs against, in tokens, set when it was attached (or
    /// when the platform changed it). Zero means nothing has been attached yet.
    context_window: i64,

    /// Which part of the request the last round sent, counted by the turn. Kept so the platform
    /// can be given the same picture again when only the window changes.
    request_breakdown: Vec<ContextUsageBreakdownItem>,

    /// The part of an answer a stopped round had already written, for the caller to record as the
    /// presentation record it is (`RolloutItem::InterruptedReply`). It is not part of the
    /// conversation: a round the provider never marked done contributes nothing to it.
    interrupted_reply: Option<String>,

    /// The thinking a stopped round had already streamed, recorded the same way
    /// (`RolloutItem::InterruptedReasoning`) and just as absent from the conversation.
    interrupted_reasoning: Option<String>,
}

impl ChatThread {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn attached_session_id(&self) -> Option<&str> {
        self.attached_session_id.as_deref()
    }

    pub fn active_invocation_id(&self) -> Option<&str> {
        self.active_invocation_id.as_deref()
    }

    pub fn pending_prompts(&self) -> impl Iterator<Item = &PendingPrompt> {
        self.pending_prompts.iter()
    }

    pub fn is_paused_for_prompt(&self) -> bool {
        self.paused_for_prompt
    }

    /// What the conversation has cost so far.
    pub fn token_usage_info(&self) -> Option<&TokenUsageInfo> {
        self.token_usage_info.as_ref()
    }

    /// The window this conversation runs against, in tokens.
    pub fn context_window(&self) -> i64 {
        self.context_window
    }

    /// Records the window the conversation was resolved to.
    pub fn note_context_window(&mut self, context_window: i64) {
        self.context_window = context_window;
    }

    /// Remembers what the round now starting is about to send, by part.
    pub fn note_request_breakdown(&mut self, breakdown: Vec<ContextUsageBreakdownItem>) {
        self.request_breakdown = breakdown;
    }

    /// The usage event as it stands: what the conversation cost, the window it fits in, and the
    /// makeup of the last request. `None` before any response has reported a cost.
    pub fn usage_event(&self) -> Option<ChatEvent> {
        self.usage_record()
            .map(|(info, breakdown)| ChatEvent::Usage { info, breakdown })
    }

    /// What goes in the conversation's file: what it has cost, and the makeup of the request that
    /// produced the last figure. `None` before any response has reported a cost.
    pub fn usage_record(&self) -> Option<(TokenUsageInfo, Vec<ContextUsageBreakdownItem>)> {
        let info = self.token_usage_info.clone()?;
        let breakdown = self.scaled_request_breakdown(info.last_token_usage.input_tokens);
        Some((info, breakdown))
    }

    /// Puts back what the conversation's file says it had cost.
    ///
    /// The stored makeup is already scaled to that total, so keeping it as the current one makes a
    /// later report (a window change, say) come out identical instead of being scaled twice — the
    /// factor is the reported input over the stored total, which is one.
    pub fn note_usage_record(
        &mut self,
        info: TokenUsageInfo,
        breakdown: Vec<ContextUsageBreakdownItem>,
    ) {
        self.token_usage_info = Some(info);
        self.request_breakdown = breakdown;
    }

    /// The last request's makeup, scaled so it adds up to what the model reported for its input.
    ///
    /// The parts are estimated here (the core has no tokenizer), so only their shares are worth
    /// anything; the size comes from the provider. When it reported nothing — or the request
    /// counted to nothing — the estimate is left as it is.
    fn scaled_request_breakdown(
        &self,
        reported_input_tokens: i64,
    ) -> Vec<ContextUsageBreakdownItem> {
        let estimated_total: i64 = self.request_breakdown.iter().map(|item| item.tokens).sum();
        if reported_input_tokens <= 0 || estimated_total <= 0 {
            return self.request_breakdown.clone();
        }

        let mut scaled: Vec<ContextUsageBreakdownItem> = self
            .request_breakdown
            .iter()
            .map(|item| ContextUsageBreakdownItem {
                source: item.source,
                tokens: item.tokens.saturating_mul(reported_input_tokens) / estimated_total,
            })
            .collect();

        // Rounding leaves a few tokens over; they go to the biggest part, where they show least.
        let scaled_total: i64 = scaled.iter().map(|item| item.tokens).sum();
        if let Some((index, _)) = scaled
            .iter()
            .enumerate()
            .max_by_key(|(_, item)| item.tokens)
        {
            scaled[index].tokens += reported_input_tokens - scaled_total;
        }
        scaled
    }

    /// Attaches the thread to a session. A known session is resumed as-is; the transcript is
    /// never re-fed to the model.
    pub fn start_session(&mut self, session_id: impl Into<String>) {
        self.end_session();
        self.attached_session_id = Some(session_id.into());
    }

    /// Detaches from the session without deleting it: dropping the live object must not
    /// erase durable history.
    pub fn end_session(&mut self) {
        self.attached_session_id = None;
        self.active_invocation_id = None;
        self.paused_for_prompt = false;
        self.pending_prompts.clear();
        self.token_usage_info = None;
        self.context_window = 0;
        self.request_breakdown = Vec::new();
        self.interrupted_reply = None;
        self.interrupted_reasoning = None;
    }

    /// Starts a turn: the pause flag belongs to the turn, not to the session.
    pub fn begin_turn(&mut self) {
        self.paused_for_prompt = false;
        self.interrupted_reply = None;
        self.interrupted_reasoning = None;
    }

    /// Keeps the part of an answer a stopped round had already written, for the caller to record.
    pub fn note_interrupted_reply(&mut self, text: &str) {
        if !text.trim().is_empty() {
            self.interrupted_reply = Some(text.to_string());
        }
    }

    /// Takes that answer back, so one stop is recorded once.
    pub fn take_interrupted_reply(&mut self) -> Option<String> {
        self.interrupted_reply.take()
    }

    /// Keeps the thinking a stopped round had already streamed, for the caller to record next to it.
    pub fn note_interrupted_reasoning(&mut self, text: &str) {
        if !text.trim().is_empty() {
            self.interrupted_reasoning = Some(text.to_string());
        }
    }

    /// Takes that thinking back, so one stop is recorded once.
    pub fn take_interrupted_reasoning(&mut self) -> Option<String> {
        self.interrupted_reasoning.take()
    }

    pub fn note_invocation(&mut self, invocation_id: Option<String>) {
        if let Some(invocation_id) = invocation_id
            && !invocation_id.is_empty()
        {
            self.active_invocation_id = Some(invocation_id);
        }
    }

    /// Maps one model event: emits what the UI sees, remembers a prompt, and stops the turn
    /// on one.
    pub fn on_response_event(&mut self, event: ResponseEvent) -> Vec<ChatEvent> {
        let mapped = map_response_event(event);
        if let Some(prompt) = mapped.prompt {
            if !self
                .pending_prompts
                .iter()
                .any(|queued| queued.id == prompt.id)
            {
                self.pending_prompts.push_back(prompt);
            }
            self.paused_for_prompt = true;
        }
        if let Some(usage) = mapped.token_usage {
            self.token_usage_info = TokenUsageInfo::new_or_append(
                self.token_usage_info.as_ref(),
                Some(&usage),
                Some(self.context_window),
            );
        }
        mapped.events
    }

    /// Pairs answers with the prompts in the order they were asked.
    ///
    /// Callers collect the answers in that same order before submitting them, so the pairing
    /// is positional.
    ///
    /// **先校验、后消费**（F2）：数量对不上就 `None`，且**一个都不动**。
    /// 以前写的是 `drain(..).take(answers.len())` —— `Drain` 一被 drop 就把整个区间移走，于是
    /// `answers` 为空这种非法输入会**清空待答提示**、却仍然报 `NoPromptWaiting`：这条会话的提问
    /// 从此再也答不上（平台重试只会再拿同一个错），而历史里留下「有 tool_call、没有 tool_result」
    /// 的残缺记录，之后每次请求都被服务端以 400 拒掉。
    ///
    /// `Some(vec![])`（数量都是 0）表示"确实没有待答"，与 `None`（数量对不上）是两回事。
    pub fn take_prompt_answers(&mut self, answers: &[String]) -> Option<Vec<(PendingPrompt, String)>> {
        if answers.len() != self.pending_prompts.len() {
            return None;
        }
        Some(
            self.pending_prompts
                .drain(..)
                .zip(answers.iter().cloned())
                .collect(),
        )
    }
}

#[cfg(test)]
mod tests {
    use super::ChatThread;
    use jasmine_api::ResponseEvent;
    use jasmine_protocol::ChatEvent;
    use jasmine_protocol::protocol::ContextUsageBreakdownItem;
    use jasmine_protocol::protocol::ContextUsageSource;
    use jasmine_protocol::protocol::TokenUsage;

    fn reported_usage() -> TokenUsage {
        TokenUsage {
            input_tokens: 10,
            cached_input_tokens: 4,
            cache_write_input_tokens: 0,
            output_tokens: 2,
            reasoning_output_tokens: 0,
            total_tokens: 12,
        }
    }

    fn completed(usage: TokenUsage) -> ResponseEvent {
        ResponseEvent::Completed {
            response_id: "resp-1".to_string(),
            token_usage: Some(usage),
            usage_metadata: None,
            end_turn: Some(true),
        }
    }

    #[test]
    fn keeps_the_usage_a_completed_response_reported() {
        let mut thread = ChatThread::new();
        thread.note_context_window(200_000);
        thread.on_response_event(completed(reported_usage()));
        let info = thread.token_usage_info().expect("usage");
        assert_eq!(info.last_token_usage, reported_usage());
        assert_eq!(info.total_token_usage, reported_usage());
        assert_eq!(info.model_context_window, Some(200_000));
    }

    #[test]
    fn adds_each_response_to_the_running_total() {
        let mut thread = ChatThread::new();
        thread.on_response_event(completed(reported_usage()));
        thread.on_response_event(completed(reported_usage()));
        let info = thread.token_usage_info().expect("usage");
        assert_eq!(info.last_token_usage.total_tokens, 12);
        assert_eq!(info.total_token_usage.total_tokens, 24);
    }

    #[test]
    fn scales_the_request_makeup_to_what_the_model_reported() {
        let mut thread = ChatThread::new();
        thread.note_context_window(200_000);
        thread.note_request_breakdown(vec![
            ContextUsageBreakdownItem {
                source: ContextUsageSource::SystemPrompt,
                tokens: 20,
            },
            ContextUsageBreakdownItem {
                source: ContextUsageSource::SystemToolSchemas,
                tokens: 135,
            },
            ContextUsageBreakdownItem {
                source: ContextUsageSource::Messages,
                tokens: 22,
            },
        ]);
        thread.on_response_event(completed(TokenUsage {
            input_tokens: 354,
            total_tokens: 354,
            ..TokenUsage::default()
        }));

        let breakdown = match thread.usage_event().expect("usage") {
            ChatEvent::Usage { breakdown, .. } => breakdown,
            other => panic!("expected a usage event, got {other:?}"),
        };
        // 354 is exactly twice 177, so the shares survive the scaling untouched.
        assert_eq!(
            breakdown.iter().map(|item| item.tokens).collect::<Vec<_>>(),
            vec![40, 270, 44]
        );
    }

    #[test]
    fn rounding_leaves_the_total_at_what_the_model_reported() {
        let mut thread = ChatThread::new();
        thread.note_context_window(200_000);
        thread.note_request_breakdown(vec![
            ContextUsageBreakdownItem {
                source: ContextUsageSource::SystemPrompt,
                tokens: 20,
            },
            ContextUsageBreakdownItem {
                source: ContextUsageSource::SystemToolSchemas,
                tokens: 135,
            },
            ContextUsageBreakdownItem {
                source: ContextUsageSource::Messages,
                tokens: 22,
            },
        ]);
        thread.on_response_event(completed(TokenUsage {
            input_tokens: 100,
            total_tokens: 100,
            ..TokenUsage::default()
        }));

        let breakdown = match thread.usage_event().expect("usage") {
            ChatEvent::Usage { breakdown, .. } => breakdown,
            other => panic!("expected a usage event, got {other:?}"),
        };
        // 11 + 76 + 12 is one short of 100; the leftover goes to the biggest part.
        assert_eq!(
            breakdown.iter().map(|item| item.tokens).collect::<Vec<_>>(),
            vec![11, 77, 12]
        );
        assert_eq!(breakdown.iter().map(|item| item.tokens).sum::<i64>(), 100);
    }

    #[test]
    fn has_no_usage_before_any_response_reports_one() {
        let thread = ChatThread::new();
        assert!(thread.token_usage_info().is_none());
    }

    /// 一条"模型追问用户"的调用事件：它会在这个线程上挂出一个待答提示。
    fn prompt_event(tool: &str, call_id: &str) -> ResponseEvent {
        ResponseEvent::OutputItemDone(jasmine_protocol::models::ResponseItem::FunctionCall {
            id: None,
            name: tool.to_string(),
            namespace: None,
            arguments: r#"{"message":"选一个"}"#.to_string(),
            encrypted_function_args: None,
            call_id: call_id.to_string(),
        })
    }

    /// 答案条数对不上时，**一个待答提示都不许被消费**（F2）。
    ///
    /// 以前写的是 `drain(..).take(answers.len())`：`answers` 为空时 `Drain` 被 drop 就把整个区间
    /// 移走 —— 于是"空提交"这种非法输入会清空待答提示、却仍然报 `NoPromptWaiting`，这条会话的提问
    /// 从此再也答不上，历史里留下「有 tool_call、没有 tool_result」的残缺记录，之后每次请求被服务端
    /// 以 400 拒掉。
    #[test]
    fn answers_that_do_not_match_the_prompts_consume_nothing() {
        let mut thread = ChatThread::new();
        for id in ["c1", "c2"] {
            thread.on_response_event(prompt_event(
                crate::event_mapping::REQUEST_INPUT_TOOL,
                id,
            ));
        }
        assert_eq!(thread.pending_prompts().count(), 2);

        // 空提交、少交、多交：都不许动状态。
        assert!(thread.take_prompt_answers(&[]).is_none(), "空提交是数量不符");
        assert_eq!(thread.pending_prompts().count(), 2, "空提交不能清掉待答提示");
        assert!(
            thread
                .take_prompt_answers(&["a".to_string()])
                .is_none(),
            "少交一个是数量不符"
        );
        assert_eq!(thread.pending_prompts().count(), 2);
        assert!(
            thread
                .take_prompt_answers(&["a".to_string(), "b".to_string(), "c".to_string()])
                .is_none(),
            "多交也是数量不符"
        );
        assert_eq!(thread.pending_prompts().count(), 2);

        // 数量对上才消费，而且按提问顺序一一配对。
        let paired = thread
            .take_prompt_answers(&["a".to_string(), "b".to_string()])
            .expect("数量对得上就该消费");
        assert_eq!(paired.len(), 2);
        assert_eq!(paired[0].0.id, "c1");
        assert_eq!(paired[0].1, "a");
        assert_eq!(paired[1].0.id, "c2");
        assert_eq!(paired[1].1, "b");
        assert!(thread.pending_prompts().next().is_none(), "消费干净了");
    }

    /// 没有待答时的空提交是 `Some(空)`（"确实没有"），不是 `None`（"数量不对"）—— 两者要分得开。
    #[test]
    fn an_empty_submission_with_no_prompts_is_not_a_mismatch() {
        let mut thread = ChatThread::new();
        let paired = thread
            .take_prompt_answers(&[])
            .expect("没有待答时不该报数量不符");
        assert!(paired.is_empty());
    }
}
