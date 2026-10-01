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
        match read_session(&path) {
            Some((meta, updated_at)) => entries.push(SessionEntry {
                path,
                meta,
                updated_at,
            }),
            // 一条会话读不出来**不带走整张列表**（那会让用户以为历史全没了），但也不能一声不吭：
            // 跳过要留下痕迹，否则"文件坏了"与"这条会话不存在"在外部完全分不出来。
            None => tracing::warn!("跳过读不出来的会话文件：{}", path.display()),
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

/// 这个会话当前的推理档位：最后一条记录为准（空串 = 未设置）。
///
/// 记录是追加式的，所以一条都没有时返回 `None` —— 那是"这个会话还没记过档位"，由调用方决定要不要写它
/// 的第一条（新建会话时写模型配置里那个）。这里不做任何回退。
pub fn reasoning_effort_value(path: &Path) -> Option<String> {
    read_lines(path)
        .ok()?
        .iter()
        .filter_map(|line| match &line.item {
            RolloutItem::ReasoningEffort { value } => Some(value.clone()),
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
            | RolloutItem::ReasoningEffort { .. }
            | RolloutItem::TokenUsageRecord { .. }
            | RolloutItem::InterruptedReply { .. }
            | RolloutItem::InterruptedReasoning { .. } => {}
        }
    }
    None
}

/// Everything the turn still open at the end of the file put down, in the order it happened.
///
/// The stopped turn's own boundary sits *after* its items, so it is walked past; the walk stops at the
/// boundary that opens this turn (or at the one that closed an earlier turn), because an earlier turn's
/// content is not carried again — it was carried when it happened. Empty when no turn is open.
///
/// The last item of a stopped turn is its [`RolloutItem::InterruptedReply`], which is where the answer
/// had got to.
pub fn interrupted_turn_items(path: &Path) -> Vec<RolloutItem> {
    let Ok(lines) = read_lines(path) else {
        return Vec::new();
    };
    let mut items = Vec::new();
    for line in lines.iter().rev() {
        match &line.item {
            // 中断那一轮的收尾写在它的内容之后，属于同一轮，继续往回走。
            RolloutItem::TurnAborted { .. } => {}
            // 走到这一轮的开头（或上一轮的收尾）就到头了：更早那一轮的内容不再带一次。
            RolloutItem::TurnStarted { .. } => break,
            RolloutItem::TurnComplete { .. } => return Vec::new(),
            RolloutItem::ResponseItem(_) | RolloutItem::InterruptedReply { .. } => {
                items.push(line.item.clone());
            }
            // 会话元信息、窗口、档位、用量这些不是这一轮"看得到的内容"；被停那一轮的思考另有自己
            // 的记录（它只展示，不跟着新消息走）。
            RolloutItem::SessionMeta(_)
            | RolloutItem::ContextWindow { .. }
            | RolloutItem::ReasoningEffort { .. }
            | RolloutItem::TokenUsageRecord { .. }
            | RolloutItem::InterruptedReasoning { .. } => {}
        }
    }
    items.reverse();
    items
}

fn read_lines(path: &Path) -> std::io::Result<Vec<RolloutLine>> {
    let file = File::open(path)?;
    let mut reader = BufReader::new(file);
    let mut lines = Vec::new();
    // 最后一个读得出来的行在文件里的结束偏移（字节，含行尾）。截尾巴就截到这里。
    let mut good_end: u64 = 0;
    let mut offset: u64 = 0;
    // 见过坏行之后又见到能读的行 ⇒ 坏行在文件中间。那种不能靠截尾巴修（会把后面的好行一起切掉）。
    let mut corrupt_seen = false;
    let mut corrupt_is_only_tail = true;
    let mut raw = String::new();
    loop {
        raw.clear();
        let read = reader.read_line(&mut raw)?;
        if read == 0 {
            break;
        }
        offset += read as u64;
        let text = raw.trim_end_matches(['\n', '\r']);
        if text.trim().is_empty() {
            good_end = offset;
            continue;
        }
        match serde_json::from_str::<RolloutLine>(text) {
            Ok(parsed) => {
                lines.push(parsed);
                if corrupt_seen {
                    corrupt_is_only_tail = false;
                }
                good_end = offset;
            }
            // 坏一行**不扔整个文件**（后面的行还是用户的记录），但要留痕：否则转写会悄悄少几行，
            // 而"读坏了"与"本来就没有"在外面看不出区别。
            Err(error) => {
                corrupt_seen = true;
                tracing::warn!(
                    "跳过 {} 里读不出来的一行（第 {} 字节处）：{error}",
                    path.display(),
                    offset,
                );
            }
        }
    }
    // 尾巴坏了（典型成因：写一半被杀掉）就把它截掉 —— 文件重新变成"每行都读得出来"，
    // 否则这条坏尾巴会在之后每一次读里重复出现，永远修不好。
    if corrupt_seen && corrupt_is_only_tail && !lines.is_empty() {
        truncate_to(path, good_end);
    }
    Ok(lines)
}

/// 把文件截到 `good_end`（保留它之前的所有内容）。
///
/// 用 `set_len` 而不是重写整个文件：正在追加的 `RolloutRecorder` 握的还是这个 inode，
/// 换成新文件会让它后面的写入落进一个已经没人看得见的旧 inode。
///
/// 修不动只记一句警告、不上抛 —— 读转写不该因为"修不好"而失败。
fn truncate_to(path: &Path, good_end: u64) {
    if let Err(error) = fs::OpenOptions::new()
        .write(true)
        .open(path)
        .and_then(|file| file.set_len(good_end))
    {
        tracing::warn!("修不掉 {} 的坏尾巴：{error}", path.display());
    } else {
        tracing::warn!(
            "{} 末尾有写坏的一行，已截到 {good_end} 字节",
            path.display()
        );
    }
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
