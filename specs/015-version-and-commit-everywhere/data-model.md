# Data model: build identity

**BuildIdentity** (value, immutable; `common/TuxGuitar-lib`, `app.tuxguitar.util`)

| Field | Rule |
|---|---|
| `version` | text: `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-label`; label = letters, digits, dots, hyphens (FR-011); or the development label `dev` |
| `numeric` | `MAJOR.MINOR.PATCH.0` (clarification 4), never contains the label |
| `commit` | 40 hexadecimal characters, or `unknown` (build without git) |
| `modified` | boolean, true for a development build with a dirty working copy; always false for a release |
| `release` | boolean: false for `dev` |

Derived: `shortCommit` = first 7 characters (or `unknown`); `title()` = `TuxGuitarAdvanced <version>` (`dev` shows `dev`); `about()` lines with short commit and `(modified)`; `copyLine()` = `TuxGuitarAdvanced <version> (<full commit>[, modified])`.

Missing or unreadable file ⇒ `dev`, commit `unknown`, no error (FR-007 case 3). File format: [contracts/build-info.md](contracts/build-info.md).

**Place** (spec): the 9 surfaces; each is checked by `verify-version.sh` against the identity (contract [verify-version.md](contracts/verify-version.md)).
