# Implementation Plan: Interactive Practice MVP

**Branch**: `feature/interactive-practice` | **Date**: 2026-09-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-interactive-practice-mvp/spec.md`

**Evidence**: every architectural claim below is backed by [research.md](./research.md) (R1–R19),
which cites the repository files inspected.

## Summary

Interactive Practice is delivered as a new desktop plugin, **TuxGuitar-practice**: a
wait-for-correct-note song-learning mode driven by live guitar pitch, on exactly one
learner-accepted Practice Track and one live input device/channel. It uses existing extension
points only (`TGToolItemPlugin`, action manager, key-binding registry, update/player events),
TuxGuitar's own repeat/alternate-ending traversal (`MidiRepeatController`) and the existing
external-beat API for a practice highlight separate from the editing cursor.

A new library module, **TuxGuitar-audio-engine**, holds the reusable, UI-independent engine
(Java Sound capture with per-channel logical inputs, level assessment, pitch estimation with
TarsosDSP `FastYin` on a 2048-sample window — validated on real recordings and live — separate
articulation detection, shared note interpretation). An **evolved tuner**
(**TuxGuitar-tuner-next**) keeps the existing tuner's identity and learner-facing capabilities
and uses that engine. For the MVP, the engine, the evolved tuner and practice are packaged only
in the validated **macOS Apple Silicon + SWT Cocoa** build; the other eight desktop builds keep
the unchanged legacy tuner (P9).

Core changes are limited to a small tablature hook (3 desktop classes) that makes the score
honour the existing external-beat API, default shortcut entries, and macOS-SWT-only packaging.

## Technical Context

**Language/Version**: Java, compiled at `<release>9</release>` (desktop parent POM); built and run
with Temurin 21.

**Primary Dependencies**: `tuxguitar-lib`, `tuxguitar-editor-utils`, `tuxguitar` (app),
`tuxguitar-ui-toolkit` (abstract UI; SWT Cocoa at runtime), new `tuxguitar-audio-engine`,
`javax.sound.sampled` (JDK), **TarsosDSP 2.5 core** (`be.tarsos.dsp:core:2.5`, repository
`https://mvn.0110.be/releases`, not Maven Central) for pitch estimation — GPL-3.0, permitted by
constitution v1.1.0; provenance below; GPLv3 distribution compliance is a release gate.

**Storage**: none. Session state, guitar confirmations and the tuner's chosen tuning are in
memory for the application run only; no learner audio is ever stored (FR-034, FR-055, FR-068).

**Testing**: JUnit 5.9.2 (as in `common/`). Engine module: standard Maven test layout with
synthetic-signal tests and the explicitly run, manifest-driven recorded benchmark. Plugin
modules: tests in `test/` outside `src` (research R16). Hardware behaviour via the manual
checklist (SC-009).

**Target Platform**: macOS on Apple Silicon, SWT Cocoa build (`tuxguitar-macosx-swt-cocoa`)
only. Engine code is pure Java/Java Sound and kept portable; other builds are not claimed.

**Project Type**: desktop application plugins + library module (existing multi-module Maven
codebase).

**Performance Goals**: perceived correct-note response ≤ 300 ms (SC-002, provisional); UI
updates coalesced ≤ ~30/s; analysis hop ≈ 5–10 ms; no UI stalls in a 30-minute session (SC-007).

**Constraints**: no blocking or allocation on the capture loop; no audio work on the UI thread;
Java 9 language level; LGPL-compatible code only; no persistence of learner audio; no change to
existing behaviour when the feature is inactive (FR-067); other eight builds unchanged (P9).

**Third-party dependency provenance** (constitution v1.1.0):

| Dependency | Version | Licence | Source | Used by |
|---|---|---|---|---|
| TarsosDSP core (`be.tarsos.dsp:core`) | 2.5 | GPL-3.0 (published POM) | https://github.com/JorenSix/TarsosDSP (Maven repository https://mvn.0110.be/releases) | `TuxGuitar-audio-engine` (pitch estimation, `FastYin`) |

**Scale/Scope**: one learner, one Practice Track, one input device/channel; songs of typical
length (thousands of events).

