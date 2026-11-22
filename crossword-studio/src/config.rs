//! Settings, kept in the user's profile folder (%APPDATA%\AcarSozStudio\settings.json).

use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Serialize, Deserialize, Clone)]
#[serde(default)]
pub struct Config {
    pub token: String,
    pub repo: String,
    pub branch: String,
    pub workflow: String,
    pub python: String,
    pub last_pack: String,
    pub last_count: u32,
    pub font_scale: f32,
}

impl Default for Config {
    fn default() -> Self {
        Config {
            token: String::new(),
            repo: "cabbari826-jpg/AAIdeas".into(),
            branch: "claude/project-thread-50gxx8".into(),
            workflow: "crossword-levels.yml".into(),
            python: "python".into(),
            last_pack: "az-starter".into(),
            last_count: 10,
            font_scale: 1.2,
        }
    }
}

pub fn home() -> PathBuf {
    let base = dirs::config_dir().unwrap_or_else(|| PathBuf::from("."));
    let d = base.join("AcarSozStudio");
    let _ = std::fs::create_dir_all(&d);
    d
}

pub fn drafts_dir() -> PathBuf {
    let d = home().join("drafts");
    let _ = std::fs::create_dir_all(&d);
    d
}

/// Local copy of the repository for running the generator on this computer.
pub fn repo_dir() -> PathBuf {
    home().join("repo")
}

impl Config {
    pub fn load() -> Config {
        std::fs::read_to_string(home().join("settings.json"))
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_default()
    }
    pub fn save(&self) {
        if let Ok(s) = serde_json::to_string_pretty(self) {
            let _ = std::fs::write(home().join("settings.json"), s);
        }
    }
}

pub const PACKS_DIR: &str = "crossword-app/app/src/main/assets/packs";
pub const MINI_DIR: &str = "crossword-app/app/src/main/assets/mini";
pub const WORDS_DIR: &str = "crossword-app/content/words";
/// Downloadable packs are also kept in the repository, so the generator can skip their words.
pub const PUBLISHED_DIR: &str = "crossword-app/content/downloadable";
pub const RELEASE_TAG: &str = "packs";

/// Where a pack lives in the game's assets.
pub fn pack_path(pack_id: &str) -> String {
    if pack_id == "az-mini" {
        format!("{MINI_DIR}/{pack_id}.json")
    } else {
        format!("{PACKS_DIR}/{pack_id}.json")
    }
}
