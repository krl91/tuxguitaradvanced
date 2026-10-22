# Final report: the same version and commit everywhere (spec 015)

Date: 2026-10-22. Branch `feature/interactive-practice`, local commits only (nothing pushed, no tag, no release made by this work).

## What exists now
- **In the application** (macOS, validated): About shows `TuxGuitarAdvanced <version>`, the short commit under it, the full commit in the description tab, and a **Copy the version** button; the default title of the main window is `TuxGuitarAdvanced <version> - <file>`; `${appversion}` and `--version` give the build identity. A local build says `dev` (with the commit and "modified"), never `9.99-SNAPSHOT`. The `.tg` header stamp and `TGVersion.CURRENT` (2.1.1) are untouched.
- **In the packages**: `share/build-info.properties` in every package; macOS Info.plist (short version `X.Y.Z`, bundle version `X.Y.Z.0`, text version and full commit in `CFBundleGetInfoString`, `TGBuildVersion`, `TGBuildCommit`); Windows exe version resource (launch4j `versionInfo`); Debian `Version: X.Y.Z~label` and the full text and commit in the description. All written in the POMs from `-Drelease.*` properties.
- **In the release pipeline**: the Release workflow validates the tag (any label), derives the identity once, stamps each build, runs `misc/verify-version.sh` on each package before attaching it, checks the draft (`misc/verify-release.sh --draft`), publishes (pre-release for any label), then checks the git tag (`--published`; back to draft if wrong).
- Scripts, each with a self-test: `release-identity`, `stamp-build-info`, `stamp-macos-plist`, `verify-version`, `verify-release`. Doc: `docs/VERSIONING.md`.

## What ran (macOS arm64, Temurin 21)
- Every test written first and seen failing (compilation error or assertion failure, or "script absent" for the shell self-tests).
- Final `mvn -e clean verify -P native-modules` (with tests): **BUILD SUCCESS**, practice module **1172 tests, 0 failures, 10 skipped** (baseline 1148 + 24 new; the same 10 skipped).
- `AboutClipboardSwtTest` (real SWT, profile `swt-gui-tests`): passes, the text is on the system clipboard.
- Self-tests of the scripts: release-identity 19 ok, stamp-build-info 16 ok, stamp-macos-plist 15 ok, verify-version 20 ok, verify-release 17 ok, 0 failed.
- A real macOS build made with `-Drelease.version=0.6.0-test -Drelease.commit=<sha>`: `verify-version.sh` read **9 places OK in the .app and 10 in a zip named like a release**, and `tuxguitar.sh --version` printed `TuxGuitarAdvanced 0.6.0-test (15f66f8)`.
- The probe of a draft release on the fork (owner's go): a draft has a tag name and a target commit but **no git tag** (404 on the ref); the draft was deleted (research R11b).

## What only CI can prove (NOT run)
- The Release workflow itself (YAML parses, every `run:` block parses as bash, actionlint not installed).
- The Windows build (launch4j `versionInfo` accepted by the plugin, exe resource read by `verify-version.sh` on a real launch4j exe: the reader is proved on a fake exe only) and the Linux build (stamp in the bundle, the Debian `control` placeholders replaced, the 4 Debian cases of the self-test).
- Windows and Linux packages were not built or looked at on this machine (no SWT artifact for them in the local repository).

## Not seen (for the owner)
- **T021, partly**: the About window, the window title and the Copy button were not seen on screen (the screenshot was black, System Events listed no window). The wiring is covered by tests (source structure, resolver with the real variables, SWT clipboard); the **layout** of the new line and button is not verified visually.
- **T022**: the `.tg` header stamp was checked by diff only (0 changed lines in `TGVersion` and `TGSongWriterImpl`), no file was saved and read.
- **T024**: the three development cases in About (clean tree, modified tree, no git) were checked at the level of the stamp script and the `--version` output, not in the window.
- **T042** (owner): push, dispatch a labelled release (for example `v0.6.0-beta.2`) and run `misc/verify-version.sh` / `misc/verify-release.sh` on the real packages.

## Decisions and findings during the implementation
- No system clipboard in the UI toolkit: a 1-method interface `UITextClipboard` was added to `TuxGuitar-ui-toolkit` and implemented by `SWTFactory` only (research R14). JavaFX and Qt do not implement it: About then says the copy is not available. Not validated on those toolkits.
- The pure classes `BuildIdentity`, `TitleLayoutDefault` and `TGBuildInfo` are in `desktop/TuxGuitar` (not `common/`), tested from the practice module (research R9).
- The settings window now shows the title layout that is really used (an old stored default shows the new one).
- Windows exe version read with `python3` only (fixed numbers and UTF-16 text), no new dependency (research R7b).
- **In this working copy `gh repo view` resolves to the upstream `helge17/tuxguitar`**: local `gh` commands must pass `--repo krl91/tuxguitaradvanced`. My first probe attempt went to upstream by mistake and failed with 404 (no rights); nothing was created there (checked). The workflow uses `$GITHUB_REPOSITORY`.
- A local build that runs the tests marks itself `modified=true`: a benchmark test rewrites the tracked file `specs/003-arcade-mode/benchmark/scene-baseline-2026-10-08.txt` before the packaging step. Releases are never "modified" (the stamp forces false), and I restored that file each time. Not fixed (outside this feature).
- 23 translations of the `appversion` variable description still say "version of TuxGuitar" (only en/fr/es were refreshed).
- The annotated-tag branch of `verify-release.sh` has no test.
- The 29 items of `checklists/release-consistency.md` are still unchecked (reviewer-owned).

## Addendum: first real release, v0.6.0-beta.2 (2026-10-22)
Pushed 69338944c (one push) and dispatched the Release workflow: run 39822851217, **all six jobs succeeded on the first run**; the release is published as a **pre-release**, its git tag `v0.6.0-beta.2` points to 69338944c, its four files are attached. I then downloaded the published files and read them again with `misc/verify-version.sh` on this machine: macOS zip 10 places OK, **Windows zip 9 places OK (the real launch4j exe: file and product version 0.6.0.0, text versions 0.6.0-beta.2)**, Linux tar.gz 5 places OK. The .deb was not run through the verifier here (no dpkg-deb); its control file, extracted by hand, reads `Version: 0.6.0~beta.2` and the description line `TuxGuitarAdvanced 0.6.0-beta.2 (69338944c3bf…)`; the workflow's own verification of it passed in CI. Still not seen on screen: About, the title and the Copy button (T021).
