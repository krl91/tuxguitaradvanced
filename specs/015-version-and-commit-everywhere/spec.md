# Feature Specification: The same version and commit everywhere

**Feature Branch**: `feature/interactive-practice` (the spec directory is `015-version-and-commit-everywhere`)

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "The version number must be the same everywhere and be the right one, with the sha of the commit: the release version (for example 0.6.0-beta.1) and the sha1 of the commit must appear and be identical in the About menu, in the title or the name of the application, in the metadata of every package (macOS Info.plist, Windows, Linux tar.gz and deb), in the names of the files and in the GitHub release, for all versions, alpha, beta or final, on the three systems. Today About shows the hard-coded version of upstream TuxGuitar (2.1.1), Info.plist and the package carry 9.99-SNAPSHOT, and no sha1 appears anywhere." (owner, 2026-10-22)

## Why this exists

A person who installs a release cannot tell, from the application, **which version and which code** they have: the About window shows the version of the upstream project (2.1.1, written in the source), the macOS bundle and the Linux package say `9.99-SNAPSHOT` (a placeholder), and no commit appears anywhere. When someone reports a problem, the owner cannot find the exact code from the report. The name of a release (`v0.6.0-beta.1`) exists only on GitHub and in the names of the files. This feature makes the **release version and the commit** a single fact, **shown the same way in every place** where a person or a tool can look, for every kind of release (alpha, beta, final) on the three systems, and **checked automatically** before a release is published.

What the code does today (inspected 2026-10-22): `TGAboutDialog` builds its title from `TGApplication.NAME` and `TGVersion.CURRENT` (written in the source as 2.1.1); `TGVersion.CURRENT` is also **written in the header of every `.tg` file the application saves** (the file-format stamp, `TGSongWriterImpl`), so it cannot simply be replaced; the release workflow sets `TG_VERSION: '9.99-SNAPSHOT'` for every build and uses the tag only for the names of the files and the release; the macOS `Info.plist` shows `CFBundleShortVersionString` = `9.99-SNAPSHOT`; the Linux package states `9.99-SNAPSHOT` as its version; nothing reads the commit.

## Clarifications

### Session 2026-10-22