**Open items**: GPLv3 distribution compliance (release gate per constitution v1.1.0, not an
implementation blocker);
direct USB/interface input validation; multi-channel validation on a real multi-input interface (P2);
launcher signing-identity check in Phase 3 (research.md "Q-L option A probe"); the
owner's initial benchmark recordings (needed before S3 and the Phase 4 conclusions). S1 (partly),
S2 and S4 have run — results in research.md "Spike results". Product questions P1–P9 and
planning point Q-B are decided.

## Constitution Check

*Pre-design gate: PASS. Re-checked after Phase 1 design: PASS.*

| Principle | Assessment |
|---|---|
| I. Preserve compatibility | Additive; no model/file-format change; practice never modifies the song. The eight unvalidated builds keep the legacy tuner unchanged (P9); the macOS SWT tuner preserves the FR-059 capabilities. The tablature hook is inert unless an external beat is shown. |
| Technical & Platform Constraints (dependency licences, constitution v1.1.0) | PASS: TarsosDSP 2.5 has an identified licence (GPL-3.0), compatible with the intended GPLv3 combined distribution and explicitly permitted; provenance documented below; GPL-dependent code confined to fork-specific modules (`TuxGuitar-audio-engine`, `TuxGuitar-tuner-next`, `TuxGuitar-practice`; the core tablature hook does not use it); GPLv3 distribution compliance is a **release gate**. |
| II. Extend before core | Practice and the evolved tuner are plugins; the engine is a library module. Core Java edits limited to the tablature hook (research R18), justified by the absence of any plugin paint/highlight contribution point. |
| III. Evidence-based | research.md R1–R19 cite every file relied on; FR-003, P7 and P9 decisions were taken on documented evidence, not assumptions. |
| IV. Spec-driven | Plan derives from the clarified spec; every product question returned and decided. |
| V. Incremental delivery | Spikes + seven buildable phases, each with observable acceptance. |
| VI. Real-time correctness | Dedicated capture/analysis/session threads; lock-free ring buffer; preallocated buffers; UI via `TGSynchronizer`; graceful input failure (FR-063). |
| VII. Separable, testable DSP | Engine is a separate module with no UI/app dependencies; deterministic synthetic tests; manifest-driven recorded benchmark. |
| VIII. Deterministic DSP first | TarsosDSP `FastYin` (deterministic YIN) chosen after S3 (beat McLeod on real clips) and live validation; no ML. |
| IX. Cross-platform conscious | Pure Java + Java Sound; UI via toolkit abstraction; shipped only in the validated build; explicit per-build migration path (research R19); `common/` untouched. |
| X. No unrelated refactoring | Legacy tuner untouched; no reformatting of untouched files. |
| XI. Build and test integrity | Each phase ends with a green `mvn -e clean verify -P native-modules`; existing tests untouched; packaging check prevents two tuners in one build. |
| XII. Observable acceptance / truthful reporting | Per-phase checks in quickstart.md; benchmark results recorded per input type; nothing claimed without a run. |
| XIII. Honest guitar-audio semantics | String/fret instructional; techniques never "verified"; offset realization worded as Practice's need, not author intent. |
| XIV. Learner-centred feedback | Four feedback states, octave hint, tuner-style cents, uncertain instead of false errors, learner-worded messages. |

## Architecture

```text
 TuxGuitar core (+ tablature hook)          TuxGuitar-audio-engine (library, lib/)
 ┌─────────────────────────────────────┐    ┌──────────────────────────────────────────┐
 │ actions / key bindings / Tools menu │    │ input   : Java Sound devices, logical     │
 │ update & MidiPlayer events          │    │           per-channel inputs, capture,    │
 │ MidiRepeatController (play order)   │    │           shared CurrentInputSelection     │
 │ external-beat API ─▶ tablature hook │    │ analysis: level, YIN, onset, frames       │
 │ TuningManager presets               │    │ pitch   : NoteInterpreter, TolerancePolicy│
 └───────────▲─────────────────────────┘    └───────────▲───────────────▲──────────────┘
             │ public APIs                               │               │
 ┌───────────┴─────────────────┐                         │               │
 │ TuxGuitar-practice (plugin) │─────────────────────────┘               │
 │ model / session / ui        │── opens tuner via action id ──┐         │
 └─────────────────────────────┘      `tuner.tuner`           ▼         │
                                     ┌───────────────────────────────────┴──┐
                                     │ TuxGuitar-tuner-next (plugin,        │
                                     │ module id tuxguitar-tuner)           │
                                     └──────────────────────────────────────┘
 Legacy TuxGuitar-tuner: unchanged, packaged only in the other eight builds.
```

