---

description: "Tasks of spec 008: integrate the official logo for the beta 0.5.0-beta.1"
---

# Tasks: TuxGuitarAdvanced brand identity (official logo integration)

**Input**: `specs/008-brand-identity/` (spec.md, plan.md, research.md, data-model.md, contracts/branding-manifest.md, contracts/branding-verification.md, quickstart.md)

**Prerequisites**: plan.md, spec.md (US1, US2, US3, FR-001 to FR-017). **No Java class of the application changes (FR-013).** The master `assets/tuxguitaradvanced-logo-primary.png` is never modified (SHA-256 `a9d369a2574396cd3208b0665648c57f398690499efb137d6781478991baf9e0`).

**Tests**: requested by FR-014 (the assets and the packages are checked automatically).

**Gates** (the owner decides; nothing after a gate starts before it is passed): **G1** the owner approves the derivatives D1 to D4 on the contact sheets; **G2** the owner looks at the built macOS application; **G3** tests and verifier green on a test run of the `Release` workflow; **G4** the owner confirms the tag `v0.5.0-beta.1` and the notes before anything is published.

**Conventions**: `[HUMAN]` = needs the owner (never reported as done before). `[P]` = can run in parallel. `SRC` = `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice`. `CR` = `desktop/build-scripts/common-resources`. Commit each phase with no co-author trailer; one push per round; a published tag is never moved.

## Phase 1: Setup

- [x] T001 Verify the master: `shasum -a 256 specs/008-brand-identity/assets/tuxguitaradvanced-logo-primary.png` equals `a9d369a2574396cd3208b0665648c57f398690499efb137d6781478991baf9e0`, and `magick identify` says 1254 x 1254 RGBA (SC-001); write the result in the commit message of the phase.
- [x] T002 (done 2026-10-13: 273 + 73 + ... tests, BUILD SUCCESS, counts in `benchmark/before-after.txt`) Record the state before the change: run `cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa && mvn -e clean verify -P native-modules,swt-gui-tests` and keep the counts of tests run, failures and skipped in `specs/008-brand-identity/benchmark/before-after.txt` (SC-006); copy the files of the Source list of `plan.md` to `/tmp/branding-before/` for comparison (done 2026-10-13 for 10 of them only: the six icon and installer files, the Linux icon and the Breeze skin; the skins are identical, so the rest is the same files).
- [x] T003 [P] Create the folder `misc/branding/` with a `README.md` saying: the scripts make every derivative from the master only, never open it for writing, never recolour, retouch, redraw or content-aware crop (FR-002), and the work folder is outside the repository (`/tmp/branding-work`).

## Phase 2: Foundational (blocks every story)

