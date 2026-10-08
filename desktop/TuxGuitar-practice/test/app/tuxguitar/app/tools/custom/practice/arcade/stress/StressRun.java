package app.tuxguitar.app.tools.custom.practice.arcade.stress;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeCanvas;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeFrameSource;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeScene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.NeckModel;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.StringColours;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.AttackCandidates;
import app.tuxguitar.app.tools.custom.practice.session.AudioRoute;
import app.tuxguitar.app.tools.custom.practice.session.FollowPlayback;
import app.tuxguitar.app.tools.custom.practice.session.FollowPlayer;
import app.tuxguitar.app.tools.custom.practice.session.FollowRun;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.session.FollowStarter;
import app.tuxguitar.app.tools.custom.practice.session.MidiFollowPlayer;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.RouteCalibrations;
import app.tuxguitar.app.tools.custom.practice.session.TechniqueJudge;
import app.tuxguitar.app.tools.custom.practice.session.TempoClock;
import app.tuxguitar.audio.engine.StressInput;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * One whole run of Arcade, as the product assembles it, for a given length (spec 003 T047, T048): the real {@code MidiPlayer} on the real javax.sound
 * sequencer playing the backing, the real live recognition chain (ring buffer, analysis thread, front end, capture clock) fed in real time by a seeded
 * recording of the learner, the tempo-following session of specification 002 under {@link FollowRun}, and, when asked, the real canvas painting on the
 * real SWT painter. Only the sound card and the speakers are absent. It reports the judgements as a digest and what happened to the audio and the frames.
 */
public final class StressRun {

	public static final class Config {
		public int seconds = 600;
		public boolean render;
		public long seed = 20261008L;
		/** Single notes a second in the first half (quarter notes at that tempo); the chords of the second half are half as many. */
		public double noteRate = 3.0;
		public int width = 1280;
		public int height = 720;
		/** True to force the fallback renderer path of the canvas (T048). */
		public boolean fallback;
		/** True to ask the system to paint at once after each request (an experiment of T047: the toolkit does not offer it, the harness reaches the native control). */
		public boolean updateAtOnce;
		/** Events of the scene that can be asked for before the run starts (T045 and T048 vary them). */
		public double scale = 1.0;
	}

	public static final class Result {
		public String digest;
		public String offsets;
		public String summary;
		public int events;
		public int judged;
		public long captureDropped;
		public long maxFeederLatenessMs;
		public long frames;
		public long maxFrameGapMs;
		public long maxTickStallMs;
		public long backingNotesExpected;
		public long backingNotesSent;
		public long gcCount;
		public long gcMillis;
		public StressMetrics metrics;
		public String ended;
		public String renderer = "none";
		public int sceneEvents;
		public String tier;
		public int tierChanges;
	}

	private final Config config;
	private final UIFactory factory;
	private final Object display;

	public StressRun(Config config, Object display, UIFactory factory) {
		this.config = config;
		this.display = display;
		this.factory = factory;
	}

	private static FollowSettings newSettings() throws Exception {
		java.lang.reflect.Constructor<FollowSettings> constructor = FollowSettings.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		return constructor.newInstance();
	}

	/** The environment of a run: the real player, the live chain, no calibration, a suitable input. */
	private static final class Environment implements FollowStarter.Environment {
		private final MidiFollowPlayer player;
		private final StressInput input;
		private final RouteCalibrations calibrations = new RouteCalibrations();

		Environment(MidiFollowPlayer player, StressInput input) {
			this.player = player;
			this.input = input;
		}

		@Override
		public FollowPlayer player() {
			return this.player;
		}

		@Override
		public long wallNanosOf(AnalysisFrame frame) {
			return this.input.engine().wallNanosOf(frame);
		}

		@Override
		public boolean captureReady() {
			return this.input.engine().getCaptureClock().isReady();
		}

		@Override
		public double lagSpreadMillis() {
			return this.input.engine().getCaptureClock().isReady() ? this.input.engine().getCaptureClock().lagSpreadMillis() : 0.0;
		}

		@Override
		public AudioRoute route() {
			return new AudioRoute("stress-input", "silent", StressInput.SAMPLE_RATE, "mono 32bit");
		}

		@Override
		public RouteCalibrations calibrations() {
			return this.calibrations;
		}

		@Override
		public InputAssessment assessment() {
			return new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
		}
	}

	private static final class Active {
		final long startSample;
		final int[] midis;
		final boolean strum;

		Active(long startSample, int[] midis, boolean strum) {
			this.startSample = startSample;
			this.midis = midis;
			this.strum = strum;
		}
	}

