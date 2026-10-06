#![allow(clippy::unwrap_used, clippy::expect_used)]

use crate::RolloutItem;
use crate::RolloutRecorder;
use crate::SessionMeta;
use crate::TurnAbortReason;
use crate::delete_session;
use crate::interrupted_turn_items;
use crate::list_sessions;
use crate::read_response_items;
use crate::read_session;
use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::ResponseItem;

fn meta(session_id: &str, title: &str) -> SessionMeta {
    SessionMeta {
        session_id: session_id.to_string(),
        timestamp: "2026-09-27T16:41:05+08:00".to_string(),
        title: title.to_string(),
        provider_id: "deepseek".to_string(),
        model_id: "deepseek-flash".to_string(),
    }
}

fn message(role: &str, text: &str) -> ResponseItem {
    ResponseItem::Message {
        id: None,
        role: role.to_string(),
        content: vec![if role == "assistant" {
            ContentItem::OutputText {
                text: text.to_string(),
            }
        } else {
            ContentItem::InputText {
                text: text.to_string(),
            }
        }],
    }
}

fn sessions_dir(name: &str) -> std::path::PathBuf {
    let dir = std::env::temp_dir().join(format!("jasmine-rollout-{name}"));
    let _ = std::fs::remove_dir_all(&dir);
    dir
}

#[test]
fn a_compaction_record_replaces_everything_before_it() {
    let dir = sessions_dir("compacted-history");
    let mut recorder = RolloutRecorder::create(&dir, &meta("s1", "compacted")).expect("create");
    recorder
        .record_items(&[
            RolloutItem::ResponseItem(message("user", "old question")),
            RolloutItem::ResponseItem(message("assistant", "old answer")),
            // 压缩：以上两条被换成「保留的用户消息 + 摘要」，摘要永远在末尾。
            RolloutItem::Compacted {
                replacement_history: vec![
                    message("user", "kept question"),
                    message("user", "SUMMARY"),
                ],
                summary: "SUMMARY".to_string(),
                active_context_tokens: 180_000,
                context_window: 200_000,
                items_before: 2,
                items_after: 2,
            },
            // 压缩之后新产生的内容照旧追加在后头。
            RolloutItem::ResponseItem(message("assistant", "new answer")),
        ])
        .expect("record");
    let path = recorder.rollout_path().to_path_buf();

    let items = read_response_items(&path).expect("read");
    assert_eq!(
        vec![
            message("user", "kept question"),
            message("user", "SUMMARY"),
            message("assistant", "new answer"),
        ],
        items,
    );

    // 压缩前的原始条目一条没删 —— 追加式写入，随时能人工回溯。
    let raw = std::fs::read_to_string(&path).expect("read raw");
    assert!(raw.contains("old question"));
    assert!(raw.contains("old answer"));
    // 用 serde 的 tag 精确匹配（`title` 之类的字段里也可能出现同样的字样）。
    assert_eq!(1, raw.matches("\"type\":\"compacted\"").count());
}

