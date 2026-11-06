# Feature Specification: Every recognition engine value is an external parameter

**Feature Branch**: `feature/interactive-practice` (no branch of its own; spec directory `specs/026-engine-external-parameters`)

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "spec 026 : toutes les valeurs internes encore codées en dur dans les deux moteurs de reconnaissance (bibliothèque native Rust et moteur Java) deviennent des paramètres externes lus au démarrage, sans recompilation de l'application ni de la bibliothèque native, lus de la même façon par les deux moteurs, chacun documenté (nom, sens, unité, valeur par défaut, bornes, où il se règle) ; la spec 024 a déjà externalisé les valeurs d'accords et de grattage ; aucune valeur par défaut ne change et aucun résultat de reconnaissance ne change (jeux de données identiques avant et après, avec les deux moteurs) ; macOS, Linux et Windows à parité ; règle du propriétaire du 2026-11-05 (ne plus toucher au code des moteurs pour les régler)."

**Where it comes from**: the owner's rule of 2026-11-05: an engine is tuned by its parameters, never by changing its code. Spec 024 made the chord and strum values external parameters and wrote the engines' documentation (`docs/architecture/recognition-engines.md`). Its section 6, "Constants still fixed in the code (left for spec 026)", lists what remains: about 120 values in the Java engine, the native engine and the practice session (onset detection, pitch estimation, polyphonic analysis, attack analysis, chord evidence, clipping, front end and noise gate, gain suggestion, note tolerance, Wait / Follow / Arcade timings). Changing any of them today needs a rebuild of the application and, for the native engine, of the native library.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - I tune an engine value without a rebuild (Priority: P1)

The owner (or a tester) wants to try another value of an engine setting that is fixed today, for example the onset sensitivity or the noise gate margin. They change it, start a run, and the engine uses the new value, with either engine, without rebuilding anything.

**Why this priority**: it is the owner's rule; every later tuning of recognition depends on it.

