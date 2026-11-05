package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.HOP;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LATENCY;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.EventResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.song.models.TGDuration;

/**
 * Follow (and Arcade, which runs the same session) with the chord rule by profile (spec 024, contracts/chord-profile-rule.md, task T012), on
 * synthetic frames every 5 ms: one profile per chord candidate of an onset, summed from its transient, a new onset with new profiles, the tolerated
 * pitches, the weakest names of a wrong attempt and the guarded attack path. Two windows never overlap (TimingTolerance), so an onset has one chord
 * candidate at most: two chords in a row, and two onsets for one chord, show that each onset keeps its own profile.
 */
public class FollowSessionChordProfileTest {

	private static final int[] STRINGS = { 5, 4, 2 };
	private static final int[] FRETS = { 0, 2, 1 };
	/** A minor: A2 E3 C4. */
	private static final int[] A_MINOR = { 45, 52, 60 };
	/** G major, loud and wrong: G2 B2 D3 G3 B3 G4. */
	private static final int[] G_MAJOR = { 43, 47, 50, 55, 59, 67 };

	private static ChordMatcher.Parameters rule(boolean attack) {
		return new ChordMatcher.Parameters(2, 1.0 / 3.0, 8, 600 * MS, RecognitionParameters.COMPLETENESS_PROFILE, 0.79, 0.40, 84, 0L, attack);
	}

	private static final class Run {
		final FollowHarness harness;
		long now = T0;

		Run(SongFixture fixture, ChordMatcher.Parameters chord, boolean withAttack) {
			this.harness = new FollowHarness(fixture, 100, LATENCY, chord);
			if( withAttack ) {
				this.harness.session.setPolyphonicAcceptance(() -> new PolyphonicAcceptance(new PolyphonicAcceptance.Parameters(0.0, 0.0, 0.0, 1, 0.0, 0L)));
			}
			this.harness.session.start();
		}

		long target(int index) {
			return this.harness.target(index);
		}

		/** One frame at the current wall time, then the clock moves one hop. */
		void frame(SpectrumFrame spectrum, boolean onset, AttackEvidence evidence) {
			this.harness.last = this.harness.session.onFrame(new AnalysisFrame(this.now - T0, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, Double.NaN, 0.0,
					onset, spectrum, null, null, evidence));
			this.now += HOP;
		}

		/** Silent frames up to the wall time (exclusive). */
		void silenceUntil(long wall) {
			while( this.now < wall ) {
				frame(null, false, null);
			}
		}

		/** A sound from the current wall time up to {@code wall}: its onset reported {@link FollowHarness#LATENCY} after its first frame when asked. */
		void soundUntil(long wall, SpectrumFrame spectrum, boolean reported) {
			long start = this.now;
			while( this.now < wall ) {
				frame(spectrum, reported && this.now == start + LATENCY, null);
			}
		}

		EventResult result(int index) {
			return this.harness.last.results().get(index);
		}
	}

