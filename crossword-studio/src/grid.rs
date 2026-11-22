//! Draws a level the way players see it, but with every answer filled in.

use crate::board::{Board, Cell, Issue};
use crate::model::Level;
use egui::{pos2, vec2, Align2, Color32, CornerRadius, FontFamily, FontId, Rect, Sense, Stroke, StrokeKind, Ui};

pub const CLUE_BG: Color32 = Color32::from_rgb(0xE3, 0xEA, 0xF4);
pub const CLUE_FG: Color32 = Color32::from_rgb(0x1F, 0x33, 0x4D);
pub const LETTER_BG: Color32 = Color32::WHITE;
pub const LETTER_FG: Color32 = Color32::from_rgb(0x11, 0x18, 0x22);
pub const EMPTY_BG: Color32 = Color32::from_rgb(0xB8, 0xC2, 0xCE);
pub const KEY_BG: Color32 = Color32::from_rgb(0xFF, 0xEE, 0xB8);
pub const SEL_BG: Color32 = Color32::from_rgb(0xFF, 0xD8, 0x6B);
pub const LINE: Color32 = Color32::from_rgb(0x7D, 0x8B, 0x9C);
pub const ERR: Color32 = Color32::from_rgb(0xD3, 0x2F, 0x2F);
pub const WARN: Color32 = Color32::from_rgb(0xF0, 0x8C, 0x00);

/// What the game puts in a clue cell (long song lines become "♪ …").
pub fn cell_clue(t: &str) -> String {
    if t.chars().count() > 16 {
        "♪ …".into()
    } else {
        t.to_string()
    }
}

/// A fixed font for the phone-fit check, so it gives the same answer on every computer.
pub fn fit_family() -> FontFamily {
    FontFamily::Name("fit".into())
}

/// Does a clue fit its cell on a small phone (46 dp cell, like ClueFitTest in the game)?
/// None = fine, Some(reason) = warning.
pub fn phone_fit(ctx: &egui::Context, t: &str, shared: usize, min_size: f32) -> Option<String> {
    if t.chars().count() > 16 {
        return Some("длиннее 16 знаков: в клетке будет «♪ …», целиком только на карточке внизу".into());
    }
    let text = format!("{t} →");
    let (w, h) = (42.0, 44.0 / shared as f32 - 5.0);
    let mut size = 12.0;
    while size >= min_size {
        let font = FontId::new(size, fit_family());
        let words_fit = text
            .split_whitespace()
            .flat_map(|w| w.split_inclusive('-'))
            .all(|word| ctx.fonts(|f| f.layout_no_wrap(word.to_string(), font.clone(), Color32::BLACK)).size().x <= w);
        if words_fit {
            let g = ctx.fonts(|f| f.layout(text.clone(), font.clone(), Color32::BLACK, w));
            if g.rows.len() as f32 * size * 1.05 <= h {
                return None;
            }
        }
        size -= 0.5;
    }
    Some("слишком мелко в клетке на маленьком телефоне: лучше короче".into())
}

pub const FIT_MIN: f32 = 7.0;

pub fn bold() -> FontFamily {
    FontFamily::Name("bold".into())
}

/// Largest font size (from `max` down to `min`) at which `text` fits the box without
/// breaking a word. Returns (size, fits).
pub fn fit_size(ctx: &egui::Context, text: &str, w: f32, h: f32, max: f32, min: f32) -> (f32, bool) {
    let mut size = max;
    while size >= min {
        let font = FontId::proportional(size);
        let words_fit = text.split_whitespace().all(|word| {
            ctx.fonts(|f| f.layout_no_wrap(word.to_string(), font.clone(), Color32::BLACK)).size().x <= w
        });
        if words_fit {
            let g = ctx.fonts(|f| f.layout(text.to_string(), font.clone(), Color32::BLACK, w));
            if g.rows.len() as f32 * size * 1.05 <= h {
                return (size, true);
            }
        }
        size -= 0.5;
    }
    (min, false)
}

pub struct GridView<'a> {
    pub level: &'a Level,
    pub board: &'a Board,
    pub issues: &'a [Issue],
    pub tier: usize,
    pub cell: f32,
}

