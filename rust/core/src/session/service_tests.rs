#![allow(clippy::unwrap_used, clippy::expect_used)]

use super::AgentChatService;
use super::AgentError;
use super::ChatSink;
use super::ConversationsBridge;
use super::interrupted_turn_as_text;
use super::interrupted_turn_fragment;
use super::interrupted_turn_marker;
use super::is_contextual_user_fragment;
use super::transcript_entry;
use crate::agent_settings::AgentSettings;
use crate::host::Clock;
use jasmine_model_provider::ResolvedProvider;
use jasmine_model_provider_info::ModelConfig;
use jasmine_model_provider_info::ModelProviderInfo;
use jasmine_model_provider_info::WireApi;
use jasmine_protocol::ChatEvent;
use jasmine_protocol::Role;
use jasmine_protocol::SessionId;
use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::FunctionCallOutputPayload;
use jasmine_protocol::models::ReasoningItemContent;
use jasmine_protocol::models::ResponseItem;
use jasmine_rollout::RolloutItem;
use jasmine_rollout::RolloutRecorder;
use jasmine_rollout::SessionMeta;
use jasmine_rollout::find_session_path;
use jasmine_rollout::read_response_items;
use jasmine_tools::ConversationTitles;
use std::sync::Arc;
use std::sync::atomic::Ordering;

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

