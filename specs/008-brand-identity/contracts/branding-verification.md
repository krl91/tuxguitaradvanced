# Contract: verifying the branding (spec 008, FR-014, FR-015, SC-001, SC-003, SC-006)

## Automatic on the repository: `BrandingAssetsTest` (JUnit 5, `desktop/TuxGuitar-practice/test/.../branding/`)

1. The master exists and its SHA-256 is `a9d369a2574396cd3208b0665648c57f398690499efb137d6781478991baf9e0`, its size 1254 x 1254 with an alpha channel (SC-001).
2. Every surface of `branding-manifest.json` exists, is readable, and has the declared format and dimensions: PNG by `ImageIO` (width, height, alpha); ICO by its header and entries (the declared sizes are all there, none missing); ICNS by its header and the types of its entries (SC-003).
3. The six skins carry the same `icon.png` and the same `splash.png` (byte-equal), and `src-skins` equals `share/skins` for the files of the manifest.
4. Every derivative used by a surface has the status `approved` in the manifest (FR-010, FR-017): the test fails on a `proposed` derivative when the property `branding.release=true` is set (the release workflow sets it).
5. No application class is part of the change: the test of the beta checks nothing of the application (FR-013); the existing tests are run unchanged (SC-006).

## Automatic on the packages: `misc/verify-branding.sh <package> <platform>`

For each package built by the `Release` workflow (macOS `.app` folder or its zip, Windows zip, Linux tar.gz, Linux deb) it checks that every file declared with a package location (manifest, column "Package location") **is inside the package and has the same SHA-256 as the repository file**, and fails otherwise. It checks presence and identity, not looks (FR-014).

## Manual, per platform (FR-015, quickstart.md)

- **macOS (done by the owner, G2):** Finder, Dock, the window title bar, the splash, the switcher (Cmd-Tab), the About window.
- **Windows and Linux:** launcher, window, installer and uninstaller (Windows), menu entry and window (Linux). Reported **done** or **not done** per platform in the release notes; a platform nobody looked at is never described as validated (constitution XII).
- **Visual review of the derivatives (G1):** contact sheets at 16, 32, 64, 128, 256, 512 and 1024 px on a light and a dark background; the owner looks for a lost silhouette, an unintended opaque square or an unreadable wordmark where the full logo is used (SC-004).
