use crate::SESSIONS_SUBDIR;
use crate::model::RolloutItem;
use crate::model::RolloutLine;
use crate::model::SessionMeta;
use crate::rollout_file_name::RolloutFileName;
use jasmine_protocol::models::ResponseItem;
use jasmine_protocol::protocol::ContextUsageBreakdownItem;
use jasmine_protocol::protocol::TokenUsageInfo;
use std::fs;
use std::fs::File;
use std::io::BufRead;
use std::io::BufReader;
use std::path::Path;
use std::path::PathBuf;

/// One conversation as a list sees it: where it lives and what it says about itself.
#[derive(Debug, Clone)]
pub struct SessionEntry {
    pub path: PathBuf,
    pub meta: SessionMeta,
    /// When the conversation was last written to, so a list can order by activity.
    pub updated_at: String,
}

/// Every conversation under the sessions tree, the most recently written first.
pub fn list_sessions(sessions_dir: &Path) -> std::io::Result<Vec<SessionEntry>> {
    let mut paths = Vec::new();
    collect_rollouts(&sessions_dir.join(SESSIONS_SUBDIR), &mut paths)?;

    let mut entries = Vec::new();
    for path in paths {
        if let Some((meta, updated_at)) = read_session(&path) {
            entries.push(SessionEntry {
                path,
                meta,
                updated_at,
            });
        }
    }
    entries.sort_by(|left, right| right.updated_at.cmp(&left.updated_at));

    Ok(entries)
}

/// Where one session's rollout lives.
pub fn find_session_path(
    sessions_dir: &Path,
    session_id: &str,
) -> std::io::Result<Option<PathBuf>> {
    let mut paths = Vec::new();
    collect_rollouts(&sessions_dir.join(SESSIONS_SUBDIR), &mut paths)?;

    Ok(paths.into_iter().find(|path| {
        path.file_name()
            .and_then(|name| name.to_str())
            .and_then(RolloutFileName::parse)
            .is_some_and(|name| name.session_id() == session_id)
    }))
}

/// Removes a conversation's file, and with it the conversation.
pub fn delete_session(sessions_dir: &Path, session_id: &str) -> std::io::Result<()> {
    let Some(path) = find_session_path(sessions_dir, session_id)? else {
        return Ok(());
    };
    fs::remove_file(path)
}

/// What a conversation says about itself, as of its last meta line, and when it was last written.
///
/// A rename appends a meta line rather than rewriting the file, so the last one wins.
pub fn read_session(path: &Path) -> Option<(SessionMeta, String)> {
    let lines = read_lines(path).ok()?;
    let meta = lines
        .iter()
        .filter_map(|line| match &line.item {
            RolloutItem::SessionMeta(meta) => Some(meta.clone()),
            _ => None,
        })
        .next_back()?;
    let updated_at = lines.last()?.timestamp.clone();

    Some((meta, updated_at))
}

/// Everything the conversation recorded, in order, each with the moment it was written.
///
/// The stamp is what a platform shows next to a line, so the reader keeps it instead of dropping
/// it with the line's wrapper.
pub fn read_timed_items(path: &Path) -> std::io::Result<Vec<(String, RolloutItem)>> {
    Ok(read_lines(path)?
        .into_iter()
        .map(|line| (line.timestamp, line.item))
        .collect())
}

/// The model-visible items of one conversation, in order.
pub fn read_response_items(path: &Path) -> std::io::Result<Vec<ResponseItem>> {
    Ok(read_lines(path)?
        .into_iter()
        .filter_map(|line| match line.item {
            RolloutItem::ResponseItem(item) => Some(item),
            _ => None,
        })
        .collect())
}

/// The context window the conversation was last set to, if it was ever set at all.
pub fn context_window_tokens(path: &Path) -> Option<u64> {
    read_lines(path)
        .ok()?
        .iter()
        .filter_map(|line| match &line.item {
            RolloutItem::ContextWindow { tokens } => Some(*tokens),
            _ => None,
        })
        .next_back()
}

/// What the conversation last reported costing, if it ever did.
///
/// A record is written per finished turn, so the last one in the file is the current picture — the
/// same one a reopened conversation shows.
pub fn token_usage(path: &Path) -> Option<(TokenUsageInfo, Vec<ContextUsageBreakdownItem>)> {
    read_lines(path)
        .ok()?
        .iter()
        .filter_map(|line| match &line.item {
            RolloutItem::TokenUsageRecord { info, breakdown } => {
                Some((info.clone(), breakdown.clone()))
            }
            _ => None,
        })
        .next_back()
}

/// The turn one conversation left unfinished, if its last boundary left one.
///
/// A finished turn writes its own closing boundary. A turn the platform interrupted was written
/// down as interrupted, and a turn the process never closed has no boundary at all — both are the
/// part a platform offers to pick up again, so both count as unfinished.
pub fn interrupted_turn(path: &Path) -> Option<String> {
    let lines = read_lines(path).ok()?;
    for line in lines.iter().rev() {
        match &line.item {
            RolloutItem::TurnStarted { turn_id, .. } | RolloutItem::TurnAborted { turn_id, .. } => {
                return Some(turn_id.clone());
            }
            RolloutItem::TurnComplete { .. } => return None,
            RolloutItem::SessionMeta(_)
            | RolloutItem::ResponseItem(_)
            | RolloutItem::ContextWindow { .. }
            | RolloutItem::TokenUsageRecord { .. } => {}
        }
    }
    None
}

fn read_lines(path: &Path) -> std::io::Result<Vec<RolloutLine>> {
    let file = File::open(path)?;
    let reader = BufReader::new(file);
    let mut lines = Vec::new();
    for line in reader.lines() {
        let line = line?;
        if line.trim().is_empty() {
            continue;
        }
        if let Ok(parsed) = serde_json::from_str::<RolloutLine>(&line) {
            lines.push(parsed);
        }
    }
    Ok(lines)
}

#[cfg(test)]
#[path = "list_tests.rs"]
mod tests;

/// Every rollout file under the sessions tree, in whatever order the filesystem hands them over.
pub(crate) fn collect_rollouts(dir: &Path, paths: &mut Vec<PathBuf>) -> std::io::Result<()> {
    let entries = match fs::read_dir(dir) {
        Ok(entries) => entries,
        // No sessions yet is not an error: the first conversation creates the tree.
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error),
    };

    for entry in entries {
        let entry = entry?;
        let path = entry.path();
        if path.is_dir() {
            collect_rollouts(&path, paths)?;
        } else if path.extension().is_some_and(|ext| ext == "jsonl") {
            paths.push(path);
        }
    }
    Ok(())
}