Threads: capture → (ring buffer) → analysis → (frames) → session → (snapshots) → UI thread.
Learner commands: UI thread → session queue. Highlight calls and widgets: UI thread only.

## Project Structure

### Documentation (this feature)

```text
specs/001-interactive-practice-mvp/
├── spec.md
├── plan.md               # this file
├── research.md           # evidence and decisions (R1–R19)
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── audio-pitch-engine.md
│   ├── actions-and-shortcuts.md
│   └── practice-ui.md
├── checklists/requirements.md
├── benchmark/            # recorded benchmark results (SC-003), per input type
└── tasks.md              # created by /speckit-tasks
```

### Source Code (repository root)

```text
desktop/TuxGuitar-audio-engine/                   # NEW library module (tuxguitar-audio-engine)
├── pom.xml                                       # deps: tuxguitar-lib (provided); junit (test)
├── src/main/java/app/tuxguitar/audio/engine/
│   ├── input/        # devices, logical per-channel inputs, capture, CurrentInputSelection
│   ├── analysis/     # level, YIN, onset, AnalysisFrame
│   └── pitch/        # NoteInterpreter, TolerancePolicy
├── src/test/java/…                               # synthetic-signal tests, benchmark runner (tagged)
└── src/test/resources/benchmark/                 # LICENSE (CC0-1.0), manifest.csv,
                                                  #   interface/<tuning>/*.wav, builtin-mic/<tuning>/*.wav (≤ 50 MB)

desktop/TuxGuitar-tuner-next/                     # NEW evolved tuner plugin (module id tuxguitar-tuner)
├── pom.xml                                       # provided: lib, tuxguitar, ui-toolkit, audio-engine
├── src/app/tuxguitar/app/tools/custom/tunernext/ # own package (no clash with legacy)
├── share/META-INF/services/app.tuxguitar.util.plugin.TGPlugin
├── share/META-INF/tuxguitar-tuner.info           # same info name as legacy (never co-packaged)
├── share/lang/messages*.properties               # tuner.* keys
└── test/…

desktop/TuxGuitar-practice/                       # NEW practice plugin (module id tuxguitar-practice)
├── pom.xml                                       # provided: lib, editor-utils, tuxguitar, ui-toolkit, audio-engine
├── src/app/tuxguitar/app/tools/custom/practice/{model,session,ui}/
├── share/META-INF/services/app.tuxguitar.util.plugin.TGPlugin
├── share/META-INF/tuxguitar-practice.info
├── share/lang/messages.properties
├── test/…
└── test-resources/*.tg

desktop/TuxGuitar-tuner/                          # legacy tuner — UNCHANGED (other eight builds)

desktop/TuxGuitar/src/app/tuxguitar/app/view/component/tab/
    TablatureEditor.java, Tablature.java, TGControl.java   # tablature hook (research R18)
desktop/TuxGuitar/dist/tuxguitar-shortcuts.xml             # default bindings (shared; inert without plugin — Q-B)
desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml   # the only build-script change
desktop/build-scripts/tuxguitar-macosx-swt-cocoa/launcher/tuxguitar-launcher.c   # NEW Q-L option A stub (SWT build only)
```

**Structure Decision**: one library module (engine) + two plugin modules (evolved tuner,
practice); legacy tuner kept unchanged for unvalidated builds; no `common/` changes; new module
POMs reference sibling artifacts with `${project.version}`, so the shared parent POM
(`desktop/pom.xml`) does not need to change.

## Build files changed for the MVP

