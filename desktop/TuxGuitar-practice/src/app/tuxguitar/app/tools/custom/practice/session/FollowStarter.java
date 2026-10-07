package app.tuxguitar.app.tools.custom.practice.session;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.util.TGContext;

/**
 * Puts together a run of the tempo-following mode from what the learner chose and what the application knows (spec 002 T096, T098): the percentage
 * becomes the timeline <b>and</b> the tempo the player plays at, the tolerance the windows of the session, the calibration of the route the latency
 * compensation, and the clocks the gate of the verdicts. Everything it uses of the audio engine and of the player comes through the
 * {@link Environment}, so what the window shows is what the session gets, and a test can say so with fakes.
 */
public final class FollowStarter {

	/** The tick of the player at the start of the first measure of the song. */
	public static final long FIRST_TICK = 960L;

	/** What a run needs of the application. */
	public interface Environment {
		FollowPlayer player();

		/** The wall time of the frame, or {@code Long.MIN_VALUE} when the capture clock cannot date it. */
		long wallNanosOf(AnalysisFrame frame);

		boolean captureReady();

		double lagSpreadMillis();

		/** The route now (input, output, format), or null when it is not known yet. */
		AudioRoute route();

		RouteCalibrations calibrations();

		InputAssessment assessment();
	}

	/** The parts of a prepared run. */
	public static final class Plan {
		private final FollowRun run;
		private final FollowSession session;
		private final FollowTimeline timeline;
		private final PracticeSequence sequence;
		private final TimingTolerance.Preset tolerance;
		private final int percent;
		private final long startTick;

		Plan(FollowRun run, FollowSession session, FollowTimeline timeline, PracticeSequence sequence, TimingTolerance.Preset tolerance, int percent,
				long startTick) {
			this.run = run;
			this.session = session;
			this.timeline = timeline;
			this.sequence = sequence;
			this.tolerance = tolerance;
			this.percent = percent;
			this.startTick = startTick;
		}

		public FollowRun run() {
			return this.run;
		}

		public FollowSession session() {
			return this.session;
		}

		public FollowTimeline timeline() {
			return this.timeline;
		}

		/** The events judged: from the start the learner chose. */
		public PracticeSequence sequence() {
			return this.sequence;
		}

		public TimingTolerance.Preset tolerance() {
			return this.tolerance;
		}

		public int percent() {
			return this.percent;
		}

		/** The player's tick the run starts at. */
		public long startTick() {
			return this.startTick;
		}
	}

	private FollowStarter() {
	}

	/**
	 * @param full the sequence of the track from its first event; the run judges the events from {@code full.getStartIndex()}
	 * @param judge the technique judge of the session, or null
	 * @param polyphonic hears a note struck over a ringing chord from the attack evidence (spec 004), or null
	 */
	public static Plan prepare(TGContext context, TGSong song, PracticeSequence full, int trackNumber, FollowSettings settings,
			PracticeParameters parameters, TechniqueJudge judge, java.util.function.Supplier<PolyphonicAcceptance> polyphonic, Environment environment,
			Consumer<FollowSession.Snapshot> uiSink, Executor uiExecutor, Consumer<FollowRun.Ended> endListener, FollowRun.EventsFactory eventsFactory,
			Runnable restart) {
		final int percent = settings.getPercent();
		final TimingTolerance.Preset preset = settings.getTolerance();
		final PracticeSequence sequence = full.startingAt(full.getStartIndex());
		final FollowTimeline timeline = FollowTimeline.build(song, sequence, percent);
		final FollowPlayer player = environment.player();
		final TimingTolerance tolerance = new TimingTolerance(preset);
		final TempoClock clock = player.clock();
		FollowSession session = new FollowSession(sequence, timeline, FIRST_TICK, clock, environment::wallNanosOf,
				() -> new TimingContext(tolerance, environment.calibrations().forRoute(environment.route()), new ClockUncertainty(environment.lagSpreadMillis()),
						environment.captureReady(), clock.isReady()),
				parameters.noteInterpreter(), parameters.tolerance(), new ChordMatcher(parameters.chordParameters()), parameters.stabilityNanos(),
				parameters.articulationLatencyNanos());
		session.setAssessment(environment.assessment());
		session.setTrackAccepted(true);
		if (judge != null) {
			session.setTechniqueJudge(judge);
		}
		if (polyphonic != null) {
			session.setPolyphonicAcceptance(polyphonic);
		}
		long startTick = FIRST_TICK + (sequence.isEmpty() ? 0L : timeline.playStartTicks(0));
		FollowRun run = new FollowRun(context, session, player, trackNumber, percent, startTick, uiSink, uiExecutor, endListener, eventsFactory, restart);
		return new Plan(run, session, timeline, sequence, preset, percent, startTick);
	}
}
