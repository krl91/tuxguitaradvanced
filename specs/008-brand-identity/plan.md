# Implementation Plan: TuxGuitarAdvanced brand identity (official logo integration)

**Branch**: `feature/interactive-practice` (spec dir `008-brand-identity`) | **Date**: 2026-10-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification `specs/008-brand-identity/spec.md` and the owner decisions of 2026-10-13 (Clarifications of the spec): the next release is the beta **0.5.0-beta.1**, carrying the new logo.

## Summary

Replace the upstream TuxGuitar identity images of the packaged application by the official logo of the owner, **without redrawing it** (FR-002): the master `assets/tuxguitaradvanced-logo-primary.png` stays byte-identical (SHA-256 `a9d369a2574396cd3208b0665648c57f398690499efb137d6781478991baf9e0`); every other image is a derivative made from it by downscaling and geometric margins or crops, listed in a manifest, shown on contact sheets at 16 to 1024 px on light and dark backgrounds, and **approved by the owner before it replaces any file** (FR-005, FR-009, FR-010, SC-005). The work is only files, tests and a package verifier: **no Java class of the application changes** (FR-013). It is delivered in gated increments (the owner approves at each gate), then released as the beta 0.5.0-beta.1 by the existing `Release` workflow.

## Technical Context

**Language/Version**: none for the assets; the tests are JUnit 5 in the fork module `desktop/TuxGuitar-practice` (Java release 11, tests outside `src/`); the verifier is a POSIX shell script; the derivatives are made with the tools of the macOS machine (`sips`, `iconutil`, ImageMagick `magick`, all present on 2026-10-13).

**Primary Dependencies**: none new. The packaging already copies `desktop/build-scripts/common-resources/*` (macOS `tuxguitar-macosx-swt-cocoa/pom.xml`, Windows installer `project.xml`, Linux `share/pixmaps`) and the skins `desktop/TuxGuitar/share/skins/*` (loaded by `TGIconManager.loadIcon("icon.png" | "splash.png" | ...)`).

**Storage**: files in the repository (binary assets: 28 files replaced plus the master, counted on 2026-10-14: 1 icns, 3 ico, 3 installer PNGs, 1 Linux PNG, 10 skin icons, 10 skin splashes).

**Testing**: `BrandingAssetsTest` (source assets against the manifest) in `desktop/TuxGuitar-practice/test/.../branding/`, `misc/verify-branding.sh` (packages) run in the `Release` workflow, contact sheets for the visual review (human), packaged-build checks (human).

**Target Platform**: macOS Apple Silicon is built **and** checked by hand; Windows (cross-built on Linux) and Linux (tar.gz and deb) are built by the workflow and checked by the verifier only until the owner can look at them: they are **not claimed as visually validated** (constitution IX, XII).

**Project Type**: desktop application (resources and packaging).

**Performance Goals**: none. The splash image keeps the size class of the current one (500 x 300) so the start is not slower.

**Constraints**: FR-002 (no regeneration, no retouching, no content-aware crop), FR-006 (aspect ratio, alpha, safe margins), FR-007 (no squeezed wordmark: a small icon is a prepared derivative), FR-013 (no behaviour change), FR-016 (provenance, upstream notices intact).

**Scale/Scope**: 28 image files replaced in the repository (10 locations for the skin icon and the skin splash, because `share/skins` has 6 skins and `src-skins` 4 source folders) (listed in `contracts/branding-manifest.md`), 7 sizes reviewed on 2 backgrounds (D1 exists only from 128 px, D2 only up to 256 px: the full logo is used from 128 px because the wordmark cannot be read below, and the icon-only version is not needed above 256 px; FR-009 allows this exclusion when its reason is stated), 3 platforms.

## Constitution Check

*GATE: passed before research; re-checked after the design (below).*

| Principle | Check |
|---|---|
| I Preserve TuxGuitar compatibility | Only images change. No file format, setting, plugin or behaviour changes (FR-013). The skins keep their file names, so every skin and every plugin that loads them keeps working. |
| II Extend before modifying core | No code of `desktop/TuxGuitar` is touched: the application already loads `icon.png` and `splash.png` by name (`TGIconManager`, lines 305-306). Only resource files change. |
| III Evidence-based | The surfaces come from inspected files: `TGIconManager.java`, `TGSplash.java`, `TGAboutDialog.java`, `common-macosx/Contents/Info.plist` (`CFBundleIconFile icon.icns`), `tuxguitar-macosx-swt-cocoa/pom.xml` (copies `common-macosx`), the installer `project.xml`, `TuxGuitar.desktop`, `share/pixmaps/tuxguitar.png` (see `research.md`). |
| IV Spec-driven | Spec 008, then this plan, then `tasks.md`; the owner decisions are recorded in the spec before any asset is made. |
| V Incremental | Four increments, each ending in a gate the owner decides (B1 derivatives approved, B2 integration reviewed, B3 tests and verifier green, B4 beta published). |
| IX Cross-platform, macOS first | The macOS package is checked by hand; Windows and Linux are produced and verified by script only, and are reported as such. |
| X No unrelated refactoring | Nothing but branding files, one test, one script and one workflow step. |
| XI Build and test integrity | The full macOS build and the existing tests are run before and after the replacement and must give the same result (SC-006). |
| XII Truthful reporting | The release notes say which platform was looked at by a human and which was only verified by script. |

