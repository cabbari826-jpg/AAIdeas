//! Small GitHub REST client (free GitHub API; the token is the user's own).

use base64::Engine;
use serde_json::{json, Value};
use std::io::Read;
use std::time::Duration;

pub struct Gh {
    agent: ureq::Agent,
    token: String,
    pub repo: String, // owner/name
}

pub type R<T> = Result<T, String>;

fn explain(e: ureq::Error) -> String {
    match e {
        ureq::Error::Status(code, resp) => {
            let body = resp.into_string().unwrap_or_default();
            let msg = serde_json::from_str::<Value>(&body)
                .ok()
                .and_then(|v| v["message"].as_str().map(String::from))
                .unwrap_or(body);
            match code {
                401 => "GitHub не принял токен (401). Проверьте токен в «Настройках».".into(),
                403 | 404 if msg.contains("rate limit") => "GitHub просит подождать: лимит запросов. Добавьте токен в «Настройках».".into(),
                403 => format!("У токена нет нужного права (403): {msg}. В «Настройках» описано, какие права дать."),
                404 => format!("Не найдено (404): {msg}"),
                _ => format!("Ошибка GitHub {code}: {msg}"),
            }
        }
        ureq::Error::Transport(t) => format!("Нет связи с GitHub: {t}"),
    }
}

impl Gh {
    pub fn new(token: &str, repo: &str) -> Gh {
        let agent = ureq::AgentBuilder::new()
            .timeout_connect(Duration::from_secs(20))
            .timeout(Duration::from_secs(300))
            .user_agent("acar-soz-studio")
            .build();
        Gh { agent, token: token.trim().to_string(), repo: repo.trim().to_string() }
    }

    pub fn has_token(&self) -> bool {
        !self.token.is_empty()
    }

    fn req(&self, method: &str, url: &str) -> ureq::Request {
        let url = if url.starts_with("http") { url.to_string() } else { format!("{}{url}", api_base()) };
        let mut r = self
            .agent
            .request(method, &url)
            .set("Accept", "application/vnd.github+json")
            .set("X-GitHub-Api-Version", "2022-11-28");
        if !self.token.is_empty() {
            r = r.set("Authorization", &format!("Bearer {}", self.token));
        }
        r
    }

    fn get_json(&self, url: &str) -> R<Value> {
        self.req("GET", url).call().map_err(explain)?.into_json().map_err(|e| e.to_string())
    }

    fn read_all(resp: ureq::Response) -> R<Vec<u8>> {
        let mut buf = Vec::new();
        resp.into_reader().take(200 << 20).read_to_end(&mut buf).map_err(|e| e.to_string())?;
        Ok(buf)
    }

    pub fn whoami(&self) -> R<String> {
        let v = self.get_json("/user")?;
        Ok(v["login"].as_str().unwrap_or("?").to_string())
    }

    /// Files of a folder on a branch: (name, download_url). Empty if the folder does not exist.
    pub fn list_dir(&self, path: &str, branch: &str) -> R<Vec<(String, String)>> {
        let v = match self.get_json(&format!("/repos/{}/contents/{path}?ref={}", self.repo, enc(branch))) {
            Ok(v) => v,
            Err(e) if e.contains("(404)") => return Ok(vec![]),
            Err(e) => return Err(e),
        };
        Ok(v.as_array()
            .map(|a| {
                a.iter()
                    .filter(|f| f["type"] == "file")
                    .map(|f| (f["name"].as_str().unwrap_or("").to_string(), f["download_url"].as_str().unwrap_or("").to_string()))
                    .collect()
            })
            .unwrap_or_default())
    }

    /// Text from a plain URL (raw files, public release assets).
    pub fn get_text(&self, url: &str) -> R<String> {
        let resp = self.agent.get(url).call().map_err(explain)?;
        Ok(String::from_utf8_lossy(&Self::read_all(resp)?).into_owned())
    }

    /// Raw file text and its sha, or None if the file does not exist.
    pub fn get_file(&self, path: &str, branch: &str) -> R<Option<(String, String)>> {
        let url = format!("/repos/{}/contents/{path}?ref={}", self.repo, enc(branch));
        let meta = match self.req("GET", &url).call() {
            Ok(r) => r.into_json::<Value>().map_err(|e| e.to_string())?,
            Err(ureq::Error::Status(404, _)) => return Ok(None),
            Err(e) => return Err(explain(e)),
        };
        let sha = meta["sha"].as_str().unwrap_or("").to_string();
        let raw = self
            .req("GET", &url)
            .set("Accept", "application/vnd.github.raw+json")
            .call()
            .map_err(explain)?;
        let bytes = Self::read_all(raw)?;
        Ok(Some((String::from_utf8_lossy(&bytes).into_owned(), sha)))
    }

    /// Creates or updates one file with one commit.
    pub fn put_file(&self, path: &str, branch: &str, text: &str, sha: Option<&str>, message: &str) -> R<String> {
        let mut body = json!({
            "message": message,
            "content": base64::engine::general_purpose::STANDARD.encode(text.as_bytes()),
            "branch": branch,
        });
        if let Some(s) = sha {
            body["sha"] = json!(s);
        }
        let v: Value = self
            .req("PUT", &format!("/repos/{}/contents/{path}", self.repo))
            .send_json(body)
            .map_err(explain)?
            .into_json()
            .map_err(|e| e.to_string())?;
        Ok(v["commit"]["html_url"].as_str().unwrap_or("").to_string())
    }

