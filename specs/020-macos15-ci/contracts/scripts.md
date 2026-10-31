# Contract: the macOS packaging scripts (spec 020)

## `misc/verify-macos-bundle.sh` (extended)

```
misc/verify-macos-bundle.sh <package> [--signed-by <sha1>]     package: a .app folder or a .zip
```

Checks, each printed on its own line as `OK      [n] …` or `WRONG   [n] …`: the existing format, with the check's number added after the word (spec 020, so that the self-test can tell which check refused):

1. `Contents/MacOS` holds only files (existing).
2. The code signature of the bundle is valid, `codesign --verify --deep --strict` (existing).
3. **New**: `Contents/Info.plist` has `LSMinimumSystemVersion` equal to `15.0`.
4. **New**: `Contents/MacOS/tuxguitar-launcher` and each slice of `Contents/Resources/tuxguitar/lib/libtuxguitar-audiounit-jni.jnilib` declare `minos 15.0`; a missing file is WRONG.
5. **New**: every other Mach-O file of the bundle declares a minimum, and none above `15.0`; the WRONG line names the first offending file (path from `Contents/`).
6. **New, only with `--signed-by`**: the designated requirement is exactly `identifier "app.tuxguitaradvanced" and certificate root = H"<sha1>"` (lower case), and `codesign --verify --strict -R='<that requirement>'` passes.

Exit code: 0 when every check is OK; 1 when one is WRONG; 2 when the package cannot be opened (existing). The expected minimum `15.0` is a constant of the script (the same value as `macos.minimumVersion`; a mismatch is caught by the local build check of the quickstart).

## `misc/test-verify-macos-bundle.sh` (new)

```
misc/test-verify-macos-bundle.sh
```

macOS only (needs clang, codesign, security, openssl). Builds fake bundles in a temporary folder and prints one line per case; exit 0 only when every case gives the expected result. A *pass* case needs exit 0 and no `WRONG` line; a *refuse* case needs exit 1 **and** a line starting `WRONG   [n]` with the number of the check named in parentheses, so that no case passes for another reason. A fake bundle that cannot be built stops the test with exit 2.

| Case | Expected |
|---|---|
| right bundle, ad hoc, no `--signed-by` | pass |
| right bundle, throwaway certificate, `--signed-by <its sha1>` | pass |
| no `LSMinimumSystemVersion` / `14.0` | refuse (check 3) |
| launcher at `14.0` / `26.0` | refuse (check 4) |
| one AudioUnit slice at `27.0` | refuse (check 4) |
| JRE-like library at `26.0` / with no minimum | refuse (check 5) |
| JRE-like library at `11.0` | pass |
| throwaway certificate, `--signed-by <another sha1>` | refuse (check 6) |
| ad hoc, `--signed-by <sha1>` | refuse (check 6) |
| throwaway certificate, requirement `certificate leaf = H"…"` | refuse (check 6) |

The throwaway keychain is deleted on exit, whatever happens; nothing is trusted.

## `misc/seal-macos-app.sh` (extended)

```
misc/seal-macos-app.sh <app> [identity] [keychain] [requirement]
```

With a 4th argument, codesign gets `-r=<requirement>` (the whole `designated => …` text). Without it, unchanged.

## `.github/scripts/sign-macos-launcher.sh` (changed)

- No `authorizationdb write`, no `add-trusted-cert`, no `remove-trusted-cert`.
- The identity is the SHA-1 of the only code-signing identity of the throwaway keychain, read with `security find-identity -p codesigning` (not `-v`), counting the distinct SHA-1 of its *Matching identities* part; none or more than one: fail with a message.
- Calls `seal-macos-app.sh <app> <sha1> <keychain> 'designated => identifier "app.tuxguitaradvanced" and certificate root = H"<sha1 lower case>"'`.
- Prints the designated requirement of the result (existing last line).
- Without the secrets: unchanged (ad hoc).

## Workflows

| File | Job | `runs-on` |
|---|---|---|
| `release.yml` | `build-macos-arm64` | `macos-15` |
| `release.yml` | `validate`, `create-release`, `build-windows`, `build-linux`, `release` | `ubuntu-24.04` |
| `macos-maven.yml` | `build-macOS` (artifact `Package-MacOS-macos-15`) | `macos-15` |
| `practice-gui-tests.yml` | `gui-tests` | `macos-15` |
| `ubuntu-maven.yml` | `build-ubuntu` (renamed from `build-ubuntu-latest`) | `ubuntu-24.04` |

In `release.yml`, job `build-macos-arm64`:
- the self-test step also runs `misc/test-verify-macos-bundle.sh`;
- the bundle check passes `--signed-by 280bd902de0512b59d70228c5faa441d3eb6af55` when `MACOS_CERT_P12_BASE64` is not empty (read from the step's environment), and no option otherwise.
