---
description: "Task list for spec 020: macOS package on macOS 15 machines, minimum macOS 15, the same stable signature, Ubuntu pinned"
---

# Tasks: macOS package on macOS 15 machines, minimum macOS 15, the same stable signature, Ubuntu pinned

**Input**: `specs/020-macos15-ci/` (spec.md, plan.md, research.md R1–R8, data-model.md, contracts/scripts.md, quickstart.md)

**Tests**: required by FR-012 and SC-004: every new check of `misc/verify-macos-bundle.sh` has a case in the new `misc/test-verify-macos-bundle.sh` that is written first and seen red.

**Rules for every phase**:
- local commands use JDK 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"`) from `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`;
- after each Maven build run `git checkout -- specs/003-arcade-mode/benchmark/`;
- nothing is ever added to the trust settings of the owner's Mac;
- the owner's open TuxGuitar is never touched;
- one commit per phase, with no Co-Authored-By trailer;
- **no push before Phase 8**.

**Paths**: `CB` = `desktop/build-scripts/tuxguitar-macosx-swt-cocoa`; `APP` = `CB/target/tuxguitar-9.99-SNAPSHOT-macosx-swt-cocoa.app`; `TEST` = `misc/test-verify-macos-bundle.sh`; `SEALED` = a fresh copy of `APP` in the scratchpad, sealed ad hoc with `misc/seal-macos-app.sh` (as the release seals the bundle; `APP` itself is never sealed or changed); `VERIFY` = `misc/verify-macos-bundle.sh`.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup (baseline)

- [X] T001 Run the full gate `mvn -o -e clean verify -P native-modules` in `CB` and record in `specs/020-macos15-ci/baseline.txt`:
  - the result and the test counts;
  - the `minos` of `APP/Contents/MacOS/tuxguitar-launcher` and of each slice (`otool -arch x86_64|arm64 -l`) of `APP/Contents/Resources/tuxguitar/lib/libtuxguitar-audiounit-jni.jnilib` (27.0 expected, R1);
  - the absence of `LSMinimumSystemVersion` in `APP/Contents/Info.plist`;
  - the output of `VERIFY APP` today: check 1 OK and check 2 WRONG (*code has no resources but signature indicates they must be present*, seen in the second analysis), because the local build signs only the launcher and the whole bundle is sealed by the release;
  - the output of `VERIFY SEALED`, where `SEALED` is a copy of `APP` in the scratchpad sealed ad hoc with `misc/seal-macos-app.sh` (2 OK lines expected);
  - the result of `misc/test-stamp-macos-plist.sh` and `misc/test-relocate-macos-payload.sh`.

---

## Phase 2: Foundational (the self-test harness)

**Purpose**: the fake bundles every story's test cases are made from. No story can write its cases before it.

