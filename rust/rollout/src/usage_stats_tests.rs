#![allow(clippy::unwrap_used, clippy::expect_used)]

use crate::RolloutItem;
use crate::RolloutLine;
use crate::SessionMeta;
use chrono::Datelike;
use jasmine_protocol::protocol::TokenUsage;
use jasmine_protocol::protocol::TokenUsageInfo;
use std::path::Path;
use std::path::PathBuf;

fn today() -> chrono::NaiveDate {
    chrono::Local::now().date_naive()
}

fn first_of_month() -> chrono::NaiveDate {
    today().with_day(1).unwrap()
}

/// A stamp on a given day, at a fixed time of it, in the offset the machine would have written.
fn stamp(date: chrono::NaiveDate) -> String {
    format!("{}T10:00:00+08:00", date.format("%Y-%m-%d"))
}

/// What a conversation says about itself.
fn meta(session_id: &str) -> SessionMeta {
    SessionMeta {
        session_id: session_id.to_string(),
        timestamp: stamp(first_of_month()),
        title: String::new(),
        provider_id: "deepseek".to_string(),
        model_id: "deepseek-flash".to_string(),
    }
}

/// A turn opening on one model.
fn turn(model_id: &str) -> RolloutItem {
    RolloutItem::TurnStarted {
        turn_id: format!("turn-{model_id}"),
        model_id: model_id.to_string(),
        reasoning_effort: "medium".to_string(),
    }
}

/// A record of what the conversation has cost so far — the running total, not one turn's spend.
fn spent(running_total: i64) -> RolloutItem {
    RolloutItem::TokenUsageRecord {
        info: TokenUsageInfo {
            total_token_usage: TokenUsage {
                total_tokens: running_total,
                ..TokenUsage::default()
            },
            last_token_usage: TokenUsage::default(),
            model_context_window: None,
        },
        breakdown: Vec::new(),
    }
}

/// A directory to build a sessions tree under, emptied first.
fn sessions_dir(name: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("jasmine-usage-{name}"));
    let _ = std::fs::remove_dir_all(&dir);
    dir
}

/// Writes one conversation's file, line by line, exactly as the recorder would have.
fn write_session(dir: &Path, session_id: &str, lines: Vec<(String, RolloutItem)>) -> PathBuf {
    let path = dir
        .join("sessions")
        .join("2026")
        .join("09")
        .join("28")
        .join(format!("rollout-2026-09-28T10-00-00-{session_id}.jsonl"));
    std::fs::create_dir_all(path.parent().unwrap()).unwrap();

    let mut text = String::new();
    for (timestamp, item) in std::iter::once((
        stamp(first_of_month()),
        RolloutItem::SessionMeta(meta(session_id)),
    ))
    .chain(lines)
    {
        let line = RolloutLine { timestamp, item };
        text.push_str(&serde_json::to_string(&line).unwrap());
        text.push('\n');
    }
    std::fs::write(&path, text).unwrap();
    path
}

#[test]
fn a_turn_is_counted_by_the_step_between_records() {
    let dir = sessions_dir("step");
    write_session(
        &dir,
        "s1",
        vec![
            (stamp(today()), turn("deepseek-flash")),
            // 100 so far, then 250: the turn spent 150, not 250.
            (stamp(today()), spent(100)),
            (stamp(today()), spent(250)),
        ],
    );

    let stats = super::usage_stats(&dir).unwrap();
    assert_eq!(stats.total_tokens, 250);
    assert_eq!(stats.days.len(), 1);
    assert_eq!(stats.days[0].tokens, 250);
}

#[test]
fn last_months_usage_is_counted_too() {
    let dir = sessions_dir("month-edge");
    let path = write_session(
        &dir,
        "s1",
        vec![
            // The last day of last month: one day before this month began.
            (
                stamp(first_of_month() - chrono::Duration::days(1)),
                turn("old-model"),
            ),
            (
                stamp(first_of_month() - chrono::Duration::days(1)),
                spent(500),
            ),
            (
                stamp(first_of_month() - chrono::Duration::days(1)),
                spent(900),
            ),
            (stamp(today()), turn("deepseek-flash")),
            (stamp(today()), spent(940)),
        ],
    );

    let stats = super::usage_stats(&dir).unwrap();
    // 两个月的都算：上月那天 900（500 + 400 两步），今天 40。
    assert_eq!(stats.total_tokens, 940);
    assert_eq!(stats.days.len(), 2);
    assert_eq!(stats.days[0].tokens, 900);
    assert_eq!(stats.days[1].tokens, 40);
    assert_eq!(stats.models.len(), 2);
    assert_eq!(stats.models[0].model_id, "old-model");
    assert_eq!(stats.models[0].tokens, 900);
    assert_eq!(stats.models[1].model_id, "deepseek-flash");
    assert_eq!(stats.models[1].tokens, 40);

    // 磁盘上一行都不动：三条记录原样都在（统计不再删任何东西）。
    let text = std::fs::read_to_string(&path).unwrap();
    assert_eq!(text.matches("token_usage_record").count(), 3);
    assert!(text.contains("\"total_tokens\":500"));
    assert!(text.contains("\"total_tokens\":900"));
    assert!(text.contains("turn_started"));
}

#[test]
fn two_turns_of_one_model_stay_one_row() {
    let dir = sessions_dir("one-model");
    write_session(
        &dir,
        "s1",
        vec![
            (stamp(today()), turn("deepseek-flash")),
            (stamp(today()), spent(100)),
            (stamp(today()), turn("deepseek-flash")),
            (stamp(today()), spent(150)),
        ],
    );

    let stats = super::usage_stats(&dir).unwrap();
    assert_eq!(stats.models.len(), 1);
    assert_eq!(stats.models[0].model_id, "deepseek-flash");
    assert_eq!(stats.models[0].tokens, 150);
}

