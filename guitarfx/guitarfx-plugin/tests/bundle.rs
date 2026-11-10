//! The plugin's bundles (spec 027, T056): the Audio Unit's version in `guitarfx/pom.xml` is the workspace's version as an Audio Unit reads
//! it, and the bundle templates name the identifiers of contracts/plugin.md.

fn property(pom: &str, name: &str) -> String {
    let open = format!("<{name}>");
    let start = pom.find(&open).unwrap_or_else(|| panic!("{name} in pom.xml")) + open.len();
    pom[start..start + pom[start..].find('<').unwrap()].to_string()
}

#[test]
fn the_audio_units_version_is_the_workspaces() {
    let pom = std::fs::read_to_string(concat!(env!("CARGO_MANIFEST_DIR"), "/../pom.xml")).unwrap();
    let version = env!("CARGO_PKG_VERSION");
    assert_eq!(property(&pom, "guitarfx.version"), version);
    let parts: Vec<u32> = version.split('.').map(|p| p.parse().unwrap()).collect();
    let expected = parts[0] * 65536 + parts[1] * 256 + parts[2];
    assert_eq!(property(&pom, "guitarfx.au.version"), expected.to_string());
}

#[test]
fn the_audio_unit_template_has_the_contracts_codes() {
    let text = std::fs::read_to_string(concat!(env!("CARGO_MANIFEST_DIR"), "/packaging/macos/component.Info.plist")).unwrap();
    for (key, value) in [
        ("type", guitarfx_plugin::AU_TYPE),
        ("subtype", guitarfx_plugin::AU_SUBTYPE),
        ("manufacturer", guitarfx_plugin::AU_MANUFACTURER),
        ("factoryFunction", "GetPluginFactoryAUV2"),
    ] {
        assert!(
            text.contains(&format!("<key>{key}</key>\n\t\t\t<string>{value}</string>")),
            "{key} = {value}"
        );
    }
}
