# Research: macOS 15 build machines, minimum macOS 15, the same stable signature, Ubuntu pinned (spec 020)

All findings below were checked on 2026-10-31, on the owner's Mac (macOS 27, Apple Silicon, Xcode command-line tools) unless stated otherwise, and in the logs of the release runs 41149428414 (v0.8.0-beta.2, published) and 41214931922 (v0.8.0-beta.3, failed in the macOS 14 brownout).

## R1. Where the minimum macOS comes from today

- **Finding**: no file of the repository sets a minimum macOS. `otool -l` on the development build gives `minos 27.0` for `Contents/MacOS/tuxguitar-launcher` and for both slices (x86_64, arm64) of `Contents/Resources/tuxguitar/lib/libtuxguitar-audiounit-jni.jnilib`: clang takes the SDK of the build machine. The Info.plist has no `LSMinimumSystemVersion`. These are the only two Mach-O files the build itself puts in the bundle.
- The launcher is compiled by an `<exec executable="/usr/bin/clang">` in `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml` (~line 407). The AudioUnit library is compiled by `make` in `desktop/TuxGuitar-AudioUnit/jni/Makefile` (upstream file, `$(CXX)` for `-arch x86_64` and `-arch arm64`), started by `desktop/build-scripts/native-modules/tuxguitar-audiounit-macos/pom.xml` (~line 40).
- **Decision**:
  - one property, `macos.minimumVersion` = `15.0`, in `desktop/pom.xml` (the parent of both build-script poms);
  - the launcher: `-mmacosx-version-min=${macos.minimumVersion}` added to its clang call;
  - the AudioUnit library: `<env key="MACOSX_DEPLOYMENT_TARGET" value="${macos.minimumVersion}"/>` on the `make` exec, so the upstream Makefile is not touched;
  - the Info.plist: `Add :LSMinimumSystemVersion string ${macos.minimumVersion}` with PlistBuddy, next to the fork's other Info.plist edits in the cocoa pom (~line 427).
