//! Running the tool calls one answer asked for.
//!
//! Every call of an answer is written into the transcript **before** any of its results: a
//! provider in thinking mode reads a call that follows a result as one whose reasoning was
//! dropped and refuses the whole request (see `DEEPSEEK_THINKING_TOOL_CALLS.md`).
//!
//! Execution hands each call its own task, gated by one `RwLock`: a tool that declares it can
//! share the session runs under a read guard alongside the others, anything else queues under a
//! write guard. Results come back in the order they finish.

use crate::tools::registry::ToolRegistry;
use jasmine_protocol::ChatEvent;
use jasmine_protocol::models::ResponseItem;
use std::sync::Arc;
use tokio::sync::RwLock;
use tokio::sync::RwLockReadGuard;
use tokio::sync::RwLockWriteGuard;
use tokio::task::JoinSet;
use tokio_util::either::Either;

/// What one call settled on, ready to be written back into the transcript.
pub struct ToolCallOutcome {
    pub call_id: String,
    pub name: String,
    pub output: String,
}

/// Runs the tool calls of one answer.
pub struct ToolCallRuntime {
    registry: Arc<ToolRegistry>,
    parallel_execution: Arc<RwLock<()>>,
}

impl ToolCallRuntime {
    pub fn new(registry: Arc<ToolRegistry>) -> Self {
        Self {
            registry,
            parallel_execution: Arc::new(RwLock::new(())),
        }
    }

    /// The tools this round offered, for building the request.
    pub fn registry(&self) -> &ToolRegistry {
        self.registry.as_ref()
    }

    /// Runs the calls and reports whatever settled, in the order it settled.
    pub async fn run(
        &self,
        calls: &[ResponseItem],
        emit: &mut impl FnMut(ChatEvent),
    ) -> Vec<ToolCallOutcome> {
        let mut tasks: JoinSet<ToolCallOutcome> = JoinSet::new();

        for call in calls {
            let ResponseItem::FunctionCall {
                name,
                arguments,
                call_id,
                ..
            } = call.clone()
            else {
                continue;
            };
            let registry = Arc::clone(&self.registry);
            let gate = Arc::clone(&self.parallel_execution);
            let supports_parallel = registry.supports_parallel(&name);

            tasks.spawn(async move {
                // Admission through the gate: sharing the session or owning it.
                let _guard: Either<RwLockReadGuard<'_, ()>, RwLockWriteGuard<'_, ()>> =
                    if supports_parallel {
                        Either::Left(gate.read().await)
                    } else {
                        Either::Right(gate.write().await)
                    };

                let output = match registry.execute(&name, &arguments).await {
                    Ok(output) => output,
                    Err(error) => error.to_string(),
                };
                ToolCallOutcome {
                    call_id,
                    name,
                    output,
                }
            });
        }

        let mut outcomes = Vec::new();
        while let Some(joined) = tasks.join_next().await {
            let Ok(outcome) = joined else {
                continue;
            };
            emit(ChatEvent::tool_result(
                outcome.name.clone(),
                &outcome.output,
            ));
            outcomes.push(outcome);
        }

        outcomes
    }
}
