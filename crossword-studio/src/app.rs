use crate::board::{check_level, Board, DupIndex, Issue};
use crate::config::{self, Config};
use crate::grid::{self, GridView};
use crate::jobs::{self, Msg};
use crate::model::{az_lower, Pack};
use crate::publish;
use egui::{Color32, Key, RichText, ScrollArea};
use std::collections::{HashMap, HashSet};
use std::path::PathBuf;
use std::sync::mpsc::{channel, Receiver, Sender};

#[derive(PartialEq, Clone, Copy)]
enum Tab {
    Generate,
    Review,
    Publish,
    Settings,
}

struct Draft {
    pack: Pack,
    path: PathBuf,
    boards: Vec<Board>,
    issues: Vec<Vec<Issue>>,
    /// Level ids the game already has with the same words.
    published: HashSet<String>,
}

pub struct App {
    cfg: Config,
    tab: Tab,
    tx: Sender<Msg>,
    rx: Receiver<Msg>,
    busy: Option<String>,
    status: Option<(bool, String)>,
    log: Vec<String>,
    reference: Vec<Pack>,
    word_files: Vec<String>,
    downloadable: Vec<String>,
    draft: Option<Draft>,
    level: usize,
    entry: Option<usize>,
    tier: usize,
    only_new: bool,
    zoom: f32,
    gen_count: u32,
    gen_seed: String,
    show_token: bool,
    confirm: Option<u8>,
    dl_id: String,
    dl_title: String,
    fit_cache: HashMap<(String, usize), Option<String>>,
    checks_dirty: bool,
    save_dirty: bool,
    local_stamp: Option<String>,
}

const GREEN: Color32 = Color32::from_rgb(0x2E, 0x7D, 0x32);
const RED: Color32 = Color32::from_rgb(0xC6, 0x28, 0x28);
const GRAY: Color32 = Color32::from_rgb(0x70, 0x78, 0x82);

fn big(text: &str) -> RichText {
    RichText::new(text).size(17.0)
}

impl App {
    pub fn new(cc: &eframe::CreationContext<'_>) -> App {
        let (tx, rx) = channel();
        let cfg = Config::load();
        cc.egui_ctx.set_zoom_factor(cfg.font_scale);
        let mut app = App {
            tab: if cfg.token.is_empty() { Tab::Settings } else { Tab::Generate },
            gen_count: cfg.last_count,
            cfg,
            tx,
            rx,
            busy: None,
            status: None,
            log: vec![],
            reference: vec![],
            word_files: vec![],
            downloadable: vec![],
            draft: None,
            level: 0,
            entry: None,
            tier: 0,
            only_new: true,
            zoom: 1.0,
            gen_seed: String::new(),
            show_token: false,
            confirm: None,
            dl_id: String::new(),
            dl_title: String::new(),
            fit_cache: HashMap::new(),
            checks_dirty: false,
            save_dirty: false,
            local_stamp: jobs::local_info(),
        };
        jobs::load_reference(&app.tx, &cc.egui_ctx, &app.cfg);
        // Reopen the newest draft, so work continues where it stopped.
        if let Some(p) = app.drafts().into_iter().next() {
            app.open(&p);
        }
        app
    }

    fn drafts(&self) -> Vec<PathBuf> {
        let mut v: Vec<(std::time::SystemTime, PathBuf)> = std::fs::read_dir(config::drafts_dir())
            .map(|rd| {
                rd.filter_map(|e| e.ok())
                    .filter(|e| e.path().extension().map(|x| x == "json").unwrap_or(false))
                    .filter_map(|e| Some((e.metadata().ok()?.modified().ok()?, e.path())))
                    .collect()
            })
            .unwrap_or_default();
        v.sort_by(|a, b| b.0.cmp(&a.0));
        v.into_iter().map(|x| x.1).collect()
    }

    fn open(&mut self, path: &PathBuf) {
        match std::fs::read_to_string(path).map_err(|e| e.to_string()).and_then(|t| Pack::parse(&t)) {
            Ok(pack) => {
                if self.downloadable.contains(&pack.pack_id) {
                    self.dl_id = pack.pack_id.clone();
                    self.dl_title = pack.title.clone();
                } else {
                    self.dl_id = format!("{}-plus", pack.pack_id);
                    self.dl_title = format!("{} +", pack.title);
                }
                let boards = pack.levels.iter().map(Board::new).collect();
                self.draft = Some(Draft { pack, path: path.clone(), boards, issues: vec![], published: HashSet::new() });
                self.mark_published();
                self.level = 0;
                self.entry = None;
                self.checks_dirty = true;
                self.confirm = None;
                self.jump_first_visible();
                self.tab = Tab::Review;
            }
            Err(e) => self.status = Some((false, format!("Не открылось: {e}"))),
        }
    }

