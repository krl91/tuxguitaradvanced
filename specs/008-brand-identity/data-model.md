# Data model: brand assets (spec 008)

All entries are files of the repository; nothing is stored at run time. The machine-readable form is `branding-manifest.json` (see `contracts/branding-manifest.md`).

| Entity | Fields | Rule |
|---|---|---|
| `Master` | path `specs/008-brand-identity/assets/tuxguitaradvanced-logo-primary.png`, 1254 x 1254, RGBA, SHA-256 `a9d369a2574396cd3208b0665648c57f398690499efb137d6781478991baf9e0` | Never modified, never replaced without the owner's explicit approval recorded in the spec (FR-004). |
| `Derivative` | `id`, `kind` (`full`, `icon-only`, `splash`, `panel`), `source` (the master), `operation` (downscale, rectangular crop with fixed coordinates, uniform scale and centre on a background), `sizes`, `background`, `status` (`proposed`, `approved`, `rejected`), `approvedBy`, `approvedOn` | Made by a script of `misc/branding/` from the master only; `status` is `approved` only after the owner's decision (FR-010). A derivative may not recolour, retouch or redraw (FR-002). |
| `Surface` | `id`, `platform` (`macos`, `windows`, `linux`, `application`, `docs`), `path` in the repository, `format` (`png`, `ico`, `icns`), `entries` (the sizes inside an `.ico` or `.icns`), `derivative`, `inPackage` (the path inside the package) | One entry per declared file. The file must exist, decode, have the declared dimensions and, for a package, be in it (FR-014). |
| `ContactSheet` | `derivative`, `sizes` (16, 32, 64, 128, 256, 512, 1024), `backgrounds` (light, dark) | Produced for the review (FR-009); kept out of the repository (images of the review only) or in `specs/008-brand-identity/review/` if the owner wants them kept. |
| `Approval` | `derivative`, `decision` (approve, amend, reject), `date`, `note` | Written in `contracts/branding-manifest.md` (FR-004, FR-005). |

State of a derivative: `proposed` -> `approved` (or `rejected`, then amended into a new `proposed`). Only `approved` derivatives are integrated and only they may be in a release (FR-017).
