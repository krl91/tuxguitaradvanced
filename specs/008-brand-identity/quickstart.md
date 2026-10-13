# Quickstart: validating the logo integration (spec 008)

Automatic parts run in the normal build; the human and platform parts are marked and are never reported as done before they are.

## 1. Check the master (any time)

```sh
shasum -a 256 specs/008-brand-identity/assets/tuxguitaradvanced-logo-primary.png
# a9d369a2574396cd3208b0665648c57f398690499efb137d6781478991baf9e0
```

## 2. Make the derivatives and the contact sheets (increment B1, scripts in `misc/branding/`, created by the tasks)

```sh
misc/branding/make-derivatives.sh      # D1 to D4 from the master only, into a work folder outside the repository
misc/branding/make-contact-sheets.sh   # 16, 32, 64, 128, 256, 512, 1024 px on a light and a dark background
open /tmp/branding-review/             # the owner looks and decides (gate G1)
```

The owner's decision per derivative (approve, amend, reject) is written in `contracts/branding-manifest.md`.

## 3. Integrate the approved derivatives (increment B2)

```sh
misc/branding/build-platform-icons.sh  # icon.icns (iconutil), the .ico files (magick), the PNGs of the skins, the installer and Linux images
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"
mvn -e clean verify -P native-modules,swt-gui-tests          # same outcome as before the change (SC-006)
```

## 4. Automatic checks (increment B3)

```sh
mvn test -pl ../../TuxGuitar-practice -am -Dtest=BrandingAssetsTest -Dsurefire.failIfNoSpecifiedTests=false
misc/verify-branding.sh target/tuxguitar-9.99-SNAPSHOT-macosx-swt-cocoa.app macos
```

The `Release` workflow runs the verifier on the three packages before the draft release is published.

## 5. Manual checks (the owner; FR-015, SC-004)

1. **macOS (G2):** the packaged application shows the new icon in Finder, the Dock, the Cmd-Tab switcher and the window title bar, and the splash at start; the silhouette is clear at the small sizes and nothing is stretched, squashed or on an opaque square.
2. **Windows (if a machine is available):** the executable and the shortcut, the window, the installer and the uninstaller show the new icon and images.
3. **Linux (if a machine is available):** the menu entry and the window show the new icon.
4. Anything not looked at is written as **not checked** in the release notes.

## 6. Beta release (increment B4, done: `v0.5.0-beta.1` published on 2026-10-14; the steps are kept for the next release)

1. Confirm the tag with the owner (for the first beta: `v0.5.0-beta.1`, confirmed by the owner on 2026-10-14), and the notes `docs/release-notes/v0.5.0-beta.1.md` (with the provenance sentence of FR-016 and the list of what was checked by hand).
2. `git push` of the branch, then the `Release` workflow with the tag (as for alpha.6; for the beta, run 38476023499, four jobs green, four packages). A published tag is never moved.
