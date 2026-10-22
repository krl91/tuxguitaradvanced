---
description: "Tasks: the same version and commit everywhere (spec 015)"
---

# Tasks: The same version and commit everywhere

**Input**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `checklists/release-consistency.md`
**Tests**: requested by the constitution (XI): each test is written first and seen failing (said honestly when it cannot fail first). Existing tests are never modified.
**Rules**: local commits only, one per phase; no push, no release, no tag without the owner's explicit go; Windows and Linux are validated by CI only and reported so.
**Format**: `- [ ] T### [P?] [US?] Description with file path`

## Phase 1: Setup

- [ ] T001 Build the baseline on macOS (`cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa && mvn -e clean verify -P native-modules`) and note which tests pass today, in `specs/015-version-and-commit-everywhere/baseline.txt`.
- [ ] T002 Find where the tests of `common/TuxGuitar-lib` live and how to run one (`common/TuxGuitar-lib/src/test/java/app/tuxguitar/util/`), and read `desktop/TuxGuitar/src/app/tuxguitar/app/view/main/TGWindow.java` around `loadTitleInCurrentThread` (line ~174); write the two facts in `research.md` R9 (confirmation).

## Phase 2: Foundational (blocks every story)

- [ ] T003 [P] Write `common/TuxGuitar-lib/src/test/java/app/tuxguitar/util/BuildIdentityTest.java`: parse of `contracts/build-info.md` (all keys; missing file/keys fall back to `dev`/`unknown`; unknown keys ignored); `numeric` never contains the label; `shortCommit` = 7 characters; `title()`, `copyLine()` (`TuxGuitarAdvanced <version> (<40 hex>[, modified])`), `modified` only for `dev`; tag/version validation `^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$` (accepts `0.6.0`, `0.6.0-beta.1`, `0.6.0-test`; refuses `0.6`, `0.6.0-`, `0.6.0-be ta`, empty); commit validation 40 hex. See it fail.
- [ ] T004 Create `common/TuxGuitar-lib/src/main/java/app/tuxguitar/util/BuildIdentity.java` (new class, Java release 9 syntax, no I/O: parses a `java.util.Properties`; fields and rules of `data-model.md`). T003 passes.
- [ ] T005 [P] Write `common/TuxGuitar-lib/src/test/java/app/tuxguitar/util/TitleLayoutDefaultTest.java`: a stored layout equal to the old default `${appname} - ${filename}` gives the new default `${appname} ${appversion} - ${filename}`; any other stored layout (empty, custom, a layout already containing `${appversion}`) is returned unchanged; null gives the new default. See it fail.
- [ ] T006 Create `common/TuxGuitar-lib/src/main/java/app/tuxguitar/util/TitleLayoutDefault.java` (new, pure). T005 passes.
- [ ] T007 Write `misc/test-stamp-build-info.sh` (self-test): the stamp script writes the four keys; refuses a bad version and a bad commit (exit 1); `--dev` outside git gives `commit=unknown`; `--dev` on a dirty tree gives `modified=true`; a release never gives `modified=true`. See it fail (script absent).
- [ ] T008 Create `misc/stamp-build-info.sh <share-dir> (--release <version> <commit> | --dev)` per `contracts/build-info.md`. T007 passes.
- [ ] T009 Commit phase 2 (explicit `git add` paths).

## Phase 3: User Story 1 — the application shows the version and the commit (P1) 🎯 MVP

**Goal**: About, the default title, `${appversion}` and `--version` show the build identity. **Independent test**: build locally, open About, read the title and the Copy line (US1, US4 scenarios).

