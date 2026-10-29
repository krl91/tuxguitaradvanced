# Contract: the fire drawing (spec 018)

`FlameDrawing.items(viewport, tier, flames, texts)` keeps its signature and its place (before every other item).

| Input | Output |
|---|---|
| flames null, OFF, or tier PERFORMANCE | no item |
| level 0 | only `FLAME_EMBER` cells on the bottom rows |
| level > 0 | `FLAME_TONGUE` square QUADs (run-merged cells) under `height(col)`, plus the ember rows |
| any cell | opacity ≤ 0.60 (≤ 0.36 discreet); no item for heat 0; within 4 px of a text ≤ 12 % (in practice `NEAR_TEXT`) |
| corners vs middle | corner height ≤ 55 % of the picture; middle height and opacity ≤ 22 % of the corners' |
| discreet | 60 % of the height and of the opacity of full |
| BALANCED | cells twice as big; no spark |
| bursts (FULL, motion) | per burst ≤ 10 spark cells (`FLAME_EMBER`), ≤ 60 in all, each gone after 1 s |
| reduced motion | clock ignored: still fire, no spark |
| same inputs | same items (deterministic) |
