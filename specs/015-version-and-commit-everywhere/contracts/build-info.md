# Contract: `share/build-info.properties`

Plain `key=value`, UTF-8, written by `misc/stamp-build-info.sh`, read by the application.

```
version=0.6.0-beta.1      # or: dev
numeric=0.6.0.0
commit=<40 hex>           # or: unknown
modified=false
```

- Written in every package under `share/` (macOS: `Contents/MacOS/share/`), for releases from the workflow's identity, for local builds from git with `version=dev`.
- Unknown keys are ignored; a missing key falls back to the `dev` value of that key.
- The stamp script refuses (exit 1) a `version` that does not match `^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$` and a `commit` that is not 40 hex.
