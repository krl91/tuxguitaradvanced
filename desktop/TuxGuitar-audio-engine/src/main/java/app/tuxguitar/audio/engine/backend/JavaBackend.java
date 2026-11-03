package app.tuxguitar.audio.engine.backend;

import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.input.CaptureClock;
import app.tuxguitar.audio.engine.input.CaptureService;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;

/** Today's chain (spec 023, contract §2): the Java Sound capture into the ring buffer and the Java analysis, used as they are. */
public final class JavaBackend implements RecognitionBackend {

	private final CaptureService captureService;
	private final AnalysisService analysisService;
	private final SampleRingBuffer ringBuffer;

	public JavaBackend(CaptureService captureService, AnalysisService analysisService, SampleRingBuffer ringBuffer) {
		this.captureService = captureService;
		this.analysisService = analysisService;
		this.ringBuffer = ringBuffer;
	}

	public CaptureService getCaptureService() {
		return this.captureService;
	}

	public AnalysisService getAnalysisService() {
		return this.analysisService;
	}

	@Override
	public PitchEngineCatalog.Kind engineKind() {
		return PitchEngineCatalog.Kind.JAVA;
	}

	@Override
	public void acquire(String consumerId) {
		this.captureService.acquire(consumerId);
	}

	@Override
	public void release(String consumerId) {
		this.captureService.release(consumerId);
	}

	@Override
	public int getConsumerCount() {
		return this.captureService.getConsumerCount();
	}

	@Override
	public void subscribe(AnalysisService.Listener listener) {
		this.analysisService.subscribe(listener);
	}

	@Override
	public void unsubscribe(AnalysisService.Listener listener) {
		this.analysisService.unsubscribe(listener);
	}

	@Override
	public void addHopListener(HopListener listener) {
		this.analysisService.addHopListener(listener);
	}

	@Override
	public void removeHopListener(HopListener listener) {
		this.analysisService.removeHopListener(listener);
	}

	@Override
	public void addCaptureListener(CaptureService.Listener listener) {
		this.captureService.addListener(listener);
	}

	@Override
	public void removeCaptureListener(CaptureService.Listener listener) {
		this.captureService.removeListener(listener);
	}

	@Override
	public void setSpectrumEnabled(boolean enabled) {
		this.analysisService.setSpectrumEnabled(enabled);
	}

	@Override
	public void setExpectedPitches(int[] midi) {
		this.analysisService.setExpectedPitches(midi);
	}

	@Override
	public void setExpectedChord(int[] required, int[] tolerated) {
		// the matcher computes the chord evidence from the spectrum, as today
	}

	@Override
	public void setAttackCandidates(int[] pitches) {
		this.analysisService.setAttackCandidates(pitches);
	}

	@Override
	public void configure(float[] parameters) {
		// the engine sets the front end and reconfigures the analysis itself
	}

	@Override
	public CaptureClock clock() {
		return this.captureService.getCaptureClock();
	}

	@Override
	public long getDroppedSampleCount() {
		return this.ringBuffer.getDroppedSampleCount();
	}
}
