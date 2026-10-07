package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LEAD;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.EventResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.PitchResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.LegatoDetector;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.audio.engine.testsupport.TechniqueSignals;

/**
 * Chords and written techniques inside the tempo-following session (spec 002 T090, FR-055): a chord is judged with the {@link ChordMatcher}, a technique
 * is judged by its own judge, and **neither changes the timing verdict** (nor the pitch verdict): the three stay apart. A technique that is not made, a
 * bend above all, never blocks the timeline. The frames come from the real analysis of synthetic audio (plucks, strums, bends, legato steps), with the
 * fake clocks of the harness: no wait. As the onset of real audio is dated by the analysis, the timing is checked with a margin, and not to the millisecond.
 */
public class FollowSessionTechniqueTest {

	private static final int RATE = 48000;
	private static final long LATENCY = ArticulationDetector.DECISION_DELAY_HOPS * 256L * 1_000_000_000L / RATE;
	/** Open E major, string 6 to 1: E2 B2 E3 G#3 B3 E4. */
	private static final int[] E_MAJOR_STRINGS = { 6, 5, 4, 3, 2, 1 };
	private static final int[] E_MAJOR_FRETS = { 0, 2, 2, 1, 0, 0 };
	private static final int[] E_MAJOR_PITCHES = { 40, 47, 52, 56, 59, 64 };

	// ------------------------------------------------------------------ audio helpers

	private static float[] silence(FollowHarness harness) {
		return new float[(int) ((LEAD + harness.timeline.durationNanos() + 3_000 * MS) * RATE / 1_000_000_000L)];
	}

	private static int sampleOf(long wall) {
		return (int) ((wall - T0) * RATE / 1_000_000_000L);
	}

	private static void add(float[] signal, float[] addition, long wall, float gain) {
		int offset = sampleOf(wall);
		for( int i = 0 ; i < addition.length && offset + i < signal.length ; i ++ ) {
			signal[offset + i] += gain * addition[i];
		}
	}

