//! Running the tool calls one answer asked for.
//!
//! Execution hands each call its own task, gated by one `RwLock`: a tool that declares it can
//! share the session runs under a read guard alongside the others, anything else queues under a
//! write guard. Results come back in the order the calls were asked, not the order they finished.
//!
//! Every call of an answer is written into the transcript before any of its results; see
//! `session/turn.rs`, which owns that order. That is also why **every** call has to come back with
//! a result: a provider refuses the turn that leaves a `tool_call` without its `tool` message.

use crate::tools::registry::ToolRegistry;
use jasmine_protocol::ChatEvent;
use jasmine_protocol::models::ResponseItem;
use std::sync::Arc;
use tokio::sync::RwLock;
use tokio::sync::RwLockReadGuard;
use tokio::sync::RwLockWriteGuard;
use tokio::task::JoinSet;
use tokio_util::either::Either;

/// What a call is answered with when its task never came back.
const DID_NOT_FINISH: &str = "The tool call did not finish.";

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

    /// Runs the calls and answers **every** one of them, in the order they were asked.
    ///
    /// The order is what the provider sees: the calls of one answer are written into the transcript
    /// together, and their results have to follow in the same order. A call whose task never came
    /// back — it panicked, or was cancelled — is still answered: left out, the transcript carries a
    /// `tool_call` with no result and the provider refuses the whole turn.
    pub async fn run(
        &self,
        calls: &[ResponseItem],
        emit: &mut impl FnMut(ChatEvent),
    ) -> Vec<ToolCallOutcome> {
        let mut tasks: JoinSet<(usize, ToolCallOutcome)> = JoinSet::new();
        // The call each slot stands for, so a slot that never settled can still be answered.
        let mut asked: Vec<(String, String)> = Vec::new();

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
            let slot = asked.len();
            asked.push((call_id.clone(), name.clone()));
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
                (
                    slot,
                    ToolCallOutcome {
                        call_id,
                        name,
                        output,
                    },
                )
            });
        }

        // Tasks settle in whatever order they finish; the slot each one names puts them back in the
        // order the answer asked for.
        let mut settled: Vec<Option<ToolCallOutcome>> = (0..asked.len()).map(|_| None).collect();
        while let Some(joined) = tasks.join_next().await {
            let Ok((slot, outcome)) = joined else {
                continue;
            };
            emit(ChatEvent::tool_result(
                outcome.name.clone(),
                &outcome.output,
            ));
            settled[slot] = Some(outcome);
        }

        let mut outcomes = Vec::with_capacity(settled.len());
        for (slot, settled) in settled.into_iter().enumerate() {
            if let Some(outcome) = settled {
                outcomes.push(outcome);
                continue;
            }
            let (call_id, name) = asked[slot].clone();
            let output = DID_NOT_FINISH.to_string();
            emit(ChatEvent::tool_result(name.clone(), &output));
            outcomes.push(ToolCallOutcome {
                call_id,
                name,
                output,
            });
        }

        outcomes
    }
}

#[cfg(test)]
mod tests {
    use super::ToolCallRuntime;
    use crate::tools::registry::ToolRegistry;
    use jasmine_protocol::ChatEvent;
    use jasmine_protocol::models::ResponseItem;
    use jasmine_tools::JsonSchema;
    use jasmine_tools::ResponsesApiTool;
    use jasmine_tools::Tool;
    use jasmine_tools::ToolFuture;
    use std::collections::BTreeMap;
    use std::sync::Arc;
    use std::time::Duration;

    /// A tool that takes a while when told to, so a round settles out of order.
    struct Sleepy {
        name: &'static str,
        slow: bool,
    }

    impl Tool for Sleepy {
        fn spec(&self) -> ResponsesApiTool {
            ResponsesApiTool {
                name: self.name.to_string(),
                description: String::new(),
                strict: false,
                defer_loading: None,
                parameters: JsonSchema::object(BTreeMap::new(), None, None),
                output_schema: None,
            }
        }

        fn execute<'a>(&'a self, _arguments: &'a str) -> ToolFuture<'a> {
            Box::pin(async move {
                if self.slow {
                    tokio::time::sleep(Duration::from_millis(30)).await;
                }
                Ok(format!("{} answered", self.name))
            })
        }

        fn supports_parallel(&self) -> bool {
            true
        }
    }

    fn call(name: &str, call_id: &str) -> ResponseItem {
        ResponseItem::FunctionCall {
            id: None,
            name: name.to_string(),
            namespace: None,
            arguments: "{}".to_string(),
            encrypted_function_args: None,
            call_id: call_id.to_string(),
        }
    }

    #[tokio::test]
    async fn answers_come_back_in_the_order_the_calls_were_asked() {
        let mut registry = ToolRegistry::new();
        registry.add(Sleepy {
            name: "slow_tool",
            slow: true,
        });
        registry.add(Sleepy {
            name: "fast_tool",
            slow: false,
        });
        let runtime = ToolCallRuntime::new(Arc::new(registry));

        let calls = vec![call("slow_tool", "call-1"), call("fast_tool", "call-2")];
        let mut reported = Vec::new();
        let outcomes = runtime.run(&calls, &mut |event| reported.push(event)).await;

        // The slow one was asked first and finished last; it still comes back first.
        assert_eq!(
            outcomes
                .iter()
                .map(|outcome| outcome.call_id.clone())
                .collect::<Vec<_>>(),
            vec!["call-1", "call-2"]
        );
        assert_eq!(outcomes[0].output, "slow_tool answered");
        assert_eq!(
            reported
                .iter()
                .filter(|event| matches!(event, ChatEvent::ToolResult { .. }))
                .count(),
            outcomes.len(),
            "every call has to be reported, or the transcript keeps a tool_call with no result"
        );
    }
}
