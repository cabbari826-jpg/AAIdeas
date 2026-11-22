//! Slow work (GitHub, generator) runs on background threads and reports back with messages.

use crate::config::{self, Config};
use crate::gh::Gh;
use crate::model::Pack;
use crate::publish;
use serde_json::{json, Value};
use std::path::PathBuf;
use std::sync::mpsc::Sender;
use std::time::{Duration, Instant};

pub enum Msg {
    Busy(Option<String>),
    Log(String),
    Info(String),
    Error(String),
    Reference { packs: Vec<Pack>, word_files: Vec<String>, downloadable: Vec<String> },
    Open(PathBuf),
    LocalReady(String),
    /// Level ids changed on publish (the number was taken): (old, new).
    Renamed(Vec<(String, String)>),
}

pub fn stamp() -> String {
    let now = time::OffsetDateTime::now_local().unwrap_or_else(|_| time::OffsetDateTime::now_utc());
    now.format(time::macros::format_description!("[year]-[month]-[day]_[hour]-[minute]-[second]")).unwrap_or_default()
}

fn spawn(tx: &Sender<Msg>, ctx: &egui::Context, what: &str, f: impl FnOnce(&Sender<Msg>) -> Result<(), String> + Send + 'static) {
    let tx = tx.clone();
    let ctx = ctx.clone();
    let what = what.to_string();
    let _ = tx.send(Msg::Busy(Some(what)));
    std::thread::spawn(move || {
        let r = f(&tx);
        if let Err(e) = r {
            let _ = tx.send(Msg::Error(e));
        }
        let _ = tx.send(Msg::Busy(None));
        ctx.request_repaint();
    });
}

fn gh(cfg: &Config) -> Gh {
    Gh::new(&cfg.token, &cfg.repo)
}

pub struct Reference {
    pub packs: Vec<Pack>,
    pub word_files: Vec<String>,
    pub downloadable: Vec<String>,
}

fn fetch_reference(g: &Gh, branch: &str) -> Result<Reference, String> {
    let mut packs = Vec::new();
    let mut downloadable = Vec::new();
    for dir in [config::PACKS_DIR, config::MINI_DIR, config::PUBLISHED_DIR] {
        for (name, url) in g.list_dir(dir, branch)? {
            if !name.ends_with(".json") || url.is_empty() {
                continue;
            }
            // With a token the API gives the fresh file; raw links can be a few minutes old.
            let text = if g.has_token() {
                g.get_file(&format!("{dir}/{name}"), branch)?.map(|x| x.0).unwrap_or_default()
            } else {
                g.get_text(&url)?
            };
            match Pack::parse(&text) {
                Ok(p) => {
                    if dir == config::PUBLISHED_DIR {
                        downloadable.push(p.pack_id.clone());
                    }
                    packs.push(p)
                }
                Err(e) => return Err(format!("{name}: {e}")),
            }
        }
    }
    let mut words: Vec<String> = g
        .list_dir(config::WORDS_DIR, branch)?
        .into_iter()
        .filter_map(|(n, _)| n.strip_suffix(".txt").map(String::from))
        .filter(|n| n != "umumi")
        .collect();
    words.sort();
    Ok(Reference { packs, word_files: words, downloadable })
}

/// The same from the local copy of the repository (when GitHub is not reachable).
fn local_reference() -> Option<Reference> {
    let root = config::repo_dir();
    if !root.join(".studio-updated").exists() {
        return None;
    }
    let mut r = Reference { packs: vec![], word_files: vec![], downloadable: vec![] };
    for dir in [config::PACKS_DIR, config::MINI_DIR, config::PUBLISHED_DIR] {
        for e in std::fs::read_dir(root.join(dir)).into_iter().flatten().flatten() {
            if e.path().extension().map(|x| x == "json").unwrap_or(false) {
                if let Some(p) = std::fs::read_to_string(e.path()).ok().and_then(|t| Pack::parse(&t).ok()) {
                    if dir == config::PUBLISHED_DIR {
                        r.downloadable.push(p.pack_id.clone());
                    }
                    r.packs.push(p);
                }
            }
        }
    }
    for e in std::fs::read_dir(root.join(config::WORDS_DIR)).into_iter().flatten().flatten() {
        let n = e.file_name().to_string_lossy().to_string();
        if let Some(id) = n.strip_suffix(".txt") {
            if id != "umumi" {
                r.word_files.push(id.to_string());
            }
        }
    }
    r.word_files.sort();
    Some(r)
}

