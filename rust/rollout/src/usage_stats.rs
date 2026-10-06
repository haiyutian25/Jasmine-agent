use crate::SESSIONS_SUBDIR;
use crate::list::read_timed_items;
use crate::model::RolloutItem;
use jasmine_protocol::protocol::AppUsageStats;
use jasmine_protocol::protocol::ModelUsage;
use jasmine_protocol::protocol::UsageDay;
use std::collections::BTreeMap;
use std::collections::HashMap;
use std::collections::HashSet;
use std::path::Path;

/// 使用统计：全部时间花了多少、连着花了几天、哪天花过、花在哪些模型上。
///
/// **数据来源是统计自己的存档**（见 [`crate::usage_archive`]），而不是每次现扫会话文件：
///
/// 1. 把现存会话文件读一遍，算出每个会话自己的用量（按天的 token、按模型的 token、以及它自己的
///    累计），按会话 id **覆盖**进存档；
/// 2. 把存档写回磁盘 —— 这一步就是"拿回来数据之后直接存档"；
/// 3. 汇总只认存档。
///
/// 于是会话那边怎么变都不会带坏统计：用户删掉一条对话时，它的快照还留在存档里，累计 / 日历 / 模型
/// 用量一点不少。会话文件还在的那些，每次刷新都用文件里的新值覆盖自己的旧快照（会话只会变多）。
///
/// 一个会话的花费是它每两条用量记录之间的差额（记录里带的是 running total）；差额按记录自己的时间戳
/// 落到日历日，按当时那一轮的模型归到模型上。
pub fn usage_stats(sessions_dir: &Path) -> std::io::Result<AppUsageStats> {
    let today = chrono::Local::now().date_naive();

    // 1) 现存会话 → 快照，覆盖进存档。
    let mut snapshots = crate::usage_archive::read(sessions_dir)?;
    let mut paths = Vec::new();
    crate::list::collect_rollouts(&sessions_dir.join(SESSIONS_SUBDIR), &mut paths)?;
    for path in paths {
        let Some(snapshot) = snapshot_of(&path) else {
            continue;
        };
        snapshots.insert(snapshot.session_id.clone(), snapshot);
    }

    // 2) 存档：会话被删掉之后，它的数字就靠这一份留下来。
    crate::usage_archive::write(sessions_dir, &snapshots)?;

    // 3) 汇总：存档里的每一个会话快照相加。
    let mut by_day: BTreeMap<String, i64> = BTreeMap::new();
    let mut by_model: HashMap<String, i64> = HashMap::new();
    let mut lifetime = 0i64;
    for snapshot in snapshots.values() {
        for (day, tokens) in &snapshot.days {
            *by_day.entry(day.clone()).or_default() += tokens;
        }
        for (model_id, tokens) in &snapshot.models {
            *by_model.entry(model_id.clone()).or_default() += tokens;
        }
        // 这个会话自己的累计，就是它最后一条用量记录里的 running total。
        lifetime += snapshot.total;
    }

    let active: HashSet<&str> = by_day.keys().map(String::as_str).collect();
    let mut models = by_model
        .into_iter()
        .map(|(model_id, tokens)| ModelUsage { model_id, tokens })
        .collect::<Vec<_>>();
    models.sort_by(|left, right| {
        right
            .tokens
            .cmp(&left.tokens)
            .then_with(|| left.model_id.cmp(&right.model_id))
    });

    Ok(AppUsageStats {
        total_tokens: lifetime,
        current_streak_days: current_streak(today, &active),
        longest_streak_days: longest_streak(&active),
        days: by_day
            .into_iter()
            .map(|(date, tokens)| UsageDay { date, tokens })
            .collect(),
        models,
    })
}

/// 一个会话文件里的全部用量，折成一份快照；读不出文件的返回 `None`。
fn snapshot_of(path: &Path) -> Option<crate::usage_archive::SessionUsageSnapshot> {
    let lines = read_timed_items(path).ok()?;
    let mut snapshot = crate::usage_archive::SessionUsageSnapshot::default();
    // 哪一轮跑的哪个模型，直到下一轮说别的：没有任何 `TurnStarted` 时退回会话元信息。
    let mut model_id = String::new();
    // 这个会话上次被看到时花了多少：记录里是 running total，所以一步的花费是差额。
    let mut spent = 0i64;

    for (timestamp, item) in lines {
        match item {
            // 压缩记录不是"花费"，不进用量统计。
            RolloutItem::Compacted { .. } => {}
            RolloutItem::SessionMeta(meta) => {
                snapshot.session_id = meta.session_id;
                model_id = meta.model_id;
            }
            RolloutItem::TurnStarted {
                model_id: turn_model,
                ..
            } => {
                model_id = turn_model;
            }
            RolloutItem::TokenUsageRecord { info, .. } => {
                let running = info.total_token_usage.total_tokens;
                let step = running - spent;
                spent = running;
                if step <= 0 {
                    continue;
                }
                *snapshot.days.entry(day_of(&timestamp)).or_default() += step;
                *snapshot.models.entry(model_id.clone()).or_default() += step;
            }
            RolloutItem::ResponseItem(_)
            | RolloutItem::TurnComplete { .. }
            | RolloutItem::ContextWindow { .. }
            | RolloutItem::ReasoningEffort { .. }
            | RolloutItem::TurnAborted { .. }
            | RolloutItem::InterruptedReply { .. }
            | RolloutItem::InterruptedReasoning { .. } => {}
        }
    }
    snapshot.total = spent;
    Some(snapshot)
}

/// The days in a row that spent something, counting back from today.
///
/// A day that spent nothing yet is not the end of a run: with today still empty the count starts
/// at yesterday, so the streak survives the morning. The count stops at the first day that spent
/// nothing, however far back that is.
fn current_streak(today: chrono::NaiveDate, active: &HashSet<&str>) -> u32 {
    let mut cursor = if active.contains(date_text(today).as_str()) {
        today
    } else {
        today - chrono::Duration::days(1)
    };
    let mut days = 0u32;
    while active.contains(date_text(cursor).as_str()) {
        days += 1;
        cursor -= chrono::Duration::days(1);
    }
    days
}

/// The longest run of days in a row that spent something, over the whole history.
fn longest_streak(active: &HashSet<&str>) -> u32 {
    let mut dates = active
        .iter()
        .filter_map(|date| chrono::NaiveDate::parse_from_str(date, "%Y-%m-%d").ok())
        .collect::<Vec<_>>();
    dates.sort_unstable();

    let mut longest = 0u32;
    let mut run = 0u32;
    let mut previous: Option<chrono::NaiveDate> = None;
    for date in dates {
        run = match previous {
            Some(previous) if date == previous + chrono::Duration::days(1) => run + 1,
            _ => 1,
        };
        longest = longest.max(run);
        previous = Some(date);
    }
    longest
}

/// The calendar day a record belongs to, in the offset the record was stamped with.
///
/// A stamp that cannot be parsed still starts with its own date (`YYYY-MM-DD`), which is the part
/// that matters here.
fn day_of(timestamp: &str) -> String {
    chrono::DateTime::parse_from_rfc3339(timestamp)
        .map(|moment| date_text(moment.date_naive()))
        .unwrap_or_else(|_| timestamp.chars().take(10).collect())
}

fn date_text(date: chrono::NaiveDate) -> String {
    date.format("%Y-%m-%d").to_string()
}

#[cfg(test)]
#[path = "usage_stats_tests.rs"]
mod tests;
