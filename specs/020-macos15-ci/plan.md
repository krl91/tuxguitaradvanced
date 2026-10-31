# Implementation Plan: macOS package on macOS 15 machines, minimum macOS 15, the same stable signature, Ubuntu pinned

**Branch**: `feature/interactive-practice` (no branch of its own) | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/020-macos15-ci/spec.md`

## Summary

GitHub retires its macOS 14 machines on 2026-11-02, and the release chain starts with the macOS package. The macOS jobs move to `macos-15` and the Ubuntu jobs are pinned to `ubuntu-24.04` (research R6). Two things break on macOS 15 and are fixed:
- **The minimum macOS**: one property, `macos.minimumVersion` = `15.0`, in `desktop/pom.xml`. It reaches:
  - the launcher's clang call (`-mmacosx-version-min`);
  - the AudioUnit library's `make` (`MACOSX_DEPLOYMENT_TARGET`, so the upstream Makefile is not touched);
  - the Info.plist (`LSMinimumSystemVersion`).

  A local build then gives 15.0 too (R1).
- **The signing**: the stable certificate signs without being trusted on the build machine, by its fingerprint. The designated requirement is forced to the exact form of the previous releases (R3).

The release checks the package (R2, R4):
- exactly 15.0 for our own parts;
- at most 15.0 for the embedded JRE;
- the signature's identity when the secrets exist.

A new self-test shows each check refusing a wrong package (R5). Everything is checked locally first. Then one push, and the v0.8.0-beta.3 release is the first run on the new machines (FR-013, R8).

## Technical Context

- **Language/Version**:
  - Bash scripts (`misc/`, `.github/scripts/`);
  - GitHub Actions YAML;
  - Maven POM XML;
  - C (the launcher, unchanged) and C++ (the AudioUnit library, unchanged), compiled by Apple clang.
- **Primary Dependencies**: Apple command-line tools (`clang`, `codesign`, `security`, `otool`, `lipo`, `PlistBuddy`), `openssl`, `gh` (unchanged), GitHub-hosted machines `macos-15` and `ubuntu-24.04`. No new third-party dependency.
- **Storage**: N/A.
- **Testing**:
  - the new `misc/test-verify-macos-bundle.sh`;
  - the existing self-tests;
  - the full macOS gate (`mvn -o -e clean verify -P native-modules`);
  - the local signing run with a throwaway certificate;
  - then the release run.
- **Target Platform**:
  - the macOS package for Apple Silicon, macOS 15 Sequoia or later;
  - the Windows and Linux packages unchanged (built on Ubuntu 24.04, as before).
- **Project Type**: desktop application, build and release tooling.
- **Performance Goals**: N/A (the release's duration is unchanged).
- **Constraints**:
  - done and pushed before 2026-11-02;
  - macOS minutes count ten times;
  - each push asks the owner's Keychain password, so there is one push for the change;
  - no published tag moves;
  - nothing is trusted on the owner's Mac by the local tests.
- **Scale/Scope**:
  - 4 workflow files;
  - 3 macOS scripts changed and 1 new self-test;
  - 2 build POMs and the desktop parent POM;
  - 4 docs and 1 release-notes file.

No NEEDS CLARIFICATION is left.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status |
|---|---|
| I. Preserve TuxGuitar compatibility | **Pass.** The upstream Makefile is not touched; the minimum goes in through the environment of the `make` call (R1). Linux and Windows builds are unchanged. |
| II. Extend before modifying core | **Pass.** No core Java module changes; build configuration only. `desktop/pom.xml` gets one property: the parent of both build POMs is the one place both can read. |
| III. Evidence-based architecture | **Pass.** Every decision cites a file and line, a local test or a run log (research R1–R8). |
| IV. Spec-driven | **Pass.** Specified, clarified (2 answers) and now planned. |
| V. Incremental delivery | **Pass.** The phases below can each be checked on their own, locally, before the single push. |
| IX. Cross-platform, macOS-first | **Pass.** The macOS package's minimum is the owner's decision (macOS 15). Windows and Linux are kept identical and still built in each release. Validation per phase is stated below. Windows and Linux packages are still never run by a person, and that is said. |
| X. No unrelated refactoring | **Pass.** Only the names that carry the old labels change (R6). |
| XI. Build and test integrity | **Pass.** No test is disabled. New logic (the checks) comes with a self-test that shows each refusal. The full gate runs before the push. |
| XII. Truthful reporting | **Pass.** Two risks are stated as unproved until they run: the untrusted signing on a macOS 15 machine (R3) and the microphone permission kept (owner, SC-002). The refusal on a Mac older than 15 is checked through the declarations only. |
| Licences | **Pass.** No new dependency. |

Re-check after Phase 1 design: unchanged, Pass.

## Project Structure

### Documentation (this feature)

```text
specs/020-macos15-ci/
├── spec.md
├── plan.md              # this file
├── research.md          # R1–R8
├── data-model.md        # the values written and checked
├── quickstart.md        # local, GitHub and owner checks
├── contracts/scripts.md # the scripts' options, checks and the workflows' labels
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
desktop/pom.xml                                                    # property macos.minimumVersion = 15.0
desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml           # launcher -mmacosx-version-min; Info.plist LSMinimumSystemVersion
desktop/build-scripts/native-modules/tuxguitar-audiounit-macos/pom.xml  # MACOSX_DEPLOYMENT_TARGET on make
misc/verify-macos-bundle.sh                                        # checks 3–6
misc/test-verify-macos-bundle.sh                                   # new self-test
misc/seal-macos-app.sh                                             # optional 4th argument: the requirement
.github/scripts/sign-macos-launcher.sh                             # no trust step; identity by SHA-1; requirement
.github/workflows/release.yml                                      # macos-15, ubuntu-24.04, self-test, --signed-by
.github/workflows/macos-maven.yml                                  # macos-15, artifact name
.github/workflows/practice-gui-tests.yml                           # macos-15
.github/workflows/ubuntu-maven.yml                                 # ubuntu-24.04, job id
README.md, docs/README.md, docs/MANUAL.md, docs/MANUAL-FR.md       # macOS 15 Sequoia or later
docs/release-notes/v0.8.0-beta.3.md                                # the new minimum, macOS 14 no longer supported
```

**Structure Decision**: the existing build-script and `misc/` layout. The minimum lives in the parent POM, the checks in the existing bundle verifier, and the self-test next to the others.

## Phases (for /speckit-tasks)

1. **Baseline (local)**: record the minimums of today's build (27.0), and run the current verifier and self-tests on it.
2. **US3, minimum macOS 15** (local, macOS):
   - first the self-test cases of checks 3–5, seen red against today's verifier;
   - then the verifier's checks 3–5;
   - then the property and the three uses;
   - the full gate; the quickstart's step 1 shows 15.0 everywhere.
3. **US1 + US2, the signature** (local, macOS):
   - first the self-test cases of check 6, seen red;
   - then check 6, `seal-macos-app.sh`'s 4th argument and the sign script;
   - quickstart step 3 with a throwaway certificate, so nothing is trusted on the Mac.
4. **US4 + US5 and the texts**:
   - the workflows' labels and names, the self-test step and `--signed-by` in the release;
   - the docs and the v0.8.0-beta.3 notes;
   - the full gate.
5. **On GitHub**:
   - one push;
   - the v0.8.0-beta.3 release (FR-011, FR-013), then the two manual workflows once each;
   - the gate is quickstart steps 5 and 6.
6. **[HUMAN]**: the owner installs v0.8.0-beta.3 over v0.8.0-beta.2 and checks *Open Anyway* and the microphone (SC-002).

Validated platforms: phases 1–4 on the owner's Mac (macOS 27, Apple Silicon); phase 5 on GitHub's macOS 15 and Ubuntu 24.04 machines. The Windows and Linux packages are built and checked by the release, never run.

## Complexity Tracking

None.
