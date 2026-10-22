---
description: "Tasks: the same version and commit everywhere (spec 015)"
---

# Tasks: The same version and commit everywhere

**Input**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `checklists/release-consistency.md`
**Tests**: each test is written first and seen failing (said honestly when it cannot fail first). Existing tests are never modified.
**Rules**: local commits only, one per phase; no push, no release, no tag without the owner's explicit go; Windows and Linux are validated by CI only and reported so. Code added to `common/` uses nothing newer than Java 9 (it is compiled for Android).
**Format**: `- [ ] T### [P?] [US?] Description with file path`

## Phase 0: Gate (nothing is implemented before it)

- [ ] T001 **Owner**: confirm or correct the decisions listed in `spec.md` (Assumptions, "Decisions taken while preparing the tasks") and review `checklists/release-consistency.md`. `/speckit-implement` does not start until this is done.

## Phase 1: Setup and unknowns

- [ ] T002 Build the baseline on macOS (`cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa && mvn -e clean verify -P native-modules`) and note which tests pass today in `specs/015-version-and-commit-everywhere/baseline.txt`.
- [ ] T003 Confirm from the launch4j-maven-plugin 2.1.2 documentation or jar which `versionInfo` elements exist (file version 4 numbers, text file version, product version, text product version, product name, original file name, internal name) and write the answer in `research.md` R7. If the text version cannot be carried, stop and report to the owner.
- [ ] T004 Confirm in `research.md` R9: tests of `common/TuxGuitar-lib` run with `mvn test -Dtest=…` from `common/TuxGuitar-lib`; tests of the application core run from `desktop/TuxGuitar-practice/test/…/ui/` (module depends on `tuxguitar`, as `PreRunWindowLevelSwtTest` shows); read `TGWindow.loadTitleInCurrentThread` (~line 174).

## Phase 2: Foundational (blocks every story)

- [ ] T005 [P] Write `common/TuxGuitar-lib/src/test/java/app/tuxguitar/util/BuildIdentityTest.java`: parse of `contracts/build-info.md` (all keys; missing file/keys fall back to `dev`/`unknown`; unknown keys ignored); `numeric` never contains the label; `shortCommit` = 7 characters; `title()`, `copyLine()` (`TuxGuitarAdvanced <version> (<40 hex>[, modified])`), the forms of the table "Forms of the development build" in `data-model.md`; `modified` only for `dev`; version validation `^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$` (accepts `0.6.0`, `0.6.0-beta.1`, `0.6.0-test`; refuses `0.6`, `0.6.0-`, `0.6.0-be ta`, empty); commit validation 40 hex. See it fail.
- [ ] T006 Create `common/TuxGuitar-lib/src/main/java/app/tuxguitar/util/BuildIdentity.java` (new class, Java 9 syntax only, no I/O: parses a `java.util.Properties`; fields and rules of `data-model.md`). T005 passes.
- [ ] T007 [P] Write `common/TuxGuitar-lib/src/test/java/app/tuxguitar/util/TitleLayoutDefaultTest.java`: a stored layout equal to the old default `${appname} - ${filename}` gives the new default `${appname} ${appversion} - ${filename}`; any other stored layout (empty, custom, one already containing `${appversion}`) is unchanged; null gives the new default. See it fail.
- [ ] T008 Create `common/TuxGuitar-lib/src/main/java/app/tuxguitar/util/TitleLayoutDefault.java` (new, pure, Java 9 only). T007 passes.
- [ ] T009 Write `misc/test-stamp-build-info.sh` (self-test): the stamp script writes the four keys; refuses a bad version and a bad commit (exit 1); `--dev` outside git gives `commit=unknown`; `--dev` on a dirty tree gives `modified=true`; a release never gives `modified=true`. See it fail (script absent).
- [ ] T010 Create `misc/stamp-build-info.sh <share-dir> (--release <version> <commit> | --dev)` per `contracts/build-info.md`. T009 passes.
- [ ] T011 Commit phase 2 (explicit `git add` paths).

## Phase 3: User Story 1 — the application shows the version and the commit (P1) 🎯 MVP

**Goal**: About, the default title, `${appversion}` and `--version` show the build identity. **Independent test**: build locally, open About, read the title and the Copy line.