- [X] T002 Create `TEST` (bash, `set -uo pipefail`, executable), in the style of `misc/test-verify-version.sh`. Requirements:
  - a temporary folder removed on exit;
  - a refusal to run off macOS (`uname` ≠ Darwin: print why and exit 2).
  
  Helpers, each building into the temporary folder with `/usr/bin/clang`:
  - `macho <file> <minos> [archs]`: a tiny Mach-O built with `-mmacosx-version-min=<minos>`, for each arch given (default arm64), joined with `lipo` when there are two;
  - `nominos <file>`: a Mach-O with no minimum: a minimal 64-bit arm64 Mach-O header (`MH_MAGIC_64`, filetype `MH_DYLIB`) with no load command, written by a small `python3` step; `file -b` says Mach-O and `otool -l` shows no minimum;
  - `bundle <dir> <plist-min|none> <launcher-min> <au-x86-min> <au-arm-min> [jre-min|none]`: a `<dir>/T.app` with `Contents/MacOS/tuxguitar-launcher`, `Contents/Resources/tuxguitar/lib/libtuxguitar-audiounit-jni.jnilib` (two slices), optionally `Contents/Resources/tuxguitar/jre/lib/libjava.dylib`, and a `Contents/Info.plist` (written with `plistlib`) holding `CFBundleIdentifier=app.tuxguitaradvanced`, `CFBundleExecutable=tuxguitar-launcher` and `LSMinimumSystemVersion` when given;
  - `adhoc <app>`: `misc/seal-macos-app.sh <app>` (ad hoc);
  - `throwaway_identity`: an openssl self-signed code-signing certificate (CN `Throwaway Test Signing`, EKU codeSigning), exported to a .p12 with `openssl pkcs12 -export … -legacy`, and without `-legacy` if that option is unknown (LibreSSL's `/usr/bin/openssl`): OpenSSL 3 and later encrypt a .p12 by default in a way `security import` may refuse (research R3; the owner's Mac has Homebrew OpenSSL 4 first in its PATH), then imported into a throwaway keychain in the temporary folder (`security create-keychain`, `import -T /usr/bin/codesign`, `set-key-partition-list`), never trusted; sets `KC` and `SHA1` (from `security find-identity -p codesigning "$KC"`); the keychain is deleted on exit, whatever happens;
  - `case_ <name> <pass|refuse:<n>> <command…>`: runs the command and counts failures. It prints `PASS <name>` or `FAIL <name> (expected …, got exit <n>: <last WRONG line or none>)`.
    - `pass` is PASS only for exit 0 with no `WRONG` line.
    - `refuse:<n>` is PASS only for **exit 1 with a line starting `WRONG   [<n>]`**, the number of the expected check, so that a case refused by another check, by an unreadable package (exit 2) or by a broken fake bundle never passes. `VERIFY` prefixes every `OK` and `WRONG` line with its check number (T004 adds the prefix to the existing checks 1 and 2 too).
    - A helper that fails to build its fake bundle stops the test with exit 2 (not a FAIL of a case).
  
  The test ends with `exit 1` if any case failed, else 0.
  
  First case: `right bundle, ad hoc` (`bundle … 15.0 15.0 15.0 15.0 11.0`, `adhoc`, expect pass). Run it: it passes against today's `VERIFY` (checks 1–2 only).

---

## Phase 3: User Story 3 — macOS 15 is the declared minimum (Priority: P2, but first: the release checks of US1 depend on it)

**Goal**: FR-002, FR-003, FR-004; SC-003.

**Independent Test**: quickstart step 1 shows 15.0 four times; `TEST` passes; `VERIFY SEALED` prints the new OK lines.

### Tests for User Story 3 (write first, see them red)

- [X] T003 [US3] Add to `TEST` the cases of contracts/scripts.md for checks 3–5:
  - `no LSMinimumSystemVersion` (refuse:3);
  - `LSMinimumSystemVersion 14.0` (refuse:3);
  - `launcher 14.0` (refuse:4);
  - `launcher 26.0` (refuse:4);
  - `AudioUnit x86_64 slice 27.0` (refuse:4);
  - `JRE library 26.0` (refuse:5);
  - `JRE library with no minimum` (refuse:5);
  - `JRE library 11.0` (pass);
  - `AudioUnit library missing` (refuse:4).
  
  Every bundle is sealed ad hoc. Run `TEST`: the refuse cases FAIL against today's `VERIFY` (red). Record the red run in baseline.txt.

### Implementation for User Story 3

- [X] T004 [US3] In `VERIFY`, after check 2, add checks 3, 4 and 5 of contracts/scripts.md, in the existing `OK      …` / `WRONG   …` format.
  - The expected value is the constant `MIN=15.0`, commented as equal to `macos.minimumVersion` in `desktop/pom.xml`.
  - Read `minos` per architecture with `lipo -archs` and `otool -arch <a> -l` (`LC_BUILD_VERSION` `minos`, else `LC_VERSION_MIN_MACOSX` `version`).
  - Compare versions numerically (major.minor).
  - Check 5 walks every regular file under `Contents/`, keeps the Mach-O ones (`file -b` starting with `Mach-O`), and skips the launcher and the AudioUnit library.
  - Every `OK` and `WRONG` line, the existing checks 1 and 2 included, starts with its check number after the word: `OK      [1] …`, `WRONG   [4] …`, as `TEST` requires.
  - A WRONG line names the file from `Contents/` and the value found.
  
  Make `TEST` green.
