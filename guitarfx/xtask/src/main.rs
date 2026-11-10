//! `cargo run -p xtask -- parameters`: writes docs/guitarfx/parameters.md from the registry.

fn main() {
    let task = std::env::args().nth(1);
    match task.as_deref() {
        Some("parameters") => {
            let path = xtask::parameters_path();
            if let Err(error) = std::fs::write(&path, xtask::parameters_markdown()) {
                eprintln!("cannot write {}: {error}", path.display());
                std::process::exit(1);
            }
            println!("written: {}", path.display());
        }
        _ => {
            eprintln!("usage: cargo run -p xtask -- parameters");
            std::process::exit(2);
        }
    }
}