    fn mark_published(&mut self) {
        let Some(d) = &mut self.draft else { return };
        d.published.clear();
        if let Some(r) = self.reference.iter().find(|p| p.pack_id == d.pack.pack_id) {
            for l in &d.pack.levels {
                if r.levels.iter().any(|o| o.id == l.id && publish::same_level(o, l)) {
                    d.published.insert(l.id.clone());
                }
            }
        }
    }

    fn visible(&self) -> Vec<usize> {
        let Some(d) = &self.draft else { return vec![] };
        let all: Vec<usize> = (0..d.pack.levels.len()).collect();
        if !self.only_new {
            return all;
        }
        let v: Vec<usize> = all.iter().copied().filter(|&i| !d.published.contains(&d.pack.levels[i].id)).collect();
        if v.is_empty() {
            all
        } else {
            v
        }
    }

    fn jump_first_visible(&mut self) {
        let v = self.visible();
        if !v.contains(&self.level) {
            self.level = v.first().copied().unwrap_or(0);
        }
    }

    fn recheck(&mut self, ctx: &egui::Context) {
        let Some(d) = &mut self.draft else { return };
        let ix = DupIndex::build(&self.reference, &d.pack);
        let cache = &mut self.fit_cache;
        let mut fit = |t: &str, shared: usize| -> Option<String> {
            cache
                .entry((t.to_string(), shared))
                .or_insert_with(|| grid::phone_fit(ctx, t, shared, grid::FIT_MIN))
                .clone()
        };
        d.issues = d.pack.levels.iter().zip(&d.boards).map(|(l, b)| check_level(l, b, &ix, &mut fit)).collect();
        self.checks_dirty = false;
    }

    fn save(&mut self) {
        if let Some(d) = &self.draft {
            if let Err(e) = std::fs::write(&d.path, d.pack.to_json()) {
                self.status = Some((false, format!("Не сохранилось: {e}")));
            }
        }
        self.save_dirty = false;
    }

    fn set_review(&mut self, v: Option<&str>) {
        if let Some(d) = &mut self.draft {
            if let Some(l) = d.pack.levels.get_mut(self.level) {
                l.review = v.map(String::from);
                self.save_dirty = true;
                self.checks_dirty = true;
            }
        }
    }

    fn step(&mut self, delta: i32) {
        let v = self.visible();
        if let Some(pos) = v.iter().position(|&i| i == self.level) {
            let np = (pos as i32 + delta).clamp(0, v.len() as i32 - 1) as usize;
            self.level = v[np];
            self.entry = None;
        }
    }

    fn handle_messages(&mut self, ctx: &egui::Context) {
        while let Ok(m) = self.rx.try_recv() {
            match m {
                Msg::Busy(b) => self.busy = b,
                Msg::Log(t) => self.log.push(t),
                Msg::Info(t) => {
                    self.log.push(t.clone());
                    self.status = Some((true, t));
                }
                Msg::Error(t) => {
                    self.log.push(format!("ОШИБКА: {t}"));
                    self.status = Some((false, t));
                }
                Msg::Reference { packs, word_files, downloadable } => {
                    self.reference = packs;
                    self.word_files = word_files;
                    self.downloadable = downloadable;
                    if let Some(d) = &self.draft {
                        if self.downloadable.contains(&d.pack.pack_id) {
                            self.dl_id = d.pack.pack_id.clone();
                            self.dl_title = d.pack.title.clone();
                        }
                    }
                    self.mark_published();
                    self.checks_dirty = true;
                }
                Msg::Open(p) => self.open(&p),
                Msg::LocalReady(s) => self.local_stamp = Some(s),
                Msg::Renamed(list) => {
                    if let Some(d) = &mut self.draft {
                        for (a, b) in list {
                            if let Some(l) = d.pack.levels.iter_mut().find(|l| l.id == a && l.is_ok()) {
                                l.id = b;
                            }
                        }
                        self.save_dirty = true;
                        self.checks_dirty = true;
                    }
                }
            }
            ctx.request_repaint();
        }
    }

