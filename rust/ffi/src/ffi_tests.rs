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
            name: "DeepSeek-V41-Flash".to_string(),
            context_length: 128_000,
            max_output_length: 0,
            reasoning_effort: String::new(),
        }],
    }
}

#[test]
fn the_provider_catalog_carries_the_names_and_windows() {
    let catalog = crate::provider_catalog("deepseek".to_string());
    let flash = catalog
        .iter()
        .find(|entry| entry.model_id == "deepseek-flash")
        .expect("deepseek-flash");
    assert_eq!(flash.name, "DeepSeek-V41-Flash");
    assert_eq!(flash.context_length, 1_000_000);
    // 档位表随目录一起出去：聊天页那张面板直接列它，界面上不再另填一份。内容照上游 ——
    // DeepSeek 是它那条路由的四档（`off`/`low`/`high`/`max`），多的（`minimal` 之类）不给。
    assert_eq!(flash.levels, ["none", "low", "high", "max"]);

    // OpenAI 那边逐模型照抄 `models.json`：这一个声明了界面档 `ultra`，而 `gpt-5.5` 连 `max` 都没有。
    let openai = crate::provider_catalog("openai".to_string());
    let astra = openai
        .iter()
        .find(|entry| entry.model_id == "gpt-6-astra")
        .expect("gpt-6-astra");
    assert!(astra.levels.iter().any(|level| level == "ultra"));
    let legacy = openai
        .iter()
        .find(|entry| entry.model_id == "gpt-5.5")
        .expect("gpt-5.5");
    assert_eq!(legacy.levels, ["low", "medium", "high", "xhigh"]);

    // 自己没目录的那几家（聚合网关、用户自己加的端点）：给的是现有那几家的并集 —— 按模型 id 认，
    // 所以 `deepseek-v4-pro`、`gpt-5.5` 在这里也认得。
    let openrouter = crate::provider_catalog("openrouter".to_string());
    assert!(
        openrouter
            .iter()
            .any(|entry| entry.model_id == "deepseek-v4-pro")
    );
    assert!(openrouter.iter().any(|entry| entry.model_id == "gpt-5.5"));
    assert!(openrouter.iter().any(|entry| entry.model_id == "gpt-6-astra"));
    // 认不出的供应商拿到的是同一份并集。
    assert_eq!(
        crate::provider_catalog("nope".to_string()).len(),
        openrouter.len()
    );
}

#[test]
fn built_in_providers_come_from_the_factory_presets() {
    let providers = crate::built_in_providers();
    let deepseek = providers
        .iter()
        .find(|provider| provider.id == "deepseek")
        .expect("DeepSeek preset");
    assert_eq!(deepseek.name, "DeepSeek");
    assert_eq!(deepseek.base_url, "https://api.deepseek.com");
    assert_eq!(deepseek.wire_api, WireApi::Chat);
    assert!(deepseek.api_key.is_empty());
    // 模型来自目录：id、上下文容量、起点档都照 dsh 那份抄。
    assert_eq!(
        deepseek
            .models
            .iter()
            .map(|model| model.model_id.as_str())
            .collect::<Vec<_>>(),
        ["deepseek-flash", "deepseek-v4-pro"]
    );
    assert_eq!(deepseek.models[0].context_length, 1_000_000);
    assert_eq!(deepseek.models[0].name, "DeepSeek-V41-Flash");

    // 第二家是 codex 那条 OpenAI 预设，模型同样来自目录。
    let openai = providers
        .iter()
        .find(|provider| provider.id == "openai")
        .expect("OpenAI preset");
    assert_eq!(openai.wire_api, WireApi::Responses);
    assert!(openai.models.iter().any(|model| model.model_id == "gpt-5.5"));

    // 第三家是 OpenRouter：聚合网关，**没有目录**，所以模型列表空着（用户拉/自己填）。
    let openrouter = providers
        .iter()
        .find(|provider| provider.id == "openrouter")
        .expect("OpenRouter preset");
    assert_eq!(openrouter.name, "OpenRouter");
    assert_eq!(openrouter.base_url, "https://openrouter.ai/api/v1");
    assert_eq!(openrouter.wire_api, WireApi::Chat);
    assert!(openrouter.models.is_empty());
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
            crate::AgentSettings {
                output_language: String::new(),
                app_language: String::new(),
            },
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
