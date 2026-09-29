#![allow(clippy::unwrap_used, clippy::expect_used)]

use super::AgentFailure;
use super::AgentHandle;
use super::ClockAdapter;
use super::EventListener;
use super::HostClock;
use super::ProviderInput;
use super::WireApi;
use jasmine_core::host::Clock;
use jasmine_protocol::ChatEvent;
use jasmine_protocol::Role;
use std::sync::Arc;
use std::sync::Mutex;

struct Host;

impl HostClock for Host {
    fn now(&self) -> String {
        "2026-09-27 10:31:05 GMT+08:00".to_string()
    }

    fn format(&self, timestamp: String) -> String {
        format!("formatted({timestamp})")
    }
}

struct Collector {
    events: Mutex<Vec<ChatEvent>>,
}

impl EventListener for Collector {
    fn on_event(&self, event: ChatEvent) {
        self.events
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .push(event);
    }
}

fn provider_input() -> ProviderInput {
    ProviderInput {
        id: "deepseek".to_string(),
        name: "DeepSeek".to_string(),
        base_url: "https://api.deepseek.com".to_string(),
        wire_api: WireApi::Responses,
        api_key: "sk-test".to_string(),
        models: vec![crate::ModelInput {
            id: "deepseek-flash".to_string(),
            model_id: "deepseek-flash".to_string(),
            context_length: 128_000,
            max_output_length: 0,
            reasoning_effort: "high".to_string(),
        }],
    }
}

#[test]
fn provider_input_carries_model_token_budgets() {
    let resolved = provider_input().into_resolved();
    let models = &resolved.info().models;
    assert_eq!(models.len(), 1);
    assert_eq!(models[0].context_length, 128_000);
}

fn sessions_dir(name: &str) -> String {
    let dir = std::env::temp_dir().join(format!("jasmine-ffi-{name}"));
    let _ = std::fs::remove_dir_all(&dir);
    dir.to_string_lossy().into_owned()
}

fn handle(dir: &str) -> AgentHandle {
    AgentHandle::new(dir.to_string(), Arc::new(Host))
}

fn attach(handle: &AgentHandle, session_id: &str, title: &str) {
    handle
        .create_conversation(
            session_id.to_string(),
            "deepseek".to_string(),
            "deepseek-flash".to_string(),
            title.to_string(),
        )
        .expect("建会话应成功");
    handle
        .start_conversation(
            session_id.to_string(),
            provider_input(),
            "deepseek-flash".to_string(),
            String::new(),
        )
        .expect("附着会话应成功");
}

#[test]
fn clock_adapter_forwards_platform_formatted_string() {
    let adapter = ClockAdapter::new(Arc::new(Host));

    assert!(adapter.now_formatted().contains("GMT"));
    assert_eq!(
        adapter.format("2026-09-27T10:00:00+08:00"),
        "formatted(2026-09-27T10:00:00+08:00)"
    );
}

#[test]
fn handle_reports_call_order_errors_as_text() {
    let handle = handle(&sessions_dir("no-session"));
    let listener = Arc::new(Collector {
        events: Mutex::new(Vec::new()),
    });

    // 未附着会话就发送：边界上应当给出一句可显示的原因
    let error = handle.send("你好".to_string(), listener).unwrap_err();

    assert!(error.to_string().contains("尚未附着会话"));
}

#[test]
fn a_new_session_starts_with_empty_context() {
    let handle = handle(&sessions_dir("fresh"));

    attach(&handle, "brand-new", "新对话");

    assert_eq!(handle.context_len(), 0);
}

#[test]
fn what_a_conversation_said_crosses_the_boundary_on_attach() {
    let dir = sessions_dir("resume");
    let first = handle(&dir);
    attach(&first, "s1", "昨天的对话");
    first
        .persist_interrupted_reply("上一次答的".to_string())
        .expect("persist");
    first.end_conversation();

    let second = handle(&dir);
    attach(&second, "s1", "昨天的对话");

    assert_eq!(second.context_len(), 1, "会话自己的文件应当被装载回来");
}

#[test]
fn the_platform_list_and_transcript_come_from_the_core() {
    let dir = sessions_dir("list");
    let handle = handle(&dir);
    attach(&handle, "s1", "列出你全部的工具");
    handle
        .persist_interrupted_reply("半句话".to_string())
        .expect("persist");

    let conversations = handle.conversations();
    assert_eq!(conversations.len(), 1);
    assert_eq!(conversations[0].session_id, "s1");
    assert_eq!(conversations[0].title, "列出你全部的工具");
    assert!(conversations[0].updated_at > 0);
    assert_eq!(conversations[0].provider_id, "deepseek");

    let transcript = handle.transcript("s1".to_string());
    assert_eq!(transcript.len(), 1);
    assert_eq!(transcript[0].role, Role::Model);
    assert_eq!(transcript[0].text, "半句话");
}

#[test]
fn start_then_end_detaches() {
    let handle = handle(&sessions_dir("detach"));
    attach(&handle, "s1", "新对话");
    handle.end_conversation();
    let listener = Arc::new(Collector {
        events: Mutex::new(Vec::new()),
    });

    let error: AgentFailure = handle.send("你好".to_string(), listener).unwrap_err();

    assert!(error.to_string().contains("尚未附着会话"));
}
