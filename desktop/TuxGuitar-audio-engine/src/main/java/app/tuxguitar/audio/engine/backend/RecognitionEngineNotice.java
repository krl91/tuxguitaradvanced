package app.tuxguitar.audio.engine.backend;

import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException.Reason;

/**
 * The native engine was asked for a run but the Java engine runs it (spec 023, FR-006, data-model "Recognition engine notice"): why, and the native text
 * for the log. Sent once per run that falls back; never for a refused permission, which is not a fallback.
 */
public final class RecognitionEngineNotice {

	/** Told on the thread of the acquire that started the run. */
	public interface Listener {
		void recognitionEngineNotice(RecognitionEngineNotice notice);
	}

	private final Reason reason;
	private final String detail;

	public RecognitionEngineNotice(Reason reason, String detail) {
		this.reason = reason;
		this.detail = (detail != null ? detail : "");
	}

	public Reason getReason() {
		return this.reason;
	}

	public String getDetail() {
		return this.detail;
	}

	@Override
	public String toString() {
		return this.reason + ": " + this.detail;
	}
}
