use crate::SESSIONS_SUBDIR;
use crate::model::RolloutItem;
use crate::model::RolloutLine;
use crate::model::SessionMeta;
use crate::rollout_file_name::RolloutFileName;
use jasmine_protocol::models::ResponseItem;
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

/// Everything the conversation recorded, in order.
pub fn read_items(path: &Path) -> std::io::Result<Vec<RolloutItem>> {
    Ok(read_lines(path)?
        .into_iter()
        .map(|line| line.item)
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

/// A line that does not parse is a line that was cut short: the transcript keeps what it has.
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

fn collect_rollouts(dir: &Path, paths: &mut Vec<PathBuf>) -> std::io::Result<()> {
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
