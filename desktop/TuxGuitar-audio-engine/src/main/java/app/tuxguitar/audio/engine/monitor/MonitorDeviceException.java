package app.tuxguitar.audio.engine.monitor;

/** The output could not be opened or was lost; the message is shown to the learner. */
public final class MonitorDeviceException extends Exception {

	private static final long serialVersionUID = 1L;

	public MonitorDeviceException(String message) {
		super(message);
	}

	public MonitorDeviceException(String message, Throwable cause) {
		super(message, cause);
	}
}
