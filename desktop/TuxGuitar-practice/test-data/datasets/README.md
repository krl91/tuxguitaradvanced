# Independent public datasets used by the dataset benchmarks

None of this material is in the repository, and none of it may be: the audio is under licences that forbid
redistribution or commercial use. The benchmarks read the datasets from a local folder
(`-Dtuxguitar.datasets=<dir>`, default `~/tuxguitar-datasets`), write only counts, and are **not part of the default test
suite** (they run with `-Dgroups=dataset -DexcludedGroups=`; a missing dataset skips them). Only this manifest (provenance and
licence) is committed.

| Dataset | Official source | Licence (read from the official record on 2026-10-06) | Used for | Local layout expected by the benchmarks |
|---|---|---|---|---|
| GuitarSet 1.0.1 (Xi, Bittner, Pauwels, Ye, Bello, ISMIR 2018) | Zenodo record 1422265, DOI 10.5281/zenodo.1422265, published 2018-09-20; archive `GuitarSet_audio_and_annotation.zip` (md5 `bce22aa8cd28995bd2094d7588aea586`) | **CC BY-NC 4.0** (non-commercial, attribution) | complete versus incomplete chords from the hexaphonic recordings (`GuitarSetChordBenchmarkTest`) | `guitarset/GuitarSet/annotation/*_comp.jams`, `.../audio/audio_hex-pickup_debleeded/*_comp_hex_cln.wav`, `.../audio/audio_mic/*_comp_mic.wav` (the 180 "comp" recordings) |
| IDMT-SMT-Guitar V2 (Kehling, Abeßer, Dittmar, Schuller, DAFx 2014) | Fraunhofer IDMT dataset page; Zenodo record 7544110 | **CC BY-NC-ND 4.0**, "for evaluation purpose" (no derivative works, no redistribution) | single notes, intervals, chords, expression styles (`DatasetBenchmarkTest`, `TechniqueDatasetBenchmarkTest`) | `guitar2/IDMT-SMT-GUITAR_V2/dataset1..4` |
| IDMT-SMT-Audio-Effects (Stein, Abeßer, Dittmar, Schuller, 2010) | Fraunhofer IDMT dataset page; Zenodo record 7544032 | **CC BY-NC-ND 4.0** | single notes and chords through 11 effects (`DatasetBenchmarkTest`) | `effects/IDMT-SMT-AUDIO-EFFECTS/` |

Consequences of the licences: no clip, fixture or derived audio is committed; results are published as counts and identifiers only
(identifiers such as `00_BN1-129-Eb_comp` are file names of the official release); a result obtained on these datasets is a
measurement of this software on independent recordings, not a redistribution of them. The project's own reference recording
(`tests/1`) is private and also stays out of the repository.
