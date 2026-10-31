# Feature Specification: Build and release the macOS package on macOS 15 machines, with macOS 15 as the stated minimum and the same stable signature

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/020-macos15-ci`)

**Created**: 2026-10-31

**Status**: Draft

**Input**: User description: "spec 020 : migrer la CI macOS de macos-14 vers macos-15 avant la retraite de macos-14 (2 novembre 2026), pour que les releases TuxGuitarAdvanced continuent de sortir sur les trois OS." Owner's decisions of 2026-10-31: the application's minimum macOS is **macOS 15 Sequoia** (the native launcher, the AudioUnit library and the application's description declare it; today nothing sets it and the application inherits the macOS of the machine that built it); the signature keeps the **same stable self-signed certificate** (not an ad hoc signature), so that macOS keeps the microphone permission from one version to the next; the full Spec Kit method is followed. The three workflows that use macOS 14 machines are the release, the macOS build-and-test check and the practice graphical tests. The release run of v0.8.0-beta.3 failed during a scheduled macOS 14 brownout and waits to be run again.

### Context (facts checked on 2026-10-31)

- GitHub stops providing its macOS 14 build machines on **2026-11-02**; before that date it switches them off in scheduled windows (the v0.8.0-beta.2 run met one on 2026-10-30, the v0.8.0-beta.3 run on 2026-10-31, which left a draft release). Without this change, no macOS package, and so no release (the chain stops at macOS), can be produced after 2026-11-02.
- A first try on macOS 15 machines (before this spec) showed two problems:
  1. **No minimum macOS is declared**: the native parts take the macOS version of the build machine as their minimum (macOS 27 for a build on the owner's Mac, checked; so the current releases, built on macOS 14 machines, already silently require macOS 14). A build on macOS 15 machines would require macOS 15 without saying it.
  2. **The signing step fails on macOS 15 machines**: making the machine trust the stable certificate is refused by the system (error -60005).
- Checked locally with a throwaway certificate: a signature made with a certificate the machine does **not** trust, chosen by its fingerprint, is valid and passes a strict verification; and the signature's "designated requirement", the rule by which macOS recognises the same application from one version to the next (and keeps its microphone permission), can be set to exactly the form the current releases have: `identifier "app.tuxguitaradvanced" and certificate root = H"280bd902de0512b59d70228c5faa441d3eb6af55"` (read from the v0.8.0-beta.2 release log).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The owner publishes a release after macOS 14 machines are gone (Priority: P1)

The owner starts the release by hand with a version tag, as today. The macOS package is built, signed and checked on a macOS 15 machine; then Windows and Linux are built; the release is published with its four files, without any manual step on the build machines.

**Why this priority**: without it, no release at all can be published after 2026-11-02 (the macOS package comes first in the chain), and the three-OS rule of every release cannot be met.

**Independent Test**: run the release workflow for a new pre-release tag; it ends published, with the macOS, Windows, Linux archive and Linux installer files attached, and every existing check of the packages passes.

**Acceptance Scenarios**:

1. **Given** the release workflow and the signing secrets, **When** the owner runs it for a new tag, **Then** the macOS package is built on a macOS 15 machine, signed with the stable certificate, passes every package check, and the release is published with its four files.
2. **Given** the signing secrets are absent (a fork), **When** the release workflow runs, **Then** the macOS package is sealed with an ad hoc signature as today, and the run does not fail for that reason.
3. **Given** the draft left by the failed v0.8.0-beta.3 run, **When** the owner runs the workflow again for the same tag after this change, **Then** the draft is replaced and the release is published (the tag was never published, so nothing is moved).

---

### User Story 2 - A macOS user updates without losing the microphone permission (Priority: P1)

A musician who already uses TuxGuitarAdvanced on macOS 15 or later replaces the application with the new version. macOS recognises it as the same application: the microphone keeps working without a new permission request, and the first launch of the downloaded package still offers *Open Anyway* (it is not called "damaged").

**Why this priority**: the practice modes depend on the microphone; losing the permission at each update was the reason a stable certificate was chosen (spec of the signing, earlier releases). Changing the signing step must not break it.

**Independent Test**: on the owner's Mac, with v0.8.0-beta.2 installed and its microphone permission granted, install the first release made by this change and start a tuner or practice session: no permission request appears and the input is heard.

**Acceptance Scenarios**:

1. **Given** a release signed before this change and its microphone permission granted, **When** the user installs a release signed after it, **Then** macOS does not ask again for the microphone permission.
2. **Given** a downloaded macOS package of the new release, **When** the user opens it the first time, **Then** macOS offers *Open Anyway* (System Settings, Privacy & Security), as for the previous releases, and never reports the application as damaged.
3. **Given** a release package, **When** its signature is checked, **Then** its designated requirement is exactly the one of the previous releases (same identifier, same certificate fingerprint).

---

### User Story 3 - A user on an older macOS is told clearly that the application needs macOS 15 (Priority: P2)

A musician on macOS 14 or older downloads the new release. Before downloading, the release notes and the installation guide say that macOS 15 Sequoia or later is required. If they open it anyway, macOS itself refuses with its usual message about the required macOS version, instead of a crash or a silent failure.

**Why this priority**: the minimum changes for nobody in practice (the current releases already need macOS 14, and macOS 14 users lose only this and later versions), but saying it clearly avoids confused reports.

**Independent Test**: read the application's description and every native part of the published package: each states macOS 15 as its minimum; read the release notes and the installation guide: both state it.

**Acceptance Scenarios**:

1. **Given** the published macOS package, **When** its application description and each of its native parts are read, **Then** every one declares macOS 15.0 as its minimum.
2. **Given** a build of the macOS package on any newer macOS (such as the owner's Mac), **When** its native parts are read, **Then** they also declare macOS 15.0, not the build machine's version.
3. **Given** the release notes and the installation guide of the first release after this change, **When** a user reads them, **Then** they state that macOS 15 Sequoia or later is required and that macOS 14 is no longer supported.

---

### User Story 4 - The owner's manual checks run on the new machines (Priority: P3)

The owner can still start, by hand, the macOS build-and-test check and the practice graphical tests, and both run on macOS 15 machines.

**Why this priority**: they are not needed to publish a release, but they stop working on 2026-11-02 too.

**Independent Test**: start each of the two workflows once by hand; each runs on a macOS 15 machine and ends green.

**Acceptance Scenarios**:

1. **Given** the macOS build-and-test workflow, **When** the owner starts it, **Then** it runs on a macOS 15 machine, builds the application and runs its unit tests, green.
2. **Given** the practice graphical tests workflow, **When** the owner starts it, **Then** it runs on a macOS 15 machine and its tests pass.

---

### Edge Cases

- **A native part without a declared minimum** (a new native part added later): the release checks refuse the package rather than publish one whose minimum depends on the build machine.
- **The stable certificate is missing from the secrets**: ad hoc signature, as today (forks); the designated-requirement check applies only when the certificate is provided.
- **The certificate's fingerprint does not match the one of the previous releases** (a certificate replaced by mistake): the release checks refuse the package, because users would lose the microphone permission.
- **GitHub renames or retires the macOS 15 machines in turn**: the workflows name the macOS version explicitly (not "the latest macOS"), so a change of build machine is always a deliberate, visible decision; a warning from GitHub is the signal for the next migration.
- **A brownout window before the change is pushed**: not handled; the change is meant to be in place before 2026-11-02.
- **Intel Macs**: out of scope, unchanged (the macOS package is for Apple Silicon only; the AudioUnit library keeps its two architectures, both declaring macOS 15).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The release, the macOS build-and-test check and the practice graphical tests MUST run on macOS 15 machines, named by their explicit version.
- **FR-002**: The application's description in the macOS package MUST declare macOS 15.0 as its minimum system version, so that macOS refuses to open it on an older system with its own message.
- **FR-003**: Every native part of the macOS package (the launcher and the AudioUnit library, in each of its architectures) MUST declare macOS 15.0 as its minimum, whatever the macOS version of the machine that builds it, in the release and in a local build.
- **FR-004**: The release MUST refuse to publish a macOS package whose application description or any native part declares a minimum other than macOS 15.0, or none.
- **FR-005**: When the stable certificate is provided, the macOS package MUST be signed with it without requiring the build machine to trust it.
- **FR-006**: When the stable certificate is provided, the signature's designated requirement MUST be exactly `identifier "app.tuxguitaradvanced" and certificate root = H"<fingerprint of the stable certificate>"`, the form of the previous releases, and the release MUST refuse a package whose requirement differs or whose fingerprint is not `280bd902de0512b59d70228c5faa441d3eb6af55`.
- **FR-007**: When the stable certificate is not provided, the macOS package MUST be sealed with an ad hoc signature, as today.
- **FR-008**: The signature of the whole application MUST stay valid under a strict verification, so that a downloaded package is offered *Open Anyway* and never reported as damaged.
- **FR-009**: The Windows and Linux packages MUST be unchanged in content and behaviour, and their existing checks MUST still pass.
- **FR-010**: The release notes of the first release after this change, the installation guide and the README MUST state that macOS 15 Sequoia or later is required (Apple Silicon), and the notes MUST say that macOS 14 is no longer supported.
- **FR-011**: The pending v0.8.0-beta.3 release MUST be produced by running the changed release workflow again for the same tag, replacing its draft; its notes MUST include the new minimum. No published tag is moved.
- **FR-012**: The existing self-tests of the macOS packaging scripts MUST keep passing, and the new checks of FR-004 and FR-006 MUST each have a self-test that shows them refusing a wrong package.

### Key Entities

- **Stable signing certificate**: the self-signed code-signing certificate kept in the repository's secrets; identified by its fingerprint `280bd902…af55`; it defines the identity under which macOS keeps the microphone permission.
- **Designated requirement**: the rule written into the signature by which macOS recognises later versions as the same application.
- **Minimum system version**: the oldest macOS on which the application and each of its native parts accept to run.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Before 2026-11-02, one release run ends published with its 4 files, with no manual step on a build machine.
- **SC-002**: After installing the first release made by this change over v0.8.0-beta.2 on the owner's Mac, 0 microphone permission requests appear and the input is heard in the tuner.
- **SC-003**: In every published macOS package from then on, 100 % of the native parts and the application description declare macOS 15.0, checked automatically by the release.
- **SC-004**: The automatic checks refuse 100 % of the wrong packages of their self-tests (missing or different minimum, different designated requirement or fingerprint).
- **SC-005**: The macOS build-and-test check and the practice graphical tests each end green once on macOS 15 machines.
- **SC-006**: The Windows and Linux packages of the first release after this change pass all their existing checks, as before.

## Assumptions

- GitHub provides Apple Silicon macOS 15 machines under an explicit version label, with the same tools as the macOS 14 machines for this build (Java 21 setup, the compiler, the signing tools).
- The stable certificate in the secrets is the one whose fingerprint is `280bd902…af55` (read from the v0.8.0-beta.2 log); it is not replaced by this change.
- macOS keeps the microphone permission when the designated requirement and the certificate are the same; this is checked by the owner on the first release (SC-002), since it cannot be proved on a build machine.
- No Mac older than macOS 15 is at hand: the refusal on an older system (User Story 3) is checked through the declarations, not on a real older Mac; this is stated as unverified in the release notes' checks.
- The embedded Java runtime (Temurin 21) and the graphical toolkit already run on macOS 15; they set no minimum above it.
- macOS minutes cost ten times more on a private repository; each try on the build machines costs a push (and the owner's Keychain password), so trial runs are kept to the minimum.
- Linux and Windows users are not affected; the three-OS rule (every feature in all three packages) is unchanged.