#[test]
fn bigger_spenders_come_first() {
    let dir = sessions_dir("ordering");
    write_session(
        &dir,
        "s1",
        vec![
            (stamp(today()), turn("small")),
            (stamp(today()), spent(10)),
            (stamp(today()), turn("big")),
            (stamp(today()), spent(110)),
        ],
    );

    let stats = super::usage_stats(&dir).unwrap();
    assert_eq!(stats.models.len(), 2);
    assert_eq!(stats.models[0].model_id, "big");
    assert_eq!(stats.models[0].tokens, 100);
    assert_eq!(stats.models[1].model_id, "small");
    assert_eq!(stats.models[1].tokens, 10);
}

#[test]
fn days_in_a_row_become_a_streak() {
    let dir = sessions_dir("streak");
    // 今天和昨天连着、再往前空一天（前天没花），所以连续停在昨天。昨天落在上个月也一样算 ——
    // 月份边界不再是断点。
    let yesterday = today() - chrono::Duration::days(1);
    let before_the_gap = today() - chrono::Duration::days(3);
    write_session(
        &dir,
        "s1",
        vec![
            (stamp(before_the_gap), turn("deepseek-flash")),
            (stamp(before_the_gap), spent(70)),
            (stamp(yesterday), turn("deepseek-flash")),
            (stamp(yesterday), spent(80)),
            (stamp(today()), turn("deepseek-flash")),
            (stamp(today()), spent(100)),
        ],
    );

    let stats = super::usage_stats(&dir).unwrap();
    assert_eq!(stats.days.len(), 3);
    assert_eq!(stats.current_streak_days, 2);
    assert_eq!(stats.longest_streak_days, 2);
}

/// 用户删掉一条对话之后，使用统计里的数据要**留下来** —— 统计读的是自己那份存档，不是会话文件本身。
#[test]
fn deleting_a_conversation_keeps_its_usage_in_the_stats() {
    let dir = sessions_dir("delete-keeps-usage");
    let path = write_session(
        &dir,
        "s1",
        vec![
            (stamp(today()), turn("deepseek-flash")),
            (stamp(today()), spent(900)),
        ],
    );

    // 第一次刷新：把这份数据算出来并存档。
    let before = super::usage_stats(&dir).unwrap();
    assert_eq!(before.total_tokens, 900);
    assert_eq!(before.days.iter().map(|day| day.tokens).sum::<i64>(), 900);

    // 删掉这个会话（就是"用户点了删除"）。
    std::fs::remove_file(&path).expect("delete");

    // 再刷新：数字一个都没少。
    let after = super::usage_stats(&dir).unwrap();
    assert_eq!(after.total_tokens, before.total_tokens);
    assert_eq!(
        after
            .days
            .iter()
            .map(|day| (day.date.clone(), day.tokens))
            .collect::<Vec<_>>(),
        before
            .days
            .iter()
            .map(|day| (day.date.clone(), day.tokens))
            .collect::<Vec<_>>()
    );
    assert_eq!(
        after
            .models
            .iter()
            .map(|model| (model.model_id.clone(), model.tokens))
            .collect::<Vec<_>>(),
        before
            .models
            .iter()
            .map(|model| (model.model_id.clone(), model.tokens))
            .collect::<Vec<_>>()
    );
    assert_eq!(after.current_streak_days, before.current_streak_days);
    assert_eq!(after.longest_streak_days, before.longest_streak_days);
}

#[test]
fn nothing_recorded_is_no_usage_at_all() {
    let stats = super::usage_stats(&sessions_dir("empty")).unwrap();
    assert_eq!(stats.total_tokens, 0);
    assert_eq!(stats.current_streak_days, 0);
    assert_eq!(stats.longest_streak_days, 0);
    assert!(stats.days.is_empty());
    assert!(stats.models.is_empty());
}

/// 用量存档里有一行坏数据时：统计仍然出得来，而且那一行会在这次刷新里被**修掉**（F6）。
///
/// 以前 `read` 用 `?` 直接上抛 → 每次读都失败；而修复存档的 `write` 只在读成功之后才走得到，
/// 于是这份存档再也回不来，必须人工删文件 —— 用户从此看不到任何用量。
#[test]
fn a_corrupt_archive_line_is_skipped_and_repaired() {
    let dir = sessions_dir("archive-corrupt");
    write_session(
        &dir,
        "s1",
        vec![
            (stamp(today()), turn("deepseek-flash")),
            (stamp(today()), spent(100)),
        ],
    );
    let first = super::usage_stats(&dir).expect("第一次统计");
    assert!(first.total_tokens > 0, "先得有一份非零的用量");

    // 往存档里塞一行坏数据。
    let archive = crate::usage_archive::archive_path(&dir);
    let mut text = std::fs::read_to_string(&archive).expect("存档应当已写出");
    text.push_str("{ this is not json }\n");
    std::fs::write(&archive, text).expect("写回");

    // 坏行不该让统计失败……
    let second = super::usage_stats(&dir).expect("坏行不该让统计失败");
    assert_eq!(first.total_tokens, second.total_tokens, "坏行不影响别的会话");
    // ……而且它会被这次刷新修掉。
    let repaired = std::fs::read_to_string(&archive).expect("存档还在");
    assert!(
        !repaired.contains("this is not json"),
        "坏行应当被这次刷新修掉：{repaired}"
    );
}
