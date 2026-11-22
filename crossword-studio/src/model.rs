//! Pack format of the game (assets/packs/*.json, formatVersion 1).
//! Unknown fields are kept as they are, so saving never loses data.

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Pack {
    pub format_version: i64,
    pub pack_id: String,
    pub language: String,
    pub normalization: String,
    pub content_version: i64,
    pub title: String,
    pub levels: Vec<Level>,
    #[serde(flatten)]
    pub extra: Map<String, Value>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct Level {
    pub id: String,
    pub rows: i64,
    pub cols: i64,
    #[serde(default = "one")]
    pub difficulty: i64,
    pub entries: Vec<Entry>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub keyword: Option<Keyword>,
    /// Studio only: "ok" or "rejected". The game ignores it.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub review: Option<String>,
    /// Studio only: true once a clue was edited here.
    #[serde(default, skip_serializing_if = "is_false")]
    pub edited: bool,
    #[serde(flatten)]
    pub extra: Map<String, Value>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Entry {
    pub id: i64,
    pub direction: String,
    pub clue_row: i64,
    pub clue_col: i64,
    pub answer: String,
    pub clue: String,
    #[serde(default)]
    pub source: String,
    #[serde(default = "candidate")]
    pub status: String,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub clues: Vec<String>,
    #[serde(flatten)]
    pub extra: Map<String, Value>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct Keyword {
    pub word: String,
    pub cells: Vec<Vec<i64>>,
    #[serde(default)]
    pub source: String,
    #[serde(flatten)]
    pub extra: Map<String, Value>,
}

fn one() -> i64 {
    1
}
fn candidate() -> String {
    "candidate".into()
}
fn is_false(b: &bool) -> bool {
    !*b
}

impl Entry {
    pub fn across(&self) -> bool {
        self.direction == "ACROSS"
    }
    /// Clue levels, hardest first (the game shows the first one in the grid).
    pub fn tiers(&self) -> Vec<String> {
        if self.clues.is_empty() {
            vec![self.clue.clone()]
        } else {
            self.clues.clone()
        }
    }
}

impl Level {
    pub fn is_ok(&self) -> bool {
        self.review.as_deref() == Some("ok")
    }
    pub fn is_rejected(&self) -> bool {
        self.review.as_deref() == Some("rejected")
    }
    /// The level as the game should get it: without studio-only fields.
    pub fn cleaned(&self) -> Level {
        let mut l = self.clone();
        l.review = None;
        l.edited = false;
        l
    }
}

impl Pack {
    pub fn parse(text: &str) -> Result<Pack, String> {
        let text = text.trim_start_matches('\u{feff}');
        serde_json::from_str(text).map_err(|e| format!("Файл не похож на пак уровней: {e}"))
    }
    pub fn to_json(&self) -> String {
        let mut s = serde_json::to_string_pretty(self).unwrap_or_default();
        s.push('\n');
        s
    }
}

pub const ALPHABET: &str = "ABCÇDEƏFGĞHXIİJKQLMNOÖPRSŞTUÜVYZ";

/// Upper case with Azerbaijani rules (i → İ, ı → I), like AzText in the game.
pub fn az_upper(s: &str) -> String {
    s.chars()
        .flat_map(|c| match c {
            'i' => vec!['İ'],
            'ı' => vec!['I'],
            c => c.to_uppercase().collect(),
        })
        .collect()
}

/// Lower case with Azerbaijani rules (İ → i, I → ı).
pub fn az_lower(s: &str) -> String {
    s.chars()
        .flat_map(|c| match c {
            'İ' => vec!['i'],
            'I' => vec!['ı'],
            c => c.to_lowercase().collect(),
        })
        .collect()
}

/// The letters of an answer, one per cell, or None if a character is not an Azerbaijani letter.
pub fn letters(answer: &str) -> Option<Vec<char>> {
    let up = az_upper(answer);
    let v: Vec<char> = up.chars().collect();
    if v.iter().all(|c| ALPHABET.contains(*c)) {
        Some(v)
    } else {
        None
    }
}