| File | Change |
|---|---|
| macOS SWT launcher (new, Q-L option A) | Tiny native Mach-O stub as `CFBundleExecutable` of the SWT `.app`; its only job is to start the existing, unchanged `Contents/MacOS/tuxguitar.sh` and wait. Launch, classpath and runtime logic stay in the script. Compiled (clang) and signed at least ad-hoc with identifier `app.tuxguitar` by the SWT build unconditionally (not only under `-P native-modules`); the packaged `Info.plist` gets `CFBundleExecutable` = the stub plus `NSMicrophoneUsageDescription` via the SWT-only post-copy step. |
| `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/pom.xml` | Modules: replace `../../TuxGuitar-tuner` with `../../TuxGuitar-audio-engine`, `../../TuxGuitar-tuner-next`, `../../TuxGuitar-practice`. Copy: `tuxguitar-audio-engine` → `Contents/MacOS/lib`; `tuxguitar-tuner-next`, `tuxguitar-practice` → `Contents/MacOS/share/plugins`; remove the legacy `tuxguitar-tuner` copy. After copying `common-macosx`, insert `NSMicrophoneUsageDescription` into the packaged `Info.plist` (SWT build only; the source plist is shared with macOS JFX). Packaging check: fail if both tuner jars are in `share/plugins`. |
| `desktop/TuxGuitar-audio-engine/pom.xml`, `desktop/TuxGuitar-tuner-next/pom.xml`, `desktop/TuxGuitar-practice/pom.xml` | New module POMs. |

Not changed: the other eight build-script POMs, `desktop/pom.xml`,
`common-resources/common-macosx/Contents/Info.plist`, `desktop/TuxGuitar-tuner`.

## Minimal core changes (with evidence)

| Change | Limitation found in source | Smallest change | Effect on other builds |
|---|---|---|---|
| `TablatureEditor.java`, `Tablature.java`, `TGControl.java` | No plugin API can highlight a beat in the tablature: playback highlight bound to `MidiPlayer`/`TGTransport`; `TGController` has no contribution point; the generic external-beat API is consumed only by fretboard and piano (research R18). | Tablature honours `TGExternalBeatViewerEvent`; `isRunning` true for the external beat while the player is stopped; `TGControl` paints it with the existing played-beat style and scrolls once per change. | Compiled into the shared app jar of every build but inert: nothing in those builds shows an external beat. |
| macOS SWT `.app` launcher | TuxGuitar's bundle executable is the shell script `Contents/MacOS/tuxguitar.sh` (`common-resources/common-macosx/Contents/Info.plist`, `CFBundleExecutable`); with a script executable macOS attributes microphone requests to the hardened-runtime `java` (no audio-input entitlement) and silently returns silence; with a native launcher the bundle is responsible and the permission dialog works (research.md "S1 follow-up"). | Q-L option A (verified): a stub that only starts the unchanged `tuxguitar.sh`, as `CFBundleExecutable` of the SWT build only (P9), plus `NSMicrophoneUsageDescription`. | None: other builds keep their launcher; the shared `common-macosx` resources are not edited (the SWT build overrides after copying). |
| `tuxguitar-shortcuts.xml` | Default bindings come only from this file (`KeyBindingActionDefaults`); plugins cannot add defaults (research R14). | Add 4–5 `<shortcut>` lines for practice action ids. | File is copied into every build; verified inert where the actions are not registered: no errors, never invoked, not shown in the shortcut editor, user files unaffected (research R14, Q-B decided). |

## Implementation phases

Each phase ends with the full build green, existing tests passing, and its observable checks
from [quickstart.md](./quickstart.md) performed and recorded. No phase implements later-phase
behaviour.

### Phase 0 — Spikes (throw-away code, results recorded in research.md)

Status (2026-09-26): S1 done for the built-in/Continuity microphones and the macOS permission
path (multi-channel interface still to validate), S2 done, S4 done on synthetic signals, S3 done
on 22 real clips plus positive live manual validation (research.md "S3 results"). The full
48-clip benchmark is not required before implementation.