    fn known_packs(&self) -> Vec<(String, String)> {
        let mut v: Vec<(String, String)> = self
            .reference
            .iter()
            .map(|p| {
                let note = if self.downloadable.contains(&p.pack_id) { " (скачиваемый)" } else { "" };
                (p.pack_id.clone(), format!("{}{note}", p.title))
            })
            .collect();
        for w in &self.word_files {
            if !v.iter().any(|x| &x.0 == w) {
                v.push((w.clone(), String::new()));
            }
        }
        v.sort();
        v
    }

    // ---------- tabs ----------

    fn tab_generate(&mut self, ui: &mut egui::Ui) {
        ui.heading("Сделать новые уровни");
        ui.label("Генератор раскладывает в сетки только слова, у которых уже есть три подсказки. Слова не повторяются с теми, что уже есть в игре.");
        ui.add_space(10.0);
        egui::Grid::new("gen").num_columns(2).spacing([12.0, 10.0]).show(ui, |ui| {
            ui.label(big("Пак:"));
            let packs = self.known_packs();
            let sel = packs.iter().find(|p| p.0 == self.cfg.last_pack).map(|p| format!("{}  {}", p.1, p.0)).unwrap_or(self.cfg.last_pack.clone());
            ui.horizontal(|ui| {
                egui::ComboBox::from_id_salt("pack").width(280.0).selected_text(big(&sel)).show_ui(ui, |ui| {
                    for (id, title) in &packs {
                        ui.selectable_value(&mut self.cfg.last_pack, id.clone(), format!("{title}  {id}"));
                    }
                });
                ui.label("или впишите:");
                ui.add(egui::TextEdit::singleline(&mut self.cfg.last_pack).desired_width(140.0));
            });
            ui.end_row();
            ui.label(big("Сколько уровней:"));
            ui.add(egui::DragValue::new(&mut self.gen_count).range(1..=60).speed(0.2));
            ui.end_row();
            ui.label("Номер варианта (можно пусто):");
            ui.add(egui::TextEdit::singleline(&mut self.gen_seed).desired_width(100.0).hint_text("случайно"));
            ui.end_row();
        });
        ui.add_space(14.0);
        let idle = self.busy.is_none();
        ui.group(|ui| {
            ui.label(big("☁  В облаке GitHub (рекомендуется)"));
            ui.label("Ничего ставить не нужно. Обычно занимает 2–5 минут. Нужен токен из «Настроек».");
            if ui.add_enabled(idle, egui::Button::new(big("Сгенерировать в облаке")).min_size([260.0, 40.0].into())).clicked() {
                self.cfg.last_count = self.gen_count;
                self.cfg.save();
                jobs::cloud_generate(&self.tx, ui.ctx(), &self.cfg, self.cfg.last_pack.clone(), self.gen_count, self.gen_seed.clone());
            }
        });
        ui.add_space(8.0);
        ui.group(|ui| {
            ui.label(big("💻  На этом компьютере"));
            ui.label("Быстрее и без интернета, но нужен установленный Python (python.org). Файлы генератора скачиваются один раз и после добавления новых слов.");
            ui.horizontal(|ui| {
                if ui.add_enabled(idle, egui::Button::new("Скачать файлы генератора")).clicked() {
                    jobs::update_local(&self.tx, ui.ctx(), &self.cfg);
                }
                match &self.local_stamp {
                    Some(s) => ui.label(RichText::new(format!("скачаны {s}")).color(GRAY)),
                    None => ui.label(RichText::new("ещё не скачаны").color(RED)),
                };
            });
            if ui.add_enabled(idle && self.local_stamp.is_some(), egui::Button::new(big("Сгенерировать здесь")).min_size([260.0, 40.0].into())).clicked() {
                self.cfg.last_count = self.gen_count;
                self.cfg.save();
                jobs::local_generate(&self.tx, ui.ctx(), &self.cfg, self.cfg.last_pack.clone(), self.gen_count, self.gen_seed.clone());
            }
        });
        ui.add_space(12.0);
        ui.collapsing("Журнал", |ui| {
            ScrollArea::vertical().max_height(260.0).stick_to_bottom(true).show(ui, |ui| {
                for l in &self.log {
                    ui.label(RichText::new(l).monospace().size(12.0));
                }
            });
        });
    }

