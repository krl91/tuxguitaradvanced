# Quickstart: validating Interactive Practice MVP

**Plan**: [plan.md](./plan.md). Validation target: macOS Apple Silicon, SWT Cocoa, Temurin 21.
Nothing here is claimed as passing until it has actually been run and recorded.

## Prerequisites

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
# SWT 4.37 installed into the local Maven repo (see INSTALL.md)
```

## Build (every phase must leave this green)

```sh
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
mvn -e clean verify -P native-modules
```

Local run of the development `.app` needs `Contents/MacOS/jre` linked to the local JDK (local
workaround only, see `CLAUDE.md`).

## Automated tests

Run from the build-script directory so sibling SNAPSHOT modules resolve:

```sh
# engine (library module)
mvn test -pl ../../TuxGuitar-audio-engine -am -Dsurefire.failIfNoSpecifiedTests=false
# evolved tuner (non-UI logic)
mvn test -pl ../../TuxGuitar-tuner-next -am -Dsurefire.failIfNoSpecifiedTests=false
# practice model/session
mvn test -pl ../../TuxGuitar-practice -am -Dsurefire.failIfNoSpecifiedTests=false
# recorded detection benchmark (explicit, not part of the default build) — Phase 4+
mvn test -pl ../../TuxGuitar-audio-engine -am -Dgroups=benchmark -DexcludedGroups= -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: existing tests unchanged and passing (SC-008); new unit tests passing; benchmark
writes a results file under `specs/001-interactive-practice-mvp/benchmark/` (SC-003).

## Manual scenarios (per phase; full list = SC-009 checklist)

| After phase | Scenario | Expected |
|---|---|---|
| 1 | Tools › Interactive Practice… with the cursor on a guitar track / on a percussion track | cursor track preselected / percussion never offered, a guitar-program track preselected instead if any |
| 1 | Preselected guitar-program track | practice cannot start until the learner explicitly accepts it |
| 1 | Accept a six-string track with a non-guitar program; stop and start again; reopen the file | no extra confirmation in this run; song not modified; asked again after reopening the file or relaunching |
| 1 | Tools › Shortcuts | practice actions listed and assignable |
| 1 | A build without the practice plugin (e.g. Linux SWT) from the same branch: start, open Tools › Shortcuts, save, press the practice default keys | no errors; no practice rows; keys do nothing (or their existing action); saved `shortcuts.xml` contains no practice ids |
| 2 | Start without audio on a song with repeats, a chord, a bend, dead notes; press Skip repeatedly | caret walks events in playback order; chord waits for Skip; bend/dead pass with brief notice; completion + Restart returns to original start |
| 2 | Start playback during practice; edit a note during practice | practice pauses then needs Resume / practice stops |
| 2 | Standard-tuned tracks with offset −8 and +15 | −8: targets below built-in presets → non-blocking "unusual setup" notice, practice still starts; +15: "capo at fret 15", no notice; expected pitches unchanged |
| 2 | Tracks with offset 0, +2 and −1 | preparation shows "play in this tuning" / "capo at fret 2" / "tune down 1 semitone" with the raw offset; expected notes match TuxGuitar playback pitches in all three |
| 2 | During practice, click another track and move the cursor | session continues on the same Practice Track and event; practice highlight stays on the event, separate from the cursor |
| 2 | Feature inactive; then normal playback | tablature rendering and playback highlight identical to before (hook inert) |
| 3 | Choose interface input, then built-in mic; turn gain from zero to clipping | level states change; headphone notice for mic/unknown; unusable blocks Start; suboptimal warns |
| 3 | Unplug interface during preparation | explained, TuxGuitar responsive, other input selectable |
| 3 | Two-input interface, guitar on Input 2; select Input 1, then Input 2 | Input 1: no signal + "try another channel" hint, no automatic switch; Input 2: level check on that channel only; tuner uses the same selection |
| 4 | Tuner opened from preparation for a −1 offset track | targets = open strings lowered by 1 semitone, labelled as the required down-tuning; song unchanged |
| 4 | macOS SWT package contents | `share/plugins` contains `tuxguitar-tuner-next` and not the legacy `tuxguitar-tuner`; `lib/` contains `tuxguitar-audio-engine`; packaged `Info.plist` has the microphone usage text; a previously disabled tuner stays disabled and a tuner shortcut still works |
| 4 | Any other build (e.g. Linux SWT) built from the same branch | still packages the legacy tuner unchanged; no practice plugin, no engine jar |
| 4 | FR-059 regression: Tools › tuner, play, open its settings | same window; live pitch + Hz; fine/rough widgets; clickable strings; settings = device/channel, level, recalibration only (no FFT/buffer/sample/noise-gate fields) |
| 4 | Tuner with no song: chromatic, then guided Standard E / Drop D / custom | correct note/octave/cents; presets and custom work; no song change |
| 4 | Standalone tuner with an unconfirmed Drop D track selected, then after that track was used as Practice Track | first: stays on Standard E (program shown only as hint); then: Drop D offered "from track"; an explicit choice persists until relaunch |
| 5 | Practice: play expected, neighbour semitone, wrong octave, slightly sharp | correct / incorrect / incorrect+octave hint / correct with sharp shown — feedback only (no advance yet in phase 5) |
| 6 | Practice a melody with a repeated note | wait-for-correct progression; repeated note needs a second pluck; Skip never marks correct |
| 6 | Pull the cable mid-session | auto-pause; after reconnection and check → "ready to resume"; only Resume continues |
| 7 | Open tuner from preparation and during a session; change input in tuner | same tuner; session paused; input change applies to both and needs a new check |
| 7 | 30-minute session; response timing | responsive, no crash (SC-007); perceived ≤ 300 ms recorded (SC-002) |
