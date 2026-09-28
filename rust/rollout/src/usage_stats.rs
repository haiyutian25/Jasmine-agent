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
/// **Nothing is cut off by the calendar.** The day list, the streaks and the model breakdown reach
/// back as far as the records go, so the figures cover the whole history — nothing is deleted off
/// the disk, and no month is dropped on the way in. The lifetime total is each conversation's own
/// running total, which is that same span read from the other end.
pub fn usage_stats(sessions_dir: &Path) -> std::io::Result<AppUsageStats> {
    let today = chrono::Local::now().date_naive();

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
                    // Every day the records reach back to is counted; the running total above is
                    // what the lifetime figure is built from, and it moved with the same step.
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