    fn review_toolbar(&mut self, ui: &mut egui::Ui) {
        ui.horizontal_wrapped(|ui| {
            ui.menu_button(big("📂 Открыть"), |ui| {
                if ui.button("Файл с компьютера…").clicked() {
                    ui.close_menu();
                    if let Some(p) = rfd::FileDialog::new().add_filter("Пак уровней", &["json"]).pick_file() {
                        self.open(&p);
                    }
                }
                ui.separator();
                ui.label(RichText::new("Последние черновики:").color(GRAY));
                for p in self.drafts().into_iter().take(15) {
                    let name = p.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
                    if ui.button(name).clicked() {
                        ui.close_menu();
                        self.open(&p);
                    }
                }
                ui.separator();
                ui.label(RichText::new("Пак, который уже в игре (проверить старые уровни):").color(GRAY));
                let mut chosen = None;
                for p in &self.reference {
                    if ui.button(format!("{}  ({} ур.)", p.title, p.levels.len())).clicked() {
                        chosen = Some(p.clone());
                    }
                }
                if let Some(p) = chosen {
                    ui.close_menu();
                    match jobs::draft_from(&p) {
                        Ok(path) => {
                            self.open(&path);
                            self.only_new = false;
                        }
                        Err(e) => self.status = Some((false, e)),
                    }
                }
                ui.separator();
                if ui.button("Открыть папку с черновиками").clicked() {
                    ui.close_menu();
                    let _ = open::that(config::drafts_dir());
                }
            });
            if let Some(d) = &self.draft {
                let total = d.pack.levels.len();
                let ok = d.pack.levels.iter().filter(|l| l.is_ok()).count();
                let rej = d.pack.levels.iter().filter(|l| l.is_rejected()).count();
                let new = total - d.published.len();
                ui.label(big(&format!("{}  ({})", d.pack.title, d.pack.pack_id)).strong());
                ui.label(format!("новых: {new} · ✓ {ok} · ✗ {rej} · всего {total}"));
                ui.checkbox(&mut self.only_new, "только новые");
            }
        });
    }