impl GridView<'_> {
    pub fn show(&self, ui: &mut Ui, selected: &mut Option<usize>) {
        let (rows, cols) = (self.board.rows.max(1), self.board.cols.max(1));
        let c = self.cell;
        let (resp, painter) = ui.allocate_painter(vec2(cols as f32 * c + 2.0, rows as f32 * c + 2.0), Sense::click());
        let origin = resp.rect.min + vec2(1.0, 1.0);
        let rect_of = |cell: Cell| Rect::from_min_size(origin + vec2(cell.1 as f32 * c, cell.0 as f32 * c), vec2(c, c));
        let sel_cells: Vec<Cell> = selected.and_then(|i| self.board.entry_cells.get(i).cloned()).unwrap_or_default();
        let ctx = ui.ctx().clone();
        let entry_state = |i: usize| -> Option<Color32> {
            if self.issues.iter().any(|x| x.error && x.entry == Some(i)) {
                Some(ERR)
            } else if self.issues.iter().any(|x| !x.error && x.entry == Some(i)) {
                Some(WARN)
            } else {
                None
            }
        };
        let mut hover_text: Option<String> = None;
        let hover = resp.hover_pos();

        for r in 0..rows {
            for col in 0..cols {
                let cell = (r, col);
                let rect = rect_of(cell);
                if let Some(list) = self.board.clues_at.get(&cell) {
                    painter.rect_filled(rect, CornerRadius::ZERO, CLUE_BG);
                    let n = list.len().max(1) as f32;
                    for (k, &i) in list.iter().enumerate() {
                        let e = &self.level.entries[i];
                        let sub = Rect::from_min_size(rect.min + vec2(0.0, k as f32 * c / n), vec2(c, c / n));
                        if *selected == Some(i) {
                            painter.rect_filled(sub.shrink(1.0), CornerRadius::same(2), SEL_BG);
                        }
                        let tiers = e.tiers();
                        let t = tiers.get(self.tier.min(tiers.len().saturating_sub(1))).cloned().unwrap_or_default();
                        let text = format!("{} {}", cell_clue(&t), if e.across() { "→" } else { "↓" });
                        let (w, h) = (sub.width() - 4.0, sub.height() - 2.0);
                        let (size, _) = fit_size(&ctx, &text, w, h, c * 0.24, (c * 0.09).max(6.0));
                        let galley = ctx.fonts(|f| f.layout(text, FontId::proportional(size), CLUE_FG, w));
                        let gpos = sub.center() - galley.size() / 2.0;
                        painter.galley(gpos, galley, CLUE_FG);
                        if let Some(col) = entry_state(i) {
                            painter.rect_stroke(sub.shrink(1.5), CornerRadius::same(2), Stroke::new(2.5_f32, col), StrokeKind::Inside);
                        }
                        if k > 0 {
                            painter.hline(sub.x_range(), sub.top(), Stroke::new(1.0_f32, LINE));
                        }
                        if hover.map(|p| sub.contains(p)).unwrap_or(false) {
                            hover_text = Some(format!(
                                "{}  ({} букв)\n1. {}\n2. {}\n3. {}",
                                e.answer,
                                self.board.entry_cells.get(i).map(|v| v.len()).unwrap_or(0),
                                tiers.first().cloned().unwrap_or_default(),
                                tiers.get(1).cloned().unwrap_or_default(),
                                tiers.get(2).cloned().unwrap_or_default()
                            ));
                        }
                    }
                } else if let Some(ch) = self.board.solution.get(&cell) {
                    let bg = if sel_cells.contains(&cell) {
                        SEL_BG
                    } else if self.board.keyword_at.contains_key(&cell) {
                        KEY_BG
                    } else {
                        LETTER_BG
                    };
                    painter.rect_filled(rect, CornerRadius::ZERO, bg);
                    painter.text(rect.center() + vec2(0.0, c * 0.04), Align2::CENTER_CENTER, ch, FontId::new(c * 0.5, bold()), LETTER_FG);
                    if let Some(n) = self.board.keyword_at.get(&cell) {
                        painter.text(rect.min + vec2(3.0, 1.0), Align2::LEFT_TOP, n.to_string(), FontId::proportional(c * 0.2), CLUE_FG);
                    }
                } else {
                    painter.rect_filled(rect, CornerRadius::ZERO, EMPTY_BG);
                }
                painter.rect_stroke(rect, CornerRadius::ZERO, Stroke::new(1.0_f32, LINE), StrokeKind::Middle);
            }
        }

        if let Some(t) = hover_text {
            resp.clone().on_hover_text(t);
        }
        if resp.clicked() {
            if let Some(p) = resp.interact_pointer_pos() {
                let rel = p - origin;
                let cell = ((rel.y / c).floor() as i64, (rel.x / c).floor() as i64);
                if let Some(list) = self.board.clues_at.get(&cell) {
                    let k = (((rel.y - cell.0 as f32 * c) / (c / list.len() as f32)).floor() as usize).min(list.len() - 1);
                    *selected = Some(list[k]);
                } else if self.board.solution.contains_key(&cell) {
                    let owners: Vec<usize> =
                        (0..self.board.entry_cells.len()).filter(|&i| self.board.entry_cells[i].contains(&cell)).collect();
                    let cur = selected.and_then(|s| owners.iter().position(|&o| o == s));
                    *selected = match cur {
                        Some(k) => Some(owners[(k + 1) % owners.len()]),
                        None => owners.first().copied(),
                    };
                }
            }
        }
        let _ = pos2;
    }
}