	private static SongFixture twoChords() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, STRINGS, FRETS);
		fixture.chord(0, 2, STRINGS, FRETS);
		return fixture;
	}

	@Test
	public void aChordCandidateIsMatchedWithTheProfileRule() {
		// quiet: the profile rule judges the names, not the level of each string
		Run run = new Run(twoChords(), rule(true), false);
		run.silenceUntil(run.target(0));
		run.soundUntil(run.target(0) + 300 * MS, ChordProfileTest.frame(-50, A_MINOR), true);
		assertEquals(Status.MATCHED, run.result(0).status());
		assertEquals(FollowSession.PitchResult.CORRECT, run.result(0).pitch());
		assertEquals(TimingVerdict.ON_TIME, run.result(0).timing());
	}

	@Test
	public void eachOnsetKeepsItsOwnProfile() {
		// two chords in a row: the loud G major of the first does not reach the profile of the second
		Run run = new Run(twoChords(), rule(true), false);
		run.silenceUntil(run.target(0));
		run.soundUntil(run.target(0) + 900 * MS, ChordProfileTest.frame(-5, G_MAJOR), true);
		assertEquals(Status.WRONG_PITCH, run.result(0).status());
		run.silenceUntil(run.target(1));
		run.soundUntil(run.target(1) + 300 * MS, ChordProfileTest.frame(-45, A_MINOR), true);
		assertEquals(Status.MATCHED, run.result(1).status(), "the second chord's onset starts new profiles");
	}

	@Test
	public void aNewOnsetForTheSameChordStartsANewProfile() {
		// a loud wrong sound reported 60 ms early, then the right chord 40 ms late: the second onset matches, from its own transient
		Run run = new Run(twoChords(), rule(true), false);
		run.silenceUntil(run.target(0) - 60 * MS);
		run.soundUntil(run.target(0) + 40 * MS, ChordProfileTest.frame(-5, G_MAJOR), true);
		run.soundUntil(run.target(0) + 400 * MS, ChordProfileTest.frame(-45, A_MINOR), true);
		EventResult chord = run.result(0);
		assertEquals(Status.MATCHED, chord.status());
		assertEquals(40 * MS, chord.offsetNanos(), 2 * MS, "the time is that of the second onset");
	}

	@Test
	public void aCandidateWithAToleratedPitchIsMatchedWhetherItRingsOrHasFaded() {
		for( boolean rings : new boolean[] { true, false } ) {
			SongFixture fixture = new SongFixture(1);
			fixture.note(fixture.track(), 0, 0, 1, 6, 1, TGDuration.WHOLE);
			fixture.chord(0, 1, STRINGS, FRETS);
			Run run = new Run(fixture, rule(true), false);
			PracticeEvent chord = run.harness.sequence.getEvents().get(1);
			assertEquals(PracticeEvent.Kind.EXPECTED_CHORD, chord.getKind());
			assertArrayEquals(new int[] { 41 }, chord.getToleratedPitches());
			run.silenceUntil(run.target(1));
			SpectrumFrame spectrum = (rings ? ChordProfileTest.frame(new double[] { -20, 45, 52, 60 }, new double[] { -8, 41 }) : ChordProfileTest.frame(-20, A_MINOR));
			run.soundUntil(run.target(1) + 300 * MS, spectrum, true);
			assertEquals(Status.MATCHED, run.result(1).status(), rings ? "ringing" : "faded");
		}
	}

	@Test
	public void aWrongAttemptCarriesThePitchesOfTheWeakestNames() {
		Run run = new Run(twoChords(), rule(true), false);
		run.silenceUntil(run.target(0));
		// A and B only: E and C are missing
		run.soundUntil(run.target(0) + 900 * MS, ChordProfileTest.frame(-20, 45, 47), true);
		EventResult chord = run.result(0);
		assertEquals(Status.WRONG_PITCH, chord.status());
		assertArrayEquals(new int[] { 52, 60 }, chord.missingPitches());
	}

	@Test
	public void anOnsetWithNoSpectrumAtAllIsJudgedAsANullSpectrum() {
		// an onset reported with no spectrum: its profile summed nothing, so the guard allows the attack as for a frame with no spectrum
		Run run = new Run(twoChords(), rule(true), true);
		run.silenceUntil(run.target(0));
		long start = run.now;
		while( run.now < run.target(0) + 120 * MS ) {
			run.frame(null, run.now == start + LATENCY, run.now > start + LATENCY ? PracticeSessionChordProfileTest.evidence(A_MINOR) : null);
		}
		assertEquals(Status.MATCHED, run.result(0).status());
	}

	@Test
	public void singleNoteCandidatesAreUnchanged() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 5, 0);
		FollowHarness harness = new FollowHarness(fixture, 100, LATENCY, rule(false));
		harness.session.start();
		harness.playEvent(0, 0);
		harness.runAll();
		assertEquals(Status.MATCHED, harness.last.results().get(0).status());
	}

	@Test
	public void theAttackPathForChordsFollowsItsFlagAndItsGuard() {
		// on, the right spectrum, no onset reported at all: the guard reads the attack's own frame and the chord is matched
		Run on = new Run(twoChords(), rule(true), true);
		on.silenceUntil(on.target(0));
		on.frame(ChordProfileTest.frame(-20, A_MINOR), false, PracticeSessionChordProfileTest.evidence(A_MINOR));
		assertEquals(Status.MATCHED, on.result(0).status());
		// on, a spectrum with two weak names: refused, whether a strum is armed or not
		for( boolean reported : new boolean[] { true, false } ) {
			Run guarded = new Run(twoChords(), rule(true), true);
			guarded.silenceUntil(guarded.target(0));
			long start = guarded.now;
			while( guarded.now < guarded.target(0) + 120 * MS ) {
				guarded.frame(ChordProfileTest.frame(-20, 45), reported && guarded.now == start + LATENCY, PracticeSessionChordProfileTest.evidence(A_MINOR));
			}
			assertNotEquals(Status.MATCHED, guarded.result(0).status(), reported ? "armed" : "not armed");
		}
		// off: the attack path matches no chord; the profile still does, from its onset
		Run off = new Run(twoChords(), rule(false), true);
		off.silenceUntil(off.target(0));
		off.frame(ChordProfileTest.frame(-20, A_MINOR), false, PracticeSessionChordProfileTest.evidence(A_MINOR));
		assertEquals(Status.PENDING, off.result(0).status());
		off.soundUntil(off.target(0) + 300 * MS, ChordProfileTest.frame(-20, A_MINOR), true);
		assertEquals(Status.MATCHED, off.result(0).status());
		// off for chords only: a single note is still matched on its attack
		SongFixture note = new SongFixture(1);
		note.quarter(0, 0, 5, 0);
		Run single = new Run(note, rule(false), true);
		single.silenceUntil(single.target(0));
		single.frame(null, false, PracticeSessionChordProfileTest.evidence(45));
		assertTrue(single.result(0).status() == Status.MATCHED, "a single note on its attack: " + single.result(0).status());
	}
}