    fn tab_review(&mut self, ctx: &egui::Context) {
        egui::TopBottomPanel::top("review_top").show(ctx, |ui| {
            ui.add_space(4.0);
            self.review_toolbar(ui);
            ui.add_space(4.0);
        });
        if self.draft.is_none() {
            egui::CentralPanel::default().show(ctx, |ui| {
                ui.add_space(40.0);
                ui.vertical_centered(|ui| {
                    ui.label(big("Нет открытых уровней."));
                    ui.label("Сгенерируйте новые на вкладке «Генерация» или откройте черновик кнопкой «📂 Открыть».");
                });
            });
            return;
        }
        let visible = self.visible();
        // Keyboard: only when no text field is being edited.
        let typing = ctx.memory(|m| m.focused().is_some());
        if !typing {
            let (enter, del, left, right, n1, n2, n3) = ctx.input(|i| {
                (
                    i.key_pressed(Key::Enter),
                    i.key_pressed(Key::Delete),
                    i.key_pressed(Key::ArrowLeft),
                    i.key_pressed(Key::ArrowRight),
                    i.key_pressed(Key::Num1),
                    i.key_pressed(Key::Num2),
                    i.key_pressed(Key::Num3),
                )
            });
            if enter {
                self.set_review(Some("ok"));
                self.step(1);
            }
            if del {
                self.set_review(Some("rejected"));
                self.step(1);
            }
            if left {
                self.step(-1);
            }
            if right {
                self.step(1);
            }
            for (k, t) in [(n1, 0), (n2, 1), (n3, 2)] {
                if k {
                    self.tier = t;
                }
            }
        }

        egui::SidePanel::left("levels").resizable(true).default_width(190.0).show(ctx, |ui| {
            ui.add_space(4.0);
            ui.label(RichText::new("Уровни").strong());
            ScrollArea::vertical().show(ui, |ui| {
                let d = self.draft.as_ref().unwrap();
                let mut pick = None;
                for &i in &visible {
                    let l = &d.pack.levels[i];
                    let errs = d.issues.get(i).map(|v| v.iter().filter(|x| x.error).count()).unwrap_or(0);
                    let warns = d.issues.get(i).map(|v| v.iter().filter(|x| !x.error).count()).unwrap_or(0);
                    let (mark, col) = if l.is_ok() {
                        ("✓", GREEN)
                    } else if l.is_rejected() {
                        ("✗", RED)
                    } else {
                        ("•", GRAY)
                    };
                    let mut text = RichText::new(format!("{mark}  {}", l.id)).color(col);
                    if self.level == i {
                        text = text.strong();
                    }
                    let resp = ui.horizontal(|ui| {
                        let r = ui.selectable_label(self.level == i, text);
                        if errs > 0 {
                            ui.label(RichText::new(format!("⛔{errs}")).color(RED));
                        } else if warns > 0 {
                            ui.label(RichText::new(format!("⚠{warns}")).color(grid::WARN));
                        }
                        if d.published.contains(&l.id) {
                            ui.label(RichText::new("в игре").small().color(GRAY));
                        }
                        r
                    });
                    if resp.inner.clicked() {
                        pick = Some(i);
                    }
                }
                if let Some(i) = pick {
                    self.level = i;
                    self.entry = None;
                }
            });
        });

        egui::SidePanel::right("details").resizable(true).default_width(420.0).show(ctx, |ui| {
            self.details(ui);
        });

        egui::CentralPanel::default().show(ctx, |ui| {
            let Some(d) = &self.draft else { return };
            let Some(level) = d.pack.levels.get(self.level) else { return };
            let board = &d.boards[self.level];
            let issues = d.issues.get(self.level).cloned().unwrap_or_default();
            ui.horizontal(|ui| {
                ui.label(big(&format!("{}", level.id)).strong());
                if let Some(k) = &level.keyword {
                    ui.label(big(&format!("· ключевое слово: {}", k.word)));
                }
                if level.is_ok() {
                    ui.label(big("· ✓ хорошо").color(GREEN));
                } else if level.is_rejected() {
                    ui.label(big("· ✗ отклонён").color(RED));
                }
                if d.published.contains(&level.id) {
                    ui.label(RichText::new("· уже в игре").color(GRAY));
                }
            });
            ui.horizontal(|ui| {
                ui.label("Подсказки в сетке:");
                ui.selectable_value(&mut self.tier, 0, "1 сложные");
                ui.selectable_value(&mut self.tier, 1, "2 средние");
                ui.selectable_value(&mut self.tier, 2, "3 лёгкие");
                ui.separator();
                ui.label("Размер:");
                ui.add(egui::Slider::new(&mut self.zoom, 0.6..=1.6).show_value(false));
            });
            ui.add_space(6.0);
            let avail = ui.available_size() - egui::vec2(0.0, 70.0);
            let cell = (((avail.x - 16.0) / board.cols.max(1) as f32).min((avail.y - 16.0) / board.rows.max(1) as f32) * self.zoom).clamp(30.0, 140.0);
            let mut sel = self.entry;
            ScrollArea::both().max_height(avail.y).show(ui, |ui| {
                GridView { level, board, issues: &issues, tier: self.tier, cell }.show(ui, &mut sel);
            });
            self.entry = sel;
            ui.add_space(8.0);
            let pos = visible.iter().position(|&i| i == self.level).unwrap_or(0);
            ui.horizontal(|ui| {
                if ui.add(egui::Button::new(big("◀")).min_size([44.0, 40.0].into())).on_hover_text("Предыдущий (←)").clicked() {
                    self.step(-1);
                }
                ui.label(big(&format!("{} из {}", pos + 1, visible.len())));
                if ui.add(egui::Button::new(big("▶")).min_size([44.0, 40.0].into())).on_hover_text("Следующий (→)").clicked() {
                    self.step(1);
                }
                ui.add_space(16.0);
                if ui.add(egui::Button::new(big("✓ Хорошо").color(GREEN)).min_size([130.0, 40.0].into())).on_hover_text("Enter: отметить и перейти дальше").clicked() {
                    self.set_review(Some("ok"));
                    self.step(1);
                }
                if ui.add(egui::Button::new(big("✗ Отклонить").color(RED)).min_size([130.0, 40.0].into())).on_hover_text("Delete: уровень не выйдет").clicked() {
                    self.set_review(Some("rejected"));
                    self.step(1);
                }
                if ui.small_button("снять отметку").clicked() {
                    self.set_review(None);
                }
            });
        });
    }