	/** Plays the recording of the learner into the live chain, in real time, from a seeded score. */
	private static final class Feeder implements Runnable {
		static final int BLOCK = 1024;
		/** The learner plays this much before the written time: the chain dates a note about this much late (block of capture, detection), which the calibration of the product removes. */
		static final long LEARNER_EARLY_NANOS = 25_000_000L;
		/** A strum is dated by its first string, which the chain hears earlier relative to the written time than a single note: the learner is not early for it. */
		static final long CHORD_EARLY_NANOS = 0L;
		private final StressInput input;
		private final TempoClock clock;
		private final app.tuxguitar.app.tools.custom.practice.session.FollowTimeline timeline;
		private final List<StressScore.Strike> strikes;
		private final long seed;
		private final Map<Long, float[]> waves = new HashMap<Long, float[]>();
		private final AtomicLong maxLateness = new AtomicLong();
		private volatile boolean stop;

		Feeder(StressInput input, TempoClock clock, app.tuxguitar.app.tools.custom.practice.session.FollowTimeline timeline, List<StressScore.Strike> strikes,
				long seed) {
			this.input = input;
			this.clock = clock;
			this.timeline = timeline;
			this.strikes = strikes;
			this.seed = seed;
		}

		private float[] wave(int midi, boolean strum) {
			long key = midi * 2L + (strum ? 1 : 0);
			float[] wave = this.waves.get(key);
			if (wave == null) {
				wave = StressScore.pluck(midi, strum, this.seed);
				this.waves.put(key, wave);
			}
			return wave;
		}

		@Override
		public void run() {
			final long blockNanos = BLOCK * 1_000_000_000L / StressInput.SAMPLE_RATE;
			final long start = System.nanoTime();
			float[] block = new float[BLOCK];
			List<Active> active = new ArrayList<Active>();
			int next = 0;
			long zeroWall = Long.MIN_VALUE;
			boolean frozen = false;
			for (long b = 1; !this.stop; b++) {
				long target = start + b * blockNanos;
				while (System.nanoTime() < target && !this.stop) {
					LockSupport.parkNanos(Math.min(1_000_000L, Math.max(10_000L, target - System.nanoTime())));
				}
				long now = System.nanoTime();
				this.maxLateness.accumulateAndGet(now - target, Math::max);
				long blockStart = (b - 1) * BLOCK;
				if (!frozen && this.clock.isReady() && !this.clock.isStalled(now)) {
					double tick = this.clock.tickAt(now);
					if (!Double.isNaN(tick)) {
						zeroWall = now - this.timeline.nanosAtTick(Math.round(tick) - FollowStarter.FIRST_TICK);
					}
				}
				if (!frozen && zeroWall != Long.MIN_VALUE && now >= zeroWall + this.strikes.get(0).nanos - 400_000_000L) {
					// the position of the player has been read for a while at a steady tempo (the lead-in): it is kept for the whole run, so that the learner
					// of every run plays at the same instants of the timeline
					frozen = true;
				}
				if (frozen) {
					while (next < this.strikes.size() && zeroWall + this.strikes.get(next).nanos < now + 1_500_000_000L) {
						StressScore.Strike strike = this.strikes.get(next);
						long strikeNanos = strike.nanos - (strike.strum ? CHORD_EARLY_NANOS : LEARNER_EARLY_NANOS);
						active.add(new Active(Math.max(blockStart, StrikePlacement.startSample(start, zeroWall, strikeNanos)), strike.midis, strike.strum));
						next++;
					}
				}
				java.util.Arrays.fill(block, 0f);
				for (java.util.Iterator<Active> it = active.iterator(); it.hasNext();) {
					Active a = it.next();
					boolean alive = false;
					for (int v = 0; v < a.midis.length; v++) {
						long voiceStart = a.startSample + (a.strum ? v * (StressInput.SAMPLE_RATE / 100) : 0);
						float[] wave = wave(a.midis[v], a.strum);
						long end = voiceStart + wave.length;
						if (end > blockStart) {
							alive = true;
							float gain = a.strum ? 0.2f : 0.3f;
							for (long i = Math.max(blockStart, voiceStart); i < Math.min(blockStart + BLOCK, end); i++) {
								block[(int) (i - blockStart)] += gain * wave[(int) (i - voiceStart)];
							}
						}
					}
					if (!alive) {
						it.remove();
					}
				}
				this.input.write(block, BLOCK);
			}
		}
	}

