package app.tuxguitar.audio.engine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import javax.sound.sampled.AudioFormat;

import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.InputAssessor;
import app.tuxguitar.audio.engine.analysis.LevelCheckStats;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;
import app.tuxguitar.audio.engine.backend.JavaBackend;
import app.tuxguitar.audio.engine.backend.NativeBackend;
import app.tuxguitar.audio.engine.backend.RecognitionBackend;
import app.tuxguitar.audio.engine.backend.RecognitionBackendFactory;
import app.tuxguitar.audio.engine.backend.RecognitionEngineNotice;
import app.tuxguitar.audio.engine.frontend.FrontEnd;
import app.tuxguitar.audio.engine.frontend.GainSuggester;
import app.tuxguitar.audio.engine.frontend.GainSuggestion;
import app.tuxguitar.audio.engine.frontend.InputWarning;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CaptureService;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.input.InputDeviceCatalog;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * The shared audio pipeline of one {@code TGContext}, used by the tuner and by practice:
 * capture → ring buffer → analysis, over the shared {@link CurrentInputSelection}.
 *
 * <p>Created lazily on first use. Capture runs only while consumers hold it
 * ({@link #acquire}/{@link #release}); analysis runs only while at least one listener is
 * subscribed. Each time capture opens an input, analysis is rebuilt for the sample rate
 * actually negotiated (no resampling). Consumers are expected to subscribe while they hold
 * capture: capture without analysis fills the ring buffer and then drops new samples.</p>
 *
 * <p>The capture and the analysis of a run are those of a {@link RecognitionBackend} (spec 023): the Java chain, or the native engine when the engine
 * that the settings resolve to is the native one. The backend is chosen when the first consumer acquires; what the consumers set (listeners, requests,
 * front end) is kept here and given to the backend that runs.</p>
 */
public final class AudioEngine {

	/** Default ring-buffer capacity in mono samples (one second at 48 kHz). */
	public static final int DEFAULT_RING_BUFFER_CAPACITY = 48000;

	/** Provisional level defaults, to be refined from the benchmark (tasks T076). */
	public static final double DEFAULT_TOO_LOW_RMS_DBFS = -40.0;
	public static final double DEFAULT_TOO_HIGH_RMS_DBFS = -6.0;
	public static final double DEFAULT_CLIPPED_SAMPLE_LEVEL = 0.99;
	public static final double DEFAULT_CLIPPING_RATIO = 0.10;

	private static final int INITIAL_SAMPLE_RATE = 48000;
	/** How long a run waits for the native input to open before it starts on Java (spec 023, FR-006, SC-003). */
	static final long NATIVE_OPEN_TIMEOUT_MILLIS = 1500L;

	private final SampleRingBuffer ringBuffer;
	private final CurrentInputSelection selection;
	private final InputDeviceCatalog catalog;
	private final CaptureService captureService;
	private final AnalysisService analysisService;
	private final IntFunction<PitchEstimator> estimatorFactory;
	private final InputAssessor assessor;
	private long checkedInputRevision;
	/** Null for engines built without settings (tests); then the analysers never change. */
	private final RecognitionSettingsStore settingsStore;
	private volatile int lastSampleRate = INITIAL_SAMPLE_RATE;
	/** Revision of the input the current gain was set for (see {@link #syncGainWithInput}). */
	private long gainInputRevision;
	/** The last format the capture negotiated, and the id of the input it was negotiated for. */
	private static final class Negotiated {
		final String inputId;
		final AudioFormat format;

		Negotiated(String inputId, AudioFormat format) {
			this.inputId = inputId;
			this.format = format;
		}
	}

	private volatile Negotiated negotiated;

	private final TGContext context;
	private final JavaBackend javaBackend;
	/** Null for the engines of the package constructors (tests): they keep the Java backend. */
	private final RecognitionBackendFactory backendFactory;
	private NativeBackend nativeBackend;
	private volatile RecognitionBackend activeBackend;
	/** Guards the choice of the backend and the moves of what the consumers set. */
	private final Object backendLock = new Object();
	private final List<RecognitionEngineNotice.Listener> noticeListeners = new CopyOnWriteArrayList<RecognitionEngineNotice.Listener>();
	private final List<AnalysisService.Listener> subscribers = new CopyOnWriteArrayList<AnalysisService.Listener>();
	private final List<HopListener> hopListeners = new CopyOnWriteArrayList<HopListener>();
	private final List<CaptureService.Listener> captureListeners = new CopyOnWriteArrayList<CaptureService.Listener>();
	private volatile boolean spectrumEnabled;
	private volatile int[] expectedPitches;
	private volatile int[] chordRequired;
	private volatile int[] chordTolerated;
	private volatile int[] attackCandidates;

	AudioEngine(TGContext context, InputDeviceCatalog catalog, CurrentInputSelection selection,
			int ringBufferCapacity, LevelAnalyzer levelAnalyzer, IntFunction<PitchEstimator> estimatorFactory) {
		this(context, catalog, selection, ringBufferCapacity, levelAnalyzer, new ArticulationDetector(), estimatorFactory, null);
	}

	/**
	 * @param settingsStore the recognition settings whose engine values (level thresholds,
	 *        attack detection, pitch engine) this engine follows, or null
	 */
	AudioEngine(TGContext context, InputDeviceCatalog catalog, CurrentInputSelection selection,
			int ringBufferCapacity, LevelAnalyzer levelAnalyzer, ArticulationDetector articulationDetector,
			IntFunction<PitchEstimator> estimatorFactory, RecognitionSettingsStore settingsStore) {
		this(context, catalog, selection, ringBufferCapacity, levelAnalyzer, articulationDetector, estimatorFactory, settingsStore, null);
	}

	/** @param backendFactory how the backend of a run is chosen, or null to keep the Java backend (spec 023) */
	AudioEngine(TGContext context, InputDeviceCatalog catalog, CurrentInputSelection selection,
			int ringBufferCapacity, LevelAnalyzer levelAnalyzer, ArticulationDetector articulationDetector,
			IntFunction<PitchEstimator> estimatorFactory, RecognitionSettingsStore settingsStore, RecognitionBackendFactory backendFactory) {
		this.context = context;
		this.backendFactory = backendFactory;
		this.settingsStore = settingsStore;
		this.ringBuffer = new SampleRingBuffer(ringBufferCapacity);
		this.selection = selection;
		this.catalog = catalog;
		this.estimatorFactory = estimatorFactory;
		this.analysisService = new AnalysisService(context, this.ringBuffer, levelAnalyzer, articulationDetector,
				estimatorFactory.apply(INITIAL_SAMPLE_RATE), INITIAL_SAMPLE_RATE,
				TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE, TarsosFastYinEstimator.DEFAULT_HOP_SIZE);
		PitchEstimator initial = estimatorFactory.apply(INITIAL_SAMPLE_RATE);
		float[] nativeParameters = nativeParameters(initial, levelAnalyzer, articulationDetector);
		if( nativeParameters != null ) {
			this.analysisService.reconfigure(INITIAL_SAMPLE_RATE, initial, 0L, levelAnalyzer, articulationDetector, nativeParameters);
		}
		this.captureService = new CaptureService(context, catalog, selection, this.ringBuffer);
		this.captureService.setFormatListener(format -> {
			formatNegotiated(format);
			rebuildAnalysis(Math.round(format.getSampleRate()));
		});
		this.javaBackend = new JavaBackend(this.captureService, this.analysisService, this.ringBuffer);
		this.activeBackend = this.javaBackend;
		this.assessor = new InputAssessor(new InputAssessor.Parameters(), this::levelCheckCompleted);
		this.checkedInputRevision = selection.snapshot().getInputRevision();
		this.gainInputRevision = this.checkedInputRevision;
		this.selection.addListener(this::selectionChanged);
		addCaptureListener(this::captureEvent);
		if( settingsStore != null ) {
			settingsStore.addListener(this::settingsChanged);
			applyFrontEndSettings(settingsStore.get());
		}
	}

	public static AudioEngine getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, AudioEngine.class.getName(), new TGSingletonFactory<AudioEngine>() {
			@Override
			public AudioEngine createInstance(TGContext singletonContext) {
				final RecognitionSettingsStore store = RecognitionSettingsStore.getInstance(singletonContext);
				RecognitionSettings settings = store.get();
				AudioEngine engine = new AudioEngine(singletonContext, new InputDeviceCatalog(),
						CurrentInputSelection.getInstance(singletonContext), DEFAULT_RING_BUFFER_CAPACITY,
						levelAnalyzer(settings), articulationDetector(settings),
						sampleRate -> PitchEngineCatalog.resolve(store.get().get(RecognitionParameters.ENGINE_PITCH)).create(sampleRate),
						store, RecognitionBackendFactory.PRODUCTION);
				// the profile of the selected device is laid over the settings, for every reader, whether or not a window is open (spec 013)
				app.tuxguitar.audio.engine.calibration.ProfileActivator.attach(singletonContext, engine);
				return engine;
			}
		});
	}

	/**
	 * Starts capture for this consumer if it is the first one, and checks a selected input that
	 * was not checked yet (FR-038).
	 */
	public void acquire(String consumerId) {
		NativeRecognitionException fallback = null;
		synchronized (this.backendLock) {
			if( this.activeBackend.getConsumerCount() == 0 ) {
				fallback = chooseBackend();
			}
			if( fallback != null && fallback.getReason() == NativeRecognitionException.Reason.PERMISSION_DENIED ) {
				this.nativeBackend.holdRefused(consumerId, fallback);
				fallback = null;
			} else {
				this.activeBackend.acquire(consumerId);
			}
		}
		if( fallback != null ) {
			tell(new RecognitionEngineNotice(fallback.getReason(), fallback.getDetail()));
		}
		CurrentInputSelection.Snapshot snapshot = this.selection.snapshot();
		if( snapshot.getInput() != null && snapshot.getAssessment().getStatus() == InputAssessment.Status.UNCHECKED ) {
			runLevelCheck();
		}
	}

	/** Stops capture when the last consumer releases it. */
	public void release(String consumerId) {
		RecognitionBackend backend;
		synchronized (this.backendLock) {
			backend = this.activeBackend;
		}
		// outside the lock: closing waits for the frame thread, whose listeners may subscribe or unsubscribe
		backend.release(consumerId);
	}

	/**
	 * The backend of the run that starts (no consumer holds the capture): the native engine when the settings resolve to it and it opens the input, else
	 * the Java chain. A changed choice therefore applies from the next run, never during one (FR-004).
	 *
	 * @return why the native engine that was asked for does not run this run, or null: the Java chain then runs it (FR-006), except for a refused
	 *         permission, which leaves the native backend chosen with no stream (no fallback: the Java capture would be refused too)
	 */
	private NativeRecognitionException chooseBackend() {
		if( this.backendFactory == null ) {
			switchTo(this.javaBackend);
			return null;
		}
		RecognitionSettings settings = currentSettings();
		if( this.backendFactory.resolveKind(settings) != PitchEngineCatalog.Kind.NATIVE ) {
			switchTo(this.javaBackend);
			String missing = this.backendFactory.nativeLibraryFailure(settings);
			return (missing != null ? new NativeRecognitionException(NativeRecognitionException.Reason.LIBRARY_MISSING, missing) : null);
		}
		NativeBackend chosen = nativeBackend();
		switchTo(chosen);
		try {
			openWithin(chosen, NATIVE_OPEN_TIMEOUT_MILLIS);
			return null;
		} catch (NativeRecognitionException exception) {
			if( exception.getReason() == NativeRecognitionException.Reason.PERMISSION_DENIED ) {
				return exception;
			}
			System.err.println("TuxGuitar recognition: the native engine could not open the input (" + exception.getMessage() + "); the Java engine is used.");
			switchTo(this.javaBackend);
			return exception;
		}
	}

	/**
	 * Opens the native input on a thread of its own and waits at most {@code millis}: an open that never returns is abandoned (a stream failure), and
	 * what it opens later is closed at once (FR-006, SC-003: the run starts within 2 s).
	 */
	private static void openWithin(NativeBackend backend, long millis) throws NativeRecognitionException {
		final int pending = 0, finished = 1, abandoned = 2;
		AtomicInteger state = new AtomicInteger(pending);
		AtomicReference<NativeRecognitionException> failure = new AtomicReference<NativeRecognitionException>();
		CountDownLatch done = new CountDownLatch(1);
		Thread opener = new Thread(() -> {
			try {
				backend.openFor(() -> state.compareAndSet(pending, finished));
			} catch (NativeRecognitionException exception) {
				failure.set(exception);
			} catch (RuntimeException exception) {
				failure.set(new NativeRecognitionException(NativeRecognitionException.Reason.STREAM_FAILED, String.valueOf(exception)));
			} finally {
				state.compareAndSet(pending, finished);
				done.countDown();
			}
		}, "tuxguitar-native-open");
		opener.setDaemon(true);
		opener.start();
		boolean interrupted = false;
		try {
			done.await(millis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException exception) {
			interrupted = true;
		}
		if( state.compareAndSet(pending, abandoned) ) {
			if( interrupted ) {
				Thread.currentThread().interrupt();
			}
			throw new NativeRecognitionException(NativeRecognitionException.Reason.STREAM_FAILED, "the input did not answer within " + millis + " ms");
		}
		// finished: the opener is only counting down
		try {
			done.await();
		} catch (InterruptedException exception) {
			interrupted = true;
		}
		if( interrupted ) {
			Thread.currentThread().interrupt();
		}
		if( failure.get() != null ) {
			throw failure.get();
		}
	}

	/** Who is told when a run that asked for the native engine runs on Java (spec 023, FR-006). */
	public void addRecognitionEngineNoticeListener(RecognitionEngineNotice.Listener listener) {
		this.noticeListeners.add(listener);
	}

	public void removeRecognitionEngineNoticeListener(RecognitionEngineNotice.Listener listener) {
		this.noticeListeners.remove(listener);
	}

	private void tell(RecognitionEngineNotice notice) {
		for( RecognitionEngineNotice.Listener listener : this.noticeListeners ) {
			try {
				listener.recognitionEngineNotice(notice);
			} catch (RuntimeException exception) {
				exception.printStackTrace();
			}
		}
	}

	private NativeBackend nativeBackend() {
		if( this.nativeBackend == null ) {
			this.nativeBackend = this.backendFactory.createNative(this.context, this.selection, this::liveNativeParameters);
			this.nativeBackend.setTuning(() -> NativeParameters.tuning(currentSettings()));
			this.nativeBackend.setFormatListener(format -> {
				formatNegotiated(format);
				this.lastSampleRate = Math.round(format.getSampleRate());
			});
		}
		return this.nativeBackend;
	}

	/** Moves what the consumers set from the backend that ran to this one. */
	private void switchTo(RecognitionBackend next) {
		RecognitionBackend previous = this.activeBackend;
		if( previous == next ) {
			return;
		}
		for( AnalysisService.Listener listener : this.subscribers ) {
			previous.unsubscribe(listener);
			next.subscribe(listener);
		}
		for( HopListener listener : this.hopListeners ) {
			previous.removeHopListener(listener);
			next.addHopListener(listener);
		}
		for( CaptureService.Listener listener : this.captureListeners ) {
			previous.removeCaptureListener(listener);
			next.addCaptureListener(listener);
		}
		next.setSpectrumEnabled(this.spectrumEnabled);
		next.setExpectedPitches(this.expectedPitches);
		next.setExpectedChord(this.chordRequired, this.chordTolerated);
		next.setAttackCandidates(this.attackCandidates);
		this.activeBackend = next;
	}

	private RecognitionSettings currentSettings() {
		return (this.settingsStore != null ? this.settingsStore.get() : RecognitionSettings.defaults());
	}

	/** The values of the native engine now: the settings and the live front end (the gain is in memory only). */
	private float[] liveNativeParameters() {
		FrontEnd frontEnd = this.analysisService.getFrontEnd();
		return NativeParameters.of(currentSettings(), frontEnd.getGain().getMultiplier(), frontEnd.isNoiseReduction(), frontEnd.isSubtraction());
	}

	/** The front end or the settings changed: the native engine of a run gets its new values (the Java analysis reads its own). */
	private void pushNativeParameters() {
		RecognitionBackend backend = this.activeBackend;
		if( backend != null && backend.engineKind() == PitchEngineCatalog.Kind.NATIVE ) {
			backend.configure(liveNativeParameters());
			backend.configureTuning(NativeParameters.tuning(currentSettings()));
		}
	}

	/** The backend of the current or last run; diagnostics and tests only. */
	RecognitionBackend getActiveBackend() {
		return this.activeBackend;
	}

	/** The consumers of the Java capture; tests only. */
	int getJavaCaptureConsumerCount() {
		return this.captureService.getConsumerCount();
	}

	/** The native strum clip measure's reading for the pre-run check, or null when the Java chain runs (its hop listener meter is used then). */
	public StrumClipMeter.Reading nativeStrumReading() {
		RecognitionBackend backend = this.activeBackend;
		return (backend instanceof NativeBackend ? ((NativeBackend) backend).strumReading() : null);
	}

	/** Starts a new native strum clip check; nothing when the Java chain runs. */
	public void resetNativeStrumReading() {
		RecognitionBackend backend = this.activeBackend;
		if( backend instanceof NativeBackend ) {
			((NativeBackend) backend).resetStrumReading();
		}
	}

	/**
	 * Starts a quick level check of the selected input while the learner plays (FR-038, also
	 * used for explicit recalibration, FR-039); the result updates the shared selection.
	 * Capture must be held by a consumer.
	 */
	public void runLevelCheck() {
		// one check at a time: asked again while one listens, it would start over and subscribe the assessor twice (spec 025, FR-003, research R2)
		if( this.assessor.isChecking() ) {
			return;
		}
		this.assessor.runLevelCheck();
		subscribe(this.assessor);
	}

	/** A level check is listening now (spec 025: the practice window says so, and starts no second one). */
	public boolean isLevelCheckRunning() {
		return this.assessor.isChecking();
	}

	public void subscribe(AnalysisService.Listener listener) {
		synchronized (this.backendLock) {
			this.subscribers.add(listener);
			this.activeBackend.subscribe(listener);
		}
	}

	/**
	 * Adds the polyphonic spectrum to analysis frames (plan stage D), for chord practice only;
	 * the tuner does not need it. Single-note analysis is unaffected.
	 */
	public void setSpectrumEnabled(boolean enabled) {
		synchronized (this.backendLock) {
			this.spectrumEnabled = enabled;
			this.activeBackend.setSpectrumEnabled(enabled);
		}
	}

	/** The pitches expected now: frames then carry {@link app.tuxguitar.audio.engine.recognition.TargetEvidence} for them (null: none). */
	public void setExpectedPitches(int[] midi) {
		synchronized (this.backendLock) {
			this.expectedPitches = (midi == null || midi.length == 0 ? null : midi.clone());
			this.activeBackend.setExpectedPitches(this.expectedPitches);
		}
	}

	/**
	 * The chord expected now (null required: none). With the native engine the frames then carry its {@link app.tuxguitar.audio.engine.analysis.ChordEvidence};
	 * with the Java chain the matcher computes it from the spectrum, as before (spec 023, contract §4).
	 */
	public void setExpectedChord(int[] required, int[] tolerated) {
		synchronized (this.backendLock) {
			this.chordRequired = (required == null ? null : required.clone());
			this.chordTolerated = (required == null ? null : (tolerated == null ? new int[0] : tolerated.clone()));
			this.activeBackend.setExpectedChord(this.chordRequired, this.chordTolerated);
		}
	}

	/** The pitches the attack analysis explains the sound with (null: off); frames then carry {@link app.tuxguitar.audio.engine.analysis.AttackEvidence}. */
	public void setAttackCandidates(int[] pitches) {
		synchronized (this.backendLock) {
			this.attackCandidates = (pitches == null || pitches.length == 0 ? null : pitches.clone());
			this.activeBackend.setAttackCandidates(this.attackCandidates);
		}
	}

	/** Every hop of the signal the analysis sees, with its configure (spec 010): see {@link AnalysisService#addHopListener}. */
	public void addHopListener(app.tuxguitar.audio.engine.analysis.HopListener listener) {
		synchronized (this.backendLock) {
			this.hopListeners.add(listener);
			this.activeBackend.addHopListener(listener);
		}
	}

	public void removeHopListener(app.tuxguitar.audio.engine.analysis.HopListener listener) {
		synchronized (this.backendLock) {
			this.hopListeners.remove(listener);
			this.activeBackend.removeHopListener(listener);
		}
	}

	public void unsubscribe(AnalysisService.Listener listener) {
		synchronized (this.backendLock) {
			this.subscribers.remove(listener);
			this.activeBackend.unsubscribe(listener);
		}
	}

	public void addCaptureListener(CaptureService.Listener listener) {
		synchronized (this.backendLock) {
			this.captureListeners.add(listener);
			this.activeBackend.addCaptureListener(listener);
		}
	}

	public void removeCaptureListener(CaptureService.Listener listener) {
		synchronized (this.backendLock) {
			this.captureListeners.remove(listener);
			this.activeBackend.removeCaptureListener(listener);
		}
	}

	/**
	 * When no input is selected yet, selects the first listed input (the system default comes
	 * first). Used by the tuner and practice when they open; never switches an existing choice.
	 *
	 * @return the selected input, or null when none is available
	 */
	public AudioInputDescriptor selectDefaultInputIfNone() {
		return selectDefaultInputIfNone(this.catalog.listInputs());
	}

	AudioInputDescriptor selectDefaultInputIfNone(List<AudioInputDescriptor> available) {
		synchronized (this) {
			AudioInputDescriptor current = this.selection.snapshot().getInput();
			if( current != null || available.isEmpty() ) {
				return current;
			}
			this.selection.select(available.get(0), null);
			return available.get(0);
		}
	}

	public CurrentInputSelection getSelection() {
		return this.selection;
	}

	public InputDeviceCatalog getCatalog() {
		return this.catalog;
	}

	/** The level analyser the analysis uses (from the saved settings); diagnostics and tests only. */
	public LevelAnalyzer getLevelAnalyzer() {
		return this.analysisService.getRequestedLevelAnalyzer();
	}

	/** The articulation detector the analysis uses (from the saved settings); diagnostics and tests only. */
	public ArticulationDetector getArticulationDetector() {
		return this.analysisService.getRequestedArticulationDetector();
	}

	/** Keeps the format the capture just negotiated, for the input selected now (capture thread). */
	void formatNegotiated(AudioFormat format) {
		AudioInputDescriptor input = this.selection.snapshot().getInput();
		this.negotiated = (input != null ? new Negotiated(input.getId(), format) : null);
	}

	/**
	 * The format the capture negotiated for this input, or null when it has not been opened since that input was selected. The selection itself never
	 * holds a format (every caller selects an input alone), so the route of the practice window reads it here (spec 016).
	 */
	public AudioFormat getNegotiatedFormat(AudioInputDescriptor input) {
		Negotiated last = this.negotiated;
		return (input != null && last != null && input.getId().equals(last.inputId) ? last.format : null);
	}

	/** Diagnostic for tests and measurements only; never shown to the learner. */
	/** The clock that dates the samples of the capture on the wall clock (spec 002 T093). */
	public app.tuxguitar.audio.engine.input.CaptureClock getCaptureClock() {
		return this.activeBackend.clock();
	}

	/**
	 * The wall clock time ({@code System.nanoTime()} scale) at which the last sample of this frame is taken to be available to the application,
	 * or {@link app.tuxguitar.audio.engine.input.CaptureClock#NOT_READY} when the frame does not come from the live capture or the clock is not ready.
	 */
	public long wallNanosOf(app.tuxguitar.audio.engine.analysis.AnalysisFrame frame) {
		if( frame.getCaptureSampleEnd() == app.tuxguitar.audio.engine.analysis.AnalysisFrame.NO_CAPTURE_SAMPLE ) {
			return app.tuxguitar.audio.engine.input.CaptureClock.NOT_READY;
		}
		return this.getCaptureClock().wallNanosOf(frame.getCaptureSampleEnd());
	}

	public long getDroppedSampleCount() {
		NativeBackend nativeCapture = this.nativeBackend;
		return this.ringBuffer.getDroppedSampleCount() + (nativeCapture != null ? nativeCapture.getDroppedSampleCount() : 0L);
	}

	SampleRingBuffer getRingBuffer() {
		return this.ringBuffer;
	}

	private void levelCheckCompleted(InputAssessment assessment) {
		unsubscribe(this.assessor);
		this.selection.updateAssessment(assessment);
	}

	/** A newly selected input is checked again when capture is running (FR-038, FR-039). */
	private void selectionChanged(CurrentInputSelection.Snapshot snapshot) {
		synchronized (this) {
			if( snapshot.getInputRevision() == this.checkedInputRevision ) {
				return;
			}
			this.checkedInputRevision = snapshot.getInputRevision();
		}
		// the gain belongs to one input and one session: a new input starts neutral (spec 002, FR-063)
		syncGainWithInput();
		// Without capture no frames arrive; acquire() starts the check then (no cancel here: a
		// late notification must not cancel a check that acquire() already started).
		if( snapshot.getInput() != null && this.activeBackend.getConsumerCount() > 0 ) {
			runLevelCheck();
		}
	}

	private void captureEvent(CaptureService.Event event) {
		if( (event.getType() == CaptureService.EventType.DEVICE_LOST || event.getType() == CaptureService.EventType.CAPTURE_ERROR)
				&& this.selection.snapshot().getInput() != null ) {
			this.assessor.deviceLost();
		}
	}

	void rebuildAnalysis(int sampleRate) {
		this.lastSampleRate = sampleRate;
		PitchEstimator estimator = this.estimatorFactory.apply(sampleRate);
		if( this.settingsStore == null ) {
			LevelAnalyzer levels = this.analysisService.getRequestedLevelAnalyzer();
			ArticulationDetector articulation = this.analysisService.getRequestedArticulationDetector();
			this.analysisService.reconfigure(sampleRate, estimator, this.ringBuffer.getWrittenSampleCount(), levels, articulation,
					nativeParameters(estimator, levels, articulation));
			return;
		}
		RecognitionSettings settings = this.settingsStore.get();
		LevelAnalyzer levels = levelAnalyzer(settings);
		ArticulationDetector articulation = articulationDetector(settings);
		this.analysisService.reconfigure(sampleRate, estimator, this.ringBuffer.getWrittenSampleCount(), levels, articulation,
				nativeParameters(estimator, levels, articulation));
	}

	/**
	 * The parameters of the native per-hop analysis when the native engine is the one these settings run and the estimator is the catalog's Java one it
	 * stands for (spec 023); null otherwise (the Java stages, and any other estimator, such as a test's).
	 */
	private float[] nativeParameters(PitchEstimator estimator, LevelAnalyzer levels, ArticulationDetector articulation) {
		if( !(estimator instanceof TarsosFastYinEstimator) ) {
			return null;
		}
		RecognitionSettings settings = (this.settingsStore != null ? this.settingsStore.get() : RecognitionSettings.defaults());
		if( PitchEngineCatalog.resolveKind(settings) != PitchEngineCatalog.Kind.NATIVE ) {
			return null;
		}
		return app.tuxguitar.audio.engine.nativeengine.NativeParameters.of(levels, articulation);
	}

	/**
	 * New engine values restart the analysis cleanly at the current rate (from the next samples,
	 * with fresh detectors), also during capture; other settings do not concern the engine.
	 */
	private void settingsChanged(RecognitionSettings previous, RecognitionSettings current) {
		applyFrontEndSettings(current);
		if( engineValuesDiffer(previous, current) ) {
			rebuildAnalysis(this.lastSampleRate);
		}
		pushNativeParameters();
	}

	/** The saved noise-reduction choice reaches the front end at once; it needs no restart of the analysis. */
	private void applyFrontEndSettings(RecognitionSettings settings) {
		this.analysisService.getFrontEnd().setNoiseReduction(settings.get(RecognitionParameters.INPUT_NOISE_REDUCTION));
	}

	// ---------------------------------------------------------------- input gain and noise reduction (spec 002, FR-060…FR-066)

	/** The software gain applied to the input, 1.0 when neutral. In memory only: back to 1.0 when the input changes and at start. */
	public double getInputGain() {
		syncGainWithInput();
		return this.analysisService.getFrontEnd().getGain().getMultiplier();
	}

	/**
	 * The gain belongs to one input: when the input changed since it was set, it is back to neutral
	 * (spec 002, FR-063). Compared by input revision, so it does not depend on when the (asynchronous)
	 * selection notification arrives.
	 */
	private synchronized void syncGainWithInput() {
		long revision = this.selection.snapshot().getInputRevision();
		if( revision != this.gainInputRevision ) {
			this.analysisService.getFrontEnd().getGain().resetToNeutral();
			this.gainInputRevision = revision;
			pushNativeParameters();
		}
	}

	/** Sets the gain (limited to the allowed range) and returns the value that was set. The level meter and the recognition follow at once. */
	public double setInputGain(double multiplier) {
		syncGainWithInput();
		double set = this.analysisService.getFrontEnd().getGain().setMultiplier(multiplier);
		pushNativeParameters();
		return set;
	}

	public synchronized void resetInputGain() {
		this.analysisService.getFrontEnd().getGain().resetToNeutral();
		this.gainInputRevision = this.selection.snapshot().getInputRevision();
		pushNativeParameters();
	}

	/** Whether the saved setting asks for the noise gate. */
	public boolean isNoiseReductionEnabled() {
		return this.analysisService.getFrontEnd().isNoiseReduction();
	}

	/** What the last level check measured, or null before any check. */
	public LevelCheckStats getLevelCheckStats() {
		return this.assessor.getLastStats();
	}

	/**
	 * A gain proposed from the last level check, or empty when the suggestion is switched off in the
	 * settings or the check heard no playing. It never changes the gain: the learner confirms it.
	 */
	public java.util.Optional<GainSuggestion> suggestGain() {
		if( this.settingsStore != null && !this.settingsStore.get().get(RecognitionParameters.INPUT_GAIN_SUGGESTION) ) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.ofNullable(GainSuggester.suggest(this.assessor.getLastStats(), getInputGain()));
	}

	/**
	 * A warning about the raised gain, from the last level check and the latest frame: the boost mostly
	 * amplifies noise, or it makes the signal clip (FR-062).
	 */
	public InputWarning getInputWarning(AnalysisFrame latest) {
		if( getInputGain() <= 1.0 ) {
			return InputWarning.NONE;
		}
		if( latest != null && latest.getLevelState() == AnalysisFrame.LevelState.CLIPPING ) {
			return InputWarning.BOOST_CAUSES_CLIPPING;
		}
		LevelCheckStats stats = this.assessor.getLastStats();
		if( stats != null && stats.hasPlaying() && GainSuggester.isNoiseDominated(stats) ) {
			return InputWarning.BOOST_AMPLIFIES_NOISE;
		}
		return InputWarning.NONE;
	}

	/** Delay added by the front end's stages in use, in milliseconds (counted in the latency budget, FR-065). */
	public double getFrontEndLatencyMillis() {
		return this.analysisService.getFrontEnd().latencyHops() * TarsosFastYinEstimator.DEFAULT_HOP_SIZE * 1000.0 / this.lastSampleRate;
	}

	static boolean engineValuesDiffer(RecognitionSettings a, RecognitionSettings b) {
		RecognitionParameter[] engineParameters = { RecognitionParameters.INPUT_LEVEL_MIN, RecognitionParameters.INPUT_LEVEL_MAX,
				RecognitionParameters.LEVEL_CLIP_SAMPLE, RecognitionParameters.LEVEL_CLIP_SHARE, RecognitionParameters.NOTE_ATTACK,
				RecognitionParameters.ATTACK_MIN_LEVEL, RecognitionParameters.ENGINE_PITCH };
		for( RecognitionParameter parameter : engineParameters ) {
			if( !a.getValue(parameter).equals(b.getValue(parameter)) ) {
				return true;
			}
		}
		return false;
	}

	/** The level analyser for these settings (defaults: the former hard-coded values). */
	public static LevelAnalyzer levelAnalyzer(RecognitionSettings settings) {
		return new LevelAnalyzer(settings.get(RecognitionParameters.INPUT_LEVEL_MIN), settings.get(RecognitionParameters.INPUT_LEVEL_MAX),
				settings.get(RecognitionParameters.LEVEL_CLIP_SAMPLE), settings.get(RecognitionParameters.LEVEL_CLIP_SHARE));
	}

	/** The articulation detector for these settings; only the gate and minimum level are settings. */
	public static ArticulationDetector articulationDetector(RecognitionSettings settings) {
		return new ArticulationDetector(ArticulationDetector.DEFAULT_FLUX_MEDIAN_FACTOR, ArticulationDetector.DEFAULT_FLUX_OFFSET,
				settings.get(RecognitionParameters.ATTACK_MIN_LEVEL), ArticulationDetector.DEFAULT_REFRACTORY_HOPS,
				settings.get(RecognitionParameters.NOTE_ATTACK));
	}
}