    fn details(&mut self, ui: &mut egui::Ui) {
        let Some(d) = &mut self.draft else { return };
        let Some(level) = d.pack.levels.get_mut(self.level) else { return };
        let issues = d.issues.get(self.level).cloned().unwrap_or_default();
        ui.add_space(4.0);
        ui.label(RichText::new("Проблемы").strong());
        if issues.is_empty() {
            ui.label(RichText::new("Проблем не найдено ✓").color(GREEN));
        }
        ScrollArea::vertical().id_salt("issues").max_height(180.0).show(ui, |ui| {
            for is in &issues {
                let col = if is.error { RED } else { grid::WARN };
                let t = format!("{} {}", if is.error { "⛔" } else { "⚠" }, is.text);
                if ui.add(egui::Label::new(RichText::new(t).color(col)).sense(egui::Sense::click())).clicked() {
                    self.entry = is.entry;
                }
            }
        });
        ui.separator();
        ui.label(RichText::new("Слова и подсказки").strong());
        ui.label(RichText::new("Нажмите на слово или клетку в сетке, чтобы поправить подсказки. 1 сложная · 2 средняя · 3 лёгкая").small().color(GRAY));
        let mut changed = false;
        let mut order: Vec<usize> = (0..level.entries.len()).collect();
        order.sort_by_key(|&i| (!level.entries[i].across(), level.entries[i].clue_row, level.entries[i].clue_col));
        ScrollArea::vertical().id_salt("entries").show(ui, |ui| {
            for i in order {
                let selected = self.entry == Some(i);
                let e = &mut level.entries[i];
                let errs = issues.iter().any(|x| x.error && x.entry == Some(i));
                let warns = issues.iter().any(|x| !x.error && x.entry == Some(i));
                let mut head = RichText::new(format!("{} {}  ({})", if e.across() { "→" } else { "↓" }, e.answer, e.answer.chars().count())).size(16.0).strong();
                if errs {
                    head = head.color(RED);
                } else if warns {
                    head = head.color(grid::WARN);
                }
                ui.horizontal(|ui| {
                    if ui.selectable_label(selected, head).clicked() {
                        self.entry = if selected { None } else { Some(i) };
                    }
                    if ui.small_button("Викисловарь ↗").on_hover_text("Только для справки: подсказку пишите своими словами, текст словаря не копируйте").clicked() {
                        let _ = open::that(format!("https://az.wiktionary.org/wiki/{}", crate::gh::enc(&az_lower(&e.answer))));
                    }
                });
                if selected {
                    if e.clues.is_empty() {
                        e.clues = vec![e.clue.clone()];
                    }
                    while e.clues.len() < 3 {
                        e.clues.push(String::new());
                    }
                    let labels = ["1 сложная", "2 средняя", "3 лёгкая"];
                    for k in 0..e.clues.len() {
                        ui.horizontal(|ui| {
                            ui.label(RichText::new(*labels.get(k).unwrap_or(&"")).small());
                            let before = e.clues[k].clone();
                            let n = before.chars().count();
                            let resp = ui.add(egui::TextEdit::singleline(&mut e.clues[k]).desired_width(260.0).font(egui::TextStyle::Body));
                            ui.label(RichText::new(format!("{n}")).small().color(if n > 16 { RED } else { GRAY }));
                            if resp.changed() {
                                if e.clue == before {
                                    e.clue = e.clues[k].clone();
                                }
                                changed = true;
                            }
                        });
                    }
                } else {
                    let t = e.tiers().join("  ·  ");
                    ui.label(RichText::new(t).color(GRAY));
                }
                ui.add_space(4.0);
            }
        });
        if changed {
            level.edited = true;
            self.save_dirty = true;
            self.checks_dirty = true;
        }
    }