*Post-design re-check (2026-10-13)*: no violation; no entry in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/008-brand-identity/
├── spec.md                       # the requirements and the decisions of 2026-10-13
├── plan.md                       # this file
├── research.md                   # decisions R1 to R8 (surfaces, tools, derivatives, tests)
├── data-model.md                 # the derivatives and the manifest entries
├── quickstart.md                 # how to make the contact sheets, run the checks, build and look
├── contracts/
│   ├── branding-manifest.md      # every declared asset: source, surface, format, sizes, approval
│   └── branding-verification.md  # what the test and the package verifier check, what stays manual
├── assets/tuxguitaradvanced-logo-primary.png   # the master, never modified
├── checklists/requirements.md
└── tasks.md                      # created by /speckit-tasks
```

### Source (what changes in the repository)

```text
desktop/build-scripts/common-resources/common-macosx/Contents/Resources/icon.icns      # macOS application icon
desktop/build-scripts/common-resources/common-windows/dist/tuxguitar.ico               # Windows executable, shortcut, "productDisplayIcon"
desktop/build-scripts/common-resources/common-windows-installer-icons/
    TG_icon_install.ico  TG_icon_uninstall.ico                                         # installer / uninstaller executables
    TG_logo_48x48.png  TG_logo_163x314.png  TG_splash_500x300.png                      # installer images (logo, left panel, splash)
desktop/build-scripts/common-resources/common-linux/share/pixmaps/tuxguitar.png        # Linux package icon (referenced by TuxGuitar.desktop and the metainfo)
desktop/TuxGuitar/share/skins/{Breeze,Breeze-dark,Oxygen,Oxygen-dark,Symbolic,Symbolic-dark}/{icon.png,splash.png}
desktop/TuxGuitar/src-skins/{Breeze,Oxygen,Symbolic}/...                              # the sources of the same images, kept identical
specs/008-brand-identity/branding-manifest.json                                         # the machine-readable manifest of contracts/branding-manifest.md
desktop/TuxGuitar-practice/test/.../branding/BrandingAssetsTest.java                    # FR-014 on the repository
misc/verify-branding.sh                                                                 # FR-014 on the packages
.github/workflows/release.yml                                                           # one step per platform: run the verifier before the draft is published
README.md, docs/README-FR.md, docs/README-ES.md, docs/release-notes/v0.5.0-beta.1.md    # documentation and release surfaces
```

**Not changed on purpose** (reasons in `research.md`): `about_description.png`, `about_authors.png`, `about_license.png` (pictograms of the About tabs), `tgdoc.icns` (icon of `.tg` documents), the Android application, the upstream `website/` and `misc/` logos, all upstream notices.

**Structure Decision**: assets stay where the packaging already reads them, under the same names, so no build script changes; the new checks live in the fork module that already holds the fork's tests and in one shell script.

## Increments

* **B0 - Record (done in this step).** Owner decisions in the spec, plan, research, data model, contracts, quickstart, tasks.
* **B1 - Derivatives and approval.** Make the proposed derivatives from the master (T-numbers in `tasks.md`): the **icon-only** one (a geometric crop of the master above the wordmark, to be judged on the contact sheet), the **full logo** at 128 to 1024 px, the **splash** (500 x 300) and the **installer panel** (163 x 314), the latter two as a composition of the unmodified full logo on a plain background with safe margins (a horizontal or vertical treatment, FR-011). Produce the contact sheets (7 sizes x light and dark). **Gate G1: the owner approves, amends or rejects each derivative; the decision is written in `contracts/branding-manifest.md`.**
* **B2 - Integration.** Build the platform files from the approved derivatives (`.icns` with `iconutil`, `.ico` with ImageMagick, PNGs) and replace the files of the Source list, keeping every file name and format. Run the full macOS build and the existing tests: the outcome must be the same as before (SC-006). **Gate G2: the owner looks at the built macOS application (Finder, Dock, window, splash).**
* **B3 - Checks.** `BrandingAssetsTest` (master digest, every declared file exists, is readable, has the declared format and dimensions) and `misc/verify-branding.sh` (the icons are in the macOS `.app`, the Windows zip and installer sources, the Linux tar.gz and deb), wired in the `Release` workflow. Manual checks of FR-015 listed in `quickstart.md`. **Gate G3: tests green, verifier green on a test run of the workflow.**
* **B4 - Documentation and beta.** README (three languages) and release notes show the logo (full logo, as the master, from a file of the repository), provenance note (FR-016), the release notes of `v0.5.0-beta.1` (which checks were done by hand and which by script), then the `Release` workflow with the tag the owner confirms. **Gate G4: the owner confirms the tag and the text; the release is published only then.** The tag is never moved after its publication.

## Risks

* The icon-only crop may cut a part of the art (the feet of the penguin, the end of the note highway): that is judged on the contact sheet and the owner may choose another crop or keep the full logo above a larger size; nothing is redrawn to fix it.
* The macOS Dock icon is not squared by the system: a transparent logo with its own margins is expected to look right, to be confirmed in G2.
* Windows and Linux appearances are not seen by a human in this beta (no machine); the verifier proves the files are in the packages, not how they look.
* The master is a 2 MB AI-assisted image: provenance is recorded (FR-016) and a legal review before a stable release is left to the owner.

## Complexity Tracking

None.