- [X] T005 [US3] Add `<macos.minimumVersion>15.0</macos.minimumVersion>` to the `<properties>` of `desktop/pom.xml`, with a comment pointing to spec 020 and `misc/verify-macos-bundle.sh`.
- [X] T006 [P] [US3] In `CB/pom.xml`:
  - add `<arg value="-mmacosx-version-min=${macos.minimumVersion}" />` to the launcher's `/usr/bin/clang` exec (~line 407);
  - add a PlistBuddy exec `Add :LSMinimumSystemVersion string ${macos.minimumVersion}` beside the fork's other Info.plist edits (~line 437).
- [X] T007 [P] [US3] In `desktop/build-scripts/native-modules/tuxguitar-audiounit-macos/pom.xml`, add `<env key="MACOSX_DEPLOYMENT_TARGET" value="${macos.minimumVersion}" />` to the `make` exec that builds the library (~line 40), not the `clean` one. `desktop/TuxGuitar-AudioUnit/jni/Makefile` (upstream) stays unchanged.
- [X] T008 [US3] Gate:
  - the full gate in `CB` (green, same counts as T001);
  - quickstart step 1: launcher 15.0, both AudioUnit slices 15.0, `LSMinimumSystemVersion` 15.0;
  - `VERIFY SEALED` (made from the new build): only OK lines;
  - `TEST` green.
  
  Record the results in baseline.txt, tick T003–T008, and commit the phase.

---

## Phase 4: User Story 2 — the same signature identity (Priority: P1)

**Goal**: FR-005, FR-006, FR-007, FR-008; SC-004.

**Independent Test**: quickstart step 3, with a throwaway certificate, prints the forced requirement and `VERIFY --signed-by` passes; `TEST` passes.

### Tests for User Story 2 (write first, see them red)

- [X] T009 [US2] Add to `TEST` the cases of contracts/scripts.md for check 6. Each uses a right bundle (all 15.0, JRE 11.0):
  - `throwaway certificate, forced root requirement, --signed-by its SHA1` (pass). Sign with `codesign --force --keychain "$KC" -s "$SHA1" --identifier app.tuxguitaradvanced -r='designated => identifier "app.tuxguitaradvanced" and certificate root = H"<sha1 lower>"'` directly, since `seal-macos-app.sh` gets its 4th argument only in T011;
  - `throwaway certificate, --signed-by another SHA1` (refuse:6);
  - `ad hoc, --signed-by SHA1` (refuse:6);
  - `throwaway certificate, default requirement (certificate leaf), --signed-by SHA1` (refuse:6).
  
  Run `TEST`: today's `VERIFY` ignores `--signed-by` (or fails on the unknown option), so the cases do not give the expected results (red). Record it in baseline.txt.

### Implementation for User Story 2

- [X] T010 [US2] In `VERIFY`:
  - parse an optional `--signed-by <sha1>` after the package (a 40-hex value, case-insensitive, stored in lower case; anything else exits 2 with a usage line);
  - with it, add check 6 of contracts/scripts.md:
    - the line `designated => …` of `codesign -d -r- "$APP"` must equal exactly `designated => identifier "app.tuxguitaradvanced" and certificate root = H"<sha1>"`;
    - `codesign --verify --strict -R='identifier "app.tuxguitaradvanced" and certificate root = H"<sha1>"' "$APP"` must pass;
    - its lines are numbered `[6]`, and the WRONG line shows the requirement found;
  - update the header comment (the checks and the usage).
  
  Make `TEST` green.