pub fn load_reference(tx: &Sender<Msg>, ctx: &egui::Context, cfg: &Config) {
    let cfg = cfg.clone();
    spawn(tx, ctx, "Загружаю уровни, которые уже есть в игре…", move |tx| {
        let r = match fetch_reference(&gh(&cfg), &cfg.branch) {
            Ok(r) => r,
            Err(e) => match local_reference() {
                Some(r) => {
                    let _ = tx.send(Msg::Log(format!("GitHub: {e}")));
                    let _ = tx.send(Msg::Info(
                        "GitHub сейчас недоступен: повторы проверяю по скачанной копии файлов генератора.".into(),
                    ));
                    r
                }
                None => return Err(e),
            },
        };
        let n: usize = r.packs.iter().map(|p| p.levels.len()).sum();
        let _ = tx.send(Msg::Log(format!("Из GitHub загружено паков: {}, уровней: {n}", r.packs.len())));
        let _ = tx.send(Msg::Reference { packs: r.packs, word_files: r.word_files, downloadable: r.downloadable });
        Ok(())
    });
}

pub fn check_token(tx: &Sender<Msg>, ctx: &egui::Context, cfg: &Config) {
    let cfg = cfg.clone();
    spawn(tx, ctx, "Проверяю токен…", move |tx| {
        let login = gh(&cfg).whoami()?;
        let _ = tx.send(Msg::Info(format!("Токен работает, аккаунт: {login}")));
        Ok(())
    });
}

fn save_draft(pack: &Pack, tag: &str) -> Result<PathBuf, String> {
    let path = config::drafts_dir().join(format!("{}-{tag}-{}.json", pack.pack_id, stamp()));
    std::fs::write(&path, pack.to_json()).map_err(|e| e.to_string())?;
    Ok(path)
}

/// Opens a pack that is already in the game as a draft (to review or fix old levels).
pub fn draft_from(pack: &Pack) -> Result<PathBuf, String> {
    save_draft(pack, "game")
}

pub fn cloud_generate(tx: &Sender<Msg>, ctx: &egui::Context, cfg: &Config, pack: String, count: u32, seed: String) {
    let cfg = cfg.clone();
    let ctx2 = ctx.clone();
    spawn(tx, ctx, "Запускаю генерацию в облаке…", move |tx| {
        let g = gh(&cfg);
        if !g.has_token() {
            return Err("Для облака нужен токен GitHub: вкладка «Настройки».".into());
        }
        let before: Vec<u64> = match g.runs(&cfg.workflow, &cfg.branch) {
            Ok(r) => r.into_iter().map(|r| r.0).collect(),
            Err(e) if e.contains("(404)") => {
                return Err(format!(
                    "На ветке {} нет облачного генератора ({}). Проверьте «Настройки» или генерируйте на компьютере.",
                    cfg.branch, cfg.workflow
                ))
            }
            Err(e) => return Err(e),
        };
        // "publish" stays at its default (false): levels go out only after review here.
        let mut inputs = json!({ "pack": pack, "count": count.to_string() });
        if !seed.trim().is_empty() {
            inputs["seed"] = json!(seed.trim());
        }
        g.dispatch(&cfg.workflow, &cfg.branch, inputs)?;
        let _ = tx.send(Msg::Log(format!("Запущено: пак {pack}, уровней {count}")));
        let start = Instant::now();
        let (id, url) = loop {
            std::thread::sleep(Duration::from_secs(4));
            if let Some(r) = g.runs(&cfg.workflow, &cfg.branch)?.into_iter().find(|r| !before.contains(&r.0)) {
                break (r.0, r.4);
            }
            if start.elapsed() > Duration::from_secs(120) {
                return Err("GitHub не начал запуск за 2 минуты. Посмотрите вкладку Actions в репозитории.".into());
            }
        };
        let _ = tx.send(Msg::Log(format!("Запуск в GitHub: {url}")));
        loop {
            let (status, conclusion) = g.run(id)?;
            if status == "completed" {
                if conclusion != "success" {
                    return Err(format!("Генерация в облаке не удалась ({conclusion}). Подробности: {url}"));
                }
                break;
            }
            let min = start.elapsed().as_secs() / 60;
            let _ = tx.send(Msg::Busy(Some(format!("Генерация в облаке идёт, прошло {min} мин…"))));
            ctx2.request_repaint();
            if start.elapsed() > Duration::from_secs(90 * 60) {
                return Err(format!("Генерация идёт больше 90 минут. Посмотрите: {url}"));
            }
            std::thread::sleep(Duration::from_secs(10));
        }
        let _ = tx.send(Msg::Busy(Some("Скачиваю результат…".into())));
        let files = g.run_artifact_files(id)?;
        let mut opened = None;
        for (name, bytes) in &files {
            let text = String::from_utf8_lossy(bytes);
            if name.ends_with(".md") {
                let _ = tx.send(Msg::Log(format!("{name}:\n{}", text.trim())));
            } else if name.ends_with(".json") {
                if let Ok(p) = Pack::parse(&text) {
                    opened = Some(save_draft(&p, "cloud")?);
                }
            }
        }
        match opened {
            Some(p) => {
                let _ = tx.send(Msg::Open(p));
                let _ = tx.send(Msg::Info("Готово. Уровни открыты на вкладке «Ревью».".into()));
                Ok(())
            }
            None => Err(format!("В результате не нашлось файла пака. Подробности: {url}")),
        }
    });
}