    fn tab_publish(&mut self, ui: &mut egui::Ui) {
        ui.heading("Выпустить уровни для игроков");
        let Some(d) = &self.draft else {
            ui.label("Сначала откройте и просмотрите уровни на вкладке «Ревью».");
            return;
        };
        ui.label(big(&format!("Пак: {} ({})", d.pack.title, d.pack.pack_id)));
        let target = self.reference.iter().find(|p| p.pack_id == d.pack.pack_id);
        let (_, plan) = publish::merge(&d.pack, target, &d.pack.pack_id);
        ui.label(big(&plan.describe()));
        let bad: Vec<String> = d
            .pack
            .levels
            .iter()
            .zip(&d.issues)
            .filter(|(l, is)| l.is_ok() && is.iter().any(|x| x.error))
            .map(|(l, _)| l.id.clone())
            .collect();
        if !bad.is_empty() {
            ui.label(big(&format!("⛔ Уровни с ошибками отмечены «Хорошо»: {}. Исправьте или отклоните их.", bad.join(", "))).color(RED));
        }
        let can = bad.is_empty() && self.busy.is_none() && !self.cfg.token.is_empty();
        if self.cfg.token.is_empty() {
            ui.label(RichText::new("Нужен токен GitHub: вкладка «Настройки».").color(RED));
        }
        ui.add_space(12.0);
        let pack = d.pack.clone();
        let is_download = self.downloadable.contains(&d.pack.pack_id);
        if is_download {
            ui.label(big("Это скачиваемый пак: выпускается только способом 2.").color(GRAY));
        }
        ui.add_enabled_ui(!is_download, |ui| ui.group(|ui| {
            ui.label(big("1. В игру, с обновлением приложения"));
            ui.label("Уровни добавятся в пак внутри приложения. GitHub сам соберёт новую версию за 10–20 минут, и телефон предложит обновиться. Подходит, пока в приложении меньше ~100 уровней.");
            if self.confirm == Some(1) {
                ui.horizontal(|ui| {
                    if ui.add_enabled(can, egui::Button::new(big("Да, выпустить").color(GREEN))).clicked() {
                        jobs::publish_bundle(&self.tx, ui.ctx(), &self.cfg, pack.clone());
                        self.confirm = None;
                    }
                    if ui.button("Отмена").clicked() {
                        self.confirm = None;
                    }
                });
            } else if ui.add_enabled(can && plan.changes() > 0, egui::Button::new(big("Выпустить в игру")).min_size([240.0, 40.0].into())).clicked() {
                self.confirm = Some(1);
            }
        }));
        ui.add_space(8.0);
        ui.group(|ui| {
            ui.label(big("2. Скачиваемый пак"));
            ui.label("Новые одобренные уровни (которых нет внутри приложения) выкладываются отдельным файлом. Игрок скачает их, когда пройдёт встроенные уровни. Приложение пока само такие паки не скачивает: это отдельная доработка игры.");
            egui::Grid::new("dl").num_columns(2).show(ui, |ui| {
                ui.label("Код пака:");
                ui.text_edit_singleline(&mut self.dl_id);
                ui.end_row();
                ui.label("Название для игроков:");
                ui.text_edit_singleline(&mut self.dl_title);
                ui.end_row();
            });
            let id_ok = !self.dl_id.is_empty() && self.dl_id.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-');
            if !id_ok {
                ui.label(RichText::new("Код: только маленькие латинские буквы, цифры и «-»").color(RED));
            }
            if self.confirm == Some(2) {
                ui.horizontal(|ui| {
                    if ui.add_enabled(can && id_ok, egui::Button::new(big("Да, выложить").color(GREEN))).clicked() {
                        jobs::publish_download(&self.tx, ui.ctx(), &self.cfg, pack.clone(), self.dl_id.clone(), self.dl_title.clone());
                        self.confirm = None;
                    }
                    if ui.button("Отмена").clicked() {
                        self.confirm = None;
                    }
                });
            } else if ui.add_enabled(can && id_ok, egui::Button::new(big("Выложить как скачиваемый пак")).min_size([240.0, 40.0].into())).clicked() {
                self.confirm = Some(2);
            }
        });
        ui.add_space(8.0);
        ui.hyperlink_to("Сборки приложения на GitHub (Actions)", format!("https://github.com/{}/actions", self.cfg.repo));
    }

