fn main() {
    // Explorer icon for the .exe (the Açar Söz app icon).
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows") {
        let mut res = winresource::WindowsResource::new();
        res.set_icon("assets/icon.ico");
        res.set("ProductName", "Açar Söz Studio");
        res.set("FileDescription", "Açar Söz Studio");
        if let Err(e) = res.compile() {
            println!("cargo:warning=icon not embedded: {e}");
        }
    }
}
