#![allow(clippy::unwrap_used, clippy::expect_used)]

use super::AgentChatService;
use super::AgentError;
use super::ChatSink;
use super::ConversationsBridge;
use crate::host::Clock;
use jasmine_model_provider::ResolvedProvider;
use jasmine_model_provider_info::ModelConfig;
use jasmine_model_provider_info::ModelProviderInfo;
use jasmine_model_provider_info::WireApi;
use jasmine_protocol::ChatEvent;
use jasmine_protocol::SessionId;
use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::ResponseItem;
use jasmine_rollout::RolloutItem;
use jasmine_rollout::RolloutRecorder;
use jasmine_rollout::SessionMeta;
use jasmine_rollout::find_session_path;
use jasmine_rollout::read_response_items;
use jasmine_tools::ConversationTitles;
use std::sync::Arc;

struct Host;

impl Clock for Host {
    fn now_formatted(&self) -> String {
        "2026-09-27 10:31:05 GMT+08:00".to_string()
    }

    fn format(&self, timestamp: &str) -> String {
        format!("formatted({timestamp})")
    }
}

struct Collector {
    events: Vec<ChatEvent>,
}

impl ChatSink for Collector {
    fn emit(&mut self, event: ChatEvent) {
        self.events.push(event);
    }
}

fn sessions_dir(name: &str) -> std::path::PathBuf {
    let dir = std::env::temp_dir().join(format!("jasmine-core-{name}"));
    let _ = std::fs::remove_dir_all(&dir);
    dir
}

fn service(dir: &std::path::Path) -> AgentChatService {
    AgentChatService::new(dir.to_path_buf(), Arc::new(Host))
}

fn model() -> ModelConfig {
    ModelConfig {
        id: "deepseek-flash".to_string(),
        model_id: "deepseek-flash".to_string(),
        ..ModelConfig::default()
    }
}

fn provider() -> ResolvedProvider {
    ResolvedProvider::from_config(
        ModelProviderInfo {
            id: "deepseek".to_string(),
            name: "DeepSeek".to_string(),
            base_url: "https://api.deepseek.com".to_string(),
            wire_api: WireApi::Responses,
            is_built_in: false,
            models: Vec::new(),
            request_max_retries: None,
            stream_idle_timeout_ms: None,
        },
        "sk-test".to_string(),
    )
}

fn attach(service: &AgentChatService, session_id: &str, title: &str) {
    service
        .create_conversation(
            &SessionId::new(session_id),
            "deepseek",
            "deepseek-flash",
            title,
        )
        .expect("create");
    service
        .start_conversation(&SessionId::new(session_id), provider(), &model(), "")
        .expect("attach");
}

/// Writes a conversation the way a previous run would have left it.
fn seed(dir: &std::path::Path, session_id: &str, texts: &[(&str, &str)]) {
    let meta = SessionMeta {
        session_id: session_id.to_string(),
        timestamp: "2026-09-27T10:00:00+08:00".to_string(),
        title: "earlier".to_string(),
        provider_id: "deepseek".to_string(),
        model_id: "deepseek-flash".to_string(),
    };
    let mut recorder = RolloutRecorder::create(dir, &meta).expect("create");
    let items = texts
        .iter()
        .map(|(role, text)| {
            RolloutItem::ResponseItem(ResponseItem::Message {
                id: None,
                role: (*role).to_string(),
                content: vec![if *role == "assistant" {
                    ContentItem::OutputText {
                        text: (*text).to_string(),
                    }
                } else {
                    ContentItem::InputText {
                        text: (*text).to_string(),
                    }
                }],
            })
        })
        .collect::<Vec<_>>();
    recorder.record_items(&items).expect("record");
}

#[test]
fn a_call_without_a_conversation_reports_it_as_text() {
    let dir = sessions_dir("no-session");
    let service = service(&dir);
    let mut sink = Collector { events: Vec::new() };

    let error = service.send("hello", &mut sink).unwrap_err();

    assert!(matches!(error, AgentError::NoSession));
    assert!(error.detail().contains("尚未附着会话"));
}

#[test]
fn attaching_loads_the_context_the_conversation_already_has() {
    let dir = sessions_dir("resume");
    seed(
        &dir,
        "known",
        &[
            ("user", "previous question"),
            ("assistant", "previous answer"),
        ],
    );
    let service = service(&dir);

    attach(&service, "known", "earlier");

    assert_eq!(service.context_len(), 2);
}

#[test]
fn a_new_conversation_starts_from_an_empty_context() {
    let dir = sessions_dir("fresh");
    let service = service(&dir);

    attach(&service, "brand-new", "new chat");

    assert_eq!(service.context_len(), 0);
    let path = find_session_path(&dir, "brand-new")
        .expect("find")
        .expect("a conversation that was attached has a file");
    assert!(read_response_items(&path).expect("read").is_empty());
}

#[test]
fn ending_the_conversation_detaches_it() {
    let dir = sessions_dir("detach");
    let service = service(&dir);
    attach(&service, "s1", "chat");
    service.end_conversation();

    let mut sink = Collector { events: Vec::new() };
    assert!(matches!(
        service.send("hello", &mut sink).unwrap_err(),
        AgentError::NoSession
    ));
}

