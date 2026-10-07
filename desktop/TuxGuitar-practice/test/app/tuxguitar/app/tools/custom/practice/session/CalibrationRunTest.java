package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.HOP;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LATENCY;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.CalibrationRun.State;
import app.tuxguitar.app.tools.custom.practice.session.CalibrationTaps.Status;
import app.tuxguitar.app.tools.custom.practice.session.FollowHarness.Note;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;

/**
 * The calibration of the rhythm run on scripted frames and a fake click player (spec 002 T097, FR-054, FR-059): no real time, no sound. The clicks
 * are played when the time of a frame reaches them, the learner's notes are scripted around the time each click was handed to the output.
 */
public class CalibrationRunTest {

	/** The clicks the fake player was asked for, with the wall time it says it handed each to the output. */
	private static final class FakeClicks implements ClickPlayer {
		long now;
		final List<Long> handed = new ArrayList<Long>();
		int silenced;
		boolean fail;

		public long click() {
			if (this.fail) {
				return Long.MIN_VALUE;
			}
			this.handed.add(this.now);
			return this.now;
		}

		public void silence() {
			this.silenced++;
		}
	}

	private final FakeClicks clicks = new FakeClicks();
	private final CalibrationRun run = new CalibrationRun(this.clicks, LATENCY);
	private final List<Note> notes = new ArrayList<Note>();
	private final Set<Note> flagged = new HashSet<Note>();
	private long now = T0;

	/** The first click comes LEAD after the first frame (the grid of 5 ms makes it exact). */
	private static long clickAt(int k) {
		return T0 + CalibrationRun.LEAD_NANOS + k * CalibrationRun.PERIOD_NANOS;
	}

	private void play(int click, long offsetMillis) {
		// the notes alternate between two pitches: a new note on a string that still rings has the pitch of the one before only by chance
		final int midi = (click % 2 == 0 ? 64 : 67);
		this.notes.add(new Note(clickAt(click) + offsetMillis * MS, 250 * MS, seconds -> midi));
	}

	private void playRinging(int click, long offsetMillis) {
		final int midi = (click % 2 == 0 ? 64 : 67);
		this.notes.add(new Note(clickAt(click) + offsetMillis * MS, 1_300 * MS, seconds -> midi));
	}

	/** A click that is heard by the microphone: an attack with no pitch. */
	private AnalysisFrame frame(long wall, boolean clickAttack) {
		AnalysisFrame frame = FollowHarness.frameOf(this.notes, this.flagged, wall);
		if (clickAttack) {
			return new AnalysisFrame(wall - T0, -30.0, -20.0, 0.0, AnalysisFrame.LevelState.SUITABLE, Double.NaN, 0.0, true);
		}
		return frame;
	}

	private void runUntil(long end) {
		while (this.now < end) {
			this.clicks.now = this.now;
			this.run.onFrame(frame(this.now, false), this.now);
			this.now += HOP;
		}
	}

	private long end() {
		return clickAt(CalibrationTaps.CLICKS - 1) + 2_000 * MS;
	}

	@Test
	public void theClicksAreAnnouncedWithTheirDurationAndNothingSoundsBeforeTheStart() {
		assertEquals(State.ANNOUNCED, this.run.state());
		assertEquals(CalibrationTaps.CLICKS, this.run.clickCount());
		// 1.5 s of lead, seven periods of one second to the eighth click, 0.7 s to hear its note and a little more: 9.4 s, said as 10
		assertEquals(10, this.run.approximateSeconds());
		runUntil(T0 + 3_000 * MS);
		assertEquals(0, this.clicks.handed.size(), "frames before the start play no click");
		assertEquals(State.ANNOUNCED, this.run.state());
	}

	@Test
	public void eightNotesAlongTheClicksGiveTheDelayOfTheRoute() {
		this.run.start();
		for (int k = 0; k < 8; k++) {
			play(k, 120 + (k % 3) - 1);
		}
		runUntil(end());

		assertEquals(State.FINISHED, this.run.state());
		assertEquals(8, this.clicks.handed.size());
		assertEquals(clickAt(0), this.clicks.handed.get(0), "the first click comes after the lead");
		assertEquals(clickAt(7) - clickAt(0), this.clicks.handed.get(7) - this.clicks.handed.get(0), "a click a second");
		assertEquals(Status.CALIBRATED, this.run.result().status());
		assertEquals(120.0, this.run.result().medianMillis(), 2.5);
		assertEquals(8, this.run.result().used());
		assertEquals(8, this.run.notesHeard());
	}

	@Test
	public void aNoteBeforeItsClickAndNotesOfAFasterRouteAreAllowed() {
		this.run.start();
		for (int k = 0; k < 8; k++) {
			play(k, -30);
		}
		runUntil(end());

		assertEquals(Status.CALIBRATED, this.run.result().status());
		assertEquals(-30.0, this.run.result().medianMillis(), 2.5);
	}

