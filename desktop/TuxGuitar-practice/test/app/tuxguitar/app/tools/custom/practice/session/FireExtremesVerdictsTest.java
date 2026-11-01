package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireParameter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.ui.PaletteDodge;
import app.tuxguitar.app.tools.custom.practice.ui.ScoreFire;
import app.tuxguitar.app.tools.custom.practice.ui.ScoreStreak;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.ui.widget.UICanvas;
import app.tuxguitar.util.TGContext;

/**
 * Spec 019 (T034, FR-016, FR-019, SC-011): the judging is the same at every extreme of the score fire's settings. The scripted Wait and Follow runs of
 * {@link ScoreFireVerdictsTest}, replayed with each setting at its minimum and at its maximum (the others at their defaults), give the same verdicts,
 * counts, timings, summary and palette guard as with the fire off. The harness is that of {@link ScoreFireVerdictsTest}, with the tuning as a parameter.
 */
public class FireExtremesVerdictsTest {

	private static final long HOP_NANOS = 256L * 1_000_000_000L / 48000L;
	private static final long STABILITY_NANOS = 100_000_000L;
	private static final long LATENCY_NANOS = 13 * HOP_NANOS;
	private static final double E4 = 329.63;
	private static final double F4 = 349.23;
	private static final InputAssessment SUITABLE = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);

	/** The fire and the streak as the window holds them, on a fake score with no canvas. */
	private static final class Window {
		final ScoreStreak streak = new ScoreStreak();
		final ScoreFire fire;
		long wall;
		double highest;

		Window(FlameSetting setting, FireTuning tuning) {
			ScoreFire.Score port = new ScoreFire.Score() {
				public UICanvas canvas() {
					return null;
				}

				public List<double[]> guardBoxes(PracticeEvent current, PracticeEvent next, PaletteDodge.Box palette) {
					return Collections.emptyList();
				}

				public void redraw(boolean overlayOnly) {
				}
			};
			ScoreFire.Ticker ticker = new ScoreFire.Ticker() {
				public void start(Runnable tick) {
				}

				public void stop() {
				}
			};
			this.fire = new ScoreFire(null, port, ticker, () -> this.wall, System::nanoTime);
			this.streak.reset();
			this.fire.start(setting, tuning, true);
		}

		void show(long wall, boolean paused, PracticeEvent current) {
			this.wall = wall;
			this.fire.update(this.streak.streak(), this.streak.judged(), this.streak.takeBursts(), paused, current, null, null);
			this.fire.tick();
			this.highest = Math.max(this.highest, this.fire.level());
		}
	}

	private long now;

	// ------------------------------------------------------------------ Wait

	private String waitDigest(FlameSetting setting, FireTuning tuning, double[] highest) {
		this.now = 0L;
		SongFixture fixture = new SongFixture(1);
		int[] frets = { 0, 1, 0, 1, 0, 1, 0, 1 };
		for (int i = 0; i < frets.length; i++) {
			fixture.quarter(0, i, 1, frets[i]);
		}
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song, PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(),
				PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeSession session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8), new TolerancePolicy(5.0, 25.0), STABILITY_NANOS,
				LATENCY_NANOS);
		session.setAssessment(SUITABLE);
		Window window = new Window(setting, tuning);
		StringBuilder digest = new StringBuilder();
		record(digest, window, session.onCommand(PracticeCommand.START));
		double[] script = { E4, F4, E4, E4, F4, Double.NaN, E4, F4 };
		for (int i = 0; i < script.length; i++) {
			if (Double.isNaN(script[i])) {
				record(digest, window, session.onCommand(PracticeCommand.SKIP));
				continue;
			}
			pluck(session, script[i], 0.95, STABILITY_NANOS + 4 * HOP_NANOS, digest, window);
			play(session, Double.NaN, 0.0, 200_000_000L, false, digest, window);
		}
		highest[0] = window.highest;
		return digest.toString();
	}

	private void record(StringBuilder digest, Window window, PracticeSnapshot snapshot) {
		window.streak.waitSnapshot(snapshot.getAcceptedCount(), snapshot.getLastPassed(), snapshot.getLastPassedOutcome(), snapshot.getVerdict());
		boolean paused = snapshot.getState() == PracticeSession.State.PAUSED || snapshot.getState() == PracticeSession.State.READY_TO_RESUME;
		PracticeEvent current = snapshot.getState() == PracticeSession.State.COMPLETE ? null : snapshot.getCurrentEvent();
		window.show(this.now, paused, current);
		// what the window shows and passes to the palette guard
		digest.append(snapshot.getState()).append('|').append(snapshot.getCurrentIndex()).append('|').append(snapshot.getAcceptedCount()).append('|')
				.append(snapshot.getVerdict()).append('|').append(snapshot.getLastPassedOutcome()).append("|guard ").append(current == null ? -1 : snapshot.getCurrentIndex())
				.append(paused).append('\n');
	}

	private void pluck(PracticeSession session, double f0Hz, double confidence, long durationNanos, StringBuilder digest, Window window) {
		play(session, f0Hz, confidence, LATENCY_NANOS, false, digest, window);
		this.now += HOP_NANOS;
		record(digest, window, session.onFrame(new AnalysisFrame(this.now, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0Hz, confidence, true)));
		play(session, f0Hz, confidence, durationNanos, false, digest, window);
	}

	private void play(PracticeSession session, double f0Hz, double confidence, long durationNanos, boolean onset, StringBuilder digest, Window window) {
		long end = this.now + durationNanos;
		while (this.now < end) {
			this.now += HOP_NANOS;
			record(digest, window, session.onFrame(new AnalysisFrame(this.now, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0Hz, confidence, onset)));
		}
	}

	// ------------------------------------------------------------------ Follow

	private static SongFixture passage() {
		SongFixture song = new SongFixture(10);
		song.tempoChange(0, 200);
		for (int i = 0; i < 40; i++) {
			song.quarter(i / 4, i % 4, i % 3 == 0 ? 3 : 2, 5 + (i % 2));
		}
		return song;
	}

	private static String followDigest(FlameSetting setting, FireTuning tuning, double[] highest) {
		FakePlayerRun run = new FakePlayerRun(passage(), 100);
		List<PracticeEvent> practice = run.sequence.getEvents();
		Window window = new Window(setting, tuning);
		run.start();
		for (int i = 0; i < practice.size(); i++) {
			if (practice.get(i).getKind() == PracticeEvent.Kind.EXPECTED_NOTE && i % 7 != 3) {
				run.playEvent(i, i % 4 == 0 ? 20 : -10);
			}
		}
		long end = FollowHarness.T0 + run.lead + run.timeline.durationNanos() + 1_500 * FollowHarness.MS;
		StringBuilder guard = new StringBuilder();
		for (long wall = FollowHarness.T0; wall < end; wall += 50 * FollowHarness.MS) {
			FollowSession.Snapshot snapshot = run.runUntil(wall);
			if (snapshot == null) {
				continue;
			}
			for (FollowSession.EventResult result : snapshot.results()) {
				window.streak.followResult(result.eventIndex(), result.status());
			}
			int open = snapshot.openEvent();
			boolean running = snapshot.state() == FollowSession.State.FOLLOWING || snapshot.state() == FollowSession.State.COUNT_IN;
			boolean paused = snapshot.state() == FollowSession.State.PAUSED || snapshot.state() == FollowSession.State.READY_TO_RESUME;
			PracticeEvent current = (running || paused) && open >= 0 ? practice.get(open) : null;
			window.show(wall, paused, current);
			guard.append(current == null ? -1 : open).append(paused).append(' ');
		}
		run.runAll();
		StringBuilder digest = new StringBuilder();
		for (FollowSession.EventResult result : run.last.results()) {
			digest.append(result.eventIndex()).append('|').append(result.status()).append('|').append(result.timing()).append('|').append(result.offsetNanos()).append('|')
					.append(java.util.Arrays.toString(result.missingPitches())).append('\n');
		}
		digest.append("extras=").append(run.last.extras().size()).append(" counts=").append(run.last.counts().onTime()).append('/').append(run.last.counts().early()).append('/')
				.append(run.last.counts().late()).append('/').append(run.last.counts().wrongPitch()).append('/').append(run.last.counts().missed()).append('\n').append("guard ").append(guard);
		highest[0] = window.highest;
		return digest.toString();
	}

	/** Every setting of the score fire at its minimum and at its maximum, the others at their defaults. */
	private static List<FireTuning> extremes() {
		List<FireTuning> tunings = new java.util.ArrayList<FireTuning>();
		for (FireParameter parameter : FireParameter.values()) {
			if (parameter.inScore()) {
				tunings.add(FireTuning.SCORE_DEFAULTS.with(parameter, parameter.min()));
				tunings.add(FireTuning.SCORE_DEFAULTS.with(parameter, parameter.max()));
			}
		}
		return tunings;
	}

	@Test
	public void aWaitRunIsJudgedTheSameAtEveryExtreme() {
		String withoutFire = waitDigest(FlameSetting.OFF, FireTuning.SCORE_DEFAULTS, new double[1]);
		assertTrue(withoutFire.contains("SKIPPED"), "the run has a mistake:\n" + withoutFire);
		for (FireTuning tuning : extremes()) {
			assertEquals(withoutFire, waitDigest(FlameSetting.FULL, tuning, new double[1]), "the Wait run changed with " + tuning);
		}
	}

	@Test
	public void aFollowRunIsJudgedTheSameAtEveryExtreme() {
		String withoutFire = followDigest(FlameSetting.OFF, FireTuning.SCORE_DEFAULTS, new double[1]);
		assertTrue(withoutFire.contains("MATCHED") && withoutFire.contains("MISSED"), "the play has good and missed notes:\n" + withoutFire);
		for (FireTuning tuning : extremes()) {
			assertEquals(withoutFire, followDigest(FlameSetting.FULL, tuning, new double[1]), "the Follow run changed with " + tuning);
		}
	}
}
