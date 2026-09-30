#![allow(clippy::unwrap_used, clippy::expect_used)]

use super::RolloutFileName;
use super::is_usable_session_id;
use chrono::NaiveDate;

fn started() -> chrono::NaiveDateTime {
    NaiveDate::from_ymd_opt(2026, 9, 27)
        .expect("date")
        .and_hms_opt(16, 41, 5)
        .expect("time")
}

#[test]
fn a_name_carries_the_moment_and_the_session() {
    let name = RolloutFileName::new(started(), "9f1c".to_string()).expect("usable id");

    assert_eq!(name.render(), "rollout-2026-09-27T16-41-05-9f1c.jsonl");
}

#[test]
fn a_rendered_name_parses_back_to_itself() {
    let rendered = RolloutFileName::new(started(), "9f1c".to_string())
        .expect("usable id")
        .render();
    let parsed = RolloutFileName::parse(&rendered).expect("parse");

    assert_eq!(parsed.render(), rendered);
    assert_eq!(parsed.session_id(), "9f1c");
}

#[test]
fn a_name_that_is_not_ours_is_rejected() {
    assert!(RolloutFileName::parse("transcript.jsonl").is_none());
    assert!(RolloutFileName::parse("rollout-2026-09-27T16-41-05.jsonl").is_none());
    assert!(RolloutFileName::parse("rollout-2026-09-27T16-41-05-9f1c.txt").is_none());
}

/// 平台给的是 UUID；除此之外的 id 一律不许当文件名 —— 它会被拼进路径，`../` 能跳出去。
#[test]
fn an_id_that_could_point_outside_the_directory_is_refused() {
    for id in [
        "",
        "..",
        "../other",
        "..\\other",
        "a/b",
        "a\\b",
        "9f1c/../9f1d",
        "9f1c.jsonl",
        "9f1c 1",
        "9f1c\n",
        "会话",
    ] {
        assert!(!is_usable_session_id(id), "不该接受这样的 id：{id:?}");
        assert!(
            RolloutFileName::new(started(), id.to_string()).is_none(),
            "不该为这样的 id 生成文件名：{id:?}"
        );
    }

    for id in [
        "9f1c",
        "9F1C-4d2b_9a0e",
        "8c31eb4a-2d5d-4fe1-ac62-1c42cbfb86f1",
    ] {
        assert!(is_usable_session_id(id), "应当接受这样的 id：{id:?}");
    }
}
