package app.tuxguitar.app.tools.custom.practice.session;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
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
		private final int windowMillis;
		private final int percent;
		private final long startTick;
		private final int playTimeMillis;
		private final FollowLeniencyPosition leniency;
		private final RecognitionSettings recognition;

		Plan(FollowRun run, FollowSession session, FollowTimeline timeline, PracticeSequence sequence, int windowMillis, int percent,
				long startTick) {
			this(run, session, timeline, sequence, windowMillis, percent, startTick, -1, null, null);
		}

		Plan(FollowRun run, FollowSession session, FollowTimeline timeline, PracticeSequence sequence, int windowMillis, int percent,
				long startTick, int playTimeMillis, FollowLeniencyPosition leniency, RecognitionSettings recognition) {
			this.run = run;
			this.session = session;
			this.timeline = timeline;
			this.sequence = sequence;
			this.windowMillis = windowMillis;
			this.percent = percent;
			this.startTick = startTick;
			this.playTimeMillis = playTimeMillis;
			this.leniency = leniency;
			this.recognition = recognition;
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

		/** The window of the on-time judgement, in milliseconds each side at 120 beats a minute: the one chosen when the run was prepared. */
		public int windowMillis() {
			return this.windowMillis;
		}

		public int percent() {
			return this.percent;
		}

		/** The player's tick the run starts at. */
		public long startTick() {
			return this.startTick;
		}

		/** The time to play the note of a Follow run (spec 016), in milliseconds; -1 when the run judges with the timing window. */
		public int playTimeMillis() {
			return this.playTimeMillis;
		}

		/** The pitch permissiveness of a Follow run (spec 016), or null when the run was prepared without one. */
		public FollowLeniencyPosition leniency() {
			return this.leniency;
		}

		/** The recognition settings of a Follow run, relaxed to its position, or null when the run was prepared without one. */
		public RecognitionSettings recognition() {
			return this.recognition;
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
		return prepare(context, song, full, trackNumber, settings, settings.getWindowMillis(), parameters, judge, polyphonic, environment, uiSink, uiExecutor, endListener,
				eventsFactory, restart);
	}

	/**
	 * The same run with the width of the on-time window given (spec 014: Arcade widens the window of the learner), in milliseconds each side at 120 beats a minute, from
	 * {@link TimingTolerance#MIN_WIDTH_MILLIS} to {@link TimingTolerance#MAX_WIDTH_MILLIS} (another width is refused); the other choices are read from the settings.
	 */
	public static Plan prepare(TGContext context, TGSong song, PracticeSequence full, int trackNumber, FollowSettings settings, int windowMillis,
			PracticeParameters parameters, TechniqueJudge judge, java.util.function.Supplier<PolyphonicAcceptance> polyphonic, Environment environment,
			Consumer<FollowSession.Snapshot> uiSink, Executor uiExecutor, Consumer<FollowRun.Ended> endListener, FollowRun.EventsFactory eventsFactory,
			Runnable restart) {
		final TimingTolerance tolerance = TimingTolerance.ofMillis(windowMillis);
		return prepare(context, song, full, trackNumber, settings, tolerance, windowMillis, -1, null, null, parameters, judge, polyphonic, environment, uiSink,
				uiExecutor, endListener, eventsFactory, restart);
	}

	/**
	 * A run of Follow (spec 016, US3): judged with the time to play of the settings ({@link PlayTimeWindow}) instead of the timing window, and with the recognition
	 * settings relaxed to the pitch permissiveness ({@link FollowLeniency}, one way). Arcade keeps {@code prepare} and its window.
	 *
	 * @param effective the recognition settings of the learner, a device profile already applied
	 */
	public static Plan prepareFollow(TGContext context, TGSong song, PracticeSequence full, int trackNumber, FollowSettings settings, FollowLeniencyPosition position,
			RecognitionSettings effective, TechniqueJudge judge, java.util.function.Supplier<PolyphonicAcceptance> polyphonic, Environment environment,
			Consumer<FollowSession.Snapshot> uiSink, Executor uiExecutor, Consumer<FollowRun.Ended> endListener, FollowRun.EventsFactory eventsFactory,
			Runnable restart) {
		int playTimeMillis = settings.getPlayTimeMillis();
		RecognitionSettings recognition = FollowLeniency.apply(position, effective);
		return prepare(context, song, full, trackNumber, settings, new PlayTimeWindow(playTimeMillis), settings.getWindowMillis(), playTimeMillis, position,
				recognition, PracticeParameters.of(recognition), judge, polyphonic, environment, uiSink, uiExecutor, endListener, eventsFactory, restart);
	}

	private static Plan prepare(TGContext context, TGSong song, PracticeSequence full, int trackNumber, FollowSettings settings, final WindowPolicy policy,
			int windowMillis, int playTimeMillis, FollowLeniencyPosition position, RecognitionSettings recognition, PracticeParameters parameters, TechniqueJudge judge,
			java.util.function.Supplier<PolyphonicAcceptance> polyphonic, Environment environment, Consumer<FollowSession.Snapshot> uiSink, Executor uiExecutor,
			Consumer<FollowRun.Ended> endListener, FollowRun.EventsFactory eventsFactory, Runnable restart) {
		final int percent = settings.getPercent();
		final PracticeSequence sequence = full.startingAt(full.getStartIndex());
		final FollowTimeline timeline = FollowTimeline.build(song, sequence, percent);
		final FollowPlayer player = environment.player();
		final TempoClock clock = player.clock();
		FollowSession session = new FollowSession(sequence, timeline, FIRST_TICK, clock, environment::wallNanosOf,
				() -> new TimingContext(policy, environment.calibrations().forRoute(environment.route()), ClockUncertainty.ofMeasurement(environment.lagSpreadMillis()),
						environment.captureReady(), clock.isReady() && !clock.isStalled(clock.nowNanos())),
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
		return new Plan(run, session, timeline, sequence, windowMillis, percent, startTick, playTimeMillis, position, recognition);
	}
}
