# Version and commit of a TuxGuitarAdvanced build

Every build says which release it is and which commit it was built from, in the same way everywhere (spec 015).

## Where to read it

| Where | What it shows |
|---|---|
| **About** window | the title `TuxGuitarAdvanced 0.6.0-beta.1`, the short commit (7 characters) under it, the full commit (40) at the top of the description tab; the **Copy the version** button puts `TuxGuitarAdvanced 0.6.0-beta.1 (<full commit>)` on the clipboard, ready to paste into a bug report |
| Title of the main window | `TuxGuitarAdvanced 0.6.0-beta.1 - song.tg` (default layout; a layout you wrote yourself in the settings is kept as you wrote it) |
| `tuxguitar.sh --version` | `TuxGuitarAdvanced 0.6.0-beta.1 (fc50a2f)` |
| The variable `${appversion}` of the title layout | `0.6.0-beta.1` |
| macOS `Info.plist` | `CFBundleShortVersionString` `0.6.0`, `CFBundleVersion` `0.6.0.0` (macOS accepts numbers only there), `CFBundleGetInfoString` `TuxGuitarAdvanced 0.6.0-beta.1 (fc50a2f)`, and the keys `TGBuildVersion` and `TGBuildCommit` (full text and full commit) |
| Windows `tuxguitar.exe` | file and product version `0.6.0.0`, text versions `0.6.0-beta.1` |
| Debian package | `Version: 0.6.0~beta.1` (the tilde makes a final release sort after its alpha and beta) and the full text and commit in the description |
| Every package | `share/build-info.properties` (`version`, `numeric`, `commit`, `modified`), the file the application reads |
| File names and the GitHub release | `TuxGuitarAdvanced-v0.6.0-beta.1-<system>…`, title `TuxGuitarAdvanced v0.6.0-beta.1`, the commit in the notes |

A **local build** (not a release) shows `dev`, for example `TuxGuitarAdvanced dev`, and gives its commit in About and in the copied line, with **modified** when the working copy had uncommitted changes. A build made without git says the commit is `unknown`. It never shows a release number, and never `9.99-SNAPSHOT`.

## How a release gets its version

The version is given **once**, as the tag asked of the Release workflow: `vMAJOR.MINOR.PATCH`, with an optional label of letters, digits, dots and hyphens (`v0.6.0`, `v0.6.0-beta.2`, `v0.6.0-test`). A tag with a label is published as a pre-release, a tag without one as a final release. `misc/release-identity.sh <tag> <commit>` derives from it every notation (numeric `X.Y.Z.0`, Debian `X.Y.Z~label`) and refuses a tag that is not a version.

`misc/stamp-build-info.sh` writes `build-info.properties` into a package; `misc/stamp-macos-plist.sh` writes the macOS `Info.plist` from it. `misc/verify-version.sh <package> <platform> <version> <commit>` reads every place of a package and fails if one differs; it is run on each package before it is attached to the release. Each script has a self-test (`misc/test-*.sh`).

## What is not changed

The version stamped in the header of the `.tg` files (`TGVersion`, 2.1.1) and the versions of the plugin interfaces are separate numbers: the application version never changes how a song is read or written, or whether a plugin loads.