#[test]
fn a_conversation_records_what_happened_and_reads_it_back() {
    let dir = sessions_dir("round-trip");
    let mut recorder = RolloutRecorder::create(&dir, &meta("s1", "first")).expect("create");
    recorder
        .record_items(&[
            RolloutItem::ResponseItem(message("user", "what time is it")),
            RolloutItem::ResponseItem(message("assistant", "let me look")),
        ])
        .expect("record");
    let path = recorder.rollout_path().to_path_buf();

    let items = read_response_items(&path).expect("read");
    assert_eq!(items.len(), 2);
    assert_eq!(items[0], message("user", "what time is it"));
    assert_eq!(items[1], message("assistant", "let me look"));

    let (meta, updated_at) = read_session(&path).expect("session");
    assert_eq!(meta.title, "first");
    assert_eq!(meta.provider_id, "deepseek");
    assert!(!updated_at.is_empty());

    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn a_rename_shows_up_without_rewriting_the_file() {
    let dir = sessions_dir("rename");
    let mut recorder = RolloutRecorder::create(&dir, &meta("s1", "new chat")).expect("create");
    recorder
        .record_items(&[RolloutItem::ResponseItem(message("user", "hello"))])
        .expect("record");
    recorder.record_meta(&meta("s1", "hello")).expect("rename");
    let path = recorder.rollout_path().to_path_buf();

    let (meta, _) = read_session(&path).expect("session");
    assert_eq!(meta.title, "hello");
    assert_eq!(read_response_items(&path).expect("read").len(), 1);

    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn a_conversation_resumes_from_its_own_file() {
    let dir = sessions_dir("resume");
    let mut recorder = RolloutRecorder::create(&dir, &meta("s1", "first")).expect("create");
    recorder
        .record_items(&[RolloutItem::ResponseItem(message("user", "one"))])
        .expect("record");
    let path = recorder.rollout_path().to_path_buf();
    drop(recorder);

    let mut resumed = RolloutRecorder::open(path).expect("open");
    resumed
        .record_items(&[RolloutItem::ResponseItem(message("assistant", "two"))])
        .expect("record");

    let listed = list_sessions(&dir).expect("list");
    assert_eq!(listed.len(), 1);
    assert_eq!(listed[0].meta.session_id, "s1");
    assert_eq!(read_response_items(&listed[0].path).expect("read").len(), 2);

    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn the_stopped_turns_own_items_read_back_in_order() {
    let dir = sessions_dir("interrupted-reply");
    let mut recorder = RolloutRecorder::create(&dir, &meta("s1", "half")).expect("create");
    let path = recorder.rollout_path().to_path_buf();

    assert!(interrupted_turn_items(&path).is_empty());

    // 中断那一轮在文件里的真实顺序：开轮 → 用户消息 → 工具调用/结果 → 半截正文 → 收尾边界。
    recorder
        .record_items(&[
            RolloutItem::TurnStarted {
                turn_id: "t1".to_string(),
                model_id: "deepseek-flash".to_string(),
                reasoning_effort: "high".to_string(),
            },
            RolloutItem::ResponseItem(message("user", "写一段示例")),
            RolloutItem::ResponseItem(message("assistant", "先看看工具")),
            RolloutItem::InterruptedReasoning {
                turn_id: "t1".to_string(),
                text: "还没想完".to_string(),
            },
            RolloutItem::InterruptedReply {
                turn_id: "t1".to_string(),
                text: "写到一半".to_string(),
            },
            RolloutItem::ResponseItem(message("user", "<turn_aborted>…")),
            RolloutItem::TurnAborted {
                turn_id: "t1".to_string(),
                reason: TurnAbortReason::Interrupted,
                duration_ms: 1200,
            },
        ])
        .expect("record");

    let items = interrupted_turn_items(&path);
    assert_eq!(
        items.len(),
        4,
        "边界不算内容，被停那一轮的思考也不跟着新消息走"
    );
    assert_eq!(
        items[0],
        RolloutItem::ResponseItem(message("user", "写一段示例"))
    );
    assert_eq!(
        items[1],
        RolloutItem::ResponseItem(message("assistant", "先看看工具"))
    );
    assert_eq!(
        items[2],
        RolloutItem::InterruptedReply {
            turn_id: "t1".to_string(),
            text: "写到一半".to_string(),
        }
    );
    assert_eq!(
        items[3],
        RolloutItem::ResponseItem(message("user", "<turn_aborted>…"))
    );

    // 用户已经直接发了新内容（新的一轮开始了）：上一轮的东西不会再被带一次。
    recorder
        .record_items(&[RolloutItem::TurnStarted {
            turn_id: "t2".to_string(),
            model_id: "deepseek-flash".to_string(),
            reasoning_effort: "high".to_string(),
        }])
        .expect("record");

    assert!(interrupted_turn_items(&path).is_empty());

    let _ = std::fs::remove_dir_all(&dir);
}

/// 坏一行**不许**带走整个文件：后面的行还是用户的记录（G6，对应 F6 的 `read_lines`）。
#[test]
fn a_corrupt_line_is_skipped_and_never_takes_the_file_down() {
    use std::io::Write;

    let dir = sessions_dir("corrupt-line");
    let mut recorder = RolloutRecorder::create(&dir, &meta("s1", "first")).expect("create");
    recorder
        .record_items(&[RolloutItem::ResponseItem(message("user", "hello"))])
        .expect("record");
    let path = recorder.rollout_path().to_path_buf();

    // 手动插一条截断的行：写了一半就断电、或磁盘上被谁改坏了。
    {
        let mut file = std::fs::OpenOptions::new()
            .append(true)
            .open(&path)
            .expect("open");
        writeln!(
            file,
            "{{\"timestamp\":\"2026-09-27T16:41:05+08:00\",\"type\":\"response_item\""
        )
        .expect("write");
    }

    let items = read_response_items(&path).expect("读坏了也不该整份失败");
    assert_eq!(items.len(), 1, "坏的那行跳过，好的照读");
    assert_eq!(items[0], message("user", "hello"));

    // 列表同样：这条会话的元信息行是好的，它就该在列表里（`updated_at` 只认读得出来的行）。
    let entries = list_sessions(&dir).expect("list");
    assert_eq!(entries.len(), 1);
    assert_eq!(entries[0].meta.session_id, "s1");

    let _ = std::fs::remove_dir_all(&dir);
}

/// 尾巴上那条没写完的行会被**截掉**：否则它会在之后每一次读里重复出现，永远修不好。
///
/// 成因就是"写到一半进程被杀"——末尾那行没有换行、JSON 也不完整。截到最后一个完整行的行尾，
/// 文件就重新变成"每行都读得出来"。用 `set_len`，不动 inode（正在追加的 recorder 还握着它）。
#[test]
fn a_corrupt_tail_is_truncated_so_it_stops_coming_back() {
    use std::io::Write;

    let dir = sessions_dir("corrupt-tail-repair");
    let mut recorder = RolloutRecorder::create(&dir, &meta("s1", "first")).expect("create");
    recorder
        .record_items(&[RolloutItem::ResponseItem(message("user", "hello"))])
        .expect("record");
    let path = recorder.rollout_path().to_path_buf();

    {
        let mut file = std::fs::OpenOptions::new()
            .append(true)
            .open(&path)
            .expect("open");
        // 注意：**没有换行**，正是"写一半被杀"留下的形状。
        write!(
            file,
            "{{\"timestamp\":\"2026-09-27T16:41:05+08:00\",\"type\":\"response_item\""
        )
        .expect("write");
    }

    let items = read_response_items(&path).expect("read");
    assert_eq!(items.len(), 1, "坏尾巴跳过，好的照读");

    let repaired = std::fs::read_to_string(&path).expect("file");
    assert_eq!(
        repaired.lines().count(),
        2,
        "坏尾巴应当被截掉，只剩元信息 + 那条消息：{repaired:?}"
    );
    assert!(
        repaired.ends_with('\n'),
        "截在最后一个完整行的行尾：{repaired:?}"
    );

    let _ = std::fs::remove_dir_all(&dir);
}

/// 坏行在**中间**（后面还有读得出来的行）时不截：那说明文件被外部改过，
/// 按"截到最后一行好的"去修会把后面那些用户记录一起切掉。
#[test]
fn a_corrupt_line_in_the_middle_is_left_alone() {
    let dir = sessions_dir("corrupt-middle");
    let mut recorder = RolloutRecorder::create(&dir, &meta("s1", "first")).expect("create");
    recorder
        .record_items(&[
            RolloutItem::ResponseItem(message("user", "hello")),
            RolloutItem::ResponseItem(message("assistant", "hi")),
        ])
        .expect("record");
    let path = recorder.rollout_path().to_path_buf();

    // 文件本来是 [元信息, 用户, 回复]，在第 3 行插一条坏的：坏行后面还有好行。
    let text = std::fs::read_to_string(&path).expect("file");
    let mut lines: Vec<String> = text.lines().map(str::to_string).collect();
    lines.insert(2, "{ this is not json }".to_string());
    std::fs::write(&path, lines.join("\n") + "\n").expect("write");

    let items = read_response_items(&path).expect("read");
    assert_eq!(items.len(), 2, "坏行跳过，它前面和后面的都照读");

    let after = std::fs::read_to_string(&path).expect("file");
    assert!(
        after.contains("this is not json"),
        "中间的坏行不截（截了会带走它后面的记录）：{after:?}"
    );

    let _ = std::fs::remove_dir_all(&dir);
}

/// 整份文件都读不出来（连元信息都没有）时，跳过**这一条**，别的会话照常列出来。
///
/// 这是"文件坏了"与"历史全没了"的分界：列表要少一条并留痕，不能因为一条坏的就把整张表清空。
#[test]
fn a_session_whose_file_cannot_be_read_is_skipped_and_the_others_still_list() {
    let dir = sessions_dir("corrupt-file");
    let mut healthy = RolloutRecorder::create(&dir, &meta("s-healthy", "healthy")).expect("create");
    healthy
        .record_items(&[RolloutItem::ResponseItem(message("user", "hi"))])
        .expect("record");
    let broken = RolloutRecorder::create(&dir, &meta("s-broken", "broken")).expect("create");
    std::fs::write(broken.rollout_path(), "not json at all\n").expect("write");

    let entries = list_sessions(&dir).expect("list");
    assert_eq!(entries.len(), 1);
    assert_eq!(entries[0].meta.session_id, "s-healthy");

    let _ = std::fs::remove_dir_all(&dir);
}

/// 删会话就是删它那一个文件；重复删是 no-op（平台可能重复发同一条指令）。
#[test]
fn deleting_a_session_removes_its_file_and_the_list_follows() {
    let dir = sessions_dir("delete");
    let mut recorder = RolloutRecorder::create(&dir, &meta("s1", "first")).expect("create");
    recorder
        .record_items(&[RolloutItem::ResponseItem(message("user", "hello"))])
        .expect("record");
    let path = recorder.rollout_path().to_path_buf();

    delete_session(&dir, "s1").expect("delete");
    assert!(!path.exists(), "文件应当被删掉");
    assert!(list_sessions(&dir).expect("list").is_empty());

    delete_session(&dir, "s1").expect("再删一次应当是 no-op");

    let _ = std::fs::remove_dir_all(&dir);
}