**Independent Test**: for one value of each family (onset, pitch, polyphonic, attack, chord evidence, clipping, front end, gain suggestion, tolerance, session timing), set a value other than the default, start a run with each engine, and check the engine used it (its effect is seen on a recorded input, or the value is reported in the run's diagnostic).

**Acceptance Scenarios**:

1. **Given** a value that was fixed in the code before this spec, **When** it is changed where parameters are set and a run is started, **Then** the run uses the new value, with the Java engine and with the native engine, and nothing was rebuilt.
2. **Given** a value out of its bounds or unreadable, **When** a run starts, **Then** the default (or the nearest bound) is used, as for the existing recognition settings, and the run is not stopped.
3. **Given** the same parameter values, **When** the same input is judged with each engine, **Then** both engines read the same values (same name, same unit, same default, same bounds).

---

### User Story 2 - Nothing changes for the learner (Priority: P1)

A learner who never touches these values hears and sees no difference: every default stays what it was, and every recognition result is the same as before the change.

**Why this priority**: the change must be safe to release; a refactor that moves a figure is a regression.

**Independent Test**: run every dataset and benchmark test of the branch (owner chords, GuitarSet, IDMT, synthetic chords, recorded practice replays) with each engine before and after; every figure is identical.

**Acceptance Scenarios**:

1. **Given** the default parameters, **When** the dataset reports are run before and after this spec with each engine, **Then** every figure is identical.
2. **Given** a configuration file saved before this spec, **When** the application starts, **Then** it is read without loss, and the new values take their defaults.

---

### User Story 3 - Each parameter is documented (Priority: P2)

The owner reads one document to know every parameter of both engines: name, meaning, unit, default, bounds, where it is set and when it takes effect.

**Why this priority**: a parameter that nobody can understand cannot be tuned.

**Independent Test**: every parameter appears in the engines' documentation with those six facts, and a check fails when a parameter is added without being documented.

**Acceptance Scenarios**:

1. **Given** the engines' documentation, **When** the owner looks up any parameter, **Then** its name, meaning, unit, default, bounds, where it is set and when it takes effect are given.
2. **Given** the list of constants still fixed in the code (section 6 of the documentation), **When** this spec is done, **Then** it holds only values that are not tuning values (buffer sizes and numerical safety margins), each with the reason it is not a parameter.

---

### Edge Cases

- A value that sets a size used to prepare buffers (a window or FFT size, the sample rate): changing it must not make the engine allocate, lock or wait during the analysis; the size is taken when the run opens.
- Two values that must stay ordered (for example an opening margin above a closing margin, a first window before a last one): an inconsistent pair is repaired to a consistent one, as `recognition.note.accept` is kept above `recognition.tuner.in-tune` today.
- The "fast" variants of the onset values (a second set used in fast passages): each is a parameter of its own.
- A device profile (spec 013) overlays some recognition settings: the new parameters are not overlaid unless listed.
- The relaxed values of Follow and Arcade (`FollowLeniency`, `ArcadeLeniency`) are derived from the note and chord settings: they become parameters as well, or stay derived, as the scope answer below says.
- An older native library that does not know the new values: the application falls back to the Java engine, as it does today when the native library does not load.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every value listed in section 6 of `docs/architecture/recognition-engines.md` that changes what the engines or the practice session decide MUST become an external parameter, read when a run starts (or pushed to a running engine as the level values are today), with no rebuild of the application or of the native library. [NEEDS CLARIFICATION: does the scope include the practice session's timings (Wait, Follow, Arcade: verdict hold, neighbour veto, evidence horizons, technique and bend windows, Follow and Arcade relaxed values), or only the two engines' analysis values?]
- **FR-002**: Values that are not tuning values (buffer and ring sizes, numerical safety margins such as epsilons) MAY stay fixed; each one kept fixed MUST be listed in the documentation with the reason.
- **FR-003**: Each parameter MUST have a stable name, a meaning, a unit, a default equal to today's fixed value, and bounds; an unreadable, missing or out-of-bounds value MUST be repaired (default or nearest bound) as the existing recognition settings are.
- **FR-004**: Both engines MUST read the same parameters with the same names, units, defaults and bounds; a value shared by a Java stage and its native port MUST be one parameter, not two.
- **FR-005**: The new parameters MUST be set where the existing recognition settings are kept, so that a value set once is used by both engines. [NEEDS CLARIFICATION: must every new parameter be shown in the Recognition settings window (with its tip in three languages), or only stored in the configuration file and documented, with the window unchanged?]
- **FR-006**: No default MUST change, and with the defaults every recognition result MUST be identical to the results before this spec, with each engine.
- **FR-007**: A configuration file saved before this spec MUST be read without loss; the new parameters take their defaults.
- **FR-008**: The engines' documentation MUST describe every parameter (name, meaning, unit, default, bounds, where it is set, when it takes effect); an automated check MUST fail when a parameter is not documented.
- **FR-009**: Passing the new values to the native engine MUST follow the owner's rule on its interface. [NEEDS CLARIFICATION: may the interface between the application and the native library be extended to carry the new values (it carries 21 values today), and must an older native library be refused, or must the change keep that interface as it is?]
- **FR-010**: Reading a parameter MUST never allocate, lock or wait in the audio callback or the analysis loop; sizes are taken when a run opens.
- **FR-011**: macOS, Linux and Windows MUST behave alike: the same parameters, the same defaults, the same documentation.

### Key Entities

- **Engine parameter**: a named value with meaning, unit, default, bounds, a section (family) and the moment it takes effect (at once, at the next run, when the listening restarts); read identically by both engines.
- **Fixed constant**: a value deliberately kept in the code, with the reason (buffer size, numerical safety), listed in the documentation.
- **Parameter set**: the values in force for a run, built once when the run starts from the stored settings and their defaults.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every tuning value of section 6 of the engines' documentation (within the scope of FR-001) can be changed without a rebuild, and a change is seen in a run with each engine (one value per family checked by an automated test).
- **SC-002**: With the defaults, every dataset and benchmark figure of the branch is identical before and after, with each engine (0 figure changed).
- **SC-003**: 100 % of the parameters are documented with their six facts; the documentation check is green.
- **SC-004**: The list of constants still fixed in the code holds only non-tuning values, each with its reason.
- **SC-005**: A configuration file of the previous release opens with every stored value kept.
- **SC-006**: The full gate of the branch (all tests, both engines, macOS; Linux and Windows builds) stays green.

## Assumptions

- The inventory is section 6 of `docs/architecture/recognition-engines.md` as written by spec 024; the plan re-checks it against the source and adds any value it missed.
- The chord and strum values made external by spec 024 stay as they are.
- The parameters keep the existing store (the TuxGuitar configuration module `tuxguitar-audio-recognition`) and its repair rules; `RecognitionParameters.VERSION` changes only if the plan shows it must.
- No new recording is needed: the datasets and the owner's series already on the machine are the before/after reference.
- The owner's constraints of earlier specs still hold: no GPL code in the native library; no allocation, lock or wait in the audio callback or the analysis loop.
- Making a value a parameter is not a tuning: no value is tuned in this spec.
