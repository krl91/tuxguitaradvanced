# Contract: the branding manifest (spec 008)

Every file that carries the product identity is declared here, with its source, its dimensions and the approval of its derivative (FR-005). The machine-readable copy `specs/008-brand-identity/branding-manifest.json` (created in increment B3, same content) is what `BrandingAssetsTest` and `misc/verify-branding.sh` read. **State on 2026-10-13: no derivative is approved yet; no file below is replaced yet.**

## Master

| File | Format | SHA-256 | Status |
|---|---|---|---|
| `specs/008-brand-identity/assets/tuxguitaradvanced-logo-primary.png` | PNG 1254 x 1254 RGBA | `a9d369a2574396cd3208b0665648c57f398690499efb137d6781478991baf9e0` | official, owner-selected 2026-10-08 |

## Derivatives (proposed)

| Id | Kind | Operation (from the master only) | Proposed use | Status |
|---|---|---|---|---|
| D1 | full | uniform downscale (Lanczos, alpha kept) to 128, 256, 512, 1024 | icon entries of 128 px and more, README, release notes | proposed |
| D2 | icon-only | rectangular crop of the master above the wordmark (fixed coordinates chosen on the contact sheet, nothing retouched), then uniform downscale to 16, 24, 32, 48, 64, 96 | every icon of less than 128 px | proposed |
| D3 | splash | D1 scaled uniformly and centred, safe margins, on a plain background, 500 x 300 | application splash, installer splash | proposed |
| D4 | panel | D1 scaled uniformly and centred, safe margins, on a plain background, 163 x 314 | Windows installer left image | proposed |

## Surfaces

| Id | Platform | File (repository path) | Format and sizes | Derivative | Package location |
|---|---|---|---|---|---|
| S1 | macOS | `desktop/build-scripts/common-resources/common-macosx/Contents/Resources/icon.icns` | ICNS: 16, 32, 64, 128, 256, 512, 1024 (the `@2x` entries included) | D2 below 128, D1 from 128 | `<app>/Contents/Resources/icon.icns` |
| S2 | Windows | `desktop/build-scripts/common-resources/common-windows/dist/tuxguitar.ico` | ICO: 16, 24, 32, 48, 64, 96, 128, 256 | D2 below 128, D1 from 128 | `dist/tuxguitar.ico` of the package and of the installer |
| S3 | Windows | `.../common-windows-installer-icons/TG_icon_install.ico` | ICO as S2 | as S2 | installer executable |
| S4 | Windows | `.../common-windows-installer-icons/TG_icon_uninstall.ico` | ICO as S2 | as S2 | uninstaller executable |
| S5 | Windows | `.../common-windows-installer-icons/TG_logo_48x48.png` | PNG 48 x 48 | D2 | installer |
| S6 | Windows | `.../common-windows-installer-icons/TG_logo_163x314.png` | PNG 163 x 314 | D4 | installer |
| S7 | Windows | `.../common-windows-installer-icons/TG_splash_500x300.png` | PNG 500 x 300 | D3 | installer |
| S8 | Linux | `desktop/build-scripts/common-resources/common-linux/share/pixmaps/tuxguitar.png` | PNG 96 x 96 | D2 | `share/pixmaps/tuxguitar.png` of the tar.gz and the deb |
| S9 | application | `desktop/TuxGuitar/share/skins/<skin>/icon.png` for the six skins, and `src-skins/<skin>/...` | PNG 96 x 96 | D2 | `share/skins/<skin>/icon.png` |
| S10 | application | `desktop/TuxGuitar/share/skins/<skin>/splash.png` for the six skins, and `src-skins/<skin>/...` | PNG 500 x 300 | D3 | `share/skins/<skin>/splash.png` |
| S11 | docs | `README.md`, `docs/README-FR.md`, `docs/README-ES.md` | the master itself, referenced by its path (no copy: one master, SC-001) | master | - |
| S12 | docs | `docs/release-notes/v0.5.0-beta.1.md` | the master, by its raw GitHub address | master | - |

Not declared on purpose: `about_*.png`, `tgdoc.icns`, Android, upstream `website/` and `misc/` logos (`research.md` R2, R3).

## Approvals (written by the owner's decision, FR-004, FR-010)

| Derivative | Decision | Date | Note |
|---|---|---|---|
| D1 | pending | | |
| D2 | pending | | the crop coordinates are chosen on the contact sheet |
| D3 | pending | | the background is chosen on the contact sheet |
| D4 | pending | | |