- [X] T011 [P] [US2] In `misc/seal-macos-app.sh`:
  - accept an optional 4th argument, the requirement text, passed to codesign as `-r=<requirement>`;
  - update the usage line and the header comment;
  - with no 4th argument the codesign call is unchanged.
  
  Then switch the T009 pass case to sign through `seal-macos-app.sh <app> "$SHA1" "$KC" '<requirement>'`, and run `TEST` (green).
- [X] T012 [US2] In `.github/scripts/sign-macos-launcher.sh`:
  - remove `sudo security authorizationdb write com.apple.trust-settings.admin allow`, `sudo security add-trusted-cert …`, the `echo "adding the certificate to the trust settings"` and the `remove-trusted-cert` line of `cleanup`;
  - find the identity with `security find-identity -p codesigning "$KEYCHAIN"` (not `-v`): exactly one **distinct** SHA-1 expected among the lines of its *Matching identities* part (an identity trusted on the machine would be listed again under *Valid identities only*), otherwise print the listing and exit 1;
  - build `REQUIREMENT='designated => identifier "app.tuxguitaradvanced" and certificate root = H"'<sha1 lower>'"'` and call `seal-macos-app.sh "$APP" "$IDENTITY" "$KEYCHAIN" "$REQUIREMENT"`;
  - keep the last line that prints the designated requirement;
  - update the header comment: no trust step (research R3), why the requirement is forced (the microphone permission), and that the ad hoc path without secrets is unchanged.
- [X] T013 [US2] Gate, quickstart step 3, on the owner's Mac:
  - copy `APP` into the scratchpad;
  - copy the local Temurin 21 JDK into its `Contents/Resources/tuxguitar/jre`, as the release does;
  - make a throwaway certificate as in `TEST` (the same `-legacy` rule for the .p12) and export it as `MACOS_CERT_P12_BASE64` / `MACOS_CERT_PASSWORD`;
  - **before**, save the keychain search list of the owner's account (`security list-keychains -d user > <scratch>/keychains.before`): the sign script adds its throwaway keychain to that list (line 44) and only its cleanup takes it out;
  - run `.github/scripts/sign-macos-launcher.sh <copy>` with `CI=1`;
  - **after, whatever happened** (also after a failure), restore the list (`security list-keychains -d user -s $(tr -d '"' < <scratch>/keychains.before)`) and check that `security list-keychains -d user` equals the saved list.
  
  Expected:
  - no `sudo` prompt;
  - the printed requirement uses the throwaway SHA-1;
  - `VERIFY <copy> --signed-by <that sha1>` prints only OK lines;
  - `VERIFY <copy> --signed-by 280bd902de0512b59d70228c5faa441d3eb6af55` refuses (check 6);
  - `security dump-trust-settings -d` shows nothing new;
  - the keychain search list is the saved one.
  
  Also run the sign script without the two variables: ad hoc, `VERIFY <copy>` OK. Delete the copy and the throwaway files. Record the results in baseline.txt, tick T009–T013, and commit the phase.

---

## Phase 5: User Story 1 — the release on macOS 15 machines (Priority: P1)

**Goal**: FR-001 (release part), FR-004 and FR-006 wired in the release, FR-012 in the release.

**Independent Test**: the release run of Phase 8 (quickstart step 5); locally, a YAML parse of the file and a review of the changed steps.