- [ ] T012 [US1] Write `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/ui/BuildInfoStringsTest.java` (same pattern as `ArcadeLevelStringsTest`): every new key exists, not empty, in `messages.properties`, `_fr`, `_es`. See it fail.
- [ ] T013 [US1] Add the strings in `common/resources/lang/messages.properties`, `messages_fr.properties`, `messages_es.properties`: About copy button, its confirmation, its failure message, labels "commit" and "modified", refreshed description of the `appversion` variable (FR-010, FR-013). T012 passes.
- [ ] T014 [US1] Create `desktop/TuxGuitar/src/app/tuxguitar/app/system/TGBuildInfo.java`: reads `build-info.properties` from the share path (`TGFileUtils`, `tuxguitar.share.path`, research R5), builds a `BuildIdentity`, never throws (missing or unreadable file ⇒ `dev`, `unknown`).
- [ ] T015 [US1] Write `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/ui/BuildInfoWiringTest.java`: with a given identity, `TGVarAppVersion.toString()` returns its version (FR-012), the title resolved from the default layout reads `TuxGuitarAdvanced <version> - <file>` and from a custom layout is unchanged (FR-002), and `--version` text has name, version, short commit (FR-014). See it fail.
- [ ] T016 [US1] Edit `desktop/TuxGuitar/src/app/tuxguitar/app/system/variables/TGVarAppVersion.java` (return the build identity's version; `TGVersion.CURRENT` stays), `system/config/TGConfigDefaults.java` line 37 (default `${appname} ${appversion} - ${filename}`), `view/main/TGWindow.java` (`loadTitleInCurrentThread` resolves the stored layout through `TitleLayoutDefault`), `app/util/ArgumentParser.java` line 62. T015 passes.
- [ ] T017 [US1] Write the native SWT test `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/ui/AboutBuildInfoSwtTest.java` (pattern of `PreRunWindowLevelSwtTest`): About's title shows the version of a given identity, the details show the full commit, the Copy button puts the expected line on the clipboard. See it fail.
- [ ] T018 [US1] Edit `desktop/TuxGuitar/src/app/tuxguitar/app/view/dialog/about/TGAboutDialog.java`: header `TuxGuitarAdvanced <version>`; a line with the short commit (and "modified"); the full commit in the description tab; the "Copy the version" button (confirmation; message if the clipboard is unavailable). No other change. T017 passes.
- [ ] T019 [US1] Wire `misc/stamp-build-info.sh` into `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml` (ant `exec` in the package phase, writing `Contents/MacOS/share/build-info.properties`; `--release` when properties `release.version`/`release.commit` are given, `--dev` otherwise).
- [ ] T020 [US1] Build macOS locally, run the new and existing tests, launch the app, open About, click Copy and read the clipboard, read the window title, run `--version`; write only what was seen in `specs/015-version-and-commit-everywhere/phase3-report.md`.
- [ ] T021 [US1] Check that a `.tg` file saved by the new build has the same header stamp as one saved by the previous build (SC-005); report the two values.
- [ ] T022 Commit phase 3.

## Phase 4: User Story 4 — a development build says so honestly (P3)

- [ ] T023 [US4] With a clean tree, a modified tree and a copy of the build without the file, open About and read the three cases (scenarios US4 1–3); report what was seen. Code changes only if a case fails (a test first).
- [ ] T024 Commit phase 4 only if files changed.

## Phase 5: User Story 2 — every package says the same version and commit (P1)

**Independent test**: build a package with `-Drelease.version=0.6.0-test -Drelease.numeric=0.6.0.0 -Drelease.commit=<sha>` and read it.

- [ ] T025 [US2] macOS POM `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml`: PlistBuddy steps set `CFBundleShortVersionString` `X.Y.Z`, `CFBundleVersion` `X.Y.Z.0`, `CFBundleGetInfoString` `TuxGuitarAdvanced <version> (<short commit>)`, custom keys `TGBuildVersion` and `TGBuildCommit` (full), from the properties; defaults = dev values. Done before the launcher is signed.
- [ ] T026 [US2] Windows POM `desktop/build-scripts/tuxguitar-windows-swt-x86_64/pom.xml`: launch4j `versionInfo` per T003 (fileVersion and productVersion `X.Y.Z.0`, text versions the full text, product name `TuxGuitarAdvanced`) + the stamp script call for `share/build-info.properties`.
- [ ] T027 [US2] Linux POMs `desktop/build-scripts/tuxguitar-linux-swt/pom.xml` and `tuxguitar-linux-swt-deb/pom.xml`: the stamp script call; Debian `Version` from the property (`X.Y.Z~label`, `-` written `.`), full text and commit in the `Description` trailer of `src/resources/DEBIAN/control` (substituted by property).
- [ ] T028 [US2] Build macOS with `-Drelease.version=0.6.0-test -Drelease.numeric=0.6.0.0 -Drelease.commit=<HEAD sha>` and read Info.plist, `build-info.properties`, About; report the values read. Windows and Linux are not built here; say so.
- [ ] T029 Commit phase 5.

## Phase 6: User Story 3 — a release that does not match is not published (P2)

- [ ] T030 [US3] Write `misc/test-verify-version.sh` (pattern of `misc/test-verify-branding.sh`): minimal fake packages per platform; `verify-version.sh` passes on a correct one and **fails on each place** with one wrong value (version, label, commit, short instead of full commit, wrong file name, `modified=true`). Cases that need `dpkg-deb` or a Windows resource reader run only where the tool exists; the list of cases skipped on this machine is printed, and they are run in CI (T034). See it fail.
- [ ] T031 [US3] Create `misc/verify-version.sh <package> <platform> <version> <commit>` per `contracts/verify-version.md`. T030 passes for the cases available locally.
- [ ] T032 [US3] Write `misc/test-verify-release.sh` with a fake `gh` on the PATH: passes on a correct draft; fails on a published release, a wrong tag, a wrong title, a tag on another commit, notes without the full commit, a missing or extra asset. See it fail.
- [ ] T033 [US3] Create `misc/verify-release.sh <tag> <version> <commit>` per `contracts/verify-version.md` (release level). T032 passes.
- [ ] T034 [US3] Edit `.github/workflows/release.yml`: (a) first job `validate` refuses a tag not matching `^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$`, refuses a tag whose release is already published and a tag existing on another commit, outputs `version`, `numeric`, `commit` (`GITHUB_SHA`); (b) the three build jobs pass `-Drelease.version/-Drelease.numeric/-Drelease.commit`; (c) macOS stamps before signing; (d) each job runs `misc/verify-version.sh` on its packages before `gh release upload`, and the self-tests of the verifiers run in CI (including the cases skipped on macOS); (e) the draft notes get a footer line with the full commit; (f) the last job runs `misc/verify-release.sh` before publishing and sets `--prerelease` for any labelled tag; (g) header comment updated. Every existing gate stays.
- [ ] T035 [US3] Validate the YAML (`actionlint` if installed, else a YAML parse) and read the file once more against FR-001, 004, 005, 006, 011; report that the workflow itself has **not run**.
- [ ] T036 Commit phase 6.

## Phase 7: Polish, documentation, report

- [ ] T037 [P] Update the documentation: how a version is given and where it shows (`README.md` or `docs/`, `INSTALL.md` if it names versions), the title variable, the dev label, consistent with `spec.md`.
- [ ] T038 [P] Run the whole macOS build with tests (`mvn -e clean verify -P native-modules`) and compare with `baseline.txt`; report exactly what passed and what did not.
- [ ] T039 Write `specs/015-version-and-commit-everywhere/final-report.md`: what ran, what only CI can prove (Windows, Linux, the workflow), what was seen by hand, the open points; note that 23 translations of the `appversion` description still say "TuxGuitar".
- [ ] T040 Commit the phase.
- [ ] T041 **Owner**: give the go to push and to dispatch a labelled release (for example `v0.6.0-beta.2`) so the checks run on real packages; read the nine places in the four packages with `misc/verify-version.sh` and the release with `misc/verify-release.sh` (SC-001, SC-002).

## Dependencies and order

- Phase 0 → 1 → 2 → 3 (MVP) → 4 → 5 → 6 → 7. Phase 5 needs T010 and T019; phase 6 needs phase 5; T026 needs T003.
- [P] only where files differ and no earlier task is needed: T005 ∥ T007; T037 ∥ T038.
- Within a phase: the test (seen failing) before the code that makes it pass.

## Implementation strategy

MVP = phases 0–3. Then packages (5), then the pre-publish checks (6). The release (T041) is the owner's step; nothing is pushed or dispatched before.