| Spike | Question | Blocks |
|---|---|---|
| S1 | macOS microphone capture from the packaged `.app` and from Terminal; effect of `NSMicrophoneUsageDescription`; how denial manifests in Java Sound; mixer names/formats for the built-in mic and a USB interface; channel counts/formats of common two-input interfaces, whether a multi-channel line can be opened and channel k de-interleaved to physical input k (exact limitation reported if not) | Phase 3 |
| S2 | Are `Port` gain controls available for macOS inputs through Java Sound? | Phase 3 (gain wording) |
| S3 | YIN vs McLeod on the synthetic suite + the owner's initial recorded set (interface and built-in mic): octave errors, false acceptance, latency, CPU | Phase 4 |
| S4 | Onset (new articulation) detection on repeated same-pitch plucks, sustained notes, legato | Phase 6 |

### Phase 1 — Practice plugin skeleton and Practice Track selection

- `TuxGuitar-practice` module, services file, plugin info, i18n bundle; macOS SWT build wiring
  for the practice plugin.
- `practice.open` Tools-menu item + `start/skip/resume/stop` actions (mapped, key-bindable,
  sync-thread); default shortcuts in the shared `tuxguitar-shortcuts.xml`, using combinations
  absent from that file (conflict check), with translation keys for the editor labels.
- Practice window with keyboard listeners and the Practice Track step: compatible candidates
  only; preselection (caret track first, guitar program second, advisory); explicit learner
  acceptance required before start; in-memory guitar-confirmation memory per song document and
  track for the run; reason when no candidate exists (FR-003).
- **Acceptance**: menu item and actions present and assignable in Tools › Shortcuts; percussion
  and non-six-string tracks never offered; preselected tracks still need acceptance; nothing
  saved into the song; build green.

### Phase 2 — Practice-event traversal without audio + practice highlight

- `PracticeSequence` over `MidiRepeatController(song,-1,-1)`; classification per data-model;
  start resolution from the caret; `TrackTuning` with offset realization, physical targets and
  the negative-offset "unusual setup" notice (FR-008).
- Session state machine without detection: Skip, auto-pass indications, multi-note stop,
  completion, Restart; pause on playback start (`MidiPlayerEvent`) with explicit Resume; stop on
  song edits and document changes (`TGUpdateEvent` `MEASURE_UPDATED`/`SONG_UPDATED`/
  `SONG_LOADED`); `SELECTION` events never stop or retarget the session.
- Tablature hook (core) + practice highlight via `TGExternalBeatViewerManager`.
- **Acceptance**: fixture tests (repeats, alternate endings, voice overlap, ties, grace, dead,
  harmonic, bend, offsets 0/+2/−1/−8); manual walk-through with Skip only; highlight stays while
  the cursor moves; rendering and playback highlight unchanged with the feature inactive; song
  never marked modified.

### Phase 3 — Audio engine input, level and input health

- `TuxGuitar-audio-engine` module: input (logical per-channel inputs, de-interleaving per S1,
  shared `CurrentInputSelection`, acquire/release), level analysis, assessment, failure
  reporting; macOS SWT packaging of the engine jar in `lib/`, the SWT-only `Info.plist` step
  (`CFBundleExecutable` → stub, `NSMicrophoneUsageDescription`) and the Q-L option A launcher
  stub (built and identifier-signed by the SWT build); verify in `tccd` that the grant is
  attributed to `app.tuxguitar` and survives a rebuild.
- Practice preparation: input/channel selector, level meter, assessment, calibration guidance,
  headphone recommendation, Start disabled while unusable.
- From S1/S2: query each device's advertised formats and open at its native channel count (never
  request stereo from a mono device); prefer 48 kHz/16-bit, accept 24-bit; open the line off
  the UI thread with a "starting input" state (cold open measured at 2.4 s, warm ≈ 0.1 s);
  treat a window of entirely exact-zero samples as "no signal received — check microphone access
  and the input" (not asserted as a denial); no automatic gain — manual guidance only (the only
  gain Java Sound exposes is the global macOS input volume of some devices).