pub fn local_info() -> Option<String> {
    let stamp = std::fs::read_to_string(config::repo_dir().join(".studio-updated")).ok()?;
    Some(stamp.trim().to_string())
}

pub fn update_local(tx: &Sender<Msg>, ctx: &egui::Context, cfg: &Config) {
    let cfg = cfg.clone();
    spawn(tx, ctx, "Скачиваю файлы генератора с GitHub…", move |tx| {
        let bytes = gh(&cfg).zipball(&cfg.branch)?;
        let dir = config::repo_dir();
        let _ = std::fs::remove_dir_all(&dir);
        let mut z = zip::ZipArchive::new(std::io::Cursor::new(bytes)).map_err(|e| e.to_string())?;
        let keep = ["crossword-app/tools/", "crossword-app/content/", "crossword-app/app/src/main/assets/"];
        let mut n = 0;
        for i in 0..z.len() {
            let mut f = z.by_index(i).map_err(|e| e.to_string())?;
            let name = f.name().to_string();
            let Some(rel) = name.split_once('/').map(|x| x.1.to_string()) else { continue };
            if f.is_dir() || !keep.iter().any(|k| rel.starts_with(k)) || rel.contains("..") {
                continue;
            }
            let out = dir.join(&rel);
            if let Some(p) = out.parent() {
                std::fs::create_dir_all(p).map_err(|e| e.to_string())?;
            }
            let mut w = std::fs::File::create(&out).map_err(|e| e.to_string())?;
            std::io::copy(&mut f, &mut w).map_err(|e| e.to_string())?;
            n += 1;
        }
        let s = stamp();
        let _ = std::fs::write(dir.join(".studio-updated"), &s);
        let _ = tx.send(Msg::LocalReady(s));
        let _ = tx.send(Msg::Info(format!("Файлы генератора обновлены ({n} файлов).")));
        Ok(())
    });
}

pub fn local_generate(tx: &Sender<Msg>, ctx: &egui::Context, cfg: &Config, pack: String, count: u32, seed: String) {
    let cfg = cfg.clone();
    spawn(tx, ctx, "Генерирую уровни на этом компьютере…", move |tx| {
        let dir = config::repo_dir();
        let script = dir.join("crossword-app/tools/make_levels.py");
        if !script.exists() {
            return Err("Сначала нажмите «Скачать файлы генератора» (нужен один раз и после новых слов).".into());
        }
        let out = config::drafts_dir().join(format!("{pack}-local-{}.json", stamp()));
        let mut args: Vec<String> = vec![script.to_string_lossy().into(), pack.clone(), count.to_string()];
        if !seed.trim().is_empty() {
            args.push(seed.trim().into());
        }
        args.push("--out".into());
        args.push(out.to_string_lossy().into());
        let mut tried = Vec::new();
        let mut result = None;
        let mut names = vec![cfg.python.clone()];
        for alt in ["py", "python", "python3"] {
            if !names.iter().any(|n| n == alt) {
                names.push(alt.into());
            }
        }
        for py in names {
            let mut cmd = std::process::Command::new(&py);
            cmd.args(&args).current_dir(&dir).env("PYTHONIOENCODING", "utf-8");
            #[cfg(windows)]
            {
                use std::os::windows::process::CommandExt;
                cmd.creation_flags(0x0800_0000); // no console window
            }
            match cmd.output() {
                Ok(o) => {
                    result = Some(o);
                    break;
                }
                Err(_) => tried.push(py),
            }
        }
        let Some(o) = result else {
            return Err(format!(
                "Python не найден (пробовал: {}). Установите Python с python.org (галочка «Add to PATH») или пользуйтесь облаком.",
                tried.join(", ")
            ));
        };
        let text = format!("{}{}", String::from_utf8_lossy(&o.stdout), String::from_utf8_lossy(&o.stderr));
        let _ = tx.send(Msg::Log(text.trim().to_string()));
        match o.status.code() {
            Some(0) | Some(2) if out.exists() => {
                if o.status.code() == Some(2) {
                    let _ = tx.send(Msg::Info("Слов хватило не на все уровни: получилось меньше, чем просили. Нужны новые слова.".into()));
                } else {
                    let _ = tx.send(Msg::Info("Готово. Уровни открыты на вкладке «Ревью».".into()));
                }
                let _ = tx.send(Msg::Open(out));
                Ok(())
            }
            c => Err(format!("Генератор завершился с ошибкой (код {}). Текст ошибки в журнале ниже.", c.unwrap_or(-1))),
        }
    });
}

