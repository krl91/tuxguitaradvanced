package app.tuxguitar.audio.engine.backend;

import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.input.CaptureClock;
import app.tuxguitar.audio.engine.input.CaptureService;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;

/**
 * What {@code AudioEngine} calls on the capture and the analysis of a run (spec 023, contract §2): today's Java chain ({@link JavaBackend}) or the
 * native engine ({@link NativeBackend}). Frames reach the subscribers on one thread of the backend, never the user interface's, in order, their
 * timestamps strictly increasing within a stream.
 */
public interface RecognitionBackend {

	/** JAVA or NATIVE, for the diagnostics and the tests. */
	PitchEngineCatalog.Kind engineKind();

	/** Starts the capture for this consumer if it is the first one. */
	void acquire(String consumerId);

	/** Stops the capture when the last consumer releases it. */
	void release(String consumerId);

	int getConsumerCount();

	void subscribe(AnalysisService.Listener listener);

	void unsubscribe(AnalysisService.Listener listener);

	void addHopListener(HopListener listener);

	void removeHopListener(HopListener listener);

	void addCaptureListener(CaptureService.Listener listener);

	void removeCaptureListener(CaptureService.Listener listener);

	void setSpectrumEnabled(boolean enabled);

	/** null: none. */
	void setExpectedPitches(int[] midi);

	/** null required: none. While set, the native frames carry the {@code ChordEvidence}; the Java backend ignores it (the matcher computes it). */
	void setExpectedChord(int[] required, int[] tolerated);

	/** null: off. */
	void setAttackCandidates(int[] pitches);

	/**
	 * The front-end, level, articulation and strum values ({@code NativeParameters}) changed. The Java backend ignores it: {@code AudioEngine} sets its
	 * front end and reconfigures its analysis itself, as before.
	 */
	void configure(float[] parameters);

	/** The clock that dates the samples of the capture on the wall clock. */
	CaptureClock clock();

	/** The input samples dropped because the analysis was late, since the engine was made. */
	long getDroppedSampleCount();
}
