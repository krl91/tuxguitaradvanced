# Implementation Plan: The same version and commit everywhere

**Branch**: `feature/interactive-practice` (spec dir `015-version-and-commit-everywhere`) | **Date**: 2026-10-22 | **Spec**: [spec.md](spec.md)

## Summary

The release version is given **once**, as the tag of the Release workflow (`v0.6.0-beta.1`). The workflow derives from it, and from the checked-out commit, one build identity (version, numeric version, full commit), stamps it into **a file inside every package** (`share/build-info.properties`, read by the application) and into **each package's own metadata** (Info.plist, Windows exe version resource, Debian control), names the files and the release with it, then **checks every place against the identity before the draft release is published**. The application shows the identity in About (title, short commit, full commit, Copy button), in the default main-window title and through the `${appversion}` variable. A local build writes the same file from `git` with the label `dev`. The `.tg` stamp (`TGVersion.CURRENT`, 2.1.1) is **not touched**.

## Technical Context

**Language/Version**: Java (app, release 9 for the modules touched), bash (stamp and verify scripts), GitHub Actions YAML, Maven/Ant `exec` in the three SWT build-script POMs.
**Primary Dependencies**: none new. Existing: `PlistBuddy` (macOS), `launch4j-maven-plugin` 2.1.2 (`versionInfo`), `dpkg-deb`, `git`.
**Storage**: one generated text file per package, `share/build-info.properties` (contract: [contracts/build-info.md](contracts/build-info.md)).
**Testing**: JUnit 5 for the parsing/formatting class and the title; shell self-test of the verifier (like `misc/test-verify-branding.sh`); native SWT test for About if a test fits the existing pattern; final check on the real packages of a release.
**Target Platform**: macOS arm64 (validated by hand), Windows x86_64 and Linux amd64/tar.gz (CI only, stated as such).
**Project Type**: desktop app + release pipeline.
**Constraints**: no change to `.tg` content; no new dependency; packages already complete (TarsosDSP) stay complete; the existing branding steps of the workflow (asset test, verifier self-test, verification of each package) stay.
**Scale/Scope**: 4 packages × 9 places (spec SC-001).

## Constitution Check

| Principle | Verdict |
|---|---|
| I Preserve compatibility | OK. `TGVersion.CURRENT` and `TGSongWriterImpl` untouched (FR-008); `${appversion}` changes value only in the fork's text (title, version line), the user's own title layout is kept. Config `CONFIG_APP_VERSION` (`TGConfigManager:39`) and `KeyBindingWriter:85` keep `TGVersion.CURRENT` (file/config formats). |
| II Extend before modifying core | **Justified core change**, minimal. Inspected limitation: `TGAboutDialog.RELEASE_NAME` (line 33) and `TGVarAppVersion.toString()` read the hard-coded `TGVersion.CURRENT` and there is no extension point for either; the default title layout is `TGConfigDefaults` WINDOW_TITLE. Changes: three new classes in `desktop/TuxGuitar`, package `app.tuxguitar.app.system` (`BuildIdentity`, `TGBuildInfo`, `TitleLayoutDefault`; no song or core model class), short edits in `TGAboutDialog`, `TGVarAppVersion`, `TGWindow` and `ArgumentParser`, one default-layout string, one button. `common/` is not touched (Android keeps its own `TGVarAppVersion`; out of scope). The pure classes are tested from `desktop/TuxGuitar-practice/test/`, which depends on the `tuxguitar` module, so no limitation forces a change in `common/TuxGuitar-lib` (confirmed by task T004 before any code). |
| III Evidence-based | Sources cited in research.md R1–R8. |
| IV Spec-driven | Spec + 5 clarifications done; this is the plan. |
| V Incremental | Phases in plan below, each leaves the app buildable. |
| VI Real-time audio | Not concerned (no audio path). |
| VII Separable logic | `BuildIdentity` (parsing/formatting) and `TitleLayoutDefault` are pure classes tested without UI. |
| VIII Deterministic | N/A. |
| IX Cross-platform | Stated per phase: macOS validated here; Windows/Linux by CI only. |
| X No unrelated refactoring | Only the files listed; no reformatting. |
| XI Build integrity | Existing tests must pass; new tests added first and seen failing. |
| XII Truthful reporting | Final report says what ran on which system. |
| XIII/XIV | N/A. |
| Strings | New texts in `common/resources/lang/messages{,_fr,_es}.properties` (FR-010). |

