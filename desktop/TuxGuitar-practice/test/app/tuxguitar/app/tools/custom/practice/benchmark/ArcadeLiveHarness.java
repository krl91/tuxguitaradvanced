package app.tuxguitar.app.tools.custom.practice.benchmark;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeStarter;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.stress.SilentMidi;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeCanvas;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeFrameSource;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeTexts;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeWindow;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.DisplayRate;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeScene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.NeckModel;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.StringColours;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.AttackCandidates;
import app.tuxguitar.app.tools.custom.practice.session.AudioRoute;
import app.tuxguitar.app.tools.custom.practice.session.FollowPlayback;
import app.tuxguitar.app.tools.custom.practice.session.FollowPlayer;
import app.tuxguitar.app.tools.custom.practice.session.FollowRun;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.session.FollowStarter;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.app.tools.custom.practice.session.MidiFollowPlayer;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.RouteCalibrations;
import app.tuxguitar.app.tools.custom.practice.session.TechniqueJudge;
import app.tuxguitar.app.tools.custom.practice.session.TempoClock;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.backend.RecognitionEngineNotice;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.util.configuration.TGConfigManager;
import app.tuxguitar.util.properties.TGProperties;
import app.tuxguitar.util.properties.TGPropertiesManager;

/**
 * One Arcade run on the owner's Mac as the product assembles it (spec 023, T070, T071): the real {@code ArcadeWindow} (maximized, its canvas and timer),
 * the real MIDI player with silent channels, the live {@link AudioEngine} on the built-in microphone with the engine asked for, the session of the run, and
 * the default settings' stage flames (discreet).
 * It records every paint of the canvas, the position of the player against the wall clock, and, when tones are given, when each tone began at the output
 * and when the hit of its event was first painted. The settings are the defaults, in memory: the owner's files are never read nor written. UI thread only:
 * the caller owns the display.
 */
final class ArcadeLiveHarness {

	private static final String CONSUMER = "arcade-live-benchmark";
	private static final ArcadeTexts TEXTS = (key, arguments) -> key;
	private static final int MAX_PAINTS = 400_000;
	private static final float OUT_RATE = 48000f;
	private static final int BLOCK_FRAMES = 256;

	/** A tone played into the run for one event of the sequence. */
	static final class Tone {
		final int eventIndex;
		final double hz;

		Tone(int eventIndex, double hz) {
			this.eventIndex = eventIndex;
			this.hz = hz;
		}
	}

	static final class Result {
		String engineAsked;
		String noticeReason = "none";
		String input;
		double toolkitRateHz;
		long[] paintStart;
		long[] paintEnd;
		int paints;
		long runStartWall;
		long runEndWall;
		/** The position of the player against the wall clock over the run (positive: the song is ahead of the wall clock), in ms; NaN when not read. */
		double driftMs = Double.NaN;
		String ended;
		long droppedSamples;
		/** For each tone: the wall time of its first sample at the output, and of the first paint that showed its hit (0 when none). */
		final List<long[]> hits = new ArrayList<long[]>();

		/** The display's period: the toolkit's rate, or else the median interval of the paints. */
		double periodNanos() {
			if( this.toolkitRateHz > 0 ) {
				return 1e9 / this.toolkitRateHz;
			}
			double[] intervals = intervals();
			return intervals.length == 0 ? ArcadeCanvas.FRAME_NANOS : percentile(intervals, 0.5);
		}

		double measuredRateHz() {
			double[] intervals = intervals();
			return intervals.length == 0 ? 0.0 : 1e9 / percentile(intervals, 0.5);
		}

		/** The intervals between two paints of the run, in ns. */
		double[] intervals() {
			List<Double> values = new ArrayList<Double>();
			for( int i = 1 ; i < this.paints ; i++ ) {
				if( this.paintEnd[i - 1] >= this.runStartWall && this.paintEnd[i] <= this.runEndWall ) {
					values.add((double) (this.paintEnd[i] - this.paintEnd[i - 1]));
				}
			}
			double[] sorted = new double[values.size()];
			for( int i = 0 ; i < sorted.length ; i++ ) {
				sorted[i] = values.get(i);
			}
			Arrays.sort(sorted);
			return sorted;
		}