- [ ] T010 [US1] Create `desktop/TuxGuitar/src/app/tuxguitar/app/system/TGBuildInfo.java`: reads `build-info.properties` from the share path (`TGFileUtils`, `tuxguitar.share.path`, research R5), builds a `BuildIdentity`, never throws (missing/unreadable file ⇒ `dev`, `unknown`).
- [ ] T011 [US1] Add the strings in `common/resources/lang/messages.properties`, `messages_fr.properties`, `messages_es.properties`: About copy button, its confirmation, its failure message, the labels "commit" and "modified", refreshed description of the `appversion` variable (FR-010, FR-013).
- [ ] T012 [US1] Write `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/…/BuildInfoStringsTest.java` (new, same pattern as `ArcadeLevelStringsTest`): every new key exists in the three languages and is not empty. See it fail before T011 if written first, otherwise say so.
- [ ] T013 [US1] Edit `desktop/TuxGuitar/src/app/tuxguitar/app/view/dialog/about/TGAboutDialog.java`: header = `TuxGuitarAdvanced <version>`; a line with the short commit (and "modified"); the full commit in the description tab; a "Copy the version" button (`copyLine()` to the clipboard, confirmation, message if the clipboard is unavailable). Do not touch other code.
- [ ] T014 [US1] Edit `desktop/TuxGuitar/src/app/tuxguitar/app/system/variables/TGVarAppVersion.java`: return the build identity's version (`dev` for a development build). `TGVersion.CURRENT` stays.
- [ ] T015 [US1] Edit `desktop/TuxGuitar/src/app/tuxguitar/app/system/config/TGConfigDefaults.java` line 37 (default `${appname} ${appversion} - ${filename}`) and `TGWindow.java` (`loadTitleInCurrentThread`) to resolve the stored layout through `TitleLayoutDefault`; `MainOption` "defaults" button keeps working.
- [ ] T016 [US1] Edit `desktop/TuxGuitar/src/app/tuxguitar/app/util/ArgumentParser.java` line 62 so `--version` prints the name, the version and the short commit (FR-014).
- [ ] T017 [US1] Write a native SWT test for About (same pattern as `PreRunWindowLevelSwtTest`): the title shows the version of a given `build-info.properties`, the Copy button puts the expected line on the clipboard. See it fail first.
- [ ] T018 [US1] Wire `misc/stamp-build-info.sh --dev` into `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml` (ant `exec` in the package phase, writing `Contents/MacOS/share/build-info.properties`; version and commit from properties `release.version`/`release.commit` when given, else `--dev`).
- [ ] T019 [US1] Build macOS locally, run the unit and SWT tests, launch the app, open About, click Copy and read the clipboard, read the window title, run `--version`; write what was seen in `specs/015-version-and-commit-everywhere/phase3-report.md` (only what was done by hand).
- [ ] T020 [US1] Check that a `.tg` file saved by the new build has the same header stamp as one saved by the previous build (SC-005): save one, read `<version major=… minor=… revision=…>`. Report the two values.
- [ ] T021 Commit phase 3.

## Phase 4: User Story 4 — a development build says so honestly (P3)

**Goal**: dev label, commit, "modified", unknown commit without error. Mostly covered by T003/T007/T010; this phase proves it in the app.

- [ ] T022 [US4] With a clean tree, a modified tree and a copy of the build without the file, open About and read the three cases (scenarios US4 1–3); report what was seen. Nothing is added to the code unless a case fails (then a test first).
- [ ] T023 Commit phase 4 only if files changed.

## Phase 5: User Story 2 — every package says the same version and commit (P1)

**Goal**: the metadata and the names of the four packages. **Independent test**: build a package with `-Drelease.version=0.6.0-test -Drelease.commit=<sha>` and read it.

- [ ] T024 [US2] macOS POM `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml`: PlistBuddy steps set `CFBundleShortVersionString` `X.Y.Z`, `CFBundleVersion` `X.Y.Z.0`, `CFBundleGetInfoString` `TuxGuitarAdvanced <version> (<short commit>)`, custom keys `TGBuildVersion` and `TGBuildCommit` (full), from properties `release.version`, `release.numeric`, `release.commit`; defaults = the dev values. Done before the launcher is signed (decision D3).
- [ ] T025 [US2] Windows POM `desktop/build-scripts/tuxguitar-windows-swt-x86_64/pom.xml`: launch4j `versionInfo` (fileVersion and productVersion `X.Y.Z.0`, `txtFileVersion` and `txtProductVersion` the full text, productName `TuxGuitarAdvanced`, originalFilename, internalName) + the stamp script call for `share/build-info.properties`. Confirm the plugin 2.1.2 element names from its documentation/jar before writing; if the text version cannot be carried, stop and report (checklist CHK004).
- [ ] T026 [US2] Linux POMs `desktop/build-scripts/tuxguitar-linux-swt/pom.xml` and `tuxguitar-linux-swt-deb/pom.xml`: the stamp script call; Debian `Version` from the property (`X.Y.Z~label`, `-`→`.`) with the full text and commit in the `Description` trailer of `src/resources/DEBIAN/control` (copied by ant: substitute by property).
- [ ] T027 [US2] Build macOS with `-Drelease.version=0.6.0-test -Drelease.numeric=0.6.0.0 -Drelease.commit=<HEAD sha>` and read Info.plist, `build-info.properties`, About; report the values read. (Windows and Linux builds are not run here; say so.)
- [ ] T028 Commit phase 5.