- P2 per-channel inputs remain the intended MVP behaviour and are implemented as designed.
  Validation status: mono capture verified on the tested devices; multi-channel
  de-interleaving and Java Sound channel k → physical input k mapping **not yet verified** (no
  multi-input interface attached). An Aggregate Device may serve as an intermediate technical
  test only; final validation requires a real multi-input USB interface before MVP acceptance.
  If that validation fails, the exact limitation returns to the product owner — no silent
  fallback to channel 1, mixing or strongest-channel selection.
- **Acceptance**: synthetic level and de-interleave tests; manual: interface (each channel
  explicitly) + built-in mic, gain sweep, silent-channel hint without automatic switch, unplug,
  permission behaviour as characterised by S1.

### Phase 4 — Pitch engine and evolved tuner

- Engine: pitch estimation via TarsosDSP `FastYin` (2048-sample window) behind the engine's
  estimator interface; `NoteInterpreter`, `TolerancePolicy`; confidence/stability/tolerance as
  provisional parameters tuned during integration; deterministic synthetic tests plus the 22
  accepted real clips as regression fixtures; octave-error mitigation and other-string
  interference handling as explicit work items.
- `TuxGuitar-tuner-next` plugin (module id `tuxguitar-tuner`, action `tuner.tuner`): chromatic
  and guided modes, presets from `TuningManager`, custom tuning, track tuning per FR-052
  (physical targets), run retention, input/channel selector and level status; preserved
  capabilities (Tools menu, window, Hz readout, fine/rough widgets, clickable strings); settings
  reduced to device/channel, level and recalibration (FR-059). macOS SWT build swaps the legacy
  tuner for it; packaging check for "never both tuners".
- **Acceptance**: engine test suite; benchmark results file; manual tuner scenarios; FR-059
  regression checks; enable/disable preference and tuner shortcut carried over on macOS SWT.

### Phase 5 — Expected-vs-detected evaluation and feedback

- Practice subscribes to the analysis stream; running view shows detected note/cents and the
  four feedback states with octave hint; uncertain on unreliable/suboptimal input. No
  progression yet.
- **Acceptance**: session tests with scripted frames; manual feedback checks; tolerance
  invariant test.

### Phase 6 — Strict progression, articulation, pauses

- Acceptance with stability window; advance; articulation requirement for same-pitch
  consecutive notes using the engine's articulation event (S4: spectral-flux transient AND
  post-vs-pre-maximum level gate; thresholds are tunable parameters selected from the real
  `*_repeat` benchmark recordings — the +1.5 dB synthetic-test value is not a product constant;
  biased toward false negatives per revised FR-022); auto-pause on unusable input;
  ready-to-resume + explicit Resume for every pause cause.
- **Acceptance**: session tests (silence/incorrect/uncertain never advance; repeated notes need
  two onsets; Skip never correct); manual melody practice and cable-pull recovery.

### Phase 7 — Integration, recovery and MVP acceptance

- Tuner from preparation/session (pause, shared input change invalidation), mismatch warning,
  message wording review, non-colour cues, performance checks (300 ms, 30 min), benchmark
  update, product-owner manual acceptance checklist (SC-009) executed and recorded.

## Migration path for other desktop builds (after the MVP)

For each further build (macOS JFX; Windows JFX/SWT; Linux JFX/SWT/Qt; FreeBSD JFX/SWT), as a
separate change:

1. validate audio capture and device/channel behaviour on that OS (S1-style checks);
2. validate the tuner UI on that toolkit;
3. run the FR-059 tuner regression checks;
4. only then switch that build-script POM from `tuxguitar-tuner` to `tuxguitar-tuner-next`
   (+ `tuxguitar-audio-engine` in `lib/`, + microphone metadata where the OS needs it);
5. never claim support before the checks pass. Interactive Practice is enabled on a build by a
   separate, equally validated decision.

When every intended build has switched, `desktop/TuxGuitar-tuner` is removed in a separate
cleanup change.

## Test strategy