		/** A frame is on time when it follows the previous one within one and a half display periods (no display cycle missed). */
		double onTimeShare() {
			double[] intervals = intervals();
			if( intervals.length == 0 ) {
				return 0.0;
			}
			double limit = 1.5 * periodNanos();
			int on = 0;
			for( double interval : intervals ) {
				if( interval <= limit ) {
					on++;
				}
			}
			return on / (double) intervals.length;
		}

		/** The display cycles a run of this length has, and the cycles without a new picture. */
		long missedCycles() {
			double period = periodNanos();
			long missed = 0L;
			for( double interval : intervals() ) {
				missed += Math.max(0L, Math.round(interval / period) - 1L);
			}
			return missed;
		}

		/** Paints that took longer than a display period: they held the frame. */
		int held() {
			double period = periodNanos();
			int held = 0;
			for( int i = 0 ; i < this.paints ; i++ ) {
				if( inRun(i) && this.paintEnd[i] - this.paintStart[i] > period ) {
					held++;
				}
			}
			return held;
		}

		double paintPercentileMs(double p) {
			List<Double> values = new ArrayList<Double>();
			for( int i = 0 ; i < this.paints ; i++ ) {
				if( inRun(i) ) {
					values.add((this.paintEnd[i] - this.paintStart[i]) / 1e6);
				}
			}
			double[] sorted = new double[values.size()];
			for( int i = 0 ; i < sorted.length ; i++ ) {
				sorted[i] = values.get(i);
			}
			Arrays.sort(sorted);
			return sorted.length == 0 ? Double.NaN : percentile(sorted, p);
		}

		int paintsInRun() {
			int count = 0;
			for( int i = 0 ; i < this.paints ; i++ ) {
				if( inRun(i) ) {
					count++;
				}
			}
			return count;
		}

		private boolean inRun(int i) {
			return this.paintEnd[i] >= this.runStartWall && this.paintEnd[i] <= this.runEndWall;
		}

		/** The delays from a tone to its hit painted, in ms, for the tones whose hit was shown. */
		List<Double> hitDelaysMs() {
			List<Double> delays = new ArrayList<Double>();
			for( long[] hit : this.hits ) {
				if( hit[0] > 0 && hit[1] > 0 ) {
					delays.add((hit[1] - hit[0]) / 1e6);
				}
			}
			return delays;
		}
	}

	private final Object display;
	private final UIFactory factory;

	ArcadeLiveHarness(Object display, UIFactory factory) {
		this.display = display;
		this.factory = factory;
	}

	/**
	 * Runs the passage of {@code track} once with the engine asked for ({@code java} or {@code native}), and plays the tones, if any, at their events.
	 *
	 * @param timeoutSeconds the longest the run may last before it is given up
	 */
	Result run(TGSong song, TGTrack track, PracticeSequence full, String engineName, List<Tone> tones, int timeoutSeconds) throws Exception {
		Result result = new Result();
		result.engineAsked = engineName;
		result.paintStart = new long[MAX_PAINTS];
		result.paintEnd = new long[MAX_PAINTS];
		String previousEngine = System.getProperty(PitchEngineCatalog.ENGINE_PROPERTY);
		System.setProperty(PitchEngineCatalog.ENGINE_PROPERTY, engineName);
		SilentMidi midi = new SilentMidi(song);
		inMemorySettings(midi);
		AudioEngine engine = AudioEngine.getInstance(midi.context);
		AtomicReference<RecognitionEngineNotice> notice = new AtomicReference<RecognitionEngineNotice>();
		RecognitionEngineNotice.Listener noticeListener = notice::set;
		engine.addRecognitionEngineNoticeListener(noticeListener);
		AudioInputDescriptor input = builtInMicrophone(engine.getCatalog().listInputs());
		if( input == null ) {
			throw new AssertionError("no built-in microphone listed: " + engine.getCatalog().listInputs());
		}
		result.input = input.getDisplayName();
		engine.getSelection().select(input, null);

		MidiFollowPlayer player = new MidiFollowPlayer(midi.player);
		final Method asyncExec = this.display.getClass().getMethod("asyncExec", Runnable.class);
		final Object shown = this.display;
		Executor ui = task -> {
			try {
				asyncExec.invoke(shown, task);
			} catch( ReflectiveOperationException exception ) {
				throw new AssertionError(exception);
			}
		};
		AtomicReference<FollowSession.Snapshot> latest = new AtomicReference<FollowSession.Snapshot>();
		CountDownLatch ended = new CountDownLatch(1);
		AtomicReference<String> endReason = new AtomicReference<String>("none");
		RecognitionSettings recognition = RecognitionSettings.defaults();
		FollowSettings settings = newSettings();
		FollowStarter.Plan plan = ArcadeStarter.prepareFrom(midi.context, song, track, full, settings, PracticeParameters.of(recognition),
				new TechniqueJudge(TechniqueCatalog.defaults(), recognition.get(RecognitionParameters.NOTE_TECHNIQUE)),
				() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()), new Environment(player, engine), latest::set, ui, reason -> {
					endReason.set(String.valueOf(reason));
					ended.countDown();
				}, null, null);
		engine.setAttackCandidates(AttackCandidates.of(plan.sequence()));
		FollowRun run = plan.run();
		AnalysisService.Listener frames = run::postFrame;
		engine.subscribe(frames);
		engine.acquire(CONSUMER);
		long droppedBefore = engine.getDroppedSampleCount();