/// OpenAI 那一家：目录里 `gpt-5.5` 只有四档（没有 `max`）—— 用来验"换模型时矫正档位"。
fn openai_provider() -> ResolvedProvider {
    ResolvedProvider::from_config(
        ModelProviderInfo {
            id: "openai".to_string(),
            name: "OpenAI".to_string(),
            base_url: "https://api.openai.com".to_string(),
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
        .start_conversation(
            &SessionId::new(session_id),
            provider(),
            &model(),
            "",
            &AgentSettings::default(),
        )
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

    let error = service.send("s1", "hello", &mut sink).unwrap_err();

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

    assert_eq!(service.context_len("known"), 2);
}

#[test]
fn a_new_conversation_starts_from_an_empty_context() {
    let dir = sessions_dir("fresh");
    let service = service(&dir);

    attach(&service, "brand-new", "new chat");

    assert_eq!(service.context_len("brand-new"), 0);
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
    service.end_conversation("s1");

    let mut sink = Collector { events: Vec::new() };
    assert!(matches!(
        service.send("s1", "hello", &mut sink).unwrap_err(),
        AgentError::NoSession
    ));
}

/// 被停的那一轮写出来的正文落进文件、转写里当普通一行显示，**不会被当成对话里的一条 assistant
/// 消息** —— 带工具表的思考模式请求要求每条 assistant 都带完整思考，半截内容当成条目进去就会被
/// 整轮拒 400。它只以"新消息里的一段普通文本"这个形态出现过（见
/// [`a_new_message_carries_the_half_written_answer_of_a_stopped_turn`]）。
#[test]
fn a_stopped_reply_is_shown_but_stays_out_of_the_context() {
    let dir = sessions_dir("interrupted");
    let service = service(&dir);
    attach(&service, "s1", "chat");

    service
        .persist_interrupted_reply("s1", "half a sentence")
        .expect("persist");

    // 模型上下文里没有它：下一轮请求不会带上。
    assert_eq!(service.context_len("s1"), 0);
    let path = find_session_path(&dir, "s1").expect("find").expect("path");
    assert!(read_response_items(&path).expect("read").is_empty());

    // 转写里有，而且是一行普通的模型回复。
    assert!(
        service
            .transcript(&SessionId::new("s1"))
            .expect("transcript")
            .iter()
            .any(|entry| entry.role == Role::Model && entry.text == "half a sentence"),
    );
}

/// 中断标记是核心注入的上下文片段：**user** 角色（每条 wire 都收，不需要任何改写 ✓），模型看得见，
/// 转写里不显示 —— 界面上的「你在 N 秒后停止了」来自 `TurnAborted`，不是它。
#[test]
fn the_interrupted_turn_marker_is_context_the_platform_does_not_show() {
    let marker = interrupted_turn_marker();
    let ResponseItem::Message { role, content, .. } = &marker else {
        panic!("the marker is a message");
    };
    assert_eq!(role, Role::User.as_str(), "标记是 user 的上下文片段");
    let text = match &content[0] {
        ContentItem::InputText { text } => text.clone(),
        other => panic!("unexpected content: {other:?}"),
    };
    assert!(
        text.starts_with("<turn_aborted>") && text.ends_with("</turn_aborted>"),
        "标记自带首尾标记：{text}"
    );

    assert!(transcript_entry(&marker).is_none(), "转写不该显示它");
}

/// 新建会话时，会话自己的档位从**核心目录里的起点档**抄一次；此后只认会话自己的记录。
#[test]
fn a_new_conversation_starts_from_the_catalogs_default_level() {
    let dir = sessions_dir("effort-seed");
    let service = service(&dir);
    service
        .create_conversation(&SessionId::new("s1"), "deepseek", "deepseek-flash", "chat")
        .expect("create");
    // 目录给 `deepseek-flash` 的起点档是 `high`（见 `presets/deepseek`）。
    service
        .start_conversation(
            &SessionId::new("s1"),
            provider(),
            &model(),
            "",
            &AgentSettings::default(),
        )
        .expect("attach");

    assert_eq!(service.reasoning_effort("s1").as_deref(), Some("high"));

    let path = find_session_path(&dir, "s1").expect("find").expect("path");
    let text = std::fs::read_to_string(&path).expect("read");
    let recorded = text
        .lines()
        .filter(|line| line.contains("\"type\":\"reasoning_effort\""))
        .collect::<Vec<_>>();
    assert_eq!(recorded.len(), 1);
    assert!(recorded[0].contains("\"value\":\"high\""));
}

/// 目录里**没有**这个模型时，起点档退回模型配置里那份默认档 —— 界面只在目录外的模型上让填它。
#[test]
fn a_model_outside_the_catalog_starts_from_its_configured_default() {
    let dir = sessions_dir("effort-config-default");
    let service = service(&dir);
    let model = ModelConfig {
        id: "custom".to_string(),
        model_id: "some-custom-model".to_string(),
        reasoning_effort: "low".to_string(),
        ..ModelConfig::default()
    };

    service
        .create_conversation(
            &SessionId::new("s1"),
            "deepseek",
            "some-custom-model",
            "chat",
        )
        .expect("create");
    service
        .start_conversation(
            &SessionId::new("s1"),
            provider(),
            &model,
            "",
            &AgentSettings::default(),
        )
        .expect("attach");

    assert_eq!(service.reasoning_effort("s1").as_deref(), Some("low"));
}

/// 会话里存过的档，**换到的模型不支持**时会被矫正掉：落到这个模型的目录起点档，并把实际用的值记进去。
///
/// 对应 codex 换模型时按 `supported_reasoning_levels` 换档那条规矩（它 `turn_context` 里就做这件事）。
#[test]
fn a_stored_level_the_new_model_does_not_support_is_corrected() {
    let dir = sessions_dir("effort-corrected");
    let service = service(&dir);
    attach(&service, "s1", "chat");
    // DeepSeek 那四档里有 `max`：先把这条会话的档位改成它。
    service.set_reasoning_effort("s1", "max").expect("set max");
    assert_eq!(service.reasoning_effort("s1").as_deref(), Some("max"));

    // 换成 OpenAI 的 `gpt-5.5`（目录里**没有** `max`）→ 矫正成它的起点档 `medium`。
    let model = ModelConfig {
        id: "gpt-5.5".to_string(),
        model_id: "gpt-5.5".to_string(),
        ..ModelConfig::default()
    };
    service
        .start_conversation(
            &SessionId::new("s1"),
            openai_provider(),
            &model,
            "",
            &AgentSettings::default(),
        )
        .expect("attach");

    assert_eq!(service.reasoning_effort("s1").as_deref(), Some("medium"));

    // 矫正不是"只在内存里改一下"：会话文件里留下这条记录，历史一条不删 ——
    // `high`（新建时目录给的起点档）→ `max`（用户改的）→ `medium`（换模型后矫正成的新模型起点档）。
    let path = find_session_path(&dir, "s1").expect("find").expect("path");
    let text = std::fs::read_to_string(&path).expect("read");
    let recorded = text
        .lines()
        .filter(|line| line.contains("\"type\":\"reasoning_effort\""))
        .filter_map(|line| {
            let marker = "\"value\":\"";
            let start = line.find(marker)? + marker.len();
            Some(line[start..].split('"').next()?.to_string())
        })
        .collect::<Vec<_>>();
    assert_eq!(recorded, ["high", "max", "medium"]);
}

/// 在对话里改档位：每改一次**追加**一条，历史一条不删，最后一条生效。
///
/// 第 1 条是新建时就有的（目录给的起点档 `high`），第 2、3 条是后来改的 —— 三条都在文件里。
#[test]
fn changing_the_effort_appends_a_record_and_takes_effect() {
    let dir = sessions_dir("effort-changes");
    let service = service(&dir);
    attach(&service, "s1", "chat");
    assert_eq!(service.reasoning_effort("s1").as_deref(), Some("high"));

    service.set_reasoning_effort("s1", "low").expect("set low");
    service
        .set_reasoning_effort("s1", "high")
        .expect("set high");

    assert_eq!(service.reasoning_effort("s1").as_deref(), Some("high"));

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
    assert_eq!(values, vec!["high", "low", "high"]);

    // 改完之后再跑一轮：这一轮记的档位跟的是**会话**的值，不是模型配置里的（模型这里是空串）。
    let mut sink = Collector { events: Vec::new() };
    let _ = service.send("s1", "hello", &mut sink);
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
    service
        .start_conversation(
            &SessionId::new("s1"),
            provider(),
            &model(),
            "",
            &AgentSettings::default(),
        )
        .expect("attach");

    let mut sink = Collector { events: Vec::new() };
    let _ = service.send("s1", "hello", &mut sink);

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

/// 暂停之后没点继续、直接在输入框发了新内容：那一轮看得到的全部内容走一段**上下文片段**，
/// 排在用户这条消息前面 —— 模型看得见 ✓，转写不认它是一条自己的消息 ✓，所以界面上不会多出气泡 ✗。
#[test]
fn the_stopped_turns_content_travels_as_a_contextual_fragment() {
    let fragment = interrupted_turn_fragment("# 示例\n\n下面是一段").expect("有内容就该有片段");

    assert!(fragment.starts_with("<interrupted_turn>"));
    assert!(fragment.ends_with("</interrupted_turn>"));
    assert!(fragment.contains("# 示例\n\n下面是一段"));
    assert!(
        is_contextual_user_fragment(Role::User.as_str(), &fragment),
        "片段要能被认出来，转写才不会把它显示成气泡"
    );
    assert!(
        transcript_entry(&ResponseItem::Message {
            id: None,
            role: Role::User.as_str().to_string(),
            content: vec![ContentItem::InputText { text: fragment }],
        })
        .is_none(),
        "上下文片段不上屏"
    );
}

/// 被停那一轮已经想到、还没写完的思考：重启后看得见（转写里一行思考 ✓），但**不进对话** ✗
/// —— 它只是展示记录，下一轮请求不会带上。
#[test]
fn the_thinking_a_stopped_turn_had_reached_is_shown_but_stays_out_of_the_context() {
    let dir = sessions_dir("stopped-thinking");
    let service = service(&dir);
    attach(&service, "s1", "chat");
    let path = find_session_path(&dir, "s1").expect("find").expect("path");
    RolloutRecorder::open(path.clone())
        .expect("open")
        .record_items(&[RolloutItem::InterruptedReasoning {
            turn_id: "t1".to_string(),
            text: "先看时间，再回答".to_string(),
        }])
        .expect("record");

    let entries = service.transcript(&SessionId::new("s1")).expect("transcript");
    assert!(
        entries
            .iter()
            .any(|entry| entry.thinking == "先看时间，再回答" && entry.text.is_empty()),
        "思考在转写里占一行"
    );
    assert!(
        read_response_items(&path).expect("read").is_empty(),
        "展示记录不是模型条目"
    );
}

/// 新建 / 切换对话时平台会释放会话 —— 它**不能等正在跑的那一轮**：平台是同步调用、而且发在界面
/// 线程上，等下去就是整个界面卡住（回复多久就卡多久）。释放记为请求，回合结束自己放手。
#[test]
fn releasing_a_conversation_does_not_wait_for_a_running_turn() {
    let dir = sessions_dir("detach-mid-turn");
    let service = service(&dir);
    attach(&service, "s1", "chat");

    // 模拟"回合正在跑"：它整个回合都占着**这条会话自己的**槽。
    let slot = service.attached_slot("s1").expect("slot");
    let mut held = slot.lock().expect("lock");
    let started = std::time::Instant::now();
    service.end_conversation("s1");
    assert!(
        started.elapsed() < std::time::Duration::from_millis(200),
        "释放不能等着回合跑完"
    );
    assert!(held.is_some(), "回合还在跑时，会话先留着（它自己要用）");

    // 回合结束时由它自己放手。
    service.release_if_detach_requested("s1", &slot, &mut held);
    assert!(held.is_none(), "回合结束才真的放掉");
    drop(held);
    assert!(service.slot("s1").is_none(), "放掉之后这条会话不再附着");

    // 再释放一次：这回没人占着，当场就清掉。
    attach(&service, "s1", "chat");
    service.end_conversation("s1");
    assert!(service.slot("s1").is_none());
}

/// 一条会话在跑，**不挡别的会话**：另一条照常附着、照常收消息，而且释放它不会碰到前一条。
///
/// 这是"多会话并行"的核心 —— 以前只有一个附着位，切到另一条会话就得等前一条跑完（或者把它掐掉）。
#[test]
fn a_running_turn_does_not_hold_up_another_conversation() {
    let dir = sessions_dir("parallel-attach");
    let service = service(&dir);
    attach(&service, "s1", "one");
    attach(&service, "s2", "two");

    // s1 的一轮正占着它自己的槽。
    let slot = service.attached_slot("s1").expect("slot");
    let held = slot.lock().expect("lock");

    // s2 照样附着：不用等 s1 那一轮。
    let started = std::time::Instant::now();
    service
        .start_conversation(
            &SessionId::new("s2"),
            provider(),
            &model(),
            "",
            &AgentSettings::default(),
        )
        .expect("attach s2");
    assert!(
        started.elapsed() < std::time::Duration::from_millis(200),
        "另一条会话的附着不能等 s1 那一轮"
    );

    // 释放 s2 也不会碰到 s1：s1 还附着着，槽还在。
    service.end_conversation("s2");
    assert!(service.slot("s2").is_none(), "s2 放掉了");
    assert!(service.slot("s1").is_some(), "s1 不受影响");
    drop(held);
}

/// 没有内容可带（没暂停过，或那一轮什么都没做出来）：不生成任何片段，用户的消息就是原样那几个字。
#[test]
fn nothing_to_carry_makes_no_fragment() {
    assert!(interrupted_turn_fragment("").is_none());
    assert!(interrupted_turn_fragment("  \n ").is_none());
}

/// 带过去的是那一轮的**整份可见记录**：正文、工具调用的过程和结果（参数与结果原样 ✓ 不裁剪 ✗）、
/// 写到一半的那一段，按发生的顺序排在普通文本里。思考不带 ✓；用户自己那条消息也不重复 ✓。
#[test]
fn a_carried_turn_lists_everything_it_had_shown_in_order() {
    // 都比界面上显示的上限（200 字符）长：带过去的是原样，不裁剪。
    let long_arguments = format!(r#"{{"command":"{}"}}"#, "x".repeat(400));
    let long_result = "y".repeat(400);
    let items = vec![
        RolloutItem::ResponseItem(ResponseItem::Message {
            id: None,
            role: Role::User.as_str().to_string(),
            content: vec![ContentItem::InputText {
                text: "几点了".to_string(),
            }],
        }),
        RolloutItem::ResponseItem(ResponseItem::Reasoning {
            id: None,
            summary: Vec::new(),
            content: Some(vec![ReasoningItemContent::ReasoningText {
                text: "先看时间".to_string(),
            }]),
            encrypted_content: None,
        }),
        RolloutItem::ResponseItem(ResponseItem::FunctionCall {
            id: None,
            name: "current_time".to_string(),
            namespace: None,
            arguments: long_arguments.clone(),
            encrypted_function_args: None,
            call_id: "c1".to_string(),
        }),
        RolloutItem::ResponseItem(ResponseItem::FunctionCallOutput {
            id: None,
            call_id: Some("c1".to_string()),
            name: None,
            namespace: None,
            output: FunctionCallOutputPayload::from_text(long_result.clone()),
        }),
        RolloutItem::ResponseItem(ResponseItem::Message {
            id: None,
            role: Role::Model.as_str().to_string(),
            content: vec![ContentItem::OutputText {
                text: "现在是下午四点四十。".to_string(),
            }],
        }),
        RolloutItem::InterruptedReasoning {
            turn_id: "t1".to_string(),
            text: "先看时间".to_string(),
        },
        RolloutItem::InterruptedReply {
            turn_id: "t1".to_string(),
            text: "下面是一段".to_string(),
        },
    ];

    let shown = interrupted_turn_as_text(&items);

    // 思考不带。
    assert!(!shown.contains("[thinking]"), "思考不该跟着走");
    assert!(!shown.contains("先看时间"));
    // 工具的参数与结果原样带上：一点都没裁。
    assert!(shown.contains(&long_arguments), "工具参数要完整");
    assert!(shown.contains(&long_result), "工具结果要完整");
    assert_eq!(
        shown,
        format!(
            "[tool] current_time {long_arguments}\n\n\
             [tool result] {long_result}\n\n\
             现在是下午四点四十。\n\n\
             下面是一段"
        )
    );
}

/// 回合**失败**时也必须走收尾（F1a）。
///
/// 以前收尾写在 `?` 之后：任一处提前返回（请求失败、落盘失败）就把 `release_if_detach_requested`
/// 整段跳过 —— 平台以为已经关掉的会话会一直留在内存里（HTTP 客户端、连接池、文件句柄都还挂着），
/// 而且下一次 `send` 还能拿它继续跑一轮。
#[test]
fn a_failed_turn_still_releases_the_conversation() {
    let dir = sessions_dir("detach-on-failure");
    let service = service(&dir);
    attach(&service, "s1", "chat");

    // "平台在回合中途请求了释放"：`end_conversation` 在有回合占着槽时就是这么记的。这里直接置位，
    // 因为测试里没有真并发。
    let slot = service.attached_slot("s1").expect("slot");
    slot.detach_requested.store(true, Ordering::SeqCst);
    drop(slot);

    // 这一轮会失败（测试不发真请求），但失败同样要走收尾。
    let mut sink = Collector { events: Vec::new() };
    let _ = service.send("s1", "hello", &mut sink);

    assert!(
        service.slot("s1").is_none(),
        "回合失败也必须在收尾里放开「平台请求过释放」的会话"
    );
}

/// 读会话长度**不排队**等正在跑的那一轮（F1c）。
///
/// 这个方法是平台在自己的线程上直接调的非挂起 FFI；回合跑着时锁在那一轮手里，以前这里会一直等，
/// 等于把调用方钉住一整轮回复。
#[test]
fn reading_the_context_length_does_not_wait_for_a_running_turn() {
    let dir = sessions_dir("context-len-no-wait");
    let service = service(&dir);
    attach(&service, "s1", "chat");

    // 模拟"回合正在跑"：它整个回合都占着这条会话自己的槽。
    let slot = service.attached_slot("s1").expect("slot");
    let _held = slot.lock().expect("lock");

    let started = std::time::Instant::now();
    assert_eq!(service.context_len("s1"), 0, "读不到就报 0，不排队");
    assert!(
        started.elapsed() < std::time::Duration::from_millis(200),
        "读长度不能等着回合跑完"
    );
}

/// 中毒的会话照样能被释放（F1d）。
///
/// 中毒意味着"上一个持锁的调用 panic 过"（平台回调里抛异常经 uniffi 就成了 Rust panic），不是
/// "这条会话正忙"。用普通 `try_lock` 会把中毒误判成忙 —— 既不清附着也不 forget，这条会话就永远
/// 留在注册表里。
#[test]
fn ending_a_poisoned_conversation_still_clears_it() {
    let dir = sessions_dir("poisoned-detach");
    let service = service(&dir);
    attach(&service, "s1", "chat");

    let slot = service.attached_slot("s1").expect("slot");
    let panicked = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let _guard = slot.lock().expect("lock");
        panic!("模拟平台回调在持锁期间抛异常");
    }));
    assert!(panicked.is_err(), "这一下应该真的 panic 了");

    service.end_conversation("s1");
    assert!(
        service.slot("s1").is_none(),
        "中毒不是「正忙」：释放必须照样把它清掉"
    );
}

/// `Outbox` 的语义（F1b）：事件按顺序攒着、信号只记一次、flush 一次就够。
///
/// 它是"回调搬出持锁范围"的承载物 —— 攒错了就等于事件丢了或重复发。
#[test]
fn the_outbox_holds_events_until_flushed_and_collapses_the_signal() {
    let outbox = super::Outbox::default();
    outbox.event(ChatEvent::Text("一段".to_string()));
    outbox.store_changed();
    outbox.store_changed();

    let mut sink = Collector { events: Vec::new() };
    let notifications = std::cell::Cell::new(0);
    outbox.flush(&mut sink, || notifications.set(notifications.get() + 1));

    assert_eq!(sink.events.len(), 1, "事件原样出去");
    assert_eq!(notifications.get(), 1, "重复的存储信号合成一次");

    // 再 flush 一次不该重发：通道式的语义是"发过就没了"。
    outbox.flush(&mut sink, || notifications.set(notifications.get() + 1));
    assert_eq!(sink.events.len(), 1);
    assert_eq!(notifications.get(), 1);
}
