use crate::SESSIONS_SUBDIR;
use crate::model::RolloutItem;
use crate::model::RolloutLine;
use crate::model::SessionMeta;
use crate::rollout_file_name::RolloutFileName;
use chrono::Local;
use std::fs::File;
use std::fs::OpenOptions;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;

/// Appends one conversation's items to its rollout file.
///
/// The file is the transcript: an item is written as it settles, so a conversation that stops
/// half-way is still readable up to its last recorded line.
pub struct RolloutRecorder {
    path: PathBuf,
    file: File,
}

impl RolloutRecorder {
    /// Creates a conversation's rollout under the day it started, and writes its meta first.
    pub fn create(sessions_dir: &Path, meta: &SessionMeta) -> std::io::Result<Self> {
        let started = Local::now().naive_local();
        let dir = sessions_dir
            .join(SESSIONS_SUBDIR)
            .join(started.format("%Y").to_string())
            .join(started.format("%m").to_string())
            .join(started.format("%d").to_string());
        std::fs::create_dir_all(&dir)?;
        let path = dir.join(RolloutFileName::new(started, meta.session_id.clone()).render());
        let mut recorder = Self::open(path)?;
        recorder.record_meta(meta)?;
        Ok(recorder)
    }

    /// Opens an existing rollout for appending what comes next.
    pub fn open(path: PathBuf) -> std::io::Result<Self> {
        let file = OpenOptions::new().create(true).append(true).open(&path)?;
        Ok(Self { path, file })
    }

    pub fn rollout_path(&self) -> &Path {
        self.path.as_path()
    }

    /// Appends what one turn produced, in the order it happened.
    pub fn record_items(&mut self, items: &[RolloutItem]) -> std::io::Result<()> {
        for item in items {
            self.write_line(item)?;
        }
        Ok(())
    }

    /// Appends the session's own facts again, so a rename is visible without rewriting the file.
    pub fn record_meta(&mut self, meta: &SessionMeta) -> std::io::Result<()> {
        self.write_line(&RolloutItem::SessionMeta(meta.clone()))
    }

    fn write_line(&mut self, item: &RolloutItem) -> std::io::Result<()> {
        let line = RolloutLine::now(item.clone());
        let encoded = serde_json::to_string(&line).map_err(std::io::Error::other)?;
        self.file.write_all(encoded.as_bytes())?;
        self.file.write_all(b"\n")?;
        self.file.flush()
    }
}
