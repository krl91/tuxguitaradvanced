package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.HOP;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LATENCY;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.EventResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.State;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;

/**
 * Why an event of a Follow run was not accepted (spec 016 FR-001, contracts/refusal-reasons.md): one case per row of the contract, through the
 * harness of the session. At 120 beats a minute the NORMAL preset has an outer window of 200 ms each side of the target, and the quarters are 500 ms
 * apart: a note 250 ms from its target lies between two windows.
 */
public class RefusalReasonTest {

	/** Five quarter notes of 64, 65, 66, 67 (measure 0) and 68 (measure 1), on the first string. */
	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	/** Five quarter notes where the third and the fourth have the same pitch (66). */
	private static SongFixture repeatedThirdNote() {
		SongFixture fixture = new SongFixture(2);
		int[] frets = { 0, 1, 2, 2 };
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, frets[beat]);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	private static FollowHarness started(SongFixture fixture) {
		FollowHarness harness = new FollowHarness(fixture, 100);
		assertEquals(State.COUNT_IN, harness.session.start());
		return harness;
	}

	private static EventResult result(FollowHarness harness, int index) {
		return harness.last.results().get(index);
	}

	@Test
	public void anEventAcceptedOnTimeEarlyOrLateHasNoReason() {
		FollowHarness harness = started(fiveNotes());
		harness.playEvent(0, 0);
		harness.playEvent(1, -150);
		harness.playEvent(2, 150);
		harness.playEvent(3, 0);
		harness.playEvent(4, 0);
		harness.runAll();
		for (int i = 0; i < 5; i++) {
			assertEquals(Status.MATCHED, result(harness, i).status(), "event " + i);
			assertEquals(RefusalReason.NONE, result(harness, i).reason(), "event " + i);
		}
	}

	@Test
	public void anEventWithNothingPlayedHasNoAttackHeard() {
		FollowHarness harness = started(fiveNotes());
		for (int i : new int[] { 0, 1, 3, 4 }) {
			harness.playEvent(i, 0);
		}
		harness.runAll();
		assertEquals(Status.MISSED, result(harness, 2).status());
		assertEquals(RefusalReason.NO_ATTACK, result(harness, 2).reason());
	}

	@Test
	public void aWrongNoteNamesThePitchHeard() {
		FollowHarness harness = started(fiveNotes());
		for (int i : new int[] { 0, 1, 3, 4 }) {
			harness.playEvent(i, 0);
		}
		harness.play(harness.target(2), 60, 400 * MS);
		harness.runAll();
		assertEquals(Status.WRONG_PITCH, result(harness, 2).status());
		assertEquals(RefusalReason.OTHER_PITCH, result(harness, 2).reason());
		assertEquals(60L, result(harness, 2).reasonDetail(), "the pitch heard");
	}

	@Test
	public void theRightNotePlayedAfterTheWindowIsLateByHowMuch() {
		FollowHarness harness = started(fiveNotes());
		for (int i : new int[] { 0, 1, 3, 4 }) {
			harness.playEvent(i, 0);
		}
		harness.play(harness.target(2) + 250 * MS, 66, 200 * MS);
		harness.runAll();
		assertEquals(Status.MISSED, result(harness, 2).status());
		assertEquals(RefusalReason.LATE_OUTSIDE, result(harness, 2).reason());
		assertEquals(250L, result(harness, 2).reasonDetail(), "ms after the target");
	}

	@Test
	public void theRightNotePlayedBeforeTheWindowIsEarlyByHowMuch() {
		FollowHarness harness = started(fiveNotes());
		for (int i : new int[] { 0, 1, 3, 4 }) {
			harness.playEvent(i, 0);
		}
		harness.play(harness.target(2) - 250 * MS, 66, 200 * MS);
		harness.runAll();
		assertEquals(Status.MISSED, result(harness, 2).status());
		assertEquals(RefusalReason.EARLY_OUTSIDE, result(harness, 2).reason());
		assertEquals(-250L, result(harness, 2).reasonDetail(), "ms before the target");
	}