    fn tab_settings(&mut self, ui: &mut egui::Ui) {
        ui.heading("Настройки");
        ui.add_space(6.0);
        ui.label(big("Токен GitHub"));
        ui.label("Нужен, чтобы запускать генерацию в облаке и выпускать уровни. Хранится только на этом компьютере.");
        ui.horizontal(|ui| {
            ui.add(egui::TextEdit::singleline(&mut self.cfg.token).password(!self.show_token).desired_width(420.0).hint_text("github_pat_…"));
            ui.checkbox(&mut self.show_token, "показать");
            if ui.add_enabled(self.busy.is_none(), egui::Button::new("Проверить")).clicked() {
                self.cfg.save();
                jobs::check_token(&self.tx, ui.ctx(), &self.cfg);
            }
        });
        ui.collapsing("Как получить токен (5 минут)", |ui| {
            ui.label("1. Откройте ссылку ниже и войдите в GitHub.");
            ui.hyperlink_to("github.com/settings/personal-access-tokens/new", "https://github.com/settings/personal-access-tokens/new");
            ui.label("2. Token name: Açar Söz Studio. Expiration: 1 год.");
            ui.label("3. Repository access: Only select repositories → AAIdeas.");
            ui.label("4. Permissions → Repository permissions: Contents — Read and write; Actions — Read and write.");
            ui.label("5. Generate token, скопируйте его (начинается с github_pat_) и вставьте сюда.");
        });
        ui.add_space(10.0);
        egui::Grid::new("adv").num_columns(2).spacing([12.0, 8.0]).show(ui, |ui| {
            ui.label("Репозиторий:");
            ui.text_edit_singleline(&mut self.cfg.repo);
            ui.end_row();
            ui.label("Ветка игры:");
            ui.text_edit_singleline(&mut self.cfg.branch);
            ui.end_row();
            ui.label("Файл генерации в облаке:");
            ui.text_edit_singleline(&mut self.cfg.workflow);
            ui.end_row();
            ui.label("Команда Python:");
            ui.text_edit_singleline(&mut self.cfg.python);
            ui.end_row();
            ui.label("Размер текста:");
            if ui.add(egui::Slider::new(&mut self.cfg.font_scale, 0.8..=1.8)).drag_stopped() {
                ui.ctx().set_zoom_factor(self.cfg.font_scale);
            }
            ui.end_row();
        });
        ui.add_space(10.0);
        ui.horizontal(|ui| {
            if ui.add(egui::Button::new(big("Сохранить"))).clicked() {
                self.cfg.save();
                ui.ctx().set_zoom_factor(self.cfg.font_scale);
                self.status = Some((true, "Настройки сохранены".into()));
                jobs::load_reference(&self.tx, ui.ctx(), &self.cfg);
            }
            if ui.button("Открыть папку программы").clicked() {
                let _ = open::that(config::home());
            }
        });
    }
}

impl eframe::App for App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.handle_messages(ctx);
        if self.checks_dirty {
            self.recheck(ctx);
        }
        egui::TopBottomPanel::top("tabs").show(ctx, |ui| {
            ui.add_space(4.0);
            ui.horizontal(|ui| {
                ui.label(RichText::new("Açar Söz Studio").size(19.0).strong());
                ui.add_space(16.0);
                for (t, name) in [(Tab::Generate, "Генерация"), (Tab::Review, "Ревью"), (Tab::Publish, "Выпуск"), (Tab::Settings, "Настройки")] {
                    if ui.selectable_label(self.tab == t, big(name)).clicked() {
                        self.tab = t;
                        self.confirm = None;
                    }
                }
            });
            ui.add_space(4.0);
        });
        egui::TopBottomPanel::bottom("status").show(ctx, |ui| {
            ui.horizontal(|ui| {
                if let Some(b) = &self.busy {
                    ui.spinner();
                    ui.label(b);
                } else if let Some((ok, t)) = &self.status {
                    ui.label(RichText::new(t).color(if *ok { GREEN } else { RED }));
                } else {
                    ui.label(RichText::new(format!("Уровней в игре для проверки повторов: {}", self.reference.iter().map(|p| p.levels.len()).sum::<usize>())).color(GRAY));
                }
            });
        });
        match self.tab {
            Tab::Review => self.tab_review(ctx),
            tab => {
                egui::CentralPanel::default().show(ctx, |ui| {
                    ScrollArea::vertical().show(ui, |ui| match tab {
                        Tab::Generate => self.tab_generate(ui),
                        Tab::Publish => self.tab_publish(ui),
                        _ => self.tab_settings(ui),
                    });
                });
            }
        }
        if self.save_dirty {
            self.save();
        }
        if self.busy.is_some() {
            ctx.request_repaint_after(std::time::Duration::from_millis(300));
        }
    }
}