- [x] T004 Write `specs/008-brand-identity/branding-manifest.json` with the content of `contracts/branding-manifest.md`: the master (path, 1254 x 1254, RGBA, SHA-256), the derivatives D1 to D4 with `status: "proposed"`, and the surfaces S1 to S12 (path, format, entries/sizes, derivative, package location). Enum `status`: `proposed`, `approved`, `rejected`; `kind`: `full`, `icon-only`, `splash`, `panel`.
- [x] T005 [P] Write `misc/branding/make-derivatives.sh`: from the master only, into `/tmp/branding-work/`: D1 full at 128, 256, 512, 1024 (`magick -filter Lanczos -resize`, alpha kept); D2 icon-only as a **rectangular crop with fixed coordinates** (parameters `CROP_X CROP_Y CROP_W CROP_H` read from `misc/branding/crop.conf`, initial proposal: the area above the wordmark) then uniform downscale to 16, 24, 32, 48, 64, 96; D3 splash 500 x 300 and D4 panel 163 x 314 = D1 scaled uniformly and centred with safe margins on a plain background (`BACKGROUND` from `misc/branding/background.conf`). No stretching, no recolouring.
- [x] T006 [P] Write `misc/branding/make-contact-sheets.sh`: for each of D1 to D4, a sheet at 16, 32, 64, 128, 256, 512, 1024 px (the sizes a derivative does not exist at are skipped and said) on a light background (#FFFFFF) and a dark background (#1E1E1E), into `/tmp/branding-review/` as PNG (FR-009, SC-004).
- [x] T007 (done 2026-10-13: written first, red for the surfaces not yet replaced: the ICO and ICNS entries that are missing today) Write `SRC/branding/BrandingAssetsTest.java` (JUnit 5, tests first, red until the manifest and the files exist): (1) the master exists with the SHA-256 and 1254 x 1254 with an alpha channel; (2) every surface of `branding-manifest.json` exists, decodes (PNG by `ImageIO`; ICO by its header and entries; ICNS by its header and entry types) and has the declared dimensions, with **all** declared sizes present in an ICO or ICNS; (3) the six skins have byte-equal `icon.png` and byte-equal `splash.png`, and `src-skins` equals `share/skins` for those files; (4) with the property `branding.release=true`, every derivative used by a surface has `status` `approved` (FR-010, FR-017). The paths are resolved from the repository root found upwards from `user.dir`; the test is skipped with a reason when the manifest is absent.
- [x] T008 Write `misc/verify-branding.sh <package> <platform>` (platforms `macos`, `windows`, `linux`): for every surface of the manifest with a package location, check that the file is **inside the package** (a `.app` folder or its zip; a Windows zip; a Linux tar.gz or deb, opened with `unzip -l`, `tar -tzf`, `dpkg-deb -c`) and that its SHA-256 equals the repository file; exit non-zero and list the missing or different files. It checks presence and identity, not looks.

**Checkpoint**: manifest, scripts and tests exist; the test is red only for the surfaces that are not yet replaced.

## Phase 3: User Story 1 - Recognise the official product (P1) MVP

**Goal**: every official surface with room for the full logo shows the authoritative logo or an approved derivative of it (FR-001, FR-002, FR-003, FR-007, FR-012).

**Independent Test**: compare each full-logo surface (splash, installer panel, README, release notes) with the master: wordmark `TuxGuitarAdvanced`, mascot, guitar, note highway, colour order, proportions (SC-002); `BrandingAssetsTest` and `verify-branding.sh` green on those surfaces.

- [x] T009 [US1] Run `misc/branding/make-derivatives.sh` and `misc/branding/make-contact-sheets.sh` for D1, D3 and D4 and write in `specs/008-brand-identity/benchmark/derivatives-proposal.md` what was made (operation, sizes, background, files) so the owner can find the sheets in `/tmp/branding-review/`.
- [x] T010 [US1] [HUMAN] (done 2026-10-13: D1, D3, D4 approved, dark backgrounds) **Gate G1 (D1, D3, D4)**: the owner looks at the contact sheets and approves, amends (background, margins) or rejects each of D1, D3, D4; the decision, date and note are written in the table "Approvals" of `specs/008-brand-identity/contracts/branding-manifest.md` and `status` is set in `branding-manifest.json` (FR-004, FR-010, SC-005). Nothing below starts for a derivative that is not approved.
- [x] T011 [P] [US1] Build the splash of the application from D3 (500 x 300 PNG, same name) and replace `desktop/TuxGuitar/share/skins/{Breeze,Breeze-dark,Oxygen,Oxygen-dark,Symbolic,Symbolic-dark}/splash.png` and `desktop/TuxGuitar/src-skins/{Breeze,Oxygen,Symbolic}/` copies of the same file (S10), byte-equal in the six skins (surface checked by T007 item 3).
- [x] T012 [P] [US1] Build the Windows installer images from D3 and D4 and replace `CR/common-windows-installer-icons/TG_splash_500x300.png` (S7) and `CR/common-windows-installer-icons/TG_logo_163x314.png` (S6), keeping the formats and sizes (`project.xml` of the installers is not changed).
- [x] T013 [P] [US1] Show the master in the documentation (S11): in `README.md`, `docs/README-FR.md` and `docs/README-ES.md` add, at the top, the logo by the path `specs/008-brand-identity/assets/tuxguitaradvanced-logo-primary.png` (no copy of the master, one master), with the text `TuxGuitarAdvanced` as alt text and the provenance sentence of FR-016 ("the logo is an AI-assisted redesign selected by the product owner; upstream TuxGuitar notices are unchanged; no ownership of third-party marks is asserted") in the three languages.
- [x] T014 [US1] Run `mvn test -pl ../../TuxGuitar-practice -am -Dtest=BrandingAssetsTest -Dsurefire.failIfNoSpecifiedTests=false` from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`: the items of T007 for S6, S7, S10 and the master must pass; report the exact result.

**Checkpoint**: the full-logo surfaces show the approved logo; the existing tests are unchanged in outcome.

## Phase 4: User Story 2 - Use a legible small icon (P1)

**Goal**: the application is recognisable in Finder, the Dock, the task switchers, the installers and the window chrome at every native size, without squeezing or cropping the full logo (FR-007, FR-008, FR-009, US2).

**Independent Test**: contact sheets of D2 and D1 at 16 to 1024 px on both backgrounds; the packaged `icon.icns`, `.ico` files and PNGs have all their declared sizes; the owner sees the macOS application (G2).

- [x] T015 (D2 proposal made 2026-10-13, crop 1254 x 935; the owner's decision is T016) [US2] Run `make-derivatives.sh` with the proposed crop for D2 (and D1 at 128 to 1024) and `make-contact-sheets.sh`; write the crop coordinates and the sheets in `specs/008-brand-identity/benchmark/derivatives-proposal.md` (what each size looks like, honestly, including any part of the art that the crop cuts).
- [x] T016 [US2] [HUMAN] (done 2026-10-13: D2 approved with the crop 935) **Gate G1 (D2, and D1 at 128 to 1024)**: the owner approves, amends the crop (new `CROP_*` in `misc/branding/crop.conf`, T015 repeated) or rejects; the decision is written in `contracts/branding-manifest.md` and `branding-manifest.json`. If D2 is rejected the small sizes keep the upstream icon and the beta says so.
- [x] T017 [P] [US2] Write `misc/branding/build-platform-icons.sh` that builds, from the approved derivatives only: `icon.icns` with `iconutil` from an `.iconset` (16, 32, 64, 128, 256, 512, 1024 including the `@2x` entries; D2 below 128, D1 from 128), the `.ico` files with `magick` (16, 24, 32, 48, 64, 96, 128, 256), and the PNGs (48, 96); it refuses a derivative whose status is not `approved`.
- [x] T018 [P] [US2] Replace the macOS application icon `CR/common-macosx/Contents/Resources/icon.icns` (S1); `tgdoc.icns` and `Info.plist` are not changed (research R2).
- [x] T019 [P] [US2] Replace the Windows icons `CR/common-windows/dist/tuxguitar.ico` (S2), `CR/common-windows-installer-icons/TG_icon_install.ico` (S3), `CR/common-windows-installer-icons/TG_icon_uninstall.ico` (S4) and `CR/common-windows-installer-icons/TG_logo_48x48.png` (S5).
- [x] T020 [P] [US2] Replace the Linux icon `CR/common-linux/share/pixmaps/tuxguitar.png` (S8, 96 x 96); `TuxGuitar.desktop` and the metainfo are not changed.
- [x] T021 [P] [US2] Replace the window icon `icon.png` (96 x 96, S9) in `desktop/TuxGuitar/share/skins/{Breeze,Breeze-dark,Oxygen,Oxygen-dark,Symbolic,Symbolic-dark}/` and in `desktop/TuxGuitar/src-skins/{Breeze,Oxygen,Symbolic}/` (byte-equal); `about_description.png`, `about_authors.png`, `about_license.png` are pictograms and are not changed (research R2).
- [x] T022 (done 2026-10-13: BUILD SUCCESS, same counts; BrandingAssetsTest 5 of 5 with -Dbranding.release=true; no .java of the application changed) [US2] Run the BrandingAssetsTest (all items) and the full macOS build `mvn -e clean verify -P native-modules,swt-gui-tests` from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`; compare the counts with `specs/008-brand-identity/benchmark/before-after.txt` (T002): same tests run, same outcome (SC-006, FR-013); `git diff --stat` shows no `.java` file changed.
- [x] T023 [US2] Relaunch the built application for the owner (`target/tuxguitar-9.99-SNAPSHOT-macosx-swt-cocoa.app`, with the `jre` symlink as in `CLAUDE.md`) and ask what the owner sees.
- [x] T024 (done 2026-10-14: the owner looked at the built macOS application and said "parfait") [US2] [HUMAN] **Gate G2** (macOS, FR-015): the owner looks at Finder, the Dock, Cmd-Tab, the window title bar and the splash of the built application and says whether the silhouette is clear at small sizes with nothing stretched, squashed or on an opaque square (SC-004); the answer is written in `specs/008-brand-identity/benchmark/derivatives-proposal.md` and in the checklist.

**Checkpoint**: the icons of the three platforms and of the application are replaced by approved derivatives; the macOS application was seen by the owner.

## Phase 5: User Story 3 - Derive additional brand formats safely (P2)

**Goal**: a maintainer can derive further formats without losing the identity, with documented purpose, source, changes and approval (FR-005, FR-010, FR-011, US3).

**Independent Test**: every derivative in the repository is in the manifest with its source, operation, sizes and approval; an unlisted or unapproved image fails `BrandingAssetsTest` with `branding.release=true`.

- [x] T025 (done: approvals and values recorded in `contracts/branding-manifest.md` and `branding-manifest.json`, crop `1254 x 935`, dark backgrounds) [US3] In `specs/008-brand-identity/contracts/branding-manifest.md` and `branding-manifest.json`, record for D1 to D4 the final `operation`, the `CROP_*` and `BACKGROUND` values, the sizes and the owner's decision, so that every derivative has its purpose, source master, changes and approval documented (US3.1, FR-005).
- [x] T026 (done in T003: `misc/branding/README.md`) [P] [US3] Add to `misc/branding/README.md` the rule of US3.2 and FR-002: an unapproved redesign or an AI regeneration is rejected even if it looks similar; list the **future derivatives not made by the beta** (monochrome, high-contrast, horizontal wordmark, document icon built on the logo for `.tg` files, Android icons) as ideas that need their own approval (FR-011).
- [x] T027 (done in T007: `noImageOfTheIdentityIsMissingFromTheManifest`) [US3] Add to `SRC/branding/BrandingAssetsTest.java` a test that every image file of the manifest surfaces is declared in the manifest and that no file of `CR/` or the six skins named `icon*`, `splash*`, `TG_*` is missing from it (no silent replacement), then run it and report the exact result.

## Phase 6: Polish, checks on the packages, and the beta

- [x] T028 (done 2026-10-14: `release.yml`, one test step and one verifier step per platform) Wire the checks in `.github/workflows/release.yml`: after each platform build (macOS, Windows, Linux) a step runs `misc/verify-branding.sh` on the package built, and the Maven test step of the macOS job runs with `-Dbranding.release=true`; a failure stops the chain before the draft release is published (FR-014, FR-017). No other step of the workflow changes.
- [x] T029 (done 2026-10-14, run 38475088761 on the test tag `v0.0.0-beta.brandingtest`: BrandingAssetsTest 5 of 5 on macOS, the verifier found 13 files identical in the macOS zip, the Windows zip, the Linux tar.gz and the deb; the four jobs succeeded; the test release was published as a pre-release by the workflow and was deleted with its tag on 2026-10-14 after the owner's confirmation) Run the `Release` workflow on a **test tag** (`v0.0.0-brandingtest`, deleted afterwards **only with the owner's confirmation**) after one push of the branch, and read the result of the three verifier steps (**Gate G3**): report what each platform printed; a platform whose verifier did not run is reported as not verified.
- [ ] T030 [HUMAN] Manual checks of FR-015 on Windows and Linux (launcher, window, installer and uninstaller on Windows; menu entry and window on Linux) from the packages of T029: done or **not done**, per platform; these may stay open and are then written as "not checked" in the release notes (constitution XII).
- [x] T031 (done 2026-10-14) [P] Write `docs/release-notes/v0.5.0-beta.1.md`: the new logo (the master by its raw GitHub address), what the beta contains since alpha.6 (the list of commits from `git log v0.5.0-alpha.6..HEAD`, or from alpha.5 if alpha.6 is not published), the provenance sentence (FR-016), a section "What was checked, and by whom" (macOS by the owner on its built application; Windows and Linux by script only unless T030 was done), the known limits, and the French paragraph; the file name must be exactly `v0.5.0-beta.1.md` (the workflow uses it when the tag is `v0.5.0-beta.1`).
- [x] T032 (done 2026-10-14 in two steps: the READMEs say beta and 0.5.0-beta.1, the modes stay marked alpha; completed on 2026-10-14 after the analysis finding I1: `specs/STATUS.md` row of 008, `checklists/requirements.md` re-validated, the manuals checked: they have no project-level alpha statement, only the labels of the modes, which stay alpha, so they are unchanged on purpose) [P] Update `README.md`, `docs/README-FR.md`, `docs/README-ES.md` ("Available now (version ...)" and the alpha warning: the project is now in **beta**, keeping the honest limits) and `specs/STATUS.md` (row of 008), `specs/008-brand-identity/checklists/requirements.md` (tick what is done, leave unticked what is not), `docs/MANUAL.md` and `docs/MANUAL-FR.md` where they say "alpha".
- [x] T033 (done 2026-10-14: full macOS build green with the same counts, BrandingAssetsTest 5 of 5 with -Dbranding.release=true, only `tests/` untracked) Final local gate before the release: `mvn -e clean verify -P native-modules,swt-gui-tests` green (reported exactly), `BrandingAssetsTest` green with `-Dbranding.release=true`, `git status` clean except the untracked `tests/`; one commit per phase already made, no co-author trailer.
- [x] T034 [HUMAN] (done 2026-10-14: the owner confirmed the tag `v0.5.0-beta.1` and the notes; one push `fe92b8edf`, then the `Release` workflow, run 38476023499) **Gate G4**: the owner confirms the tag `v0.5.0-beta.1` (or another), the release notes and the list of what was checked; only then push the branch (one `git push origin feature/interactive-practice feature/interactive-practice:master`, no verification fetch) and run `gh workflow run release.yml --repo krl91/tuxguitaradvanced --ref master -f tag=v0.5.0-beta.1`; the tag is never moved after publication.
- [x] T035 (done 2026-10-14: the four jobs succeeded, the release `v0.5.0-beta.1` is published as a pre-release with the macOS zip, the Windows zip, the Linux tar.gz and the deb; nothing was moved) After the run, report the state of the workflow (one `gh run list` call) and of the draft or published release, exactly as it is; if a platform failed, say which and why, and do not move the tag (a new version is needed for new code).

## Dependencies and order

- Phase 1 then Phase 2 (blocking). US1 (Phase 3) and US2 (Phase 4) are independent after Phase 2 except for the **gates**: T010 (G1 for D1, D3, D4) before T011 to T014; T016 (G1 for D2 and D1) before T017 to T022; T024 (G2) before Phase 6. US3 (Phase 5) needs the decisions of T010 and T016. Phase 6 needs G2; T029 (G3) before T034 (G4).
- T017 before T018 to T021; T018 to T021 are parallel (different files); T011, T012, T013 are parallel.
- The **Windows and Linux visual checks (T030)** do not block the beta: they stay open and are reported as not checked.

## Parallel examples

- After G1: T011 || T012 || T013; T018 || T019 || T020 || T021.
- In Phase 2: T005 || T006 || T008 (T007 needs T004).

## Implementation strategy

- **MVP = US1 with the gate G1**: the full logo on the splash, the installer images and the documentation, with the manifest and the test. Then US2 (the icons, the most visible surface) and G2, then US3 (documentation of derivatives) and the beta.
- Every replacement waits for the owner's approval of its derivative; a rejected derivative leaves the upstream file in place and the release notes say so.
- Report every test, build and check exactly as run; nothing is described as verified when it was not.

## Phase 7: Findings of the analysis of 2026-10-14

- [x] T036 Fix the findings of `/speckit-analyze` (first analysis: I1, I2, I4, A1, C2, F1, F2; second analysis: N1, N2, N3, N5, corrected in commit `53bec8e94`; N4, the order of FR-017 and FR-016, left as it is on purpose; third analysis: M1, this line) (done 2026-10-14: this file, `plan.md`, `spec.md` Out of Scope, `specs/STATUS.md`, `checklists/requirements.md`, the test `theDocumentationShowsTheMasterByItsPathAndNotACopyOfIt` in `BrandingAssetsTest`).
- [x] T037 [HUMAN] (done 2026-10-14: the owner reviewed the light-background sheets and said "c'est ok"; recorded in `contracts/branding-manifest.md` and `branding-manifest.json`) Finding C1: the owner reviews the contact sheets on the **light** background (`/tmp/branding-review/D1-light.png`, `D2-light.png`, `D3-light.png`, `D4-light.png`, regenerated by `misc/branding/make-derivatives.sh` and `make-contact-sheets.sh`) and says whether the silhouette, the wordmark and the transparency hold (FR-009, SC-004); the owner's answer is written in `contracts/branding-manifest.md`. The approvals of 2026-10-13 were given on the dark background only.

## Process notes (Spec Kit, written 2026-10-14 for honesty)

Commands of the Spec Kit method run on this spec: `/speckit-plan` and `/speckit-tasks` (2026-10-13), `/speckit-analyze` (2026-10-14, read-only, run three times: the first found I1-P1, the second N1-N5, the third M1, then a fourth after the correction of M1), `/speckit-checklist` (2026-10-14, after the analysis). `/speckit-converge` (2026-10-14, appended T038-T039) and `/speckit-implement` (2026-10-14, for the tasks of Phase 8 only: T038, T039). **Not run:** `/speckit-clarify` (the "Clarifications" section of `spec.md` was written by hand from the owner's answers), and `/speckit-implement` for Phases 1 to 7 (those tasks were executed and ticked by hand). This cannot be redone; the rule for the next features is in the owner's instruction of 2026-10-14: the whole method, in order, with a reminder each time.

## Phase 8: Convergence

- [x] T038 (done 2026-10-14 by `/speckit-implement`: test `theWindowsInstallersReadExactlyTheDeclaredImagesAndIcons`, `BrandingAssetsTest` 7 of 7 with `-Dbranding.release=true`; sentence added to `contracts/branding-verification.md`) Add to `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/branding/BrandingAssetsTest.java` a test that the files named by `<leftImage>`, `<logoImage>`, `<splashImage>`, `<windowsExecutableIcon>` and `<windowsUninstallerExecutableIcon>` in both `desktop/build-scripts/tuxguitar-windows-swt-x86_64-installer/src/installbuilder/project.xml` and `desktop/build-scripts/tuxguitar-windows-jfx-x86_64-installer/src/installbuilder/project.xml` exist and are exactly the surfaces S3 to S7 of `branding-manifest.json`; and say in `specs/008-brand-identity/contracts/branding-verification.md` that the Windows installer is not built by the `Release` workflow, so S3 to S7 are verified in the repository only, not in a package, per FR-014 (partial)
- [x] T039 (done 2026-10-14 by `/speckit-implement`: `misc/test-verify-branding.sh`, 4 checks ok locally on macOS, step added to the macOS job of `.github/workflows/release.yml` before the verifier steps; not yet run on GitHub Actions) Add a negative self-test of `misc/verify-branding.sh` (for example `misc/test-verify-branding.sh`, run by the `Release` workflow before the first verifier step): a temporary folder that lacks one declared icon must make the verifier exit non-zero and print `MISSING`, and one with a different icon must print `DIFFERENT`, so that the promise of the release notes ("a package that would miss the new icons is not published") is shown to hold per FR-014, FR-017 and Constitution XII (partial)