    pub fn dispatch(&self, workflow: &str, branch: &str, inputs: Value) -> R<()> {
        self.req("POST", &format!("/repos/{}/actions/workflows/{workflow}/dispatches", self.repo))
            .send_json(json!({ "ref": branch, "inputs": inputs }))
            .map_err(explain)?;
        Ok(())
    }

    /// Newest workflow_dispatch runs: (id, status, conclusion, created_at, html_url).
    pub fn runs(&self, workflow: &str, branch: &str) -> R<Vec<(u64, String, String, String, String)>> {
        let v = self.get_json(&format!(
            "/repos/{}/actions/workflows/{workflow}/runs?event=workflow_dispatch&branch={}&per_page=10",
            self.repo,
            enc(branch)
        ))?;
        Ok(v["workflow_runs"]
            .as_array()
            .map(|a| {
                a.iter()
                    .map(|r| {
                        (
                            r["id"].as_u64().unwrap_or(0),
                            r["status"].as_str().unwrap_or("").to_string(),
                            r["conclusion"].as_str().unwrap_or("").to_string(),
                            r["created_at"].as_str().unwrap_or("").to_string(),
                            r["html_url"].as_str().unwrap_or("").to_string(),
                        )
                    })
                    .collect()
            })
            .unwrap_or_default())
    }

    pub fn run(&self, id: u64) -> R<(String, String)> {
        let v = self.get_json(&format!("/repos/{}/actions/runs/{id}", self.repo))?;
        Ok((v["status"].as_str().unwrap_or("").into(), v["conclusion"].as_str().unwrap_or("").into()))
    }

    /// Downloads every artifact of a run and returns the files inside: (name, bytes).
    pub fn run_artifact_files(&self, id: u64) -> R<Vec<(String, Vec<u8>)>> {
        let v = self.get_json(&format!("/repos/{}/actions/runs/{id}/artifacts", self.repo))?;
        let mut out = Vec::new();
        for a in v["artifacts"].as_array().cloned().unwrap_or_default() {
            let url = a["archive_download_url"].as_str().unwrap_or("").to_string();
            let resp = self.req("GET", &url).call().map_err(explain)?;
            let bytes = Self::read_all(resp)?;
            let mut z = zip::ZipArchive::new(std::io::Cursor::new(bytes)).map_err(|e| e.to_string())?;
            for i in 0..z.len() {
                let mut f = z.by_index(i).map_err(|e| e.to_string())?;
                if f.is_dir() {
                    continue;
                }
                let name = f.name().to_string();
                let mut buf = Vec::new();
                f.read_to_end(&mut buf).map_err(|e| e.to_string())?;
                out.push((name, buf));
            }
        }
        Ok(out)
    }

    /// The release with this tag; created (as a pre-release, never "latest") if missing.
    pub fn ensure_release(&self, tag: &str, branch: &str, title: &str, notes: &str) -> R<Value> {
        match self.req("GET", &format!("/repos/{}/releases/tags/{tag}", self.repo)).call() {
            Ok(r) => return r.into_json().map_err(|e| e.to_string()),
            Err(ureq::Error::Status(404, _)) => {}
            Err(e) => return Err(explain(e)),
        }
        self.req("POST", &format!("/repos/{}/releases", self.repo))
            .send_json(json!({
                "tag_name": tag, "target_commitish": branch, "name": title, "body": notes,
                "prerelease": true, "make_latest": "false"
            }))
            .map_err(explain)?
            .into_json()
            .map_err(|e| e.to_string())
    }

    /// Text of a release asset, if present.
    pub fn asset_text(&self, release: &Value, name: &str) -> R<Option<String>> {
        for a in release["assets"].as_array().cloned().unwrap_or_default() {
            if a["name"] == name {
                let url = a["url"].as_str().unwrap_or("");
                let resp = self.req("GET", url).set("Accept", "application/octet-stream").call().map_err(explain)?;
                return Ok(Some(String::from_utf8_lossy(&Self::read_all(resp)?).into_owned()));
            }
        }
        Ok(None)
    }

    /// Uploads (or replaces) a release asset. Returns its download URL.
    pub fn upload_asset(&self, release: &Value, name: &str, text: &str) -> R<String> {
        for a in release["assets"].as_array().cloned().unwrap_or_default() {
            if a["name"] == name {
                let id = a["id"].as_u64().unwrap_or(0);
                self.req("DELETE", &format!("/repos/{}/releases/assets/{id}", self.repo)).call().map_err(explain)?;
            }
        }
        let id = release["id"].as_u64().unwrap_or(0);
        let url = format!("{}/repos/{}/releases/{id}/assets?name={}", uploads_base(), self.repo, enc(name));
        let v: Value = self
            .req("POST", &url)
            .set("Content-Type", "application/json")
            .send_bytes(text.as_bytes())
            .map_err(explain)?
            .into_json()
            .map_err(|e| e.to_string())?;
        Ok(v["browser_download_url"].as_str().unwrap_or("").to_string())
    }

    /// Whole branch as a zip (to run the generator on this computer).
    pub fn zipball(&self, branch: &str) -> R<Vec<u8>> {
        let resp = self.req("GET", &format!("/repos/{}/zipball/{}", self.repo, enc(branch))).call().map_err(explain)?;
        Self::read_all(resp)
    }
}

/// Only tests point these elsewhere (a local fake GitHub).
fn api_base() -> String {
    std::env::var("ACAR_STUDIO_TEST_API").unwrap_or_else(|_| "https://api.github.com".into())
}
fn uploads_base() -> String {
    std::env::var("ACAR_STUDIO_TEST_API").unwrap_or_else(|_| "https://uploads.github.com".into())
}

pub fn enc(s: &str) -> String {
    let mut out = String::new();
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' | b'/' => out.push(b as char),
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}
