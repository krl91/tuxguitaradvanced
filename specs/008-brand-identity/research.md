# Research: integrating the official logo (spec 008)

Every claim cites a file that was opened on 2026-10-13.

## R1 - Where the application and its packages take their images

**Evidence.**
- Window and splash: `desktop/TuxGuitar/src/app/tuxguitar/app/system/icons/TGIconManager.java` lines 305-308 load `icon.png`, `splash.png`, `about_description.png`, `about_license.png` from the skin; `TGSplash.java` uses `getAppIcon()` and `getAppSplash()`; ten dialogs use `getAppIcon()` as their window image.
- The six skins (`share/skins/Breeze`, `Breeze-dark`, `Oxygen`, `Oxygen-dark`, `Symbolic`, `Symbolic-dark`) each hold `icon.png` (96 x 96) and `splash.png` (500 x 300); `src-skins/{Breeze,Oxygen,Symbolic}` hold the sources of the same files.
- macOS: `common-macosx/Contents/Info.plist` (`CFBundleIconFile` = `icon.icns`, four `CFBundleTypeIconFile` entries for documents), copied into the `.app` by `tuxguitar-macosx-swt-cocoa/pom.xml` (`<fileset dir="../common-resources/common-macosx" />`).
- Windows: `tuxguitar-windows-swt-x86_64-installer/src/installbuilder/project.xml` lines 9-18: `TG_logo_163x314.png` (left image), `TG_logo_48x48.png`, `TG_splash_500x300.png`, `TG_icon_install.ico`, `TG_icon_uninstall.ico`, and `dist/tuxguitar.ico` as the product display icon.
- Linux: `common-linux/share/applications/TuxGuitar.desktop` (`Icon=tuxguitar`), `share/pixmaps/tuxguitar.png` (96 x 96), `share/metainfo/app.tuxguitar.tuxguitar.metainfo.xml` (`<icon type="stock">tuxguitar</icon>`).

**Decision.** Replace the files in place, with the same names and formats: no build script, no Java class changes (FR-013, constitution II).

## R2 - What is not the logo and stays as it is

**Evidence.** `about_description.png`, `about_authors.png`, `about_license.png` are pictograms (an *i*, two people, a *c* in a circle) of the three tabs of `TGAboutDialog`, not the product logo (viewed on 2026-10-13). `tgdoc.icns` is the icon of the `.tg` score documents (`CFBundleTypeIconFile`).

**Decision.** They are not changed by the beta (open for the owner as a later derivative: a document icon built on the logo would be a new derivative of FR-011, with its own approval).

## R3 - Android and the other variants

**Evidence.** `android/` has its own icons and is not built by the `Release` workflow, which builds macOS Apple Silicon, Windows and Linux (`.github/workflows/release.yml`). The FreeBSD, JavaFX and Qt build scripts share `common-resources`.

**Decision.** Android is out of the beta (not a surface of the releases); the shared `common-resources` files serve the other desktop variants without extra work.

## R4 - Tools for the derivatives

**Evidence.** On 2026-10-13 `sips`, `iconutil`, `magick` (ImageMagick, also `convert`) and `rsvg-convert` are installed; Python has no Pillow.

**Decision.** Downscale with `magick -filter Lanczos -resize` (alpha kept), build `.icns` with `iconutil` from an `.iconset` folder, build `.ico` with `magick`. Scripts are kept in the repository (`misc/branding/`) so that the derivatives can be made again from the master with the same result; the master is never opened for writing.

**Alternatives.** An image editor by hand (rejected: not reproducible, not auditable against FR-002).

## R5 - The small icon

**Decision.** At 16, 32, 48, 64 px the full wordmark cannot be read, so an **icon-only derivative** is used (FR-007, US2): the proposal is a **geometric rectangular crop** of the master above the wordmark (the penguin, the guitar and the note highway), then downscaled. A rectangular crop with fixed coordinates is not content-aware, but it may cut a part of the art: this is decided on the contact sheet by the owner. From 128 px the full logo is proposed. The sizes 128 to 1024 are reviewed on both backgrounds in any case (FR-009).

**Alternatives.** Squeezing or cropping the wordmark to fit (forbidden, FR-006, out of scope of the spec); drawing a new simplified mark (forbidden, FR-002).

## R6 - Splash and installer images

**Decision.** The splash (500 x 300) and the installer images (163 x 314 panel, 48 x 48 logo, 500 x 300 splash) are **compositions**: the unmodified master (or the icon-only derivative at 48 px) scaled uniformly and centred with safe margins on a plain background. Their background is chosen on the contact sheet (transparent where the host allows alpha, a neutral dark otherwise). They are horizontal or vertical treatments (FR-011) and need the owner's approval like any derivative.

## R7 - How FR-014 and FR-015 are met

**Decision.** (a) `BrandingAssetsTest` reads `branding-manifest.json` and checks the master digest, that each declared file exists, decodes (PNG by `ImageIO`, ICO and ICNS by their headers) and has the declared dimensions; (b) `misc/verify-branding.sh` opens each package (`.app` folder or its zip, Windows zip, Linux tar.gz and deb) and checks that the declared icon files are inside and equal (SHA-256) to the repository files; (c) the manual checks (Finder, Dock, window, splash on macOS; launcher, window, installer on Windows and Linux) are in `quickstart.md` and are reported done or not done, per platform.

**Alternatives.** Looking at the packages only by hand (rejected: FR-014 asks for an automatic test).

## R8 - Provenance and release

**Decision.** The provenance sentence of FR-016 (AI-assisted redesign selected by the product owner; no ownership of third-party marks asserted; upstream notices intact) is added to the documentation that shows the logo and to the release notes of the beta. The beta tag is `v0.5.0-beta.1`, confirmed by the owner before the workflow runs and never moved once published; the notes file is `docs/release-notes/v0.5.0-beta.1.md` (the workflow uses it when it exists).
