//! 使用统计自己的存档。
//!
//! 统计**不再只看会话文件**：每次刷新（拉数据）时，把每个会话算出来的用量按会话 id 存一份快照进这里，
//! 之后汇总只认这份存档。这样会话那边怎么变都不影响统计 —— 用户删掉一条对话时，它的快照还留在存档
//! 里，使用统计里的累计、日历、模型用量都照旧。
//!
//! 一份快照就是"这个会话到目前为止的全部数字"：按天的 token、按模型的 token、以及它自己的累计。会话
//! 文件还在时每次刷新都用文件里的新值覆盖它（会话只会变多），文件没了就保留最后一次的。

use serde::Deserialize;
use serde::Serialize;
use std::collections::BTreeMap;
use std::path::Path;
use std::path::PathBuf;

/// 存档文件名，放在会话目录旁边（与 `sessions/` 平级）。
const USAGE_ARCHIVE_FILE: &str = "usage_archive.jsonl";

/// 一个会话的用量快照。
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct SessionUsageSnapshot {
    pub session_id: String,
    /// 日期（`YYYY-MM-DD`）→ 当天花掉的 token。
    pub days: BTreeMap<String, i64>,
    /// 模型 → 花掉的 token。
    pub models: BTreeMap<String, i64>,
    /// 这个会话自己的累计 —— 它最后一条用量记录里的 running total。
    pub total: i64,
}

fn archive_path(sessions_dir: &Path) -> PathBuf {
    sessions_dir.join(USAGE_ARCHIVE_FILE)
}

/// 读回全部快照，键是会话 id。
pub fn read(sessions_dir: &Path) -> std::io::Result<BTreeMap<String, SessionUsageSnapshot>> {
    let path = archive_path(sessions_dir);
    let text = match std::fs::read_to_string(&path) {
        Ok(text) => text,
        // 还没存过档（第一次打开使用统计）就是空存档；别的错误照实上报，不吞。
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(BTreeMap::new()),
        Err(error) => return Err(error),
    };

    let mut snapshots = BTreeMap::new();
    for line in text.lines().filter(|line| !line.trim().is_empty()) {
        let snapshot: SessionUsageSnapshot = serde_json::from_str(line)?;
        snapshots.insert(snapshot.session_id.clone(), snapshot);
    }
    Ok(snapshots)
}

/// 覆盖写回存档：先写临时文件再改名，写到一半被打断也不会毁掉旧存档。
pub fn write(
    sessions_dir: &Path,
    snapshots: &BTreeMap<String, SessionUsageSnapshot>,
) -> std::io::Result<()> {
    let path = archive_path(sessions_dir);
    if snapshots.is_empty() {
        // 一份都没有时不留空文件（空文件与"没存过档"等价）。
        let _ = std::fs::remove_file(&path);
        return Ok(());
    }

    let mut text = String::new();
    for snapshot in snapshots.values() {
        text.push_str(&serde_json::to_string(snapshot)?);
        text.push('\n');
    }
    let temporary = path.with_extension("jsonl.tmp");
    std::fs::write(&temporary, text)?;
    std::fs::rename(&temporary, &path)
}
