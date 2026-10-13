# Branding scripts (spec 008)

These scripts make **every derivative of the official logo from the master only**:
`specs/008-brand-identity/assets/tuxguitaradvanced-logo-primary.png` (SHA-256 `a9d369a2574396cd3208b0665648c57f398690499efb137d6781478991baf9e0`).

Rules (spec 008, FR-002, FR-006, FR-010, FR-011):

* the master is **never opened for writing**; the scripts only read it and write into a work folder outside the repository (`/tmp/branding-work`, the review sheets in `/tmp/branding-review`);
* **no recolouring, retouching, redrawing, regeneration or content-aware cropping**: a derivative is a uniform downscale, a rectangular crop with the fixed coordinates of `crop.conf`, or the unmodified logo scaled uniformly and centred with margins on a plain background (`background.conf`);
* an **unapproved redesign or an AI regeneration is rejected, even if it looks similar** to the master (US3.2); a derivative is official only after the owner's approval written in `specs/008-brand-identity/contracts/branding-manifest.md`;
* `build-platform-icons.sh` (task T017) refuses a derivative that is not `approved` in `branding-manifest.json`.

Files: `make-derivatives.sh` (D1 full, D2 icon-only, D3 splash, D4 panel), `make-contact-sheets.sh` (16 to 1024 px, light and dark backgrounds), `crop.conf` (the rectangular crop of D2), `background.conf` (the plain background of D3 and D4).

## Future derivatives, not made by the beta (each needs its own approval, FR-011)

monochrome; high-contrast; horizontal wordmark; a document icon built on the logo for `.tg` files (`tgdoc.icns`); Android icons.