		// the hits of the events: seen by the picture when it reads the run (inside the paint), dated by the end of that paint
		ArcadeRun arcadeRun = new ArcadeRun(plan.sequence(), ArcadeScoring.standard());
		int events = plan.sequence().getEvents().size();
		long[] hitPainted = new long[events];
		boolean[] seen = new boolean[events];
		List<Integer> pendingHits = new ArrayList<Integer>();
		ArcadeFrameSource source = new ArcadeFrameSource(sceneOf(plan, track), plan.timeline(), FollowStarter.FIRST_TICK, player.clock(), () -> {
			FollowSession.Snapshot snapshot = latest.get();
			ArcadeRun.View view = (snapshot == null ? null : arcadeRun.update(snapshot));
			if( view != null ) {
				for( ArcadeRun.EventState state : view.states() ) {
					ArcadeVerdict verdict = state.verdict();
					int index = state.eventIndex();
					if( index >= 0 && index < events && !seen[index] && (verdict == ArcadeVerdict.ON_TIME || verdict == ArcadeVerdict.EARLY || verdict == ArcadeVerdict.LATE) ) {
						seen[index] = true;
						pendingHits.add(index);
					}
				}
			}
			return view;
		}, () -> 100, ArcadeWindow.newQuality(), 0L, 0L, settings.getFlames(), true, ArcadeScoring.standard());
		app.tuxguitar.ui.widget.UIWindow parent = this.factory.createWindow();
		ArcadeWindow window = new ArcadeWindow(this.factory, parent, source, ui::execute, TEXTS, new ArcadeWindow.Actions() {
			public void pause() {
			}

			public void resume() {
			}

			public void stop() {
				ended.countDown();
			}

			public void restart() {
			}
		});
		window.setFrameObserver(new ArcadeCanvas.FrameObserver() {
			public void tick(long nanos, boolean requested) {
			}

			public void painted(long startNanos, long endNanos, long allocatedBytes) {
				if( result.paints < MAX_PAINTS ) {
					result.paintStart[result.paints] = startNanos;
					result.paintEnd[result.paints] = endNanos;
					result.paints++;
				}
				for( int index : pendingHits ) {
					hitPainted[index] = endNanos;
				}
				pendingHits.clear();
			}
		});
		Method readAndDispatch = this.display.getClass().getMethod("readAndDispatch");
		Method sleep = this.display.getClass().getMethod("sleep");
		ToneThread toneThread = null;
		List<long[]> drift = new ArrayList<long[]>();
		try {
			window.open();
			// the window of the learner is the one in front: the system slows the painting of a window that is hidden or not the active one
			Object shell = window.getWindow().getClass().getMethod("getControl").invoke(window.getWindow());
			Class.forName("org.eclipse.swt.widgets.Shell").getMethod("forceActive").invoke(shell);
			result.toolkitRateHz = DisplayRate.current();
			// the window is in front, maximized, painting the neck a moment before the run begins, as the learner sees it
			long warm = System.nanoTime() + 1_500_000_000L;
			while( System.nanoTime() < warm ) {
				if( !(Boolean) readAndDispatch.invoke(this.display) ) {
					sleep.invoke(this.display);
				}
			}
			FollowPlayback.Result started = run.start();
			if( started != FollowPlayback.Result.STARTED ) {
				throw new AssertionError("the run did not start: " + started);
			}
			result.runStartWall = System.nanoTime();
			if( tones != null && !tones.isEmpty() ) {
				toneThread = new ToneThread(player.clock(), plan.timeline(), tones);
				toneThread.start();
			}
			long deadline = result.runStartWall + TimeUnit.SECONDS.toNanos(timeoutSeconds);
			long nextReading = result.runStartWall;
			TempoClock clock = player.clock();
			while( ended.getCount() > 0 && System.nanoTime() < deadline ) {
				if( !(Boolean) readAndDispatch.invoke(this.display) ) {
					sleep.invoke(this.display);
				}
				long now = System.nanoTime();
				if( now >= nextReading ) {
					nextReading = now + 250_000_000L;
					double tick = clock.isReady() && !clock.isStalled(now) ? clock.tickAt(now) : Double.NaN;
					if( !Double.isNaN(tick) ) {
						drift.add(new long[] { now, plan.timeline().nanosAtTick(Math.round(tick - FollowStarter.FIRST_TICK)) });
					}
				}
			}
			result.runEndWall = System.nanoTime();
			result.ended = ended.getCount() == 0 ? endReason.get() : "NOT ENDED IN TIME";
			if( toneThread != null ) {
				toneThread.join(5000L);
			}
		} finally {
			window.close();
			parent.dispose();
			engine.unsubscribe(frames);
			run.stop();
			engine.release(CONSUMER);
			engine.removeRecognitionEngineNoticeListener(noticeListener);
			engine.setAttackCandidates(null);
			TGThreadManager.getInstance(midi.context).dispose();
			if( previousEngine == null ) {
				System.clearProperty(PitchEngineCatalog.ENGINE_PROPERTY);
			} else {
				System.setProperty(PitchEngineCatalog.ENGINE_PROPERTY, previousEngine);
			}
		}
		result.droppedSamples = engine.getDroppedSampleCount() - droppedBefore;
		RecognitionEngineNotice told = notice.get();
		result.noticeReason = told == null ? "none" : told.getReason() + ": " + told.getDetail();
		result.driftMs = driftOf(drift);
		if( toneThread != null ) {
			for( int i = 0 ; i < toneThread.played.size() ; i++ ) {
				long[] tone = toneThread.played.get(i);
				long wall = toneThread.wallOf(tone[0]);
				result.hits.add(new long[] { wall, hitPainted[(int) tone[1]], tone[1] });
			}
		}
		return result;
	}

	/** The drift between the first and the last reading taken while the player moved, leaving out the last two seconds (the end of the song). */
	private static double driftOf(List<long[]> readings) {
		if( readings.size() < 2 ) {
			return Double.NaN;
		}
		long[] first = readings.get(Math.min(readings.size() - 1, 8));
		long[] last = readings.get(readings.size() - 1);
		for( int i = readings.size() - 1 ; i > 0 ; i-- ) {
			if( last[0] - readings.get(i)[0] >= 2_000_000_000L ) {
				last = readings.get(i);
				break;
			}
		}
		if( last[0] <= first[0] ) {
			return Double.NaN;
		}
		return ((last[1] - first[1]) - (last[0] - first[0])) / 1e6;
	}

	private static ArcadeScene sceneOf(FollowStarter.Plan plan, TGTrack track) {
		TrackTuning tuning = TrackTuning.ofAnyStrings(track);
		List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
		List<PracticeEvent> practice = plan.sequence().getEvents();
		for( int i = 0 ; i < practice.size() ; i++ ) {
			events.add(ArcadeRenderEvent.of(i, practice.get(i), i + 1 < practice.size() ? practice.get(i + 1) : null, plan.timeline()));
		}
		NeckModel neck = NeckModel.of(tuning, 24);
		return new ArcadeScene(events, neck, StringColours.forStrings(tuning.getStringCount()), CameraTrack.build(events, neck, CameraTrack.Settings.standard()),
				SceneFixture.ENGLISH, false);
	}

	/** Plays each tone so that it begins at its event's written time, and dates its first sample from the output line's position. */
	private static final class ToneThread extends Thread {
		private final TempoClock clock;
		private final FollowTimeline timeline;
		private final List<Tone> tones;
		/** For each tone played: its first sample's index on the line, and its event. */
		final List<long[]> played = new ArrayList<long[]>();
		private final List<long[]> positions = new ArrayList<long[]>();

		ToneThread(TempoClock clock, FollowTimeline timeline, List<Tone> tones) {
			super("arcade-live-tones");
			this.clock = clock;
			this.timeline = timeline;
			this.tones = tones;
			setDaemon(true);
		}

		@Override
		public void run() {
			try {
				play();
			} catch( Exception exception ) {
				throw new AssertionError(exception);
			}
		}

		private void play() throws Exception {
			AudioFormat format = new AudioFormat(OUT_RATE, 16, 1, true, false);
			SourceDataLine out = AudioSystem.getSourceDataLine(format);
			out.open(format, BLOCK_FRAMES * 2 * 8);
			try {
				out.start();
				byte[] bytes = new byte[BLOCK_FRAMES * 2];
				long zeroWall = Long.MIN_VALUE;
				long lastPosition = 0L;
				long lastWall = System.nanoTime();
				int next = 0;
				long[] starts = new long[this.tones.size()];
				Arrays.fill(starts, -1L);
				long end = Long.MAX_VALUE;
				for( long frame = 0 ; frame < end ; frame += BLOCK_FRAMES ) {
					long now = System.nanoTime();
					if( zeroWall == Long.MIN_VALUE && this.clock.isReady() && !this.clock.isStalled(now) ) {
						double tick = this.clock.tickAt(now);
						if( !Double.isNaN(tick) ) {
							zeroWall = now - this.timeline.nanosAtTick(Math.round(tick - FollowStarter.FIRST_TICK));
						}
					}
					// the wall time at which each sample of this block will be heard, from the last position read
					long blockWall = lastWall + (long) ((frame - lastPosition) / OUT_RATE * 1e9);
					while( zeroWall != Long.MIN_VALUE && next < this.tones.size() ) {
						long target = zeroWall + this.timeline.targetNanos(this.tones.get(next).eventIndex);
						if( target < now + 200_000_000L ) {
							// too close to place: left out
							next++;
							continue;
						}
						if( target < blockWall + (long) (BLOCK_FRAMES / OUT_RATE * 1e9) ) {
							starts[next] = frame + Math.max(0L, (long) ((target - blockWall) / 1e9 * OUT_RATE));
							this.played.add(new long[] { starts[next], this.tones.get(next).eventIndex });
							next++;
							continue;
						}
						break;
					}
					if( next >= this.tones.size() && end == Long.MAX_VALUE ) {
						end = frame + (long) (1.5 * OUT_RATE);
					}
					Arrays.fill(bytes, (byte) 0);
					for( int t = 0 ; t < starts.length ; t++ ) {
						if( starts[t] < 0 ) {
							continue;
						}
						for( int i = 0 ; i < BLOCK_FRAMES ; i++ ) {
							long within = frame + i - starts[t];
							if( within >= 0 && within < (long) (0.120 * OUT_RATE) ) {
								int value = (short) (bytes[2 * i] & 0xFF | bytes[2 * i + 1] << 8) + (int) Math.round(32767 * pluck(this.tones.get(t).hz, within));
								short clipped = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
								bytes[2 * i] = (byte) clipped;
								bytes[2 * i + 1] = (byte) (clipped >> 8);
							}
						}
					}
					out.write(bytes, 0, bytes.length);
					lastPosition = out.getLongFramePosition();
					lastWall = System.nanoTime();
					synchronized( this.positions ) {
						this.positions.add(new long[] { lastPosition, lastWall });
					}
				}
				out.drain();
			} finally {
				out.stop();
				out.close();
			}
		}

		/** The wall time at which the line's position passed this frame, interpolated between two readings; 0 when not known. */
		long wallOf(long frame) {
			synchronized( this.positions ) {
				for( int i = 1 ; i < this.positions.size() ; i++ ) {
					long[] a = this.positions.get(i - 1);
					long[] b = this.positions.get(i);
					if( a[0] <= frame && b[0] >= frame ) {
						return b[0] == a[0] ? a[1] : a[1] + (long) ((double) (frame - a[0]) / (b[0] - a[0]) * (b[1] - a[1]));
					}
				}
			}
			return 0L;
		}

		/** The plucked tone of T003: the fundamental and three partials, a 2 ms rise and an exponential decay. */
		private static double pluck(double hz, long index) {
			double t = index / OUT_RATE;
			double envelope = Math.min(1.0, t / 0.002) * Math.exp(-t / 0.06);
			double value = Math.sin(2 * Math.PI * hz * t) + 0.5 * Math.sin(4 * Math.PI * hz * t) + 0.3 * Math.sin(6 * Math.PI * hz * t)
					+ 0.2 * Math.sin(8 * Math.PI * hz * t);
			return 0.35 * envelope * value;
		}
	}

	/** The environment of the run: the silent player, the live engine, no calibration. */
	private static final class Environment implements FollowStarter.Environment {
		private final MidiFollowPlayer player;
		private final AudioEngine engine;
		private final RouteCalibrations calibrations = new RouteCalibrations();

		Environment(MidiFollowPlayer player, AudioEngine engine) {
			this.player = player;
			this.engine = engine;
		}

		public FollowPlayer player() {
			return this.player;
		}

		public long wallNanosOf(AnalysisFrame frame) {
			return this.engine.wallNanosOf(frame);
		}

		public boolean captureReady() {
			return this.engine.getCaptureClock().isReady();
		}

		public double lagSpreadMillis() {
			return this.engine.getCaptureClock().isReady() ? this.engine.getCaptureClock().lagSpreadMillis() : 0.0;
		}

		public AudioRoute route() {
			return new AudioRoute("built-in microphone", "silent", 48000, "mono");
		}

		public RouteCalibrations calibrations() {
			return this.calibrations;
		}

		public InputAssessment assessment() {
			return new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
		}
	}

	private static FollowSettings newSettings() throws Exception {
		java.lang.reflect.Constructor<FollowSettings> constructor = FollowSettings.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		return constructor.newInstance();
	}

	/** The built-in microphone; never the Rocksmith cable nor a USB microphone. */
	static AudioInputDescriptor builtInMicrophone(List<AudioInputDescriptor> all) {
		for( AudioInputDescriptor input : all ) {
			String name = (input.getDeviceName() + " " + input.getDisplayName()).toLowerCase(Locale.ROOT);
			if( input.getChannelIndex().isPresent() && input.getChannelIndex().getAsInt() > 0 ) {
				continue;
			}
			if( name.contains("macbook") || name.contains("built-in") || name.contains("imac") || name.contains("mac mini") ) {
				return input;
			}
		}
		return null;
	}

	static double percentile(double[] sorted, double p) {
		int index = (int) Math.ceil(p * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
	}

	/** The default settings in memory: the owner's configuration files are never read nor written. */
	private static void inMemorySettings(SilentMidi midi) {
		TGPropertiesManager manager = TGPropertiesManager.getInstance(midi.context);
		manager.setPropertiesFactory(() -> new MapProperties());
		manager.addPropertiesReader(TGConfigManager.RESOURCE, (properties, module) -> {
		});
		manager.addPropertiesWriter(TGConfigManager.RESOURCE, (properties, module) -> {
		});
	}

	private static final class MapProperties implements TGProperties {
		private final Map<String, String> values = new LinkedHashMap<String, String>();

		public String getValue(String key) {
			return this.values.get(key);
		}

		public void setValue(String key, String value) {
			this.values.put(key, value);
		}

		public void remove(String key) {
			this.values.remove(key);
		}

		public void clear() {
			this.values.clear();
		}

		public void update(java.util.Properties newProperties) {
			for( String key : newProperties.stringPropertyNames() ) {
				this.values.put(key, newProperties.getProperty(key));
			}
		}

		public java.util.Set<String> getStringKeys() {
			return this.values.keySet();
		}
	}
}
