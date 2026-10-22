---
description: "Tasks: the same version and commit everywhere (spec 015)"
---

# Tasks: The same version and commit everywhere

**Input**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `checklists/release-consistency.md`
**Tests**: each test is written first and seen failing (said honestly when it cannot fail first). Existing tests are never modified.
**Rules**: local commits only, one per phase; no push, no release, no tag without the owner's explicit go; Windows and Linux are validated by CI only and reported so. `common/` is not modified; new classes use nothing newer than Java 9 (every existing module stays at release 9).
**Format**: `- [ ] T### [P?] [US?] Description with file path`

## Phase 0: Gate (nothing is implemented before it)

- [x] T001 **Owner** (confirmed 2026-10-22): confirm or correct the decisions listed in `spec.md` (Assumptions, "Decisions taken while preparing the tasks") and review `checklists/release-consistency.md`. `/speckit-implement` does not start until this is done.

## Phase 1: Setup and unknowns

- [x] T002 (tests: 1148 run in the practice module, 0 failures, 10 skipped; the .app packaging step failed on a Finder .DS_Store race and is verified at T021, see baseline.txt) Build the baseline on macOS (`cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa && mvn -e clean verify -P native-modules`) and note which tests pass today in `specs/015-version-and-commit-everywhere/baseline.txt`.
- [x] T003 Choose and write in `research.md` R7 the tool that reads the version resource of `tuxguitar.exe` on the Ubuntu runner and on macOS (for example `python3` with the `pefile` module, or `strings`; check what is installed and what `pip` would add, and keep the choice free of new project dependencies). Confirm from the launch4j-maven-plugin 2.1.2 documentation or jar which `versionInfo` elements exist (file version 4 numbers, text file version, product version, text product version, product name, original file name, internal name) and write the answer in `research.md` R7. If the text version cannot be carried, stop and report to the owner.
- [x] T004 Confirm in `research.md` R9: a test in `desktop/TuxGuitar-practice/test/app/tuxguitar/app/system/` can load a class of `desktop/TuxGuitar` (dependency in `desktop/TuxGuitar-practice/pom.xml`; one trivial throwaway check, then deleted). Read `TGWindow.loadTitleInCurrentThread` (~line 174) and find the class that resolves `${…}` variables (`TGVarAdapter` and its resolver); say whether it can be exercised without a window; if not, T016 tests the resolver with the layout string only and says so.
- [x] T005 (done 2026-10-22 with the owner's go; result in research.md R11b) **Owner approval, then experiment** (outward-facing, a few network calls): create a throwaway draft release with `gh release create zz-probe-015 --draft --target <sha>`, read whether a git tag exists (`gh api repos/<repo>/git/ref/tags/zz-probe-015`) and what `target_commitish` shows, then delete the draft; write the result in `research.md` R11. Not done without the owner's go.

## Phase 2: Foundational (blocks every story)

- [x] T006 [P] Write `desktop/TuxGuitar-practice/test/app/tuxguitar/app/system/BuildIdentityTest.java`: parse of `contracts/build-info.md` (all keys; missing file/keys fall back to `dev`/`unknown`; unknown keys ignored); `numeric` never contains the label; `shortCommit` = 7 characters; `title()`, `copyLine()` (`TuxGuitarAdvanced <version> (<40 hex>[, modified])`), the forms of the table "Forms of the development build" in `data-model.md`; `modified` only for `dev`; version validation `^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$` (accepts `0.6.0`, `0.6.0-beta.1`, `0.6.0-test`; refuses `0.6`, `0.6.0-`, `0.6.0-be ta`, empty); commit validation 40 hex. See it fail.
- [x] T007 Create `desktop/TuxGuitar/src/app/tuxguitar/app/system/BuildIdentity.java` (new class, Java 9 syntax only, no I/O: parses a `java.util.Properties`; fields and rules of `data-model.md`). T006 passes.
- [x] T008 [P] Write `desktop/TuxGuitar-practice/test/app/tuxguitar/app/system/TitleLayoutDefaultTest.java`: a stored layout equal to the old default `${appname} - ${filename}` gives the new default `${appname} ${appversion} - ${filename}`; any other stored layout (empty, custom, one already containing `${appversion}`) is unchanged; null gives the new default. See it fail.
- [x] T009 Create `desktop/TuxGuitar/src/app/tuxguitar/app/system/TitleLayoutDefault.java` (new, pure, Java 9 only). T008 passes.
- [x] T010 Write `misc/test-stamp-build-info.sh` (self-test): the stamp script writes the four keys; refuses a bad version and a bad commit (exit 1); `--dev` outside git gives `commit=unknown`; `--dev` on a dirty tree gives `modified=true`; a release never gives `modified=true`. See it fail (script absent).
- [x] T011 Create `misc/stamp-build-info.sh <share-dir> (--release <version> <commit> | --dev)` per `contracts/build-info.md`. T010 passes.
- [x] T012 Commit phase 2 (explicit `git add` paths).

## Phase 3: User Story 1 — the application shows the version and the commit (P1) 🎯 MVP

**Goal**: About, the default title, `${appversion}` and `--version` show the build identity. **Independent test**: build locally, open About, read the title and the Copy line.

- [x] T013 [US1] Write `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/ui/BuildInfoStringsTest.java` (same pattern as `ArcadeLevelStringsTest`): every new key exists, not empty, in `messages.properties`, `_fr`, `_es`. See it fail.
- [x] T014 [US1] Add the strings in `common/resources/lang/messages.properties`, `messages_fr.properties`, `messages_es.properties`: About copy button, its confirmation, its failure message, labels "commit" and "modified", refreshed description of the `appversion` variable (FR-010, FR-013). T013 passes.
- [x] T015 [US1] Create `desktop/TuxGuitar/src/app/tuxguitar/app/system/TGBuildInfo.java`: reads `build-info.properties` from the share path (`TGFileUtils`, `tuxguitar.share.path`, research R5), builds a `BuildIdentity`, never throws (missing or unreadable file ⇒ `dev`, `unknown`).
- [x] T016 [US1] Write `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/ui/BuildInfoWiringTest.java`: with a given identity, `TGVarAppVersion.toString()` returns its version (FR-012), the title resolved from the default layout reads `TuxGuitarAdvanced <version> - <file>` and from a custom layout is unchanged (FR-002), and `--version` text has name, version, short commit (FR-014). See it fail.
- [x] T017 [US1] Edit `desktop/TuxGuitar/src/app/tuxguitar/app/system/variables/TGVarAppVersion.java` (return the build identity's version; `TGVersion.CURRENT` stays), `system/config/TGConfigDefaults.java` line 37 (default `${appname} ${appversion} - ${filename}`), `view/main/TGWindow.java` (`loadTitleInCurrentThread` resolves the stored layout through `TitleLayoutDefault`), `app/util/ArgumentParser.java` line 62. T016 passes.
- [x] T018 [US1] Write two tests. (a) `desktop/TuxGuitar-practice/test/app/tuxguitar/app/tools/custom/practice/ui/AboutBuildInfoSourceTest.java` (source-structure test, pattern of `PracticeHostSourceTest`): `TGAboutDialog` builds its title from `TGBuildInfo`, no longer from `TGVersion`, shows the short commit and the full commit, has the Copy button, and the copy line comes from `BuildIdentity.copyLine()`. (b) `AboutClipboardSwtTest.java` (tag `swt-gui`, pattern of `PreRunWindowLevelSwtTest`): the SWT factory implements `UITextClipboard` and `copyText(line)` puts the line on the system clipboard (read back with SWT). A native test of the whole About window is not possible (it needs the whole application context); that part is seen by hand in T021. See both fail.
- [x] T019 [US1] Edit `desktop/TuxGuitar/src/app/tuxguitar/app/view/dialog/about/TGAboutDialog.java`: header `TuxGuitarAdvanced <version>` and under it a line with the short commit (and "modified"); the full commit at the top of the description tab; the copy line in a read-only selectable field and a "Copy the version" button that calls `copyText` when the factory implements `UITextClipboard` (confirmation text) and otherwise or on failure says so (the line stays selectable). **Clipboard (found while implementing, research R14)**: the toolkit has no clipboard, so add the small interface `desktop/TuxGuitar-ui-toolkit/src/app/tuxguitar/ui/UITextClipboard.java` (`boolean copyText(String)`) and implement it in `desktop/TuxGuitar-ui-toolkit-swt/src/app/tuxguitar/ui/swt/SWTFactory.java` with `org.eclipse.swt.dnd.Clipboard` and `TextTransfer`; JavaFX and Qt are not changed (the fallback applies; not validated there). T018 passes.
- [x] T020 [US1] Wire `misc/stamp-build-info.sh` into `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml` (ant `exec` in the package phase, writing `Contents/MacOS/share/build-info.properties`; `--release` when properties `release.version`/`release.commit` are given, `--dev` otherwise).
- [ ] T021 [US1] (PARTLY DONE: build, tests and `--version` ran; the About window, the title and the Copy button were not seen, see phase3-report.md; for the owner or a later run) Build macOS locally, run the new and existing tests, launch the app, open About, click Copy and read the clipboard, read the window title, run `--version`; write only what was seen in `specs/015-version-and-commit-everywhere/phase3-report.md`.
- [ ] T022 [US1] (checked by diff only: 0 changed lines in TGVersion and TGSongWriterImpl; no file saved, see phase3-report.md) Check that a `.tg` file saved by the new build has the same header stamp as one saved by the previous build (SC-005); report the two values.
- [x] T023 Commit phase 3.

## Phase 4: User Story 4 — a development build says so honestly (P3)

- [ ] T024 [US4] With a clean tree, a modified tree and a copy of the build without the file, open About and read the three cases (scenarios US4 1–3); report what was seen. Code changes only if a case fails (a test first).
- [ ] T025 Commit phase 4 only if files changed.

## Phase 5: User Story 2 — every package says the same version and commit (P1)

**Independent test**: build a package with `-Drelease.version=0.6.0-test -Drelease.numeric=0.6.0.0 -Drelease.commit=<sha>` and read it.

- [x] T026 [US2] macOS POM `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml`: PlistBuddy steps set `CFBundleShortVersionString` `X.Y.Z`, `CFBundleVersion` `X.Y.Z.0`, `CFBundleGetInfoString` `TuxGuitarAdvanced <version> (<short commit>)`, custom keys `TGBuildVersion` and `TGBuildCommit` (full), from the properties; defaults = dev values. Done before the launcher is signed.
- [x] T027 [US2] (written, NOT run: needs the Windows SWT artifact and Mingw, CI only) Windows POM `desktop/build-scripts/tuxguitar-windows-swt-x86_64/pom.xml`: launch4j `versionInfo` per T003 (fileVersion and productVersion `X.Y.Z.0`, text versions the full text, product name `TuxGuitarAdvanced`) + the stamp script call for `share/build-info.properties`.
- [x] T028 [US2] (written, NOT run: needs the Linux SWT artifact and dpkg-deb, CI only) Linux POMs `desktop/build-scripts/tuxguitar-linux-swt/pom.xml` and `tuxguitar-linux-swt-deb/pom.xml`: the stamp script call; Debian `Version` from the property (`X.Y.Z~label`, `-` written `.`), full text and commit in the `Description` trailer of `src/resources/DEBIAN/control` (substituted by property).
- [x] T029 (macOS: 9 places read OK in the .app and 10 in a zip named like a release, built with 0.6.0-test; the Windows and Linux builds cannot run on this machine: no SWT artifact for them in the local repository; their POM changes are read, not run) [US2] Build macOS with `-Drelease.version=0.6.0-test -Drelease.numeric=0.6.0.0 -Drelease.commit=<HEAD sha>` and read Info.plist, `build-info.properties`, About; report the values read. Windows and Linux are not built here; say so.
- [x] T030 Commit phase 5.

## Phase 6: User Story 3 — a release that does not match is not published (P2)

- [x] T031 [US3] Write `misc/test-verify-version.sh` (pattern of `misc/test-verify-branding.sh`): minimal fake packages per platform; `verify-version.sh` passes on a correct one and **fails on each place** with one wrong value (version, label, commit, short instead of full commit, wrong file name, `modified=true`). Cases that need `dpkg-deb` or a Windows resource reader run only where the tool exists; the list of cases skipped on this machine is printed, and they are run in CI (T035). See it fail.
- [x] T032 [US3] Create `misc/verify-version.sh <package> <platform> <version> <commit>` per `contracts/verify-version.md`. T031 passes for the cases available locally.
- [x] T033 [US3] Write `misc/test-verify-release.sh` with a fake `gh` on the PATH: passes on a correct draft (`--draft`) and on a correct published release (`--published`); fails on a published release in draft mode, a wrong tag name, a wrong title, a wrong target commit, notes without the full commit, a missing or extra asset, and in published mode a tag on another commit. See it fail.
- [x] T034 [US3] (17 cases of the self-test pass with a fake gh; the annotated-tag branch is not covered by a test) Create `misc/verify-release.sh <tag> <version> <commit>` per `contracts/verify-version.md` (release level). T033 passes.
- [x] T035 [US3] Edit `.github/workflows/release.yml`: (a) first job `validate` refuses a tag not matching `^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$`, refuses a tag whose release is already published and a git tag of that name existing on another commit, outputs `version`, `numeric`, `commit` (`GITHUB_SHA`); (b) the three build jobs pass `-Drelease.version/-Drelease.numeric/-Drelease.commit`; (c) macOS stamps before signing; (d) each job runs `misc/verify-version.sh` on its packages before `gh release upload`, and the self-tests of the verifiers run in CI (including the cases skipped on macOS); (e) the draft notes get a footer line with the full commit; (f) the last job runs `misc/verify-release.sh --draft` before publishing, sets `--prerelease` for any labelled tag, then runs `misc/verify-release.sh --published` and puts the release back to draft and fails if the tag is not on the commit; (g) header comment updated. Every existing gate stays.
- [x] T036 [US3] (YAML parses; every `run:` block parses as bash; actionlint is not installed here; the workflow has NOT run) Validate the YAML (`actionlint` if installed, else a YAML parse) and read the file once more against FR-001, 004, 005, 006, 011; report that the workflow itself has **not run**.
- [x] T037 Commit phase 6.

## Phase 7: Polish, documentation, report

- [x] T038 [P] Update the documentation: how a version is given and where it shows (`README.md` or `docs/`, `INSTALL.md` if it names versions), the title variable, the dev label, consistent with `spec.md`.
- [x] T039 [P] (BUILD SUCCESS, 1172 tests, 0 failures, 10 skipped as in the baseline) Run the whole macOS build with tests (`mvn -e clean verify -P native-modules`) and compare with `baseline.txt`; report exactly what passed and what did not.
- [x] T040 Write `specs/015-version-and-commit-everywhere/final-report.md`: what ran, what only CI can prove (Windows, Linux, the workflow), what was seen by hand, the open points; note that 23 translations of the `appversion` description still say "TuxGuitar".
- [x] T041 Commit the phase.
- [x] T042 (done 2026-10-22: pushed 69338944c, release v0.6.0-beta.2, run 39822851217 all jobs green; packages downloaded and read again by hand, see final-report.md addendum) **Owner**: give the go to push and to dispatch a labelled release (for example `v0.6.0-beta.2`) so the checks run on real packages; read the nine places in the four packages with `misc/verify-version.sh` and the release with `misc/verify-release.sh` (SC-001, SC-002).

## Dependencies and order

- Phase 0 → 1 → 2 → 3 (MVP) → 4 → 5 → 6 → 7. Phase 5 needs T011 and T020; phase 6 needs phase 5; T027 needs T003; T033, T034 and T035 need the result of T005 (if a draft turns out to have a git tag, the two modes of `verify-release.sh` and the contract are rewritten before T033).
- [P] only where files differ and no earlier task is needed: T006 ∥ T008; T038 ∥ T039.
- Within a phase: the test (seen failing) before the code that makes it pass.

## Implementation strategy

MVP = phases 0–3. Then packages (5), then the pre-publish checks (6). The release (T042) is the owner's step; nothing is pushed or dispatched before.
