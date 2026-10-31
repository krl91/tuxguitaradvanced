# Quickstart: validating spec 020

## Local (before the push; owner's Mac, JDK 21)

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21); export PATH="$JAVA_HOME/bin:$PATH"
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa && mvn -o -e clean verify -P native-modules
```

1. **Minimums of the build** (expected `15.0` three times, then `15.0` in the Info.plist):
   ```sh
   A=target/tuxguitar-9.99-SNAPSHOT-macosx-swt-cocoa.app
   otool -l $A/Contents/MacOS/tuxguitar-launcher | grep -A3 LC_BUILD_VERSION | grep minos
   for a in x86_64 arm64; do otool -arch $a -l $A/Contents/Resources/tuxguitar/lib/libtuxguitar-audiounit-jni.jnilib | grep -A3 LC_BUILD_VERSION | grep minos; done
   /usr/libexec/PlistBuddy -c 'Print :LSMinimumSystemVersion' $A/Contents/Info.plist
   ```
   Then copy the `.app` to the scratchpad, seal the copy ad hoc (`misc/seal-macos-app.sh <copy>`, as the release seals the bundle; the local build signs only the launcher, so `misc/verify-macos-bundle.sh` refuses the unsealed `.app` at its signature check), and run `misc/verify-macos-bundle.sh <copy>`: only `OK` lines.
2. **Self-tests** (all pass): `misc/test-verify-macos-bundle.sh`, `misc/test-stamp-macos-plist.sh`, `misc/test-relocate-macos-payload.sh`.
3. **Signing as on the build machine, with a throwaway certificate** given through `MACOS_CERT_P12_BASE64` / `MACOS_CERT_PASSWORD` on a copy of the `.app` (with a JRE copied in, as the release does): the script ends without any trust step, prints `designated => identifier "app.tuxguitaradvanced" and certificate root = H"<its sha1>"`, and `misc/verify-macos-bundle.sh <copy> --signed-by <its sha1>` prints only `OK` lines. Nothing is added to the trust settings of the Mac.
4. **The full gate** stays green (the build above runs the unit tests).

## On GitHub (after one push)

5. Run the release for `v0.8.0-beta.3` (it replaces its draft). Expected: every job green; the macOS job runs on a macOS 15 image and its log shows the designated requirement with `H"280bd902…af55"` and the bundle check's `OK` lines, the identity line included; every Ubuntu job shows `Image: ubuntu-24.04`; the release is published with its 4 files.
6. Run once each: *MacOS Java CI with Maven* and *Practice GUI tests*, green on macOS 15; and the Ubuntu build-and-test workflow, green on Ubuntu 24.04.

## By the owner [HUMAN]

7. Over v0.8.0-beta.2 (microphone permission granted), install v0.8.0-beta.3: *Open Anyway* is offered at the first launch, no microphone permission request appears, the tuner hears the input (SC-002).