- [X] T014 [US1] In `.github/workflows/release.yml`, job `build-macos-arm64`:
  - set `runs-on: macos-15   # Apple Silicon`;
  - in the step *Self-tests of the macOS scripts*, add `misc/test-verify-macos-bundle.sh`, and add "bundle verifier" to the step's name;
  - in the step *Verify the bundle*:
    - give it `env: MACOS_CERT_P12_BASE64: ${{ secrets.MACOS_CERT_P12_BASE64 }}`;
    - run `VERIFY` with `--signed-by 280bd902de0512b59d70228c5faa441d3eb6af55` when that variable is not empty, and without it otherwise;
    - write the fingerprint once, as a job-level `env: MACOS_CERT_SHA1: 280bd902de0512b59d70228c5faa441d3eb6af55` with a comment: the stable certificate's public fingerprint, read from the v0.8.0-beta.2 log, spec 020 FR-006;
    - rename the step to say it checks the minimums and the identity too;
  - in the step *Sign the whole application*, change its name: no trust step;
  - update the file's header comment (Notes): macOS 15 machines; minimum macOS 15; the certificate is not trusted on the machine; the identity is checked.
  
  Check the file parses: `python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))" .github/workflows/release.yml` (or `ruby -ryaml`).
- [X] T015 [US1] Gate: the YAML parse; `git diff .github/workflows/release.yml` reviewed against contracts/scripts.md ("Workflows"); `TEST` green. Tick T014–T015 and commit the phase.

---

## Phase 6: User Story 4 — the Ubuntu jobs pinned (Priority: P2)

**Goal**: FR-014, FR-009; SC-007.

- [ ] T016 [P] [US4] In `.github/workflows/release.yml`, set `runs-on: ubuntu-24.04` on the jobs `validate`, `create-release`, `build-windows` (keep its comment `# cross-compiled with Mingw-w64`), `build-linux` and `release`.
- [ ] T017 [P] [US4] In `.github/workflows/ubuntu-maven.yml`:
  - set `runs-on: ubuntu-24.04`;
  - rename the job `build-ubuntu-latest` to `build-ubuntu`, and any name in the file derived from it.
- [ ] T018 [US4] Gate:
  - `grep -rn "ubuntu-latest\|macos-14" .github/` prints only comments, or nothing;
  - YAML parse of both files.
  
  Tick T016–T018 and commit the phase.

---

## Phase 7: User Story 5 — the manual checks on macOS 15, and the texts of US1 and US3 (Priority: P3)

**Goal**: FR-001 (the two manual workflows), FR-010, FR-011 (notes).

