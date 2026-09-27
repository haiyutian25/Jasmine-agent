#![allow(clippy::unwrap_used, clippy::expect_used)]

use crate::RolloutItem;
use crate::RolloutRecorder;
use crate::SessionMeta;
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