	@Test
	public void theClickHeardByTheMicrophoneIsNotANote() {
		this.run.start();
		for (int k = 0; k < 8; k++) {
			play(k, 200);
		}
		// every click is also heard by the microphone 60 ms after it: an attack with no pitch
		long step = HOP;
		while (this.now < end()) {
			this.clicks.now = this.now;
			boolean clickAttack = false;
			for (long handed : this.clicks.handed) {
				clickAttack |= (this.now - handed == 60 * MS + LATENCY);
			}
			this.run.onFrame(frame(this.now, clickAttack), this.now);
			this.now += step;
		}

		assertEquals(Status.CALIBRATED, this.run.result().status());
		assertEquals(200.0, this.run.result().medianMillis(), 2.5, "the delay of the notes, not the one of the click that came back");
		assertEquals(8, this.run.notesHeard());
	}

	@Test
	public void aNoteStillRingingIsNotTheNoteOfTheNextClickAndTheClickHeardOverItIsNotANote() {
		this.run.start();
		for (int k = 0; k < 8; k++) {
			playRinging(k, 200);
		}
		while (this.now < end()) {
			this.clicks.now = this.now;
			boolean clickAttack = false;
			for (long handed : this.clicks.handed) {
				clickAttack |= (this.now - handed == 60 * MS + LATENCY);
			}
			// the attack of the click is heard over the note of the click before, which still rings with its pitch
			AnalysisFrame frame = frame(this.now, false);
			if (clickAttack) {
				frame = new AnalysisFrame(this.now - T0, -30.0, -20.0, 0.0, AnalysisFrame.LevelState.SUITABLE, frame.getF0Hz(), frame.getConfidence(), true);
			}
			this.run.onFrame(frame, this.now);
			this.now += HOP;
		}

		assertEquals(Status.CALIBRATED, this.run.result().status());
		assertEquals(200.0, this.run.result().medianMillis(), 2.5, "the notes, at 200 ms, not the clicks heard at 60 ms");
	}

	@Test
	public void aSecondNoteOnTheSameClickIsNotCountedAndAWrongOneIsSetAside() {
		this.run.start();
		for (int k = 0; k < 8; k++) {
			play(k, 100);
		}
		play(2, 300);          // a second note on the same click
		play(4, 600);          // very late: another note, still a candidate for that click
		runUntil(end());

		assertEquals(Status.CALIBRATED, this.run.result().status());
		assertEquals(100.0, this.run.result().medianMillis(), 2.5);
	}

	@Test
	public void fewerThanFiveNotesAreNotKept() {
		this.run.start();
		for (int k = 0; k < 3; k++) {
			play(k, 110);
		}
		runUntil(end());

		assertEquals(State.FINISHED, this.run.state());
		assertEquals(Status.TOO_FEW, this.run.result().status());
		assertTrue(!this.run.result().compensation().isCalibrated());
	}

	@Test
	public void cancelStopsAtOnceNoMoreClickNothingKeptAndTheFramesThatFollowAreIgnored() {
		this.run.start();
		for (int k = 0; k < 8; k++) {
			play(k, 110);
		}
		runUntil(clickAt(2) + 400 * MS);
		assertEquals(3, this.clicks.handed.size());

		this.run.cancel();
		this.run.cancel();
		runUntil(end());

		assertEquals(State.CANCELLED, this.run.state());
		assertEquals(3, this.clicks.handed.size(), "no click after the cancel");
		assertEquals(1, this.clicks.silenced, "the sound is cut once");
		assertNull(this.run.result());
	}

	@Test
	public void cancelBeforeTheStartAlsoCutsTheSound() {
		this.run.cancel();
		this.run.start();
		runUntil(end());

		assertEquals(State.CANCELLED, this.run.state());
		assertEquals(0, this.clicks.handed.size());
		assertEquals(1, this.clicks.silenced);
	}

	@Test
	public void aClickThatCannotBePlayedFailsTheRunAndSilencesTheOthers() {
		this.run.start();
		this.clicks.fail = true;
		runUntil(end());

		assertEquals(State.FAILED, this.run.state());
		assertNull(this.run.result());
		assertEquals(1, this.clicks.silenced);
	}

	@Test
	public void framesThatCannotBeDatedAreWaitedFor() {
		this.run.start();
		for (long wall = T0; wall < T0 + 5_000 * MS; wall += HOP) {
			this.run.onFrame(frame(wall, false), Long.MIN_VALUE);
		}
		assertEquals(0, this.clicks.handed.size(), "without a clock the time of a click is not known");
		assertNotNull(this.run);
	}
}