| Layer | How | Where |
|---|---|---|
| DSP, level, onset, interpretation, de-interleave | JUnit, synthetic signals, deterministic | engine `src/test/java` |
| Detection quality (SC-003) | recorded WAVs, manifest-driven tagged benchmark, results per input type | engine `src/test/resources/benchmark`, `specs/…/benchmark/` |
| Practice sequence, offsets, eligibility | JUnit on `.tg` fixtures / `TGFactory` songs | practice `test/` |
| Session state machine | JUnit with scripted `AnalysisFrame`s and commands | practice `test/` |
| Tuner mode/tuning logic | JUnit (non-UI parts) | tuner-next `test/` |
| UI, devices, permission, latency, 30-min run, packaging | manual checklist (SC-009), recorded results | quickstart, checklist |

## Major risks

1. macOS microphone permission: verified that a native stub starting `tuxguitar.sh` makes the
   app's launcher the responsible process, enables the prompt and yields real audio after Allow
   (Q-L option A probe); Deny yields silent zeros, never an exception. Remaining risks: the
   launcher's signing identity (to be `app.tuxguitar`), grant persistence across rebuilds, and
   hardened-runtime signing (would need the audio-input entitlement).
2. Per-channel access on multi-input interfaces through Java Sound: not yet verified (no
   interface available); mono devices only advertise mono formats.
3. Detection quality: built-in microphone positively validated live (qualitative); known
   weak areas — octave errors (G3/F#4 cases, pluck-transient subharmonics), sympathetic or
   other ringing strings, low-frequency false readings in silence; direct USB/interface input
   not yet tested.
4. Same-pitch re-articulation: no deterministic detector tested was both miss-free and
   false-free (S4); the chosen gated detector trades weak-re-pluck misses for robustness; a
   strong body knock could still count as an articulation.
5. Latency budget: window for low strings + stability window vs 300 ms (measured in Phases 6/7).
6. Tablature hook must not alter existing rendering or playback highlighting.
7. Two tuner modules in the repository during migration: mitigated by the per-build rule,
   packaging check and distinct Java packages (research R19).

## Specification items found problematic after inspecting the code (all decided)

1. FR-003 — no guitar-identification data → learner-accepted Practice Track (P1, P6).
2. Multi-channel interfaces → explicit per-channel logical inputs; limitation returned if S1
   shows no per-channel access (P2).
3. Track offset → expected pitch unchanged; capo-equivalent / tune-down realization (P3);
   negative-offset "unusual setup" notice from built-in presets (P8).
4. Existing tuner settings → FR-059 capability-based regression (P4).
5. Permission denial may look like silence (FR-063) → wording decided after S1.
6. Input-type classification: only the built-in mic identifiable (FR-036 allows).
7. Automatic gain expected unavailable (FR-041 allows manual guidance only).
8. Tuner rollout → macOS SWT only, legacy tuner elsewhere, migration path (P9).
9. Practice highlight → tablature hook; fretboard/piano also show the beat when open (P7).
10. Benchmark recordings → owner-recorded CC0 set in repository, ≤ 50 MB (P5).

## Product decisions (all decided)

P1 Practice Track selection; P2 per-channel input; P3 offset realization; P4 tuner regression
definition; P5 benchmark assets; P6 preselection/confirmation lifetime/track clicks; P7 separate
practice highlight; P8 negative-offset notice; P9 macOS-SWT-only rollout with migration path.

**Q-L (decided)**: option A — tiny native stub that only invokes the existing `tuxguitar.sh`
(verified by the option A probe); option B and jpackage not needed.

**Q-B (decided)**: practice default shortcuts stay in the shared `tuxguitar-shortcuts.xml`; source
verification (research R14) shows they are inert on builds without the practice plugin. No
macOS-specific build transformation.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| Separate engine library module | Enforces UI-free, testable engine; shared by tuner and practice without plugin-to-plugin coupling | Engine inside a plugin couples practice to a tuner plugin jar and mixes UI and DSP in one module |
| Two tuner modules temporarily | Keep unvalidated builds unchanged (P9) while macOS SWT gets one evolved tuner | Evolving the single module would change the tuner in eight unvalidated builds |
| Three worker threads (capture, analysis, session) | Keep capture non-blocking and state single-writer | Analysis on the capture thread risks overruns; state on the UI thread risks UI stalls |
