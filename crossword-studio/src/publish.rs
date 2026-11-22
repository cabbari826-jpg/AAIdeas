//! What "publish" does to a pack: pure functions, so they can be tested.

use crate::model::{Level, Pack};

#[derive(Default, Debug, Clone)]
pub struct Plan {
    pub added: Vec<String>,
    pub updated: Vec<String>,
    pub removed: Vec<String>,
    pub skipped_unreviewed: usize,
    pub skipped_rejected: usize,
    pub renamed: Vec<(String, String)>,
}

impl Plan {
    pub fn changes(&self) -> usize {
        self.added.len() + self.updated.len() + self.removed.len()
    }
    pub fn describe(&self) -> String {
        let mut v = vec![];
        if !self.added.is_empty() {
            v.push(format!("новых уровней: {}", self.added.len()));
        }
        if !self.updated.is_empty() {
            v.push(format!("исправленных: {}", self.updated.len()));
        }
        if !self.removed.is_empty() {
            v.push(format!("убрать из игры: {} ({})", self.removed.len(), self.removed.join(", ")));
        }
        if self.skipped_unreviewed > 0 {
            v.push(format!("не просмотрено (не выйдут): {}", self.skipped_unreviewed));
        }
        if self.skipped_rejected > 0 {
            v.push(format!("отклонено (не выйдут): {}", self.skipped_rejected));
        }
        if v.is_empty() {
            "нечего выпускать: отметьте уровни «Хорошо»".into()
        } else {
            v.join(" · ")
        }
    }
}

/// Same words in the same grid: the level the game already has (clues may differ).
pub fn same_level(a: &Level, b: &Level) -> bool {
    let key = |l: &Level| {
        let mut v: Vec<(String, i64, i64, String)> =
            l.entries.iter().map(|e| (e.answer.clone(), e.clue_row, e.clue_col, e.direction.clone())).collect();
        v.sort();
        (l.rows, l.cols, v)
    };
    key(a) == key(b)
}

fn next_id(pack_id: &str, taken: &[String]) -> String {
    let max = taken
        .iter()
        .filter_map(|id| id.rsplit('-').next().and_then(|n| n.parse::<u32>().ok()))
        .max()
        .unwrap_or(0);
    format!("{pack_id}-{:03}", max + 1)
}

fn approved(l: &Level) -> Level {
    let mut c = l.cleaned();
    for e in &mut c.entries {
        e.status = "approved".into();
    }
    c
}

/// Merges the reviewed draft into `target` (the pack as it is on GitHub now, or None if new).
/// - an approved new level is added (renumbered if its id is taken);
/// - an already published level is replaced if it was approved or edited, removed if rejected;
/// - unreviewed new levels are left out.
/// `allow_existing` lists levels that may be matched as "already published" in `target`.
pub fn merge(draft: &Pack, target: Option<&Pack>, id_prefix: &str) -> (Pack, Plan) {
    let mut out = match target {
        Some(t) => t.clone(),
        None => {
            let mut p = draft.clone();
            p.levels.clear();
            p
        }
    };
    let mut plan = Plan::default();
    for l in &draft.levels {
        let pos = out.levels.iter().position(|o| o.id == l.id && same_level(o, l));
        match pos {
            Some(i) => {
                if l.is_rejected() {
                    plan.removed.push(l.id.clone());
                    out.levels.remove(i);
                } else if l.is_ok() || l.edited {
                    let new = if l.is_ok() { approved(l) } else { l.cleaned() };
                    if new != out.levels[i] {
                        plan.updated.push(l.id.clone());
                        out.levels[i] = new;
                    }
                }
            }
            None => {
                if l.is_rejected() {
                    plan.skipped_rejected += 1;
                } else if !l.is_ok() {
                    plan.skipped_unreviewed += 1;
                } else {
                    let mut n = approved(l);
                    let taken: Vec<String> = out.levels.iter().map(|o| o.id.clone()).collect();
                    if taken.contains(&n.id) {
                        let id = next_id(id_prefix, &taken);
                        plan.renamed.push((n.id.clone(), id.clone()));
                        n.id = id;
                    }
                    plan.added.push(n.id.clone());
                    out.levels.push(n);
                }
            }
        }
    }
    if plan.changes() > 0 {
        out.content_version = target.map(|t| t.content_version + 1).unwrap_or(1);
    }
    (out, plan)
}

/// Approved levels of the draft that the game does not have yet (for a downloadable pack).
pub fn new_only(draft: &Pack, bundled: Option<&Pack>) -> Pack {
    let mut p = draft.clone();
    p.levels.retain(|l| !bundled.map(|b| b.levels.iter().any(|o| o.id == l.id && same_level(o, l))).unwrap_or(false));
    p
}

#[cfg(test)]
mod tests {
    use super::*;

    fn kimya() -> Pack {
        let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../crossword-app/app/src/main/assets/packs/az-kimya.json");
        Pack::parse(&std::fs::read_to_string(path).unwrap()).unwrap()
    }

    #[test]
    fn merge_adds_only_approved_and_renumbers() {
        let base = kimya();
        let mut draft = base.clone();
        // A "new" level that reuses an existing id with other words.
        let mut n = base.levels[1].clone();
        n.id = base.levels[0].id.clone();
        n.review = Some("ok".into());
        draft.levels.push(n);
        let mut unreviewed = base.levels[2].clone();
        unreviewed.id = "az-kimya-900".into();
        unreviewed.entries[0].answer = "X".into();
        draft.levels.push(unreviewed);
        draft.levels[3].review = Some("rejected".into());
        let (out, plan) = merge(&draft, Some(&base), "az-kimya");
        assert_eq!(plan.added, vec!["az-kimya-013".to_string()]);
        assert_eq!(plan.removed, vec![base.levels[3].id.clone()]);
        assert_eq!(plan.skipped_unreviewed, 1);
        assert_eq!(out.levels.len(), base.levels.len());
        assert_eq!(out.content_version, base.content_version + 1);
        assert!(out.levels.iter().all(|l| l.review.is_none()));
    }

    #[test]
    fn edited_published_level_is_updated() {
        let base = kimya();
        let mut draft = base.clone();
        draft.levels[0].entries[0].clues[0] = "Yeni ipucu".into();
        draft.levels[0].edited = true;
        let (out, plan) = merge(&draft, Some(&base), "az-kimya");
        assert_eq!(plan.updated.len(), 1);
        assert_eq!(out.levels[0].entries[0].clues[0], "Yeni ipucu");
        assert!(!out.to_json().contains("\"edited\""));
    }
}
