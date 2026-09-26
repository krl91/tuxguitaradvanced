package app.tuxguitar.audio.engine;

import java.util.function.IntFunction;

import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;
import app.tuxguitar.audio.engine.input.CaptureService;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputDeviceCatalog;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
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

	AudioEngine(TGContext context, InputDeviceCatalog catalog, CurrentInputSelection selection,
			int ringBufferCapacity, LevelAnalyzer levelAnalyzer, IntFunction<PitchEstimator> estimatorFactory) {
		this.ringBuffer = new SampleRingBuffer(ringBufferCapacity);
		this.selection = selection;
		this.catalog = catalog;
		this.estimatorFactory = estimatorFactory;
		this.analysisService = new AnalysisService(context, this.ringBuffer, levelAnalyzer,
				estimatorFactory.apply(INITIAL_SAMPLE_RATE), INITIAL_SAMPLE_RATE,
				TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE, TarsosFastYinEstimator.DEFAULT_HOP_SIZE);
		this.captureService = new CaptureService(context, catalog, selection, this.ringBuffer);
		this.captureService.setFormatListener(format -> rebuildAnalysis(Math.round(format.getSampleRate())));
	}

	public static AudioEngine getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, AudioEngine.class.getName(), new TGSingletonFactory<AudioEngine>() {
			@Override
			public AudioEngine createInstance(TGContext singletonContext) {
				return new AudioEngine(singletonContext, new InputDeviceCatalog(),
						CurrentInputSelection.getInstance(singletonContext), DEFAULT_RING_BUFFER_CAPACITY,
						new LevelAnalyzer(DEFAULT_TOO_LOW_RMS_DBFS, DEFAULT_TOO_HIGH_RMS_DBFS,
								DEFAULT_CLIPPED_SAMPLE_LEVEL, DEFAULT_CLIPPING_RATIO),
						sampleRate -> new TarsosFastYinEstimator(sampleRate,
								TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE, TarsosFastYinEstimator.DEFAULT_HOP_SIZE));
			}
		});
	}

	/** Starts capture for this consumer if it is the first one. */
	public void acquire(String consumerId) {
		this.captureService.acquire(consumerId);
	}

	/** Stops capture when the last consumer releases it. */
	public void release(String consumerId) {
		this.captureService.release(consumerId);
	}

	public void subscribe(AnalysisService.Listener listener) {
		this.analysisService.subscribe(listener);
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

	public CurrentInputSelection getSelection() {
		return this.selection;
	}

	public InputDeviceCatalog getCatalog() {
		return this.catalog;
	}

	/** Diagnostic for tests and measurements only; never shown to the learner. */
	public long getDroppedSampleCount() {
		return this.ringBuffer.getDroppedSampleCount();
	}

	SampleRingBuffer getRingBuffer() {
		return this.ringBuffer;
	}

	void rebuildAnalysis(int sampleRate) {
		this.analysisService.reconfigure(sampleRate, this.estimatorFactory.apply(sampleRate),
				this.ringBuffer.getWrittenSampleCount());
	}
}
