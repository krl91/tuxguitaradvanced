//! With MinGW (spec 027, T056): the VST3 wrapper's `FOLDERID_*` come from `libuuid`, which clap-wrapper does not link; and its C++
//! standard library is linked statically, so that the plugin's DLL needs no `libstdc++-6.dll` next to the host's program. A folder
//! holding only the static library comes first in the search, so `-lstdc++` finds it before the DLL's import library.

use std::path::PathBuf;
use std::process::Command;

fn main() {
    let os = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    let env = std::env::var("CARGO_CFG_TARGET_ENV").unwrap_or_default();
    if os != "windows" || env != "gnu" {
        return;
    }
    // after the wrapper's static library on the line, which needs it
    println!("cargo:rustc-link-arg=-luuid");
    let target = std::env::var("TARGET").unwrap();
    let compiler = std::env::var(format!("CXX_{}", target.replace('-', "_")))
        .or_else(|_| std::env::var("CXX"))
        .unwrap_or_else(|_| "x86_64-w64-mingw32-g++".to_string());
    let output = Command::new(&compiler)
        .arg("-print-file-name=libstdc++.a")
        .output()
        .unwrap_or_else(|e| panic!("{compiler}: {e}"));
    let library = PathBuf::from(String::from_utf8_lossy(&output.stdout).trim());
    assert!(
        library.is_absolute() && library.exists(),
        "{compiler} has no static libstdc++: {}",
        library.display()
    );
    let folder = PathBuf::from(std::env::var("OUT_DIR").unwrap()).join("static-stdc++");
    std::fs::create_dir_all(&folder).unwrap();
    std::fs::copy(&library, folder.join("libstdc++.a")).unwrap();
    // clap-wrapper asks for `Shell32`, MinGW ships `libshell32.a`: found only on a case-insensitive disk (macOS), not on Linux
    let output = Command::new(&compiler)
        .arg("-print-file-name=libshell32.a")
        .output()
        .unwrap_or_else(|e| panic!("{compiler}: {e}"));
    let shell32 = PathBuf::from(String::from_utf8_lossy(&output.stdout).trim());
    assert!(
        shell32.is_absolute() && shell32.exists(),
        "{compiler} has no libshell32.a: {}",
        shell32.display()
    );
    std::fs::copy(&shell32, folder.join("libShell32.a")).unwrap();
    println!("cargo:rustc-link-search=native={}", folder.display());
    println!("cargo:rerun-if-env-changed=CXX_{}", target.replace('-', "_"));
}