	private static void pluck(float[] signal, long wall, int midi, long seed) {
		float[] note = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(midi), RATE, (int) (0.9 * RATE), seed);
		add(signal, note, wall, 0.3f);
	}

	/** A strum: 10 ms between the strings, from the lowest. */
	private static void strum(float[] signal, long wall, int[] pitches, long seed) {
		for( int i = 0 ; i < pitches.length ; i ++ ) {
			float[] note = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(pitches[i]), RATE, (int) (1.3 * RATE), seed + i);
			add(signal, note, wall + i * 10 * MS, 0.2f);
		}
	}

	private static List<AnalysisFrame> analyse(FollowHarness harness, float[] signal) {
		return OfflineAnalysis.analyze(signal, RATE, RecognitionSettings.defaults(), true, AttackCandidates.of(harness.sequence));
	}

	private static FollowHarness harness(SongFixture fixture) {
		FollowHarness harness = new FollowHarness(fixture, 100, LATENCY);
		harness.session.setPolyphonicAcceptance(() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		harness.session.start();
		return harness;
	}

	private static EventResult result(FollowHarness harness, int index) {
		return harness.last.results().get(index);
	}

	/** What a performance gave, event by event, without the techniques: status, pitch and timing (and the offset to the 5 ms). */
	private static String verdicts(FollowHarness harness) {
		StringBuilder sb = new StringBuilder();
		for( EventResult r : harness.last.results() ) {
			sb.append(r.status()).append('/').append(r.pitch()).append('/').append(r.timing()).append('/').append(Math.round(r.offsetNanos() / (5.0 * MS))).append(' ');
		}
		return sb.toString().trim();
	}

	private static TechniqueResult technique(EventResult result, PlayingTechnique technique) {
		for( TechniqueResult t : result.techniques() ) {
			if( t.getTechnique() == technique ) {
				return t;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ chords

	/** An E major chord on the beat, then two single notes. */
	private static SongFixture chordThenTwoNotes() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, E_MAJOR_STRINGS, E_MAJOR_FRETS);
		fixture.quarter(0, 1, 1, 1);
		fixture.quarter(0, 2, 1, 3);
		return fixture;
	}

	@Test
	public void aChordStrummedOnTheBeatIsMatchedByTheChordMatcherAndTimed() {
		FollowHarness harness = harness(chordThenTwoNotes());
		float[] signal = silence(harness);
		strum(signal, harness.target(0), E_MAJOR_PITCHES, 11L);
		pluck(signal, harness.target(1), 65, 40L);
		pluck(signal, harness.target(2), 67, 41L);
		harness.feed(analyse(harness, signal));

		EventResult chord = result(harness, 0);
		assertEquals(Status.MATCHED, chord.status());
		assertEquals(PitchResult.CORRECT, chord.pitch());
		assertEquals(TimingVerdict.ON_TIME, chord.timing());
		assertTrue(Math.abs(chord.offsetNanos()) <= 60 * MS, "the offset of the strum is that of its first string: " + chord.offsetNanos() / MS + " ms");
		assertEquals(Status.MATCHED, result(harness, 1).status());
		assertEquals(Status.MATCHED, result(harness, 2).status());
	}

	@Test
	public void aChordStrummedLateIsLateAndStillMatched() {
		FollowHarness harness = harness(chordThenTwoNotes());
		float[] signal = silence(harness);
		strum(signal, harness.target(0) + 150 * MS, E_MAJOR_PITCHES, 11L);
		pluck(signal, harness.target(1), 65, 40L);
		harness.feed(analyse(harness, signal));

		assertEquals(Status.MATCHED, result(harness, 0).status());
		assertEquals(PitchResult.CORRECT, result(harness, 0).pitch());
		assertEquals(TimingVerdict.LATE, result(harness, 0).timing());
	}

	@Test
	public void aChordWithAStringMissingIsAWrongChordWithItsMissingPitchAndItsTiming() {
		FollowHarness harness = harness(chordThenTwoNotes());
		float[] signal = silence(harness);
		strum(signal, harness.target(0), new int[] { 40, 47, 52, 59, 64 }, 11L);          // the G#3 is missing
		pluck(signal, harness.target(1), 65, 40L);
		pluck(signal, harness.target(2), 67, 41L);
		harness.feed(analyse(harness, signal));

		EventResult chord = result(harness, 0);
		assertEquals(Status.WRONG_PITCH, chord.status());
		assertEquals(PitchResult.WRONG, chord.pitch());
		assertEquals(TimingVerdict.ON_TIME, chord.timing(), "the timing of the strum is told apart from its pitch");
		assertTrue(java.util.Arrays.stream(chord.missingPitches()).anyMatch(p -> p == 56), "the missing G#3 is named");
		assertEquals(Status.MATCHED, result(harness, 1).status(), "the next notes are not moved by the wrong chord");
		assertEquals(Status.MATCHED, result(harness, 2).status());
	}

	@Test
	public void aChordThatIsNotPlayedIsMissedAndNotWrong() {
		FollowHarness harness = harness(chordThenTwoNotes());
		float[] signal = silence(harness);
		pluck(signal, harness.target(1), 65, 40L);
		pluck(signal, harness.target(2), 67, 41L);
		harness.feed(analyse(harness, signal));

		assertEquals(Status.MISSED, result(harness, 0).status());
		assertEquals(PitchResult.NONE, result(harness, 0).pitch());
		assertEquals(Status.MATCHED, result(harness, 1).status());
	}

	// ------------------------------------------------------------------ a technique never changes the timing or the pitch

	/** A whole-tone bend of D4 on the beat, then two plain notes. */
	private static SongFixture bendThenTwoNotes() {
		SongFixture fixture = new SongFixture(1);
		fixture.bend(0, 0, 3, 7, 2);
		fixture.quarter(0, 1, 3, 12);
		fixture.quarter(0, 2, 3, 14);
		return fixture;
	}

	/** D4 from the beat, bent to E4 ({@code bent}) or held, for 0.45 s; then G4 and A4 plucked on their beats. */
	private static float[] bendPerformance(FollowHarness harness, boolean bent) {
		float[] signal = silence(harness);
		float[] tone = (bent
				? TechniqueSignals.bend(62, RATE, (int) (0.45 * RATE), 0.0, 0.0, 0.1, 0.0, 0.30, 2.0)
				: TechniqueSignals.steadyNote(62, RATE, (int) (0.45 * RATE)));
		add(signal, tone, harness.target(0), 1.0f);
		pluck(signal, harness.target(1), 67, 40L);
		pluck(signal, harness.target(2), 69, 41L);
		return signal;
	}

	private static FollowHarness bendRun(boolean bent, TechniqueJudge judge) {
		FollowHarness harness = harness(bendThenTwoNotes());
		if( judge != null ) {
			harness.session.setTechniqueJudge(judge);
		}
		harness.feed(analyse(harness, bendPerformance(harness, bent)));
		return harness;
	}

	@Test
	public void theTechniqueResultNeverChangesTheTimingNorThePitchVerdict() {
		TechniqueJudge bendVerified = new TechniqueJudge(TechniqueCatalog.of(new BendDetector(BendDetector.Parameters.defaults()), true, "test"), true);
		FollowHarness without = bendRun(true, null);
		FollowHarness detected = bendRun(true, bendVerified);
		FollowHarness notMade = bendRun(false, bendVerified);

		// the three performances have the same pitch and timing verdicts on every event, whatever the technique said
		assertEquals(verdicts(without), verdicts(detected));
		assertEquals(verdicts(without), verdicts(notMade));
		assertEquals(Status.MATCHED, result(detected, 0).status());
		assertEquals(TimingVerdict.ON_TIME, result(detected, 0).timing());

		// the technique results are what differ: made, not made, and not judged at all
		TechniqueResult made = technique(result(detected, 0), PlayingTechnique.BEND);
		TechniqueResult missing = technique(result(notMade, 0), PlayingTechnique.BEND);
		assertEquals(TechniqueVerdict.DETECTED, made.getVerdict());
		assertEquals(TechniqueVerdict.NOT_DETECTED, missing.getVerdict());
		assertTrue(result(without, 0).techniques().isEmpty(), "without a judge no technique result is given");
	}

	@Test
	public void aBendThatIsNotMadeNeverBlocksTheTimeline() {
		TechniqueJudge bendVerified = new TechniqueJudge(TechniqueCatalog.of(new BendDetector(BendDetector.Parameters.defaults()), true, "test"), true);
		FollowHarness harness = bendRun(false, bendVerified);

		// the bend is not made: its note is judged on the base pitch and on its time, the following notes on theirs, nothing waits
		assertEquals(Status.MATCHED, result(harness, 0).status());
		assertEquals(Status.MATCHED, result(harness, 1).status());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 1).timing());
		assertEquals(Status.MATCHED, result(harness, 2).status());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 2).timing());
		assertEquals(FollowSession.State.COMPLETE, harness.last.state());
	}

	@Test
	public void aBendMadeSlowlyOrTooFarDoesNotMoveTheNextNotes() {
		FollowHarness harness = harness(bendThenTwoNotes());
		// a slow bend that is still under way when its window closes, and goes past its target; the next notes are played on their beats
		harness.playMoving(harness.target(0), 450 * MS, seconds -> 62.0 + 2.5 * Math.min(1.0, Math.max(0.0, (seconds - 0.2) / 0.24)));
		harness.play(harness.target(1), 67, 400 * MS);
		harness.play(harness.target(2), 69, 400 * MS);
		harness.runAll();

		assertEquals(Status.MATCHED, result(harness, 0).status());
		assertEquals(Status.MATCHED, result(harness, 1).status());
		assertEquals(Status.MATCHED, result(harness, 2).status());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 1).timing(), "offset " + result(harness, 1).offsetNanos() / MS + " ms");
		assertEquals(TimingVerdict.ON_TIME, result(harness, 2).timing());
	}

	// ------------------------------------------------------------------ hammer-on and pull-off: the arrival note has no attack

	private static SongFixture hammerOnThenNote() {
		SongFixture fixture = new SongFixture(1);
		fixture.legatoPair(0, 0, 2, 5, 7);          // E4 hammered onto F#4
		fixture.quarter(0, 2, 2, 8);                // G4, picked
		return fixture;
	}

	@Test
	public void theNoteAHammerOnArrivesOnIsMatchedWithoutAnAttackAndTimedFromItsPitchStep() {
		FollowHarness harness = harness(hammerOnThenNote());
		float[] signal = silence(harness);
		// E4 picked on the beat, then the pitch steps to F#4 on the next beat with no new attack
		add(signal, TechniqueSignals.legatoStep(64, 66, RATE, 0.5, (int) (0.95 * RATE)), harness.target(0), 1.0f);
		pluck(signal, harness.target(2), 67, 40L);
		harness.feed(analyse(harness, signal));

		assertEquals(Status.MATCHED, result(harness, 0).status());
		EventResult arrival = result(harness, 1);
		assertEquals(Status.MATCHED, arrival.status(), "the arrival note of a hammer-on has no attack: it is heard from its pitch");
		assertEquals(PitchResult.CORRECT, arrival.pitch());
		assertEquals(TimingVerdict.ON_TIME, arrival.timing());
		assertTrue(Math.abs(arrival.offsetNanos()) <= 75 * MS, "timed from the pitch step: " + arrival.offsetNanos() / MS + " ms");
		assertEquals(0, harness.last.counts().extra(), "the step is not an extra note");
		assertEquals(Status.MATCHED, result(harness, 2).status());
	}

	@Test
	public void aHammerOnAndTheSameTwoNotesPickedHaveTheSameTimingVerdicts() {
		TechniqueJudge legatoVerified = new TechniqueJudge(TechniqueCatalog.of(new LegatoDetector(LegatoDetector.Parameters.defaults()), true, "test"), true);
		FollowHarness legato = harness(hammerOnThenNote());
		legato.session.setTechniqueJudge(legatoVerified);
		float[] legatoSignal = silence(legato);
		add(legatoSignal, TechniqueSignals.legatoStep(64, 66, RATE, 0.5, (int) (0.95 * RATE)), legato.target(0), 1.0f);
		pluck(legatoSignal, legato.target(2), 67, 40L);
		legato.feed(analyse(legato, legatoSignal));

		FollowHarness picked = harness(hammerOnThenNote());
		picked.session.setTechniqueJudge(legatoVerified);
		float[] pickedSignal = silence(picked);
		pluck(pickedSignal, picked.target(0), 64, 12L);
		pluck(pickedSignal, picked.target(1), 66, 13L);
		pluck(pickedSignal, picked.target(2), 67, 40L);
		picked.feed(analyse(picked, pickedSignal));

		// both are matched on time on the three events: how the second note was made is the technique's business, not the timing's
		for( int i = 0 ; i < 3 ; i ++ ) {
			assertEquals(Status.MATCHED, result(legato, i).status(), "legato event " + i);
			assertEquals(Status.MATCHED, result(picked, i).status(), "picked event " + i);
			assertEquals(TimingVerdict.ON_TIME, result(legato, i).timing(), "legato event " + i);
			assertEquals(TimingVerdict.ON_TIME, result(picked, i).timing(), "picked event " + i);
		}
		TechniqueResult heard = technique(result(legato, 1), PlayingTechnique.HAMMER_ON);
		TechniqueResult plain = technique(result(picked, 1), PlayingTechnique.HAMMER_ON);
		assertNotEquals(heard.getVerdict(), plain.getVerdict(), "the technique tells the legato from the picked notes");
	}

	// ------------------------------------------------------------------ techniques that are not verified, and a chord with a technique

	@Test
	public void aTechniqueWithoutAVerifiedDetectorIsNotVerifiableAndChangesNothing() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 2, 5).getEffect().setVibrato(true);
		fixture.quarter(0, 1, 2, 7);
		FollowHarness harness = harness(fixture);
		harness.session.setTechniqueJudge(new TechniqueJudge(TechniqueCatalog.defaults(), true));
		float[] signal = silence(harness);
		pluck(signal, harness.target(0), 64, 12L);
		pluck(signal, harness.target(1), 66, 13L);
		harness.feed(analyse(harness, signal));

		assertEquals(Status.MATCHED, result(harness, 0).status());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 0).timing());
		assertEquals(TechniqueVerdict.NOT_VERIFIABLE, technique(result(harness, 0), PlayingTechnique.VIBRATO).getVerdict());
		assertEquals(Status.MATCHED, result(harness, 1).status());
	}

	@Test
	public void aChordAndABendInTheSamePassageAreJudgedEachOnItsOwn() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, E_MAJOR_STRINGS, E_MAJOR_FRETS);
		fixture.bend(0, 1, 3, 7, 2);
		fixture.quarter(0, 2, 3, 12);
		FollowHarness harness = harness(fixture);
		harness.session.setTechniqueJudge(new TechniqueJudge(TechniqueCatalog.of(new BendDetector(BendDetector.Parameters.defaults()), true, "test"), true));
		float[] signal = silence(harness);
		strum(signal, harness.target(0), E_MAJOR_PITCHES, 11L);
		add(signal, TechniqueSignals.bend(62, RATE, (int) (0.45 * RATE), 0.0, 0.0, 0.1, 0.0, 0.30, 2.0), harness.target(1), 1.0f);
		pluck(signal, harness.target(2), 67, 40L);
		harness.feed(analyse(harness, signal));

		assertEquals(Status.MATCHED, result(harness, 0).status());
		assertEquals(Status.MATCHED, result(harness, 1).status());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 1).timing());
		assertEquals(TechniqueVerdict.DETECTED, technique(result(harness, 1), PlayingTechnique.BEND).getVerdict());
		assertTrue(result(harness, 0).techniques().isEmpty(), "a chord without a written technique has no technique result");
		assertFalse(harness.last.results().isEmpty());
	}
}
