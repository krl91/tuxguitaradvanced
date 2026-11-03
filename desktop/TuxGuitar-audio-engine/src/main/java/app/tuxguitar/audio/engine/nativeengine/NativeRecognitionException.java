package app.tuxguitar.audio.engine.nativeengine;

/** Why the native recognition could not open or keep its input (spec 023, contract §1), with a detail for the log. */
public final class NativeRecognitionException extends Exception {

	private static final long serialVersionUID = 1L;

	public enum Reason {
		LIBRARY_MISSING, DEVICE_NOT_FOUND, PERMISSION_DENIED, FORMAT_UNSUPPORTED, STREAM_FAILED
	}

	private final Reason reason;
	private final String detail;

	public NativeRecognitionException(Reason reason, String detail) {
		super(reason + ": " + detail);
		this.reason = reason;
		this.detail = detail;
	}

	/** Thrown by the crate as "REASON: detail"; an unknown reason is a stream failure. */
	public NativeRecognitionException(String message) {
		this(reasonOf(message), detailOf(message));
	}

	public Reason getReason() {
		return this.reason;
	}

	public String getDetail() {
		return this.detail;
	}

	static Reason reasonOf(String message) {
		if (message != null) {
			int colon = message.indexOf(':');
			String name = (colon >= 0 ? message.substring(0, colon) : message).trim();
			for (Reason reason : Reason.values()) {
				if (reason.name().equals(name)) {
					return reason;
				}
			}
		}
		return Reason.STREAM_FAILED;
	}

	static String detailOf(String message) {
		if (message == null) {
			return "";
		}
		int colon = message.indexOf(':');
		return colon >= 0 && reasonOf(message).name().equals(message.substring(0, colon).trim()) ? message.substring(colon + 1).trim() : message;
	}
}
