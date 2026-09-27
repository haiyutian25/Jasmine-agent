#![allow(clippy::unwrap_used, clippy::expect_used)]

use super::RolloutFileName;
use chrono::NaiveDate;

fn started() -> chrono::NaiveDateTime {
    NaiveDate::from_ymd_opt(2026, 9, 27)
        .expect("date")
        .and_hms_opt(16, 41, 5)
        .expect("time")
}

#[test]
fn a_name_carries_the_moment_and_the_session() {
    let name = RolloutFileName::new(started(), "9f1c".to_string());

    assert_eq!(name.render(), "rollout-2026-09-27T16-41-05-9f1c.jsonl");
}

#[test]
fn a_rendered_name_parses_back_to_itself() {
    let rendered = RolloutFileName::new(started(), "9f1c".to_string()).render();
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