## Phase 6: User Story 3 — a release that does not match is not published (P2)

**Goal**: the workflow validates the tag, stamps, names, checks every package, publishes as pre-release when labelled.

- [ ] T029 [US3] Write `misc/test-verify-version.sh` (self-test, pattern of `misc/test-verify-branding.sh`): builds minimal fake packages for macos/windows/linux (folder + zip/tar.gz/deb as available on the machine) and requires `verify-version.sh` to pass on a correct one and to **fail on each of the places** with one wrong value (version, label, commit, short instead of full commit, wrong file name, `modified=true`). See it fail (script absent).
- [ ] T030 [US3] Create `misc/verify-version.sh <package> <platform> <version> <commit>` per `contracts/verify-version.md` (reads the build-info file, plist keys via `plutil`/PlistBuddy on macOS or a text parse, `.deb` `dpkg-deb --field`, exe version resource via `strings`/`peres` fallback documented, file names). T029 passes on macOS-available cases; report which platform cases could not be run locally.
- [ ] T031 [US3] Edit `.github/workflows/release.yml`: (a) first job `validate` refuses a tag not matching `^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$`, refuses an existing tag on another commit, and outputs `version`, `numeric`, `commit` (`GITHUB_SHA`); (b) the three build jobs pass `-Drelease.version/-Drelease.numeric/-Drelease.commit`; (c) the macOS job stamps before signing; (d) each job runs `misc/verify-version.sh` on its packages before `gh release upload`; (e) the draft release notes get a footer line with the full commit; (f) the final job sets `--prerelease` when the tag has a label (any), final otherwise; (g) header comment updated. Keep every existing gate.
- [ ] T032 [US3] Validate the YAML (`actionlint` if installed, else a YAML parse) and read the file once more against FR-001, 004, 005, 006, 011; report that the workflow itself has **not run**.
- [ ] T033 Commit phase 6.

## Phase 7: Polish, documentation, report

- [ ] T034 [P] Update the documentation: how a version is given and where it shows (`README.md` or `docs/` release page, `INSTALL.md` if it names versions), the title variable, the dev label; keep it consistent with `spec.md`.
- [ ] T035 [P] Run the whole macOS build with tests (`mvn -e clean verify -P native-modules`) and compare with `baseline.txt`; report exactly what passed and what did not.
- [ ] T036 Write `specs/015-version-and-commit-everywhere/final-report.md`: what ran, what only CI can prove (Windows, Linux), what was seen by hand, the open points, the five decisions awaiting the owner's confirmation.
- [ ] T037 Commit the phase.
- [ ] T038 **Owner**: confirm the decisions of `spec.md` (Assumptions), then give the go to push and to dispatch a labelled release (for example `v0.6.0-beta.2`) so the check runs on real packages; read the nine places in the four packages with `misc/verify-version.sh` (SC-001, SC-002).

## Dependencies and order

- Phase 1 → 2 → 3 (MVP) → 4 → 5 → 6 → 7. Phase 5 needs T008 and T018; phase 6 needs phase 5.
- [P] only where files differ and no earlier task is needed: T003 ∥ T005; T034 ∥ T035.
- Within a phase: tests before the code that makes them pass.

## Implementation strategy

MVP = phases 1–3 (the application shows the identity, locally). Then packages (5), then the workflow check (6). The release itself (T038) is the owner's step; nothing is pushed or dispatched before.