	private static String digestOf(FollowSession.Snapshot snapshot, ArcadeResult result, List<PracticeEvent> events) {
		StringBuilder digest = new StringBuilder();
		for (FollowSession.EventResult r : snapshot.results()) {
			digest.append(r.eventIndex()).append('|').append(r.status()).append('|').append(r.timing()).append('|')
					.append(java.util.Arrays.toString(r.missingPitches())).append(" exp=").append(java.util.Arrays.toString(events.get(r.eventIndex()).getRequiredPitches())).append('\n');
		}
		digest.append("extras=").append(snapshot.extras().size()).append('\n');
		digest.append("score=").append(result.score()).append(" max=").append(result.maxScore()).append(" best=").append(result.bestStreak()).append(" percent=")
				.append(result.percent()).append('\n');
		return digest.toString();
	}

	private static String offsetsOf(FollowSession.Snapshot snapshot, List<PracticeEvent> events) {
		List<Long> notes = new ArrayList<Long>();
		List<Long> chords = new ArrayList<Long>();
		for (FollowSession.EventResult r : snapshot.results()) {
			if (r.status() == FollowSession.Status.MATCHED) {
				(events.get(r.eventIndex()).getKind() == PracticeEvent.Kind.EXPECTED_CHORD ? chords : notes).add(r.offsetNanos() / 1_000_000L);
			}
		}
		return "single notes " + spread(notes) + "; chords " + spread(chords);
	}

	private static String spread(List<Long> offsets) {
		if (offsets.isEmpty()) {
			return "none matched";
		}
		java.util.Collections.sort(offsets);
		return String.format(Locale.ROOT, "matched %d, offset from the written time (negative = early) min %d, p5 %d, p50 %d, p95 %d, max %d ms", offsets.size(), offsets.get(0),
				offsets.get((int) (offsets.size() * 0.05)), offsets.get(offsets.size() / 2), offsets.get((int) (offsets.size() * 0.95)), offsets.get(offsets.size() - 1));
	}

	public Result run() throws Exception {
		StressScore score = new StressScore(this.config.seconds, this.config.seed, this.config.noteRate);
		SilentMidi midi = new SilentMidi(score.fixture.song);
		RecognitionSettings recognition = RecognitionSettings.defaults();
		StressInput input = StressInput.create(midi.context, recognition);
		MidiFollowPlayer player = new MidiFollowPlayer(midi.player);
		Environment environment = new Environment(player, input);
		final AtomicReference<FollowSession.Snapshot> latest = new AtomicReference<FollowSession.Snapshot>();
		final CountDownLatch ended = new CountDownLatch(1);
		final AtomicReference<String> endReason = new AtomicReference<String>("none");
		PracticeParameters parameters = PracticeParameters.of(recognition);
		FollowSettings settings = newSettings();
		FollowStarter.Plan plan = FollowStarter.prepare(midi.context, score.fixture.song, score.fixture.sequence(), score.guitar.getNumber(), settings, parameters,
				new TechniqueJudge(TechniqueCatalog.defaults(), recognition.get(RecognitionParameters.NOTE_TECHNIQUE)),
				() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()), environment, latest::set, Runnable::run, reason -> {
					endReason.set(String.valueOf(reason));
					ended.countDown();
				}, null, null);
		input.engine().setAttackCandidates(AttackCandidates.of(plan.sequence()));
		input.engine().setSpectrumEnabled(true);
		final FollowRun run = plan.run();

		// the frames of the analysis: to the session, and counted
		final AtomicLong frames = new AtomicLong();
		final AtomicLong lastFrame = new AtomicLong();
		final AtomicLong maxGap = new AtomicLong();
		app.tuxguitar.audio.engine.analysis.AnalysisService.Listener listener = frame -> {
			long now = System.nanoTime();
			long previous = lastFrame.getAndSet(now);
			if (previous != 0L) {
				maxGap.accumulateAndGet(now - previous, Math::max);
			}
			frames.incrementAndGet();
			run.postFrame(frame);
		};

		List<StressScore.Strike> strikes = score.strikes(plan.sequence().getEvents(), plan.timeline());
		Feeder feeder = new Feeder(input, player.clock(), plan.timeline(), strikes, this.config.seed);
		Thread feederThread = new Thread(feeder, "stress-capture");
		feederThread.setPriority(Thread.NORM_PRIORITY + 1);
		feederThread.setDaemon(true);

