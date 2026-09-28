use crate::SESSIONS_SUBDIR;
use crate::list::read_timed_items;
use crate::model::RolloutItem;
use crate::model::RolloutLine;
use chrono::Datelike;
use jasmine_protocol::protocol::AppUsageStats;
use jasmine_protocol::protocol::ModelUsage;
use jasmine_protocol::protocol::UsageDay;
use std::collections::BTreeMap;
use std::collections::HashMap;
use std::collections::HashSet;
use std::path::Path;

/// The app's own usage for **the current month**, read back out of the conversations themselves.
///
/// Every conversation file records, per finished turn, what that conversation has cost **so far**,
/// next to the moment it was written and the model the turn ran on. The spend of one turn is
/// therefore the step between two records, and this walks every file to add those steps up:
///
/// - by the calendar day the record was written on, taken in the offset the record itself carries
///   (each line is stamped in the local time of the machine that wrote it);
/// - under the model of the turn that produced it, taken from the `TurnStarted` the turn opened
///   with, so a conversation that switched models still places each step correctly.
///
/// A conversation that never reported a cost contributes nothing, and the last record of a
/// conversation is not itself a cost — the steps are what is spent, which is why the running total
/// is differenced rather than summed.
///
/// **Everything except the total covers this month only.** The month turning over wipes last
/// month: the usage records dated before the first of the current month are deleted off the disk
/// first, so the day list, the streaks and the model breakdown never reach further back than one
/// month. The lifetime total is the exception — it is each conversation's own running total, which
/// the kept baseline still carries, so it keeps counting across the months. The rest of each file
/// — the transcript — is left alone.
pub fn usage_stats(sessions_dir: &Path) -> std::io::Result<AppUsageStats> {
    let today = chrono::Local::now().date_naive();
    let first_of_month = date_text(today.with_day(1).unwrap_or(today));

    // 上个月的用量记录先清掉：统计只算本月，磁盘上也不留上个月的（转写本身一行不动）。
    prune_usage_before(sessions_dir, &first_of_month)?;

    let mut paths = Vec::new();
    crate::list::collect_rollouts(&sessions_dir.join(SESSIONS_SUBDIR), &mut paths)?;

    let mut by_day: BTreeMap<String, i64> = BTreeMap::new();
    let mut by_model: HashMap<String, i64> = HashMap::new();
    // Everything ever spent. Read from each conversation's own running total rather than by adding
    // the steps up, so the figure survives the pruning above: the kept baseline still carries how
    // much that conversation had spent before the cut.
    let mut lifetime = 0i64;

    for path in paths {
        let Ok(lines) = read_timed_items(&path) else {
            continue;
        };
        // Which model the conversation ran on, until a turn says otherwise: a step recorded before
        // any `TurnStarted` (there is none today, but the shape allows it) falls back to the meta.
        let mut model_id = String::new();
        // What this conversation had cost when it was last seen: the records carry a running total,
        // so one turn's own spend is the difference.
        let mut spent = 0i64;

        for (timestamp, item) in lines {
            match item {
                RolloutItem::SessionMeta(meta) => {
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
                    let day = day_of(&timestamp);
                    // A month ago is as far back as the month's own figures go; the conversation's
                    // running total — which is what the lifetime figure is built from — already
                    // moved with the step above.
                    if day < first_of_month {
                        continue;
                    }
                    *by_day.entry(day).or_default() += step;
                    *by_model.entry(model_id.clone()).or_default() += step;
                }
                RolloutItem::ResponseItem(_)
                | RolloutItem::TurnComplete { .. }
                | RolloutItem::ContextWindow { .. }
                | RolloutItem::TurnAborted { .. } => {}
            }
        }
        // Where this conversation's running total ended up: its whole history, whatever happened.
        lifetime += spent;
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

/// Drops the usage records of months gone by from every conversation file.
///
/// A usage record is metadata: it only feeds the usage figures, so removing it changes nothing
/// about the transcript, resuming a conversation or continuing an interrupted turn. Everything
/// that stays is written back exactly as it was — the file is rewritten line by line (a temporary
/// file, then a rename, so an interrupted write cannot damage it).
///
/// **One record per conversation is kept: the last one from before the cut.** The records carry a
/// running total rather than one turn's spend, so the first surviving record can only be read as a
/// difference if something older is still there to subtract — dropping that line too would make
/// this month's first turn swallow everything the conversation ever spent. The kept line is outside
/// the month, so it never counts towards any figure.
///
/// Returns how many records were removed, and does nothing to a file that has none to remove.
pub fn prune_usage_before(sessions_dir: &Path, keep_from: &str) -> std::io::Result<usize> {
    let mut paths = Vec::new();
    crate::list::collect_rollouts(&sessions_dir.join(SESSIONS_SUBDIR), &mut paths)?;

    let mut removed = 0usize;
    for path in paths {
        let Ok(text) = std::fs::read_to_string(&path) else {
            continue;
        };
        let lines = text.lines().collect::<Vec<_>>();
        let mut stale = vec![false; lines.len()];
        // The newest record from before the cut: it stays, as the baseline the rest is read against.
        let mut baseline = None;
        for (index, line) in lines.iter().enumerate() {
            let is_stale = serde_json::from_str::<RolloutLine>(line)
                .ok()
                .is_some_and(|parsed| {
                    matches!(parsed.item, RolloutItem::TokenUsageRecord { .. })
                        && day_of(&parsed.timestamp).as_str() < keep_from
                });
            if is_stale {
                stale[index] = true;
                baseline = Some(index);
            }
        }
        if baseline.is_none() {
            continue;
        }

        let mut kept = String::with_capacity(text.len());
        let mut dropped_here = 0usize;
        for (index, line) in lines.iter().enumerate() {
            if stale[index] && Some(index) != baseline {
                dropped_here += 1;
                continue;
            }
            kept.push_str(line);
            kept.push('\n');
        }
        if dropped_here == 0 {
            continue;
        }
        let temporary = path.with_extension("jsonl.tmp");
        std::fs::write(&temporary, kept)?;
        std::fs::rename(&temporary, &path)?;
        removed += dropped_here;
    }
    Ok(removed)
}

/// The days in a row that spent something, counting back from today.
///
/// A day that spent nothing yet is not the end of a run: with today still empty the count starts
/// at yesterday, so the streak survives the morning. The count stops at the first day this month
/// that spent nothing — and since nothing older than this month is in the set, it also stops at
/// the month's own edge.
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

/// The longest run of days in a row that spent something, within this month.
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
