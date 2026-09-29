package app.tuxguitar.audio.engine.input;

/** Latest assessment of the selected input. Immutable. */
public final class InputAssessment {

	public enum Status {
		UNCHECKED,
		SUITABLE,
		SUBOPTIMAL,
		UNUSABLE
	}

	public enum Reason {
		NONE,
		NO_SIGNAL,
		TOO_LOW,
		TOO_HIGH,
		CLIPPING,
		DEVICE_LOST,
		ACCESS_DENIED_SUSPECTED
	}

	public static final InputAssessment UNCHECKED = new InputAssessment(Status.UNCHECKED, Reason.NONE);

	private final Status status;
	private final Reason reason;

	public InputAssessment(Status status, Reason reason) {
		if (status == null || reason == null) {
			throw new IllegalArgumentException("status and reason are required");
		}
		this.status = status;
		this.reason = reason;
	}

	public Status getStatus() {
		return this.status;
	}

	public Reason getReason() {
		return this.reason;
	}

	public boolean isUnusable() {
		return this.status == Status.UNUSABLE;
	}
}