		// the sampler: the player's position, the ring buffer
		final AtomicLong maxStall = new AtomicLong();
		final AtomicLong sampling = new AtomicLong(1);
		Thread sampler = new Thread(() -> {
			long lastTick = -1;
			long lastChange = System.nanoTime();
			while (sampling.get() == 1) {
				long now = System.nanoTime();
				long tick;
				try {
					tick = midi.player.getTickPosition();
				} catch (RuntimeException exception) {
					tick = lastTick;
				}
				if (tick != lastTick) {
					lastTick = tick;
					lastChange = now;
				} else if (midi.player.isRunning() && tick > FollowStarter.FIRST_TICK) {
					maxStall.accumulateAndGet(now - lastChange, Math::max);
				}
				LockSupport.parkNanos(10_000_000L);
			}
		}, "stress-sampler");
		sampler.setDaemon(true);

		int sceneEvents = 0;
		long gcCount0 = 0;
		long gcMillis0 = 0;
		for (java.lang.management.GarbageCollectorMXBean bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
			gcCount0 += Math.max(0, bean.getCollectionCount());
			gcMillis0 += Math.max(0, bean.getCollectionTime());
		}

		StressMetrics metrics = new StressMetrics();
		ArcadeCanvas canvas = null;
		final ArcadeCanvas[] holder = new ArcadeCanvas[1];
		Thread probe = null;
		UIWindow window = null;
		QualityController quality = new QualityController(ArcadeCanvas.FRAME_NANOS);
		ArcadeRun arcadeRun = new ArcadeRun(plan.sequence(), ArcadeScoring.standard());
		if (this.config.render) {
			TrackTuning tuning = TrackTuning.ofAnyStrings(score.guitar);
			NeckModel neck = NeckModel.of(tuning, 24);
			// the picture shows a denser stand-in of the judged passage (see StressScore.visualLoad): the worst case for the painting, on the same clock
			app.tuxguitar.app.tools.custom.practice.model.SongFixture load = StressScore.visualLoad(this.config.seconds + 20);
			app.tuxguitar.app.tools.custom.practice.model.PracticeSequence loadSequence = load.sequence();
			app.tuxguitar.app.tools.custom.practice.session.FollowTimeline loadTimeline = app.tuxguitar.app.tools.custom.practice.session.FollowTimeline.build(load.song,
					loadSequence, 100);
			List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
			List<PracticeEvent> practice = loadSequence.getEvents();
			for (int i = 0; i < practice.size(); i++) {
				events.add(ArcadeRenderEvent.of(i, practice.get(i), loadTimeline));
			}
			sceneEvents = events.size();
			ArcadeScene scene = new ArcadeScene(events, neck, StringColours.forStrings(tuning.getStringCount()),
					CameraTrack.build(events, neck, CameraTrack.Settings.standard()), app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture.ENGLISH, false);
			ArcadeFrameSource source = new ArcadeFrameSource(scene, plan.timeline(), FollowStarter.FIRST_TICK, player.clock(), () -> {
				FollowSession.Snapshot snapshot = latest.get();
				return snapshot == null ? null : arcadeRun.update(snapshot);
			}, () -> 100, quality);
			window = this.factory.createWindow();
			window.setLayout(new UITableLayout());
			final Method asyncExec = this.display.getClass().getMethod("asyncExec", Runnable.class);
			final Object display = this.display;
			canvas = new ArcadeCanvas(this.factory, window, source, task -> {
				metrics.postedToUi();
				try {
					asyncExec.invoke(display, (Runnable) () -> {
						metrics.ranOnUi();
						task.run();
						if (this.config.updateAtOnce && holder[0] != null && !holder[0].getControl().isDisposed()) {
							try {
								Object nativeCanvas = holder[0].getControl().getClass().getMethod("getControl").invoke(holder[0].getControl());
								Class.forName("org.eclipse.swt.widgets.Control").getMethod("update").invoke(nativeCanvas);
							} catch (ReflectiveOperationException exception) {
								throw new AssertionError(exception);
							}
						}
					});
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError(exception);
				}
			}, System::nanoTime, "Sans", this.config.fallback);
			holder[0] = canvas;
			((UITableLayout) window.getLayout()).set(canvas.getControl(), 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1,
					(float) this.config.width, (float) this.config.height, 0f);
			window.pack();
			window.setBounds(new UIRectangle(20, 20, this.config.width, this.config.height));
			window.open();
			window.moveToTop();
			if (!ArcadeStressTest.setting("tuxguitar.arcade.stress.background", "").isEmpty()) {
				// the window is left as the system puts it: possibly behind other windows (a test of the cause of the gaps, see the report)
			} else {
				try {
					// the window of the learner is the one in front: the system does not slow the painting of a window that is hidden or that is not the active one
					Object shell = window.getClass().getMethod("getControl").invoke(window);
					Class.forName("org.eclipse.swt.widgets.Shell").getMethod("forceActive").invoke(shell);
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError(exception);
				}
			}
			canvas.setObserver(metrics);
		}