- Q: Which commit identifier is shown, and where? → A: Defaults taken by the author, to be confirmed by the owner: the **short form (7 characters)** next to the version where space is small (the title of About, the name of a file) and the **full 40 characters** in the details (a line of About, the release page, the package metadata where it fits).
- Q: What does a build that is not a release show? → A: A **development label** with the commit when it is known, for example `dev (a1b2c3d)`, and **modified** when the working copy has uncommitted changes; never a release number and never `9.99-SNAPSHOT`.
- Q: Should the title of the main window also show the version? → A: **Yes**, so that a screenshot in a report shows it (chosen by the owner, 2026-10-22, among: About and the packages only, the title when no file is open, always in the title). The title is a layout that every user can change (default `${appname} - ${filename}`, with a variable `${appversion}` that gives the application version, today the hard-coded 2.1.1): the **default layout adds the version after the name**, `TuxGuitarAdvanced 0.6.0-beta.1 - song.tg`, and a layout that a user has set himself is **kept as he wrote it**.
- Q: Should About offer a button that copies the version and the commit for a bug report? → A: **Yes** (chosen by the owner, 2026-10-22): a "Copy the version" button copies one line, `TuxGuitarAdvanced <version> (<full commit>)`, with the full commit of 40 characters and, for a development build, its label and **modified** mark.
- Q: Which labels may a version tag carry? → A: **Any label** (chosen by the owner, 2026-10-22, among: `alpha`/`beta`/`rc` only; `alpha`/`beta` only; any). The label is free text made of letters, digits, dots and hyphens (`v0.6.0-test`, `v0.6.0-beta.2`); a tag **with** a label is published as a pre-release, a tag **without** one as a final release.
- Q: Should the title of the main window also carry the short commit? → A: **No**, the version only (`TuxGuitarAdvanced 0.6.0-beta.2 - song.tg`); the short commit is in About and the full commit in the Copy button (chosen by the owner, 2026-10-22). A development build's title shows its `dev` label in place of the version.
- Q: Which number goes in a numeric-only version field for a labelled version such as `0.6.0-beta.2`? → A: **`0.6.0.0`**, the numeric part of the version with a last number of 0, for every labelled or unlabelled release (chosen by the owner, 2026-10-22, against reporting the label's number, which a free label such as `test` does not have). The full text version is carried by a text field of the same package.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A person can read, in the application, which version and which commit they run (Priority: P1)

Whoever opens the **About** window of a release sees the **release version** (`0.6.0-beta.1`) and the **commit** it was built from, written the same way as on the release page, and can copy them into a report.

**Why this priority**: it is the whole request; without it a report cannot be tied to a code.

**Independent Test**: build a release, open About, compare with the release page.

**Acceptance Scenarios**:

1. **Given** a release `0.6.0-beta.1` built from commit `fc50a2f…`, **When** About is opened, **Then** its title shows `TuxGuitarAdvanced 0.6.0-beta.1` and a line shows the commit (short, and full in the details).
2. **Given** the same release on macOS, Windows and Linux, **When** About is opened on each, **Then** the version and the commit are identical.
3. **Given** an alpha, a beta and a final release, **When** each is built, **Then** each shows its own full version, with its label (`-alpha.3`, `-beta.1`, none for a final).
4. **Given** the default title of the main window, **When** a file is open, **Then** it reads `TuxGuitarAdvanced <version> - <file name>`; **Given** a user who set his own title layout, **Then** his layout is unchanged.
5. **Given** a `.tg` file saved by the application, **When** its header is read, **Then** it carries the **same file-format stamp as before this feature** (nothing about the saved files changes).

---

### User Story 2 - Every package says the same version and commit (Priority: P1)

The metadata of each package (the macOS application bundle, the Windows executable and folder, the Linux archive and the Debian package), the **names of the files**, and the **GitHub release** (its tag, its title, its page) all carry the **same version** and the **same commit**.

**Why this priority**: the owner asked for "the same everywhere", on every system.

**Independent Test**: build a release; read the version and the commit from each package and from the release page, and compare.

**Acceptance Scenarios**:

1. **Given** a release, **When** the metadata of the four packages are read, **Then** each states the release version (in the notation its system accepts, see Assumptions) and the full commit.
2. **Given** a release, **When** the names of the four files and the title of the release are read, **Then** they carry the same version.
3. **Given** a release page, **When** it is read, **Then** it states the commit the packages were built from, and the tag points to that same commit.

---

### User Story 3 - A release that does not match is not published (Priority: P2)

Before a release becomes public, an **automatic check** reads the version and the commit from each package and from the release, and **refuses to publish** if one differs from the version asked for or from the commit built.

**Why this priority**: "the right one" needs a guard, as the missing library of `0.6.0-alpha.1` showed for packaging.

**Independent Test**: build a release with a deliberately wrong value in one place; the release is not published and the check says where.

**Acceptance Scenarios**:

1. **Given** a build whose package has another version than the release asked for, **When** the check runs, **Then** the release stays unpublished and the message names the package, the version found and the version expected.
2. **Given** a build whose commit differs from the commit of the tag, **When** the check runs, **Then** the release stays unpublished.
3. **Given** a correct build, **When** the check runs, **Then** it passes and the release is published as before.

---

### User Story 4 - A development build says so honestly (Priority: P3)

A build made on a developer's machine, outside a release, shows a **development label** with the commit when it is known, and says **modified** when there are uncommitted changes; it never shows a release number or `9.99-SNAPSHOT`.

**Why this priority**: the owner's own builds are the ones that are tried first; they must not pass for a release.

**Independent Test**: build locally, open About; build with an uncommitted change and open it again.

**Acceptance Scenarios**:

1. **Given** a local build from a clean working copy, **When** About is opened, **Then** it shows `dev` and the commit.
2. **Given** a local build with uncommitted changes, **Then** it shows `dev` and the commit and **modified**.
3. **Given** a build made without git available, **Then** it shows `dev` and that the commit is unknown, without an error.

### Edge Cases

- **A version label with a hyphen or letters** (`-alpha.3`, `-beta.1`): some systems accept only digits and dots in a version field (the macOS bundle version, the Windows file version) and Debian orders pre-releases with another punctuation: the digits are the same everywhere, the label goes where the system allows it, and the full text is always readable in About and on the release page.
- **A tag that is not a version** (for example a test tag): the workflow refuses it with a clear message.
- **The commit is not the tip of the branch** when the release is run (a later commit was pushed): the commit shown is the one that was built, and the check compares with that one.
- **Two releases of the same version** are refused (a published tag is never moved).
- **The About window of an older release** is not changed: only releases built after this feature show the new fields.
- **The file-format stamp and the plugin interfaces** keep their own versions: the application version never changes how a song is read or written, or whether a plugin loads.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The **release version** MUST be given **once**, when a release is asked for (the tag, for example `v0.6.0-beta.1`), and every place of this specification MUST derive from it: nothing else MUST write a version by hand.
- **FR-002**: The **About window** MUST show the release version in its title (`TuxGuitarAdvanced <version>`) and the commit it was built from, short in the title or the first line and **full** in the details; the **title of the main window**, in its default layout, MUST show the version after the name of the application (`TuxGuitarAdvanced <version> - <file name>`), a layout that a user has changed being left as he wrote it.
- **FR-003**: The **macOS package** (the application bundle), the **Windows package** (the executable and its folder), the **Linux archive** and the **Debian package** MUST each state the **release version** and the **full commit** in their own metadata, in the notation their system accepts (FR-009).
- **FR-004**: The **names of the package files** and the **title and tag of the GitHub release** MUST carry the same version, and the release page MUST state the commit; the **tag MUST point to the commit the packages were built from**.
- **FR-005**: The version and the commit MUST be the same on the **three systems** and for **every kind of release** (alpha, beta, final): the label is part of the version.
- **FR-006**: An **automatic check**, run after the packages are built and **before the release is published**, MUST read the version and the commit from each package, the file names and the release, and MUST **refuse to publish** if any differs from the version asked for or the commit built, naming the place, the value found and the value expected.
- **FR-007**: A **build outside a release** MUST show a **development label** and the commit when it is known, and **modified** when the working copy has uncommitted changes; it MUST NOT show a release number or `9.99-SNAPSHOT`, and MUST NOT fail when the commit is unknown.
- **FR-008**: The **file-format stamp** written in the header of the `.tg` files, the versions of the plugin interfaces and every other version that has a compatibility meaning MUST be **unchanged**; the application version is a separate value.
- **FR-009**: Where a system accepts only a numeric version (the macOS bundle version, the Windows file version) the field MUST carry the **numeric part** of the release version completed with a last number of 0 (`0.6.0.0` for `0.6.0`, `0.6.0-beta.2` and `0.6.0-test` alike), and the **full text** MUST be carried by another field of the same package; for the Debian package the version MUST use Debian's own punctuation for pre-releases so that a final release sorts after its alpha and beta; in every case the digits and the label are the same as the release's.
- **FR-010**: The texts of the new fields MUST exist in the three shipped languages (English, French, Spanish) and be described in the documentation.
- **FR-012**: The **application version variable** that the title layout and any other text already use (`${appversion}`) MUST give the **release version** (or the development label), never the hard-coded upstream number, so that everything that shows "the application version" shows the right one.
- **FR-013**: The **About window** MUST offer a button that copies to the clipboard one line stating the application name, the version and the **full** commit (with the development label and the **modified** mark when they apply), so that it can be pasted into a bug report; its text exists in the three shipped languages (FR-010).
- **FR-011**: A tag that is not a version (not of the form `vMAJOR.MINOR.PATCH` with an optional label of letters, digits, dots and hyphens, whatever the label) MUST be refused before anything is built; a tag with a label is published as a pre-release and a tag without one as a final release.

### Key Entities

- **Release version**: the text given when a release is asked for, for example `0.6.0-beta.1`: a numeric part (`0.6.0`) and an optional label (`beta.1`).
- **Commit**: the identifier of the source the build was made from, in short (7 characters) and full (40) forms, with a "modified" mark for an unclean working copy.
- **Build identity**: the pair {release version or development label, commit} that every place of the specification shows.
- **Place**: one of the surfaces that must show the build identity: the About window, the macOS bundle, the Windows executable and folder, the Linux archive, the Debian package, the file names, the GitHub tag, title and page.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every release, the version read from **each of the 9 places** (About window, macOS bundle, Windows package, Linux archive, Debian package, file names, release tag, title and page) is **the version asked for**, in 100 % of the releases built after this feature; the check of FR-006 reports **0 differences**.
- **SC-002**: The commit read from each place that carries it is **the commit built** (and the tag's), in 100 % of the releases.
- **SC-009**: The line copied by the About button holds the same version and the same full commit as the window shows, in 100 % of the builds tried.
- **SC-008**: A screenshot of the main window with the **default title** shows the release version in 100 % of the releases.
- **SC-003**: A person can quote the version and the commit of the application they run **from About alone**, without any other tool, in under **10 seconds**.
- **SC-004**: A release built with **one** wrong value in **any** place is **not published** in 100 % of the cases tried (one case per place).
- **SC-005**: A `.tg` file saved before and after this feature has the **same header stamp** (0 differences), and every plugin that loaded before still loads.
- **SC-006**: The texts exist in **3 of 3 languages**.
- **SC-007**: A local build never shows a release number or `9.99-SNAPSHOT` (0 cases).

## Assumptions

- The **short commit has 7 characters** and the full one 40; the owner may ask for another length.
- The **development label** is `dev` followed by the commit, and "modified" when the working copy is unclean.
- The macOS bundle's numeric version fields carry `MAJOR.MINOR.PATCH`; the Windows file version carries `MAJOR.MINOR.PATCH.0`; the full text goes in a field of the same package (a custom key of the bundle, the product-version string of the executable); the Debian version uses a tilde before the label (so `0.6.0~beta.1` sorts before `0.6.0`). These are decided at the plan from what each system accepts.
- The name of the application, **TuxGuitarAdvanced**, does not change; the version is added where a version belongs. The title or name of the application in the system's menus keeps the name; the version appears in About and in the package metadata.
- **Releases already published are not changed** (their tags are immutable); only releases built after this feature carry the new identity. The release notes of an older release may mention it.
- The **owner's trial** is the final check: About on his Mac for a real release.
- Out of scope: an update-checking feature, a changelog window, the version of the plugins or of the file format, the Android application, the installers that are not part of the release workflow.
