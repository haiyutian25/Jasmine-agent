use crate::event_mapping::PendingPrompt;
use crate::event_mapping::map_response_event;
use jasmine_api::ResponseEvent;
use jasmine_protocol::ChatEvent;
use jasmine_protocol::protocol::TokenUsage;
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

    /// What the most recent response reported it cost, when it reported that.
    ///
    /// A response's usage is metadata about that response — the caller reads it after the
    /// turn, it is not a step in the reply.
    last_token_usage: Option<TokenUsage>,
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

    /// What the most recent response cost.
    pub fn last_token_usage(&self) -> Option<&TokenUsage> {
        self.last_token_usage.as_ref()
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
        self.last_token_usage = None;
    }

    /// Starts a turn: the pause flag belongs to the turn, not to the session.
    pub fn begin_turn(&mut self) {
        self.paused_for_prompt = false;
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
            self.last_token_usage = Some(usage);
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

    #[test]
    fn keeps_the_usage_a_completed_response_reported() {
        let mut thread = ChatThread::new();
        thread.on_response_event(ResponseEvent::Completed {
            response_id: "resp-1".to_string(),
            token_usage: Some(reported_usage()),
            usage_metadata: None,
            end_turn: Some(true),
        });
        assert_eq!(thread.last_token_usage(), Some(&reported_usage()));
    }

    #[test]
    fn has_no_usage_before_any_response_reports_one() {
        let thread = ChatThread::new();
        assert_eq!(thread.last_token_usage(), None);
    }
}