- **Checked**: `MACOSX_DEPLOYMENT_TARGET=15.0 make CXX=g++` on a two-arch Makefile of the same shape gives `minos 15.0` for both slices; `clang -mmacosx-version-min=15.0` gives `minos 15.0` for a C11 launcher.
- **Alternatives rejected**: flags in the upstream Makefile (an upstream change for a fork decision, Principle I); a value in each of the two poms (two places to keep equal); `MACOSX_DEPLOYMENT_TARGET` exported in the workflows only (a local build would still get the build machine's version, against FR-003).

## R2. The minimum of the third-party native code in the bundle

- **Finding**: the release copies the runner's Temurin 21 JDK into `Contents/Resources/tuxguitar/jre`. All 72 Mach-O files of the local Temurin 21 declare `minos 11.0`. SWT's native libraries are inside its jar and are extracted at run time, so they are not in the bundle.
- **Decision**: the check of FR-004 requires **exactly 15.0** for the Info.plist and the two parts the build makes (each slice), and **at most 15.0** for every other Mach-O file of the bundle. The spec's FR-004 was made precise in the same sense.
- **Alternative rejected**: exactly 15.0 for every Mach-O file (it would refuse every release because of the JRE).

## R3. Why the signing step fails on macOS 15 machines, and the way out

- **Finding (owner's earlier try, memory of 2026-10-30)**: on macOS 15 machines, `sudo security add-trusted-cert -d -r trustRoot -p codeSign …` in `.github/scripts/sign-macos-launcher.sh` fails with `NO (-60005)`; the `authorizationdb write com.apple.trust-settings.admin allow` trick no longer allows it.
- **Finding (local test with a throwaway self-signed code-signing certificate in a throwaway keychain, never trusted)**:
  - `security find-identity -v -p codesigning` lists **0 valid identities** (the script's current way to find the identity), but `security find-identity -p codesigning` lists it with `CSSMERR_TP_NOT_TRUSTED` and its SHA-1;
  - `codesign --force --keychain <kc> -s <SHA-1>` signs (exit 0), and `codesign --verify --strict` passes;
  - with no requirement given, the designated requirement becomes `identifier … and certificate leaf = H"…"`;
  - with `-r='designated => identifier "app.test" and certificate root = H"<sha1>"'` it is exactly that, and `codesign --verify --strict -R='identifier "app.test" and certificate root = H"<sha1>"'` passes (for a self-signed certificate, the root is the leaf).
- **Finding (beta.2 log)**: the published releases have `designated => identifier "app.tuxguitaradvanced" and certificate root = H"280bd902de0512b59d70228c5faa441d3eb6af55"`.
- **Why trust on the build machine never mattered to users**: the user's Mac does not trust the certificate either; macOS offers *Open Anyway* for a valid signature by an unknown developer, and calls an application damaged only when its signature does not verify. The trust on the build machine only made `security find-identity -v` and `codesign` accept the identity by name.
- **Decision**: in `sign-macos-launcher.sh`, drop the two trust lines (and the matching `remove-trusted-cert` of the cleanup); take the identity's SHA-1 from `security find-identity -p codesigning "$KEYCHAIN"` (exactly one identity expected, else fail); build the requirement `designated => identifier "app.tuxguitaradvanced" and certificate root = H"<sha1 in lower case>"`; pass it to `misc/seal-macos-app.sh` as a new optional 4th argument that becomes `codesign -r=<requirement>`. Without the secrets, nothing changes (ad hoc, FR-007).
- **Risk, stated**: the untrusted-identity signing was checked on macOS 27, not on a macOS 15 machine; the first release run (FR-013) is where it is proved. The microphone permission kept across the update is proved only by the owner (SC-002).
- **Alternatives rejected**: ad hoc signing (the owner's choice is the stable certificate; ad hoc changes the identity at each release and loses the microphone permission); a user-domain trust (`add-trusted-cert` without `-d`) (asks for an interactive authorisation, which hangs a build machine); a paid Apple Developer ID (not the owner's choice).

## R4. Checking the signature's identity in the release (FR-006)

- **Decision**: `misc/verify-macos-bundle.sh` gets an optional `--signed-by <sha1>`. With it, the verifier reads `codesign -d -r-` and requires the designated requirement to be exactly `identifier "app.tuxguitaradvanced" and certificate root = H"<sha1>"`, and requires `codesign --verify --strict -R='<same requirement>'` to pass (the signature really satisfies it). The release passes `--signed-by 280bd902de0512b59d70228c5faa441d3eb6af55` when the signing secrets exist (a public value, written in the workflow), and nothing otherwise.
- **Alternative rejected**: checking the fingerprint inside the sign script only (the package itself would not be checked, and a check that never refuses anything in a self-test proves nothing, FR-012).

## R5. The self-tests (FR-012, SC-004)

- **Finding**: the macOS scripts already have self-tests run by the release's macOS job (`misc/test-stamp-macos-plist.sh`, `misc/test-relocate-macos-payload.sh`), and the verifiers have their own (`misc/test-verify-version.sh`, `test-verify-branding.sh`, `test-verify-release.sh`). There is none for `verify-macos-bundle.sh`.
- **Decision**: a new `misc/test-verify-macos-bundle.sh`, macOS only, that builds small fake `.app` folders with clang (launcher and a two-slice library, each with a chosen `-mmacosx-version-min`, a JRE-like library at 11.0 or 16.0, an Info.plist with or without `LSMinimumSystemVersion`), signs them ad hoc or with a throwaway certificate in a throwaway keychain (never trusted, as in R3), and checks that the verifier passes the right one and refuses each wrong one, naming the place. It runs locally and in the release's macOS job, next to the other self-tests.

## R6. The build machines' labels

- **Finding**: `runs-on: macos-14` in `release.yml` (job `build-macos-arm64`), `macos-maven.yml` and `practice-gui-tests.yml`; `runs-on: ubuntu-latest` in five jobs of `release.yml` (`validate`, `create-release`, `build-windows`, `build-linux`, `release`) and in `ubuntu-maven.yml`. The v0.8.0-beta.2 log shows `Image: ubuntu-24.04` for its Ubuntu jobs. `macos-15` is Apple Silicon on GitHub, like `macos-14`.
- **Decision**: `macos-15` and `ubuntu-24.04`, written explicitly; the names that carry the old label are updated with it (the artifact name `Package-MacOS-macos-14`; the job id `build-ubuntu-latest` and anything named after it in `ubuntu-maven.yml`), so that no "latest" or "14" is left that says something false.

## R7. Where the minimum is told to users (FR-010)

- **Finding**: the macOS requirement is stated in `README.md` and `docs/README.md` (lines ~53 and ~137), `docs/MANUAL.md` and `docs/MANUAL-FR.md` (lines ~50 and ~429), as "macOS (Apple Silicon)".
- **Decision**: "macOS 15 Sequoia or later (Apple Silicon)" in those places (French: "macOS 15 Sequoia ou plus récent (Apple Silicon)"), and in `docs/release-notes/v0.8.0-beta.3.md`, which also says that macOS 14 is no longer supported. The tag v0.8.0-beta.3 was never published (its run left only a draft), so its notes may still change (FR-011).

## R8. Cost and order of the tries (FR-013)

- macOS minutes count ten times on a private repository, and each push asks the owner's Keychain password. Everything that can be checked locally is checked before the push: the local build's minimums and the verifier on it, the self-tests, the sign script with a throwaway certificate given through the same environment variables as the secrets. Then one push, and the v0.8.0-beta.3 release is the first run on the new machines; the two manual workflows run once after it.