fn summary_message(pack: &str, plan: &publish::Plan) -> String {
    format!(
        "Açar Söz Studio: {pack} +{} ~{} -{}",
        plan.added.len(),
        plan.updated.len(),
        plan.removed.len()
    )
}

/// Option 1: put the reviewed levels into the game's own pack file; the existing
/// GitHub workflow then builds a new APK and phones get it through the auto-update.
pub fn publish_bundle(tx: &Sender<Msg>, ctx: &egui::Context, cfg: &Config, draft: Pack) {
    let cfg = cfg.clone();
    spawn(tx, ctx, "Выпускаю уровни в игру…", move |tx| {
        let g = gh(&cfg);
        let path = config::pack_path(&draft.pack_id);
        let current = g.get_file(&path, &cfg.branch)?;
        let target = match &current {
            Some((t, _)) => Some(Pack::parse(t)?),
            None => None,
        };
        let (merged, plan) = publish::merge(&draft, target.as_ref(), &draft.pack_id);
        if plan.changes() == 0 {
            return Err(format!("Нечего выпускать: всё одобренное уже в игре ({}).", plan.describe()));
        }
        let url = g.put_file(&path, &cfg.branch, &merged.to_json(), current.as_ref().map(|c| c.1.as_str()), &summary_message(&draft.pack_id, &plan))?;
        for (a, b) in &plan.renamed {
            let _ = tx.send(Msg::Log(format!("Номер уровня {a} был занят, уровень выпущен как {b}")));
        }
        let _ = tx.send(Msg::Renamed(plan.renamed.clone()));
        let _ = tx.send(Msg::Log(format!("Коммит: {url}")));
        let _ = tx.send(Msg::Info(format!(
            "Выпущено в игру: {}. Новая версия соберётся за 10–20 минут, телефон предложит обновиться.",
            plan.describe()
        )));
        if let Ok(r) = fetch_reference(&g, &cfg.branch) {
            let _ = tx.send(Msg::Reference { packs: r.packs, word_files: r.word_files, downloadable: r.downloadable });
        }
        Ok(())
    });
}