		feederThread.start();
		sampler.start();
		input.engine().subscribe(listener);
		Method readAndDispatch = null;
		Method sleep = null;
		if (canvas != null) {
			readAndDispatch = this.display.getClass().getMethod("readAndDispatch");
			sleep = this.display.getClass().getMethod("sleep");
			// the window is open and painting the neck for a moment before the run begins, as the learner sees it in the preparation
			canvas.start();
			long warm = System.nanoTime() + 1_500_000_000L;
			while (System.nanoTime() < warm) {
				if (!(Boolean) readAndDispatch.invoke(this.display)) {
					sleep.invoke(this.display);
				}
			}
			metrics.beginRun(System.nanoTime());
			// what the UI thread is doing, every 4 ms: only to explain a gap between two paints, if there is one
			final Thread uiThread = Thread.currentThread();
			final StressMetrics watched = metrics;
			if (ArcadeStressTest.setting("tuxguitar.arcade.stress.probe", "").isEmpty()) {
				// the probe stops the UI thread to read its stack: it is a diagnostic, never part of the acceptance run
			} else {
			probe = new Thread(() -> {
				while (sampling.get() == 1) {
					StackTraceElement[] stack = uiThread.getStackTrace();
					StringBuilder where = new StringBuilder();
					for (int i = 0; i < Math.min(3, stack.length); i++) {
						where.append(i == 0 ? "" : " < ").append(stack[i].getClassName().replaceAll(".*\\.", "")).append('.').append(stack[i].getMethodName());
					}
					watched.sampleUiThread(System.nanoTime(), where.toString());
					LockSupport.parkNanos(4_000_000L);
				}
			}, "stress-ui-probe");
			probe.setDaemon(true);
			probe.start();
			}
		}
		FollowPlayback.Result started = run.start();
		if (started != FollowPlayback.Result.STARTED) {
			throw new AssertionError("the run did not start: " + started);
		}
		if (canvas != null) {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(this.config.seconds + 120L);
			while (ended.getCount() > 0 && System.nanoTime() < deadline) {
				if (!(Boolean) readAndDispatch.invoke(this.display)) {
					// asleep until the timer or the session posts something: the timer ticks sixty times a second
					sleep.invoke(this.display);
				}
			}
		} else {
			ended.await(this.config.seconds + 120L, TimeUnit.SECONDS);
		}
		boolean finished = ended.getCount() == 0;
		if (canvas != null) {
			canvas.stop();
			metrics.schedulerFigures(canvas.scheduler().lost(), canvas.scheduler().skipped());
		}
		input.engine().unsubscribe(listener);
		feeder.stop = true;
		sampling.set(0);
		run.stop();
		FollowSession.Snapshot last = latest.get();
		long gcCount = 0;
		long gcMillis = 0;
		for (java.lang.management.GarbageCollectorMXBean bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
			gcCount += Math.max(0, bean.getCollectionCount());
			gcMillis += Math.max(0, bean.getCollectionTime());
		}
		Result result = new Result();
		ArcadeResult arcade = arcadeRun.result(last);
		result.digest = digestOf(last, arcade, plan.sequence().getEvents());
		result.offsets = offsetsOf(last, plan.sequence().getEvents());
		result.summary = String.format(Locale.ROOT, "score %d of %d (%d %%), best streak %d", arcade.score(), arcade.maxScore(), arcade.percent(), arcade.bestStreak());
		result.events = last.results().size();
		result.judged = arcade.summary().events();
		result.captureDropped = input.droppedSamples();
		result.maxFeederLatenessMs = feeder.maxLateness.get() / 1_000_000L;
		result.frames = frames.get();
		result.maxFrameGapMs = maxGap.get() / 1_000_000L;
		result.maxTickStallMs = maxStall.get() / 1_000_000L;
		result.backingNotesExpected = score.backingNotes();
		result.backingNotesSent = midi.notesSent.get();
		result.gcCount = gcCount - gcCount0;
		result.gcMillis = gcMillis - gcMillis0;
		result.metrics = metrics;
		result.ended = (finished ? endReason.get() : "NOT ENDED IN TIME");
		result.sceneEvents = sceneEvents;
		result.tier = quality.tier().name();
		result.tierChanges = quality.changes();
		if (canvas != null) {
			result.renderer = canvas.rendererName();
			canvas.dispose();
			window.dispose();
		}
		TGThreadManager.getInstance(midi.context).dispose();
		return result;
	}

}
