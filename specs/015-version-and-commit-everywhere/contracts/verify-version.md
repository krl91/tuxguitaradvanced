# Contract: `misc/verify-version.sh <package> <platform> <version> <commit>`

Opens the package like `misc/verify-branding.sh` (.app/.zip/.tar.gz/.deb) and reads, per platform, every place that carries the version, comparing with the expected values:

| Platform | Places read |
|---|---|
| macos | `Contents/MacOS/share/build-info.properties`; Info.plist `CFBundleShortVersionString` (`X.Y.Z`), `CFBundleVersion` (`X.Y.Z.0`), `CFBundleGetInfoString` (full text); the zip file name |
| windows | `share/build-info.properties`; version resource of `tuxguitar.exe` (file and product version, text version); zip file name |
| linux | `share/build-info.properties` in the tar.gz and in the .deb; `dpkg-deb --field Version` (`X.Y.Z~label`); both file names |

Output: one line per place `OK|WRONG|MISSING <place>: <read> (expected <value>)`; exit 0 only if all places are OK. It never reads the version from the tag name of the file alone. `misc/test-verify-version.sh` builds one package per platform with **one** wrong value per place and requires the verifier to fail on each (SC-004).

## Release level: `misc/verify-release.sh <tag> <version> <commit>`

Run with `gh`. **Before publication** (`--draft`): the release is still a draft; its tag name equals the asked tag; its **title** is `TuxGuitarAdvanced <tag>`; its **target commit** is `<commit>`; the **notes** state the full `<commit>`; the attached assets are exactly the four expected names built from `<tag>`. **Right after publication** (`--published`): the git **tag** `<tag>` points to `<commit>`; if it does not, the workflow puts the release back to draft and fails (the tag is reported, never moved). Output and exit code as above; its self-test injects one wrong value per check using a fake `gh` on the PATH (both modes). A tag whose release is already published is refused at the start of the workflow (the validate job).