- [ ] T019 [P] [US5] In `.github/workflows/macos-maven.yml`, set `runs-on: macos-15   # Apple Silicon` and rename the artifact `Package-MacOS-macos-14` to `Package-MacOS-macos-15`.
- [ ] T020 [P] [US5] In `.github/workflows/practice-gui-tests.yml`, set `runs-on: macos-15`.
- [ ] T021 [P] [US3] In `README.md` and `docs/README.md` (~lines 53 and 137), `docs/MANUAL.md` and `docs/MANUAL-FR.md` (~lines 50 and 429), state the requirement as "macOS 15 Sequoia or later (Apple Silicon)" (FR: "macOS 15 Sequoia ou plus récent (Apple Silicon)"). Change only those words; keep everything else on those lines. `INSTALL.md` (upstream's build guide) is not changed (FR-010).
- [ ] T022 [P] [US1] In `docs/release-notes/v0.8.0-beta.3.md`:
  - under *About this release*, say that from this version the macOS package needs **macOS 15 Sequoia or later** and that macOS 14 is no longer supported; that the packages are now built on macOS 15 machines (macOS 14 machines retired) and the Linux and Windows ones on Ubuntu 24.04, as before;
  - under *What was checked*, say that the refusal on a Mac older than 15 is checked through the declarations only (no such Mac at hand), and that the microphone permission kept across the update is for the author to check;
  - keep the rest.
- [ ] T023 Gate:
  - the full gate in `CB`, green with the same counts as T001;
  - `TEST`, `misc/test-stamp-macos-plist.sh` and `misc/test-relocate-macos-payload.sh` green;
  - `grep -rn "macos-14\|ubuntu-latest" .github/` empty except comments;
  - YAML parse of the four workflow files.
  
  Tick T019–T023, update `specs/STATUS.md` (020: implemented locally, waiting for the release run), and commit the phase.

---

## Phase 8: On GitHub (FR-011, FR-013; SC-001, SC-005, SC-006, SC-007)

- [ ] T024 One push of the branch and `master` together: `git push origin feature/interactive-practice feature/interactive-practice:master`. No fetch before or after.
- [ ] T025 Start the release for the same tag, which replaces its draft: `gh workflow run release.yml -R krl91/tuxguitaradvanced --ref master -f tag=v0.8.0-beta.3`. Follow it with `gh run watch … --exit-status`. Expected (quickstart step 5):
  - every job green;
  - the macOS job's log shows a macOS 15 image, the designated requirement with `H"280bd902de0512b59d70228c5faa441d3eb6af55"`, and the bundle check's OK lines including the identity;
  - every Ubuntu job shows `Image: ubuntu-24.04`;
  - the release is published with 4 files.
  
  On a failure, follow the bug fix protocol: read the log, reproduce locally if possible, fix, at most 3 tries, each try a new push and a run for the same tag, never a moved tag. Then report.
- [ ] T026 [P] Start *MacOS Java CI with Maven*, *Practice GUI tests* and the Ubuntu build-and-test workflow (`ubuntu-maven.yml`) once each (`gh workflow run … -R krl91/tuxguitaradvanced --ref master`) and follow them; expected green, the first two on macOS 15 (SC-005), the third on Ubuntu 24.04 (FR-014, SC-007). If a graphical test fails:
  - note its name and its message;
  - run it once locally (`mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am -Dtest=<name> -Dsurefire.failIfNoSpecifiedTests=false`);
  - say whether it depends on painting time (for example a quality tier stepping down) or on macOS 15.
  
  Only a failure caused by macOS 15 is fixed within spec 020, following the bug fix protocol. Any other is reported to the owner. A test is never changed or skipped (never modify existing tests).
- [ ] T027 Record the run ids, images and results in `specs/020-macos15-ci/baseline.txt`, set 020 in `specs/STATUS.md`, update the memories `pending-macos15-ci` (done) and `pending-score-fire-spec` (v0.8.0-beta.3 published). Set `.specify/feature.json` back to `specs/019-score-demo-fire`, so that the next `/speckit-implement` resumes spec 019 (phases 4 to 6), and say so in the report. Commit; the push waits for the next round.

---

## Phase 9: Owner's check [HUMAN]

- [ ] T028 [HUMAN] Over v0.8.0-beta.2, with the microphone permission granted, install v0.8.0-beta.3. Check that:
  - *Open Anyway* is offered at the first launch;
  - no microphone permission request appears;
  - the tuner hears the input (SC-002, quickstart step 7).

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2** before any story.
- **Phase 3 (US3)** comes before Phase 5, because the release's bundle check (T014) relies on checks 3–5 and on the 15.0 build.
- **Phase 4 (US2)** comes before Phase 5, because T014 passes `--signed-by`, which needs check 6 and the new sign script.
- **Phases 6 and 7** can come in any order after Phase 5. T016 edits `release.yml` like T014, so it runs after T014.
- **Phase 8** needs Phases 3–7 committed. **T028** needs T025 published.
- **Same-file sequencing**:
  - `TEST`: T002 → T003 → T009 → T011;
  - `VERIFY`: T004 → T010;
  - `release.yml`: T014 → T016.

## Parallel Opportunities

- T006 and T007 (two POMs), after T005.
- T011 beside T010 (different files); the switch of the T009 case waits for both.
- T016 and T017; T019, T020, T021 and T022.

## Implementation Strategy

Everything that can be proved without GitHub is proved locally first (Phases 1–7: the minimums of the real build, the checks refusing wrong packages, the real sign script with a throwaway certificate and no trust step). Then a single push, and the v0.8.0-beta.3 release is both the first run on macOS 15 machines and the delivery the owner is waiting to test (FR-013). It must happen before 2026-11-02.