/// Option 2: a downloadable pack. Kept in the repository (so the generator skips its words)
/// and uploaded to the GitHub release "packs" together with index.json.
pub fn publish_download(tx: &Sender<Msg>, ctx: &egui::Context, cfg: &Config, draft: Pack, dl_id: String, dl_title: String) {
    let cfg = cfg.clone();
    spawn(tx, ctx, "Выкладываю скачиваемый пак…", move |tx| {
        let g = gh(&cfg);
        let bundled = match g.get_file(&config::pack_path(&draft.pack_id), &cfg.branch)? {
            Some((t, _)) => Some(Pack::parse(&t)?),
            None => None,
        };
        let mut src = publish::new_only(&draft, bundled.as_ref());
        src.pack_id = dl_id.clone();
        src.title = dl_title.clone();
        let repo_path = format!("{}/{dl_id}.json", config::PUBLISHED_DIR);
        let current = g.get_file(&repo_path, &cfg.branch)?;
        let target = match &current {
            Some((t, _)) => Some(Pack::parse(t)?),
            None => None,
        };
        let (mut merged, plan) = publish::merge(&src, target.as_ref(), &draft.pack_id);
        if plan.changes() == 0 {
            return Err(format!("Нечего выкладывать: всё одобренное уже выложено ({}).", plan.describe()));
        }
        merged.title = dl_title.clone();
        let text = merged.to_json();
        g.put_file(&repo_path, &cfg.branch, &text, current.as_ref().map(|c| c.1.as_str()), &summary_message(&dl_id, &plan))?;
        let release = g.ensure_release(
            config::RELEASE_TAG,
            &cfg.branch,
            "Açar Söz: скачиваемые паки",
            "Паки уровней, которые игра скачивает сама. Список: index.json. Не удалять.",
        )?;
        let file = format!("{dl_id}.json");
        let url = g.upload_asset(&release, &file, &text)?;
        let mut index: Value = g
            .asset_text(&release, "index.json")?
            .and_then(|t| serde_json::from_str(&t).ok())
            .unwrap_or_else(|| json!({ "formatVersion": 1, "packs": [] }));
        let entry = json!({
            "packId": dl_id, "title": dl_title, "levels": merged.levels.len(),
            "contentVersion": merged.content_version, "file": file, "bytes": text.len(),
            "after": draft.pack_id,
        });
        let list = index["packs"].as_array_mut().ok_or("index.json испорчен")?;
        list.retain(|p| p["packId"] != json!(dl_id));
        list.push(entry);
        index["updated"] = json!(stamp());
        let index_text = serde_json::to_string_pretty(&index).unwrap_or_default();
        // Re-read the release: the old index asset id is needed to replace it.
        let release = g.ensure_release(config::RELEASE_TAG, &cfg.branch, "", "")?;
        g.upload_asset(&release, "index.json", &index_text)?;
        let _ = tx.send(Msg::Renamed(plan.renamed.clone()));
        let _ = tx.send(Msg::Log(format!("Файл пака: {url}")));
        let _ = tx.send(Msg::Info(format!("Скачиваемый пак «{dl_title}» выложен: {}", plan.describe())));
        if let Ok(r) = fetch_reference(&g, &cfg.branch) {
            let _ = tx.send(Msg::Reference { packs: r.packs, word_files: r.word_files, downloadable: r.downloadable });
        }
        Ok(())
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    fn wait(rx: &std::sync::mpsc::Receiver<Msg>) -> Vec<String> {
        let mut out = vec![];
        loop {
            match rx.recv_timeout(Duration::from_secs(30)).expect("job hung") {
                Msg::Busy(None) => return out,
                Msg::Info(t) => out.push(format!("INFO {t}")),
                Msg::Error(t) => out.push(format!("ERR {t}")),
                Msg::Log(t) => out.push(format!("LOG {t}")),
                _ => {}
            }
        }
    }

    /// Runs against tools' fake GitHub when ACAR_STUDIO_TEST_API is set (see crossword-studio/README).
    #[test]
    fn publish_against_fake_github() {
        if std::env::var("ACAR_STUDIO_TEST_API").is_err() {
            return;
        }
        let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../crossword-app/app/src/main/assets/packs/az-kimya.json");
        let base = Pack::parse(&std::fs::read_to_string(path).unwrap()).unwrap();
        let mut draft = base.clone();
        let mut n = base.levels[1].clone();
        n.id = "az-kimya-013".into();
        n.entries[0].answer = "ZZZ".into();
        n.review = Some("ok".into());
        draft.levels.push(n.clone());
        draft.levels[0].edited = true;
        draft.levels[0].entries[0].clues[0] = "Təzə".into();
        let cfg = Config { token: "t".into(), repo: "o/r".into(), branch: "b".into(), ..Config::default() };
        let ctx = egui::Context::default();
        let (tx, rx) = std::sync::mpsc::channel();
        publish_bundle(&tx, &ctx, &cfg, draft.clone());
        let r = wait(&rx);
        println!("{r:#?}");
        assert!(r.iter().any(|m| m.starts_with("INFO Выпущено в игру: новых уровней: 1 · исправленных: 1")));
        // Second time: nothing new.
        publish_bundle(&tx, &ctx, &cfg, draft.clone());
        let r = wait(&rx);
        assert!(r.iter().any(|m| m.starts_with("ERR Нечего выпускать: всё одобренное уже в игре")), "{r:?}");
        // Downloadable pack: a new level the game does not have.
        let mut d2 = draft.clone();
        let mut m = base.levels[2].clone();
        m.id = "az-kimya-014".into();
        m.entries[0].answer = "YYY".into();
        m.review = Some("ok".into());
        d2.levels.push(m);
        for _ in 0..2 {
            publish_download(&tx, &ctx, &cfg, d2.clone(), "az-kimya-plus".into(), "Kimya +".into());
            let r = wait(&rx);
            println!("{r:#?}");
        }
    }
}