No deviation to record. Post-design re-check: unchanged.

## Phases (each buildable, tests first)

0. **Gate and check of the unknowns** — owner confirms the decisions of the spec Assumptions; launch4j 2.1.2 version-info fields are confirmed from its documentation/jar before the Windows work is planned further.
1. **Identity in the app (macOS, local)** — `TGBuildInfo` (read `share/build-info.properties`; formats: title `TuxGuitarAdvanced <version>`, copy line, short commit 7); `TGAboutDialog` title + commit lines + Copy button; `TGVarAppVersion` = the version of `BuildIdentity` (read through `TGBuildInfo`); default title layout; strings ×3 languages; `misc/stamp-build-info.sh` and its call from the macOS POM (dev label, commit from git, `modified`). Covers US1 (app side), US4, FR-002, 007, 012, 013.
2. **Package metadata + names** — the three SWT POMs take `-Drelease.version/-Drelease.numeric/-Drelease.commit` (defaults = dev values): Info.plist (`CFBundleShortVersionString` text, `CFBundleVersion` `X.Y.Z.0`, `CFBundleGetInfoString`), launch4j `versionInfo` (`fileVersion`/`txtFileVersion`/`productVersion`), Debian `Version` (`~` for the label), the build-info call in the Linux and Windows POMs. Covers US2 packages, FR-003, FR-009.
3. **Workflow + pre-publish check** — tag validation job first (FR-011), derive identity once and pass it to the three builds, release notes footer with the commit, `misc/verify-version.sh <package> <platform> <version> <commit>` run on each package before upload, `misc/verify-release.sh <tag> <version> <commit>` on the draft before publication (title, target commit, notes, assets) and again right after (the tag points to the commit), a published tag refused up front, any label ⇒ pre-release, self-test of the verifier. Covers US3, FR-001, 004, 005, 006.
4. **Docs + a real release** — README/INSTALL/release docs, `docs/release-notes`; the check run on the packages of an actual labelled release (needs the owner's go to push and dispatch).

## Project Structure

```text
specs/015-version-and-commit-everywhere/  spec, plan, research, data-model, quickstart, contracts/build-info.md, contracts/verify-version.md
desktop/TuxGuitar/src/app/tuxguitar/app/system/{BuildIdentity,TGBuildInfo,TitleLayoutDefault}.java   (new)
desktop/TuxGuitar/src/app/tuxguitar/app/view/dialog/about/TGAboutDialog.java   (edit)
desktop/TuxGuitar/src/app/tuxguitar/app/system/variables/TGVarAppVersion.java  (edit)
desktop/TuxGuitar/src/app/tuxguitar/app/system/config/TGConfigDefaults.java    (default title layout)
common/resources/lang/messages{,_fr,_es}.properties
desktop/TuxGuitar-ui-toolkit/src/app/tuxguitar/ui/UITextClipboard.java   (new, 1 method)
desktop/TuxGuitar-ui-toolkit-swt/src/app/tuxguitar/ui/swt/SWTFactory.java   (implements it)
desktop/build-scripts/{tuxguitar-macosx-swt-cocoa,tuxguitar-windows-swt-x86_64,tuxguitar-linux-swt,tuxguitar-linux-swt-deb}/pom.xml
misc/stamp-build-info.sh, misc/verify-version.sh, misc/verify-release.sh and their self-tests   (new)
.github/workflows/release.yml
```

`desktop/TuxGuitar` has no test directory; its new classes are tested from `desktop/TuxGuitar-practice/test/app/tuxguitar/app/system/` and `…/ui/` (research R9).
