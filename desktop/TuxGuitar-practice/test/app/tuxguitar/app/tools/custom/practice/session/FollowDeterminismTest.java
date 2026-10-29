package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.FIRST_TICK;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.HOP;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LEAD;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowHarness.Note;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.EventResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Spec 016 (T034, FR-006, FR-009): a Follow session built as {@link FollowStarter#prepareFollow} builds it (the time to play, the recognition of the position)
 * gives the same verdicts and reasons for the same frames; silence is never accepted, even at Very permissive; one attack never counts for two events, even with
 * the longest time to play; a chord stays one event at Very permissive, and a chord with a string missing is judged as at Strict. The frames are scripted, or come
 * from the real analysis of synthetic audio (the helpers of {@code FollowSessionTechniqueTest}, which stays as it is, copied).
 */
public class FollowDeterminismTest {

	private static final int RATE = 48000;
	/** Open E major, string 6 to 1: E2 B2 E3 G#3 B3 E4. */
	private static final int[] E_MAJOR_STRINGS = { 6, 5, 4, 3, 2, 1 };
	private static final int[] E_MAJOR_FRETS = { 0, 2, 2, 1, 0, 0 };
	private static final int[] E_MAJOR_PITCHES = { 40, 47, 52, 56, 59, 64 };

	/** A Follow session on fake clocks, with the time to play and the recognition of a position. */
	private static final class Run {
		final PracticeSequence sequence;
		final FollowTimeline timeline;
		final FollowSession session;

		Run(SongFixture fixture, FollowLeniencyPosition position, int playTimeMillis) {
			this.sequence = fixture.sequence();
			this.timeline = FollowTimeline.build(fixture.song, this.sequence, 100);
			FollowHarness.FakeClock clock = new FollowHarness.FakeClock(this.timeline);
			PracticeParameters parameters = PracticeParameters.of(FollowLeniency.apply(position, RecognitionSettings.defaults()));
			final PlayTimeWindow window = new PlayTimeWindow(playTimeMillis);
			this.session = new FollowSession(this.sequence, this.timeline, FIRST_TICK, clock, frame -> T0 + frame.getTimestampNanos(),
					() -> new TimingContext(window, LatencyCompensation.calibrated(0.0), new ClockUncertainty(5.0), true, true), parameters.noteInterpreter(),
					parameters.tolerance(), new ChordMatcher(parameters.chordParameters()), parameters.stabilityNanos(), parameters.articulationLatencyNanos());
			this.session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
			this.session.setTrackAccepted(true);
			this.session.setPolyphonicAcceptance(() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
			this.session.start();
		}

		long target(int event) {
			return T0 + LEAD + this.timeline.targetNanos(event);
		}

		long end() {
			return T0 + LEAD + this.timeline.durationNanos() + 1_500 * MS;
		}

		List<EventResult> feed(List<AnalysisFrame> frames) {
			FollowSession.Snapshot last = null;
			for (AnalysisFrame frame : frames) {
				last = this.session.onFrame(frame);
			}
			return last.results();
		}
	}

	/** The scripted frames of the notes, from {@link FollowHarness#T0} to the end. */
	private static List<AnalysisFrame> scripted(List<Note> notes, long end) {
		List<AnalysisFrame> frames = new ArrayList<AnalysisFrame>();
		HashSet<Note> flagged = new HashSet<Note>();
		for (long wall = T0; wall < end; wall += HOP) {
			frames.add(FollowHarness.frameOf(notes, flagged, wall));
		}
		return frames;
	}

	private static Note note(long start, int midi) {
		return new Note(start, 150 * MS, seconds -> midi);
	}

	/** index:STATUS/TIMING/offset in ms/REASON/detail, for every event. */
	private static String describe(List<EventResult> results) {
		StringBuilder text = new StringBuilder();
		for (EventResult r : results) {
			text.append(text.length() > 0 ? " " : "").append(r.eventIndex()).append(':').append(r.status()).append('/').append(r.timing()).append('/')
					.append(Math.round(r.offsetNanos() / 1e6)).append('/').append(r.reason()).append('/').append(r.reasonDetail());
		}
		return text.toString();
	}

	/** Five quarter notes of 64, 65, 66, 67 and 68 at 120 beats a minute. */
	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	// ------------------------------------------------------------------ audio helpers (copied from FollowSessionTechniqueTest)

	private static float[] silence(Run run) {
		return new float[(int) ((LEAD + run.timeline.durationNanos() + 3_000 * MS) * RATE / 1_000_000_000L)];
	}

	private static void add(float[] signal, float[] addition, long wall, float gain) {
		int offset = (int) ((wall - T0) * RATE / 1_000_000_000L);
		for (int i = 0; i < addition.length && offset + i < signal.length; i++) {
			signal[offset + i] += gain * addition[i];
		}
	}

	private static void pluck(float[] signal, long wall, int midi, long seed) {
		add(signal, SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(midi), RATE, (int) (0.9 * RATE), seed), wall, 0.3f);
	}

	/** A strum: 10 ms between the strings, from the lowest. */
	private static void strum(float[] signal, long wall, int[] pitches, long seed) {
		for (int i = 0; i < pitches.length; i++) {
			add(signal, SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(pitches[i]), RATE, (int) (1.3 * RATE), seed + i), wall + i * 10 * MS, 0.2f);
		}
	}

	private static List<AnalysisFrame> analyse(Run run, float[] signal) {
		return OfflineAnalysis.analyze(signal, RATE, RecognitionSettings.defaults(), true, AttackCandidates.of(run.sequence));
	}

	/** An E major chord on the beat, then two single notes (65 and 67). */
	private static SongFixture chordThenTwoNotes() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, E_MAJOR_STRINGS, E_MAJOR_FRETS);
		fixture.quarter(0, 1, 1, 1);
		fixture.quarter(0, 2, 1, 3);
		return fixture;
	}

	/** The chord (all its strings, or all but G#3) then the two notes, as audio, judged at the position. */
	private static List<EventResult> chordRun(FollowLeniencyPosition position, boolean stringMissing) {
		Run run = new Run(chordThenTwoNotes(), position, PlayTimeWindow.DEFAULT_MILLIS);
		float[] signal = silence(run);
		strum(signal, run.target(0), stringMissing ? new int[] { 40, 47, 52, 59, 64 } : E_MAJOR_PITCHES, 11L);
		pluck(signal, run.target(1), 65, 40L);
		pluck(signal, run.target(2), 67, 41L);
		return run.feed(analyse(run, signal));
	}

	// ------------------------------------------------------------------ the tests

	@Test
	public void theSameFramesSettingsPositionAndTimeToPlayGiveTheSameVerdictsAndReasons() {
		for (FollowLeniencyPosition position : FollowLeniencyPosition.values()) {
			for (int playTime : new int[] { 100, 300, 600 }) {
				String[] twice = new String[2];
				for (int k = 0; k < 2; k++) {
					Run run = new Run(fiveNotes(), position, playTime);
					List<Note> notes = new ArrayList<Note>();
					notes.add(note(run.target(0), 64));
					notes.add(note(run.target(1) - 30 * MS, 65));
					notes.add(note(run.target(2) + 250 * MS, 66));
					notes.add(note(run.target(3), 61));
					twice[k] = describe(run.feed(scripted(notes, run.end())));
				}
				assertEquals(twice[0], twice[1], position + " at " + playTime + " ms");
			}
		}
		assertEquals(describe(chordRun(FollowLeniencyPosition.VERY_PERMISSIVE, false)), describe(chordRun(FollowLeniencyPosition.VERY_PERMISSIVE, false)),
				"the real analysis of the same audio too");
	}

	@Test
	public void silenceIsNeverAcceptedEvenAtVeryPermissiveWithTheLongestTime() {
		Run run = new Run(fiveNotes(), FollowLeniencyPosition.VERY_PERMISSIVE, PlayTimeWindow.MAX_MILLIS);
		for (EventResult result : run.feed(scripted(new ArrayList<Note>(), run.end()))) {
			assertEquals(Status.MISSED, result.status(), "event " + result.eventIndex());
			assertEquals(RefusalReason.NO_ATTACK, result.reason(), "event " + result.eventIndex());
		}
		Run audio = new Run(chordThenTwoNotes(), FollowLeniencyPosition.VERY_PERMISSIVE, PlayTimeWindow.MAX_MILLIS);
		for (EventResult result : audio.feed(analyse(audio, silence(audio)))) {
			assertNotEquals(Status.MATCHED, result.status(), "the analysis of silence: event " + result.eventIndex());
		}
	}

	@Test
	public void oneAttackNeverCountsForTwoEventsWithSixHundredMillisecondsAndNotesThreeHundredApart() {
		// at 200 beats a minute, quarters 300 ms apart, all of the same pitch: only the time tells them apart
		SongFixture fixture = new SongFixture(1);
		fixture.tempoChange(0, 200);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, 2);
		}
		for (long after = 0; after <= 300; after += 10) {
			Run run = new Run(fixture, FollowLeniencyPosition.VERY_PERMISSIVE, PlayTimeWindow.MAX_MILLIS);
			assertEquals(300 * MS, run.target(1) - run.target(0));
			List<Note> notes = new ArrayList<Note>();
			notes.add(note(run.target(1) + after * MS, 66));
			int matched = 0;
			for (EventResult result : run.feed(scripted(notes, run.end()))) {
				matched += (result.status() == Status.MATCHED ? 1 : 0);
			}
			assertEquals(1, matched, "one attack " + after + " ms after the second red counts once");
		}
	}

	@Test
	public void aChordIsOneEventAtVeryPermissiveAndAChordWithAStringMissingIsJudgedAsAtStrict() {
		List<EventResult> whole = chordRun(FollowLeniencyPosition.VERY_PERMISSIVE, false);
		assertEquals(3, whole.size(), "the chord is one event");
		assertEquals(Status.MATCHED, whole.get(0).status());
		assertEquals(Status.MATCHED, whole.get(1).status(), "its six strings did not take the next note");
		assertEquals(Status.MATCHED, whole.get(2).status());
		List<EventResult> strict = chordRun(FollowLeniencyPosition.STRICT, true);
		List<EventResult> very = chordRun(FollowLeniencyPosition.VERY_PERMISSIVE, true);
		assertEquals(strict.get(0).status(), very.get(0).status(), "a chord with a string missing: " + describe(strict) + " | " + describe(very));
		assertEquals(strict.get(0).pitch(), very.get(0).pitch());
		assertTrue(strict.get(0).status() != Status.MATCHED, "a chord with a string missing is not accepted at Strict: " + describe(strict));
	}
}