	@Test
	public void anAttackMatchedByAnotherEventIsNotUsedForThisOne() {
		FollowHarness harness = started(repeatedThirdNote());
		for (int i : new int[] { 0, 1, 4 }) {
			harness.playEvent(i, 0);
		}
		// the 66 of event 3, early but inside its own window, lies 300 ms after the target of event 2: it is event 3's, not a late event 2
		harness.playEvent(3, -200);
		harness.runAll();
		assertEquals(Status.MATCHED, result(harness, 3).status());
		assertEquals(Status.MISSED, result(harness, 2).status());
		assertEquals(RefusalReason.NO_ATTACK, result(harness, 2).reason());
	}

	@Test
	public void aRightNoteFartherThanTheNextEventIsNotLookedFor() {
		FollowHarness harness = started(fiveNotes());
		for (int i : new int[] { 0, 1, 3, 4 }) {
			harness.playEvent(i, 0);
		}
		// 66 heard 700 ms after the target of event 2: after the target of event 3, out of the search span of event 2
		harness.play(harness.target(2) + 700 * MS, 66, 150 * MS);
		harness.runAll();
		assertEquals(RefusalReason.NO_ATTACK, result(harness, 2).reason());
	}

	@Test
	public void aSoundHeardInTheWindowWithoutAClearPitchIsNotClearEnoughNeverWrong() {
		FollowHarness harness = started(fiveNotes());
		List<Sound> sounds = new ArrayList<Sound>();
		for (int i : new int[] { 0, 1, 3, 4 }) {
			sounds.add(new Sound(harness.target(i), 400 * MS, harness.sequence.getEvents().get(i).getExpectedMidi(), 0.95));
		}
		// an attack at the target of event 2, its pitch never clear enough (confidence 0.55, below the 0.80 of the harness)
		sounds.add(new Sound(harness.target(2), 300 * MS, 66, 0.55));
		harness.feed(frames(sounds, T0 + FollowHarness.LEAD + harness.timeline.durationNanos() + 1_500 * MS));
		assertEquals(Status.MISSED, result(harness, 2).status());
		assertEquals(RefusalReason.UNCLEAR, result(harness, 2).reason());
		assertEquals(55L, result(harness, 2).reasonDetail(), "the best confidence seen, in hundredths");
	}

	@Test
	public void anEventNotJudgedSaysSo() {
		FollowHarness harness = started(fiveNotes());
		for (int i = 0; i < 5; i++) {
			harness.playEvent(i, 0);
		}
		harness.captureReady = false;
		harness.runUntil(harness.target(1) + 300 * MS);
		harness.captureReady = true;
		harness.runAll();
		assertEquals(Status.NOT_JUDGED, result(harness, 0).status());
		assertEquals(RefusalReason.NOT_JUDGED, result(harness, 0).reason());
		assertEquals(RefusalReason.NONE, result(harness, 3).reason());
	}

	// ------------------------------------------------------------------ frames with a confidence of their own

	private static final class Sound {
		final long start;
		final long duration;
		final int midi;
		final double confidence;

		Sound(long start, long duration, int midi, double confidence) {
			this.start = start;
			this.duration = duration;
			this.midi = midi;
			this.confidence = confidence;
		}
	}

	/** The frames of the sounds from {@link FollowHarness#T0} to the end, as the harness makes them, each sound with its own confidence. */
	private static List<AnalysisFrame> frames(List<Sound> sounds, long end) {
		List<AnalysisFrame> frames = new ArrayList<AnalysisFrame>();
		List<Sound> flagged = new ArrayList<Sound>();
		for (long wall = T0; wall < end; wall += HOP) {
			Sound sounding = null;
			for (Sound sound : sounds) {
				if (sound.start <= wall && wall < sound.start + sound.duration) {
					sounding = sound;
				}
			}
			boolean onset = false;
			for (Sound sound : sounds) {
				if (!flagged.contains(sound) && wall >= sound.start + LATENCY) {
					flagged.add(sound);
					onset = true;
				}
			}
			double f0 = (sounding != null ? FollowHarness.hz(sounding.midi) : Double.NaN);
			double confidence = (sounding != null ? sounding.confidence : 0.0);
			frames.add(new AnalysisFrame(wall - T0, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0, confidence, onset));
		}
		return frames;
	}
}