#[test]
fn an_interrupted_reply_joins_the_context_and_the_file() {
    let dir = sessions_dir("interrupted");
    let service = service(&dir);
    attach(&service, "s1", "chat");

    service
        .persist_interrupted_reply("half a sentence")
        .expect("persist");

    assert_eq!(service.context_len(), 1);
    let path = find_session_path(&dir, "s1").expect("find").expect("path");
    assert_eq!(read_response_items(&path).expect("read").len(), 1);
}

/// 新建会话时，会话自己的档位从**模型配置**里抄一次；此后只认会话自己的记录。
#[test]
fn a_new_conversation_starts_from_the_models_effort() {
    let dir = sessions_dir("effort-seed");
    let service = service(&dir);
    service
        .create_conversation(&SessionId::new("s1"), "deepseek", "deepseek-flash", "chat")
        .expect("create");
    let model = ModelConfig {
        reasoning_effort: "high".to_string(),
        ..model()
    };
    service
        .start_conversation(&SessionId::new("s1"), provider(), &model, "")
        .expect("attach");

    assert_eq!(service.reasoning_effort().as_deref(), Some("high"));

    let path = find_session_path(&dir, "s1").expect("find").expect("path");
    let text = std::fs::read_to_string(&path).expect("read");
    let recorded = text
        .lines()
        .filter(|line| line.contains("\"type\":\"reasoning_effort\""))
        .collect::<Vec<_>>();
    assert_eq!(recorded.len(), 1);
    assert!(recorded[0].contains("\"value\":\"high\""));
}

/// 在对话里改档位：每改一次**追加**一条，历史一条不删，最后一条生效。
///
/// 第 1 条是新建时就有的（模型没设 = 空 = 未设置），第 2、3 条是后来改的 —— 三条都在文件里。
#[test]
fn changing_the_effort_appends_a_record_and_takes_effect() {
    let dir = sessions_dir("effort-changes");
    let service = service(&dir);
    attach(&service, "s1", "chat");
    assert_eq!(service.reasoning_effort().as_deref(), Some(""));

    service.set_reasoning_effort("low").expect("set low");
    service.set_reasoning_effort("high").expect("set high");

    assert_eq!(service.reasoning_effort().as_deref(), Some("high"));

    let path = find_session_path(&dir, "s1").expect("find").expect("path");
    let text = std::fs::read_to_string(&path).expect("read");
    let values = text
        .lines()
        .filter(|line| line.contains("\"type\":\"reasoning_effort\""))
        .filter_map(|line| {
            let marker = "\"value\":\"";
            let start = line.find(marker)? + marker.len();
            Some(
                line[start..]
                    .chars()
                    .take_while(|c| *c != '"')
                    .collect::<String>(),
            )
        })
        .collect::<Vec<_>>();
    assert_eq!(values, vec!["", "low", "high"]);

    // 改完之后再跑一轮：这一轮记的档位跟的是**会话**的值，不是模型配置里的（模型这里是空串）。
    let mut sink = Collector { events: Vec::new() };
    let _ = service.send("hello", &mut sink);
    let text = std::fs::read_to_string(&path).expect("read");
    let turn_line = text
        .lines()
        .find(|line| line.contains("\"turn_started\""))
        .expect("a turn_started line");
    assert!(
        turn_line.contains("\"reasoning_effort\":\"high\""),
        "the turn did not record the session's effort: {turn_line}"
    );
}

/// 每一轮开始时，把这一轮用的推理档位写进会话文件 —— 往回翻历史时就能看到"这一轮用的什么档位"。
///
/// 请求本身在这个用例里会失败（测试用的 Host 不发真请求），但 `TurnStarted` 是在发请求**之前**写的，
/// 所以不影响这条断言。
#[test]
fn each_turn_records_the_reasoning_effort() {
    let dir = sessions_dir("turn-effort");
    let service = service(&dir);
    service
        .create_conversation(&SessionId::new("s1"), "deepseek", "deepseek-flash", "chat")
        .expect("create");
    let model = ModelConfig {
        reasoning_effort: "high".to_string(),
        ..model()
    };
    service
        .start_conversation(&SessionId::new("s1"), provider(), &model, "")
        .expect("attach");

    let mut sink = Collector { events: Vec::new() };
    let _ = service.send("hello", &mut sink);

    let path = find_session_path(&dir, "s1").expect("find").expect("path");
    let text = std::fs::read_to_string(&path).expect("read");
    let line = text
        .lines()
        .find(|line| line.contains("\"turn_started\""))
        .expect("a turn_started line");
    assert!(
        line.contains("\"reasoning_effort\":\"high\""),
        "the turn did not record its effort: {line}"
    );
}

#[test]
fn the_tool_list_reads_the_conversations_own_files() {
    let dir = sessions_dir("tool-list");
    seed(&dir, "older", &[("user", "one")]);
    let bridge = ConversationsBridge {
        sessions_dir: dir,
        clock: Arc::new(Host),
    };

    let conversations = bridge.conversations();

    assert_eq!(conversations.len(), 1);
    assert_eq!(conversations[0].title, "earlier");
    assert!(conversations[0].updated_at.starts_with("formatted("));
}
