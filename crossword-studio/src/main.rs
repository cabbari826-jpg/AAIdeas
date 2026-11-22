#![windows_subsystem = "windows"]
//! Açar Söz Studio: generate, review and publish crossword levels.

mod app;
mod board;
mod config;
mod gh;
mod grid;
mod jobs;
mod model;
mod publish;

use egui::{FontData, FontDefinitions, FontFamily};
use std::sync::Arc;

fn read_first(paths: &[&str]) -> Option<Vec<u8>> {
    paths.iter().find_map(|p| std::fs::read(p).ok())
}

/// Windows fonts (Segoe UI) cover every Azerbaijani letter; egui's built-in font is the fallback.
pub fn fonts(ctx: &egui::Context) {
    let mut f = FontDefinitions::default();
    let regular = read_first(&[
        "C:\\Windows\\Fonts\\segoeui.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]);
    let bold = read_first(&[
        "C:\\Windows\\Fonts\\segoeuib.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ]);
    let symbols = read_first(&["C:\\Windows\\Fonts\\seguisym.ttf"]);
    if let Some(b) = regular {
        f.font_data.insert("ui".into(), Arc::new(FontData::from_owned(b)));
        f.families.get_mut(&FontFamily::Proportional).unwrap().insert(0, "ui".into());
    }
    if let Some(b) = symbols {
        f.font_data.insert("sym".into(), Arc::new(FontData::from_owned(b)));
        f.families.get_mut(&FontFamily::Proportional).unwrap().push("sym".into());
    }
    let mut bold_family = f.families[&FontFamily::Proportional].clone();
    if let Some(b) = bold {
        f.font_data.insert("bold".into(), Arc::new(FontData::from_owned(b)));
        bold_family.insert(0, "bold".into());
    }
    f.families.insert(grid::bold(), bold_family);
    let mut fit = vec!["Ubuntu-Light".to_string()];
    fit.extend(f.families[&FontFamily::Proportional].iter().cloned().filter(|n| n != "Ubuntu-Light"));
    f.families.insert(grid::fit_family(), fit);
    ctx.set_fonts(f);
}

#[cfg(test)]
mod fit_tests {
    use crate::board::{Board, DupIndex};
    use crate::model::Pack;

    /// Shipped levels pass the game's own ClueFitTest, so the studio should not flag them.
    #[test]
    fn calibrate_phone_fit() {
        let ctx = egui::Context::default();
        crate::fonts(&ctx);
        let _ = ctx.run(egui::RawInput::default(), |_| {});
        let dir = concat!(env!("CARGO_MANIFEST_DIR"), "/../crossword-app/app/src/main/assets/packs");
        let mut total = 0;
        let mut bad = vec![];
        for e in std::fs::read_dir(dir).unwrap().flatten() {
            let p = Pack::parse(&std::fs::read_to_string(e.path()).unwrap()).unwrap();
            for l in &p.levels {
                let b = Board::new(l);
                for (_, list) in &b.clues_at {
                    for &i in list {
                        for t in l.entries[i].tiers() {
                            total += 1;
                            if t.chars().count() <= 16 {
                                if let Some(w) = crate::grid::phone_fit(&ctx, &t, list.len(), crate::grid::FIT_MIN) {
                                    bad.push(format!("{} [{}] {t}: {w}", l.id, list.len()));
                                }
                            }
                        }
                    }
                }
            }
        }
        let _ = DupIndex::default();
        println!("flagged {} of {total}", bad.len());
        for b in bad.iter().take(30) {
            println!("{b}");
        }
        assert!(bad.len() * 100 <= total, "too many false alarms: {}", bad.len());
    }
}

fn main() -> eframe::Result<()> {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_title("Açar Söz Studio")
            .with_inner_size([1360.0, 860.0])
            .with_min_inner_size([900.0, 600.0])
            .with_icon(std::sync::Arc::new(egui::IconData {
                rgba: include_bytes!("../assets/icon64.rgba").to_vec(),
                width: 64,
                height: 64,
            })),
        ..Default::default()
    };
    eframe::run_native(
        "Açar Söz Studio",
        options,
        Box::new(|cc| {
            fonts(&cc.egui_ctx);
            cc.egui_ctx.set_theme(egui::Theme::Light);
            Ok(Box::new(app::App::new(cc)))
        }),
    )
}
