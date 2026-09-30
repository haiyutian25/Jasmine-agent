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
    }

    /// Starts a turn: the pause flag belongs to the turn, not to the session.
    pub fn begin_turn(&mut self) {
        self.paused_for_prompt = false;
        self.interrupted_reply = None;
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
    pub fn take_prompt_answers(&mut self, answers: &[String]) -> Vec<(PendingPrompt, String)> {
        self.pending_prompts
            .drain(..)
            .take(answers.len())
            .zip(answers.iter().cloned())
            .collect()
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
}
