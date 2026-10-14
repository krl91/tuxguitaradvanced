package app.tuxguitar.audio.engine;

import java.util.List;
import java.util.function.IntFunction;

import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.InputAssessor;
import app.tuxguitar.audio.engine.analysis.LevelCheckStats;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;
import app.tuxguitar.audio.engine.frontend.GainSuggester;
import app.tuxguitar.audio.engine.frontend.GainSuggestion;
import app.tuxguitar.audio.engine.frontend.InputWarning;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CaptureService;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.input.InputDeviceCatalog;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
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
		this.settingsStore = settingsStore;
		this.ringBuffer = new SampleRingBuffer(ringBufferCapacity);
		this.selection = selection;
		this.catalog = catalog;
		this.estimatorFactory = estimatorFactory;
		this.analysisService = new AnalysisService(context, this.ringBuffer, levelAnalyzer, articulationDetector,
				estimatorFactory.apply(INITIAL_SAMPLE_RATE), INITIAL_SAMPLE_RATE,
				TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE, TarsosFastYinEstimator.DEFAULT_HOP_SIZE);
		this.captureService = new CaptureService(context, catalog, selection, this.ringBuffer);
		this.captureService.setFormatListener(format -> rebuildAnalysis(Math.round(format.getSampleRate())));
		this.assessor = new InputAssessor(new InputAssessor.Parameters(), this::levelCheckCompleted);
		this.checkedInputRevision = selection.snapshot().getInputRevision();
		this.gainInputRevision = this.checkedInputRevision;
		this.selection.addListener(this::selectionChanged);
		this.captureService.addListener(this::captureEvent);
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
				return new AudioEngine(singletonContext, new InputDeviceCatalog(),
						CurrentInputSelection.getInstance(singletonContext), DEFAULT_RING_BUFFER_CAPACITY,
						levelAnalyzer(settings), articulationDetector(settings),
						sampleRate -> PitchEngineCatalog.resolve(store.get().get(RecognitionParameters.ENGINE_PITCH)).create(sampleRate),
						store);
			}
		});
	}

	/**
	 * Starts capture for this consumer if it is the first one, and checks a selected input that
	 * was not checked yet (FR-038).
	 */
	public void acquire(String consumerId) {
		this.captureService.acquire(consumerId);
		CurrentInputSelection.Snapshot snapshot = this.selection.snapshot();
		if( snapshot.getInput() != null && snapshot.getAssessment().getStatus() == InputAssessment.Status.UNCHECKED ) {
			runLevelCheck();
		}
	}

	/** Stops capture when the last consumer releases it. */
	public void release(String consumerId) {
		this.captureService.release(consumerId);
	}

	/**
	 * Starts a quick level check of the selected input while the learner plays (FR-038, also
	 * used for explicit recalibration, FR-039); the result updates the shared selection.
	 * Capture must be held by a consumer.
	 */
	public void runLevelCheck() {
		this.assessor.runLevelCheck();
		this.analysisService.subscribe(this.assessor);
	}

	public void subscribe(AnalysisService.Listener listener) {
		this.analysisService.subscribe(listener);
	}

	/**
	 * Adds the polyphonic spectrum to analysis frames (plan stage D), for chord practice only;
	 * the tuner does not need it. Single-note analysis is unaffected.
	 */
	public void setSpectrumEnabled(boolean enabled) {
		this.analysisService.setSpectrumEnabled(enabled);
	}

	/** The pitches expected now: frames then carry {@link app.tuxguitar.audio.engine.recognition.TargetEvidence} for them (null: none). */
	public void setExpectedPitches(int[] midi) {
		this.analysisService.setExpectedPitches(midi);
	}

	/** The pitches the attack analysis explains the sound with (null: off); frames then carry {@link app.tuxguitar.audio.engine.analysis.AttackEvidence}. */
	public void setAttackCandidates(int[] pitches) {
		this.analysisService.setAttackCandidates(pitches);
	}

	/** Every hop of the signal the analysis sees, with its configure (spec 010): see {@link AnalysisService#addHopListener}. */
	public void addHopListener(app.tuxguitar.audio.engine.analysis.HopListener listener) {
		this.analysisService.addHopListener(listener);
	}

	public void removeHopListener(app.tuxguitar.audio.engine.analysis.HopListener listener) {
		this.analysisService.removeHopListener(listener);
	}

	public void unsubscribe(AnalysisService.Listener listener) {
		this.analysisService.unsubscribe(listener);
	}

	public void addCaptureListener(CaptureService.Listener listener) {
		this.captureService.addListener(listener);
	}

	public void removeCaptureListener(CaptureService.Listener listener) {
		this.captureService.removeListener(listener);
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

	/** Diagnostic for tests and measurements only; never shown to the learner. */
	/** The clock that dates the samples of the capture on the wall clock (spec 002 T093). */
	public app.tuxguitar.audio.engine.input.CaptureClock getCaptureClock() {
		return this.captureService.getCaptureClock();
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
		return this.ringBuffer.getDroppedSampleCount();
	}

	SampleRingBuffer getRingBuffer() {
		return this.ringBuffer;
	}

	private void levelCheckCompleted(InputAssessment assessment) {
		this.analysisService.unsubscribe(this.assessor);
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
		if( snapshot.getInput() != null && this.captureService.getConsumerCount() > 0 ) {
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
		if( this.settingsStore == null ) {
			this.analysisService.reconfigure(sampleRate, this.estimatorFactory.apply(sampleRate),
					this.ringBuffer.getWrittenSampleCount());
			return;
		}
		RecognitionSettings settings = this.settingsStore.get();
		this.analysisService.reconfigure(sampleRate, this.estimatorFactory.apply(sampleRate),
				this.ringBuffer.getWrittenSampleCount(), levelAnalyzer(settings), articulationDetector(settings));
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
		}
	}

	/** Sets the gain (limited to the allowed range) and returns the value that was set. The level meter and the recognition follow at once. */
	public double setInputGain(double multiplier) {
		syncGainWithInput();
		return this.analysisService.getFrontEnd().getGain().setMultiplier(multiplier);
	}

	public synchronized void resetInputGain() {
		this.analysisService.getFrontEnd().getGain().resetToNeutral();
		this.gainInputRevision = this.selection.snapshot().getInputRevision();
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
