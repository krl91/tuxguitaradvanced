# Data model: spec 020

No stored data. The "entities" are the values the build writes into the macOS package and the values the release checks in it.

## Minimum system version

| Where | Value | Set by | Checked by |
|---|---|---|---|
| `desktop/pom.xml` property `macos.minimumVersion` | `15.0` | this spec (the one place) | — |
| `Contents/Info.plist` key `LSMinimumSystemVersion` | exactly `15.0` | cocoa pom, PlistBuddy (R1) | `verify-macos-bundle.sh` |
| `Contents/MacOS/tuxguitar-launcher` (`LC_BUILD_VERSION minos`) | exactly `15.0` | cocoa pom, clang `-mmacosx-version-min` | `verify-macos-bundle.sh` |
| `Contents/Resources/tuxguitar/lib/libtuxguitar-audiounit-jni.jnilib`, each slice (x86_64, arm64) | exactly `15.0` | audiounit pom, `MACOSX_DEPLOYMENT_TARGET` on `make` | `verify-macos-bundle.sh` |
| every other Mach-O file of the bundle (the embedded JRE: `11.0` today) | at most `15.0` | not ours | `verify-macos-bundle.sh` |

A Mach-O file with no `minos` (neither `LC_BUILD_VERSION` nor `LC_VERSION_MIN_MACOSX`) is refused: its minimum is unknown.

## Stable signing certificate

- Kept in the secrets `MACOS_CERT_P12_BASE64` and `MACOS_CERT_PASSWORD` (unchanged).
- Fingerprint (SHA-1): `280bd902de0512b59d70228c5faa441d3eb6af55`, public, written in `release.yml` as the expected value.
- Never trusted on the build machine (R3).

## Designated requirement

- With the certificate: exactly `identifier "app.tuxguitaradvanced" and certificate root = H"<SHA-1 of the certificate, lower case>"`, written by `seal-macos-app.sh` from the 4th argument given by `sign-macos-launcher.sh`.
- Without the certificate (forks): the ad hoc requirement codesign makes; not checked.

## States of a release run (macOS job)

```
build (minimums 15.0) → self-tests (stamp, relocate, verify-macos-bundle) → embed JRE → sign
   → zip → verify bundle (layout, signature, minimums, [identity when secrets]) → branding → version → attach
```

Any refusal stops the job; the draft stays and a run for the same tag replaces it.
