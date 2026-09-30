//! Starting a turn: from what the user typed, or from the answers to a prompt.

use crate::session::SessionError;
use crate::session::turn::Turn;
use crate::session::turn::run_turn;
use jasmine_client::HttpTransport;
use jasmine_protocol::ChatEvent;
use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::FunctionCallOutputPayload;
use jasmine_protocol::models::ResponseItem;

/// Runs a turn that starts from the user's own message.
pub async fn send_text<T: HttpTransport>(
    turn: Turn<'_, T>,
    text: String,
    emit: &mut impl FnMut(ChatEvent),
) -> Result<(), SessionError> {
    turn.history.push(ResponseItem::Message {
        id: None,
        role: "user".to_string(),
        content: vec![ContentItem::InputText { text }],
    });
    run_turn(turn, emit).await
}

/// Answers the prompts a turn stopped on and continues that same turn.
///
/// The answers are paired with the prompts in the order they were asked; each one becomes the
/// tool result the model is waiting for.
pub async fn respond_to_prompts<T: HttpTransport>(
    turn: Turn<'_, T>,
    answers: &[String],
    emit: &mut impl FnMut(ChatEvent),
) -> Result<(), SessionError> {
    // 数量对不上就**一个都不消费**（F2）：这条会话的待答提示保持原样，平台可以照着条数重交一次。
    let expected = turn.thread.pending_prompts().count();
    let Some(paired) = turn.thread.take_prompt_answers(answers) else {
        return Err(SessionError::AnswerCountMismatch {
            expected,
            got: answers.len(),
        });
    };
    if paired.is_empty() {
        return Err(SessionError::NoPromptWaiting);
    }

    for (prompt, answer) in paired {
        // The user's answer is not echoed back in the event stream, so the tool card is fed
        // from here; the text matches what the card shows when the transcript is rebuilt.
        emit(ChatEvent::tool_result(prompt.name.clone(), &answer));
        turn.history.push(ResponseItem::FunctionCallOutput {
            id: None,
            call_id: Some(prompt.id),
            name: Some(prompt.name),
            namespace: None,
            output: FunctionCallOutputPayload::from_text(answer),
        });
    }

    run_turn(turn, emit).await
}
