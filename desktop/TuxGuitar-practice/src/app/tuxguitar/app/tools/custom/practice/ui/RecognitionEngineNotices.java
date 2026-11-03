package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.Locale;

import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException.Reason;

/** The texts of the notice of a run that asked for the native engine and runs on Java (spec 023, contract §5): one per reason that falls back. */
final class RecognitionEngineNotices {

	private RecognitionEngineNotices() {
	}

	/** {@code recognition.engine.notice.device-not-found} and the like; null for a refused permission, which is not a fallback (today's message comes). */
	static String key(Reason reason) {
		if( reason == null || reason == Reason.PERMISSION_DENIED ) {
			return null;
		}
		return "recognition.engine.notice." + reason.name().toLowerCase(Locale.ROOT).replace('_', '-');
	}
}
