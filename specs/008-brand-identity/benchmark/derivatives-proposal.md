# Proposed derivatives of the official logo (spec 008, tasks T009 and T015) - 2026-10-13

Made from the master only by `misc/branding/make-derivatives.sh` (work folder `/tmp/branding-work`), reviewed on the sheets made by `misc/branding/make-contact-sheets.sh` (`/tmp/branding-review/`). **Nothing is approved and no packaged file is replaced** (gate G1 is the owner's).

| Id | What | How | Files in `/tmp/branding-review/` |
|---|---|---|---|
| D1 | the full logo at 128, 256, 512 and 1024 px | uniform Lanczos downscale, alpha kept | `D1-light.png`, `D1-dark.png` (the sizes below 128 do not exist for D1: the wordmark would not be readable) |
| D2 | the icon-only version at 16, 24, 32, 48, 64, 96, 128 and 256 px | rectangular crop of the master `1254 x 935` from the top-left corner (`misc/branding/crop.conf`), centred on a transparent square, then a uniform downscale | `D2-light.png`, `D2-dark.png` |
| D3 | the splash 500 x 300 | the unmodified logo scaled uniformly (276 px high), centred, 12 px margin, on a plain background: dark `#1E1E1E` or light `#FFFFFF` | `D3-dark.png`, `D3-light.png` |
| D4 | the installer panel 163 x 314 | the unmodified logo scaled uniformly (147 px wide), centred, 8 px margin, on the same two backgrounds | `D4-dark.png`, `D4-light.png` |

## What the owner has to decide (gate G1)

1. **D1** (full logo from 128 px): approve or reject.
2. **D2 (the small icon)**: the wordmark and the feet of the penguin overlap between y = 940 and y = 975 of the master, so a rectangular crop above the wordmark has to choose. Tried: 920 and 935 (the feet are cut flat, no letter shown), 950 and 968 (the tops of a few letters appear at the bottom: not proposed). **Proposal: 935.** Other choices: another crop height, or no icon-only version (the small sizes then keep the upstream icon and the beta says so; a downscale of the full logo is not proposed because its wordmark is unreadable below 128 px, spec 008 US2).
3. **D3 and D4**: the background (dark or light) and the margins; the dark one is proposed because the glow of the logo is made for it, the light one is shown as the alternative.

## What was checked, and what was not

* The sheets are made at the true pixel sizes; I looked at D2 and D3 as images: the proposed D2 is clean at the bottom edge, small sizes (16, 24, 32) keep the penguin and the rainbow road as two blobs; whether they are "identifiable" at 16 px is the owner's judgement.
* Not looked at yet: any packaged build (nothing is replaced).
