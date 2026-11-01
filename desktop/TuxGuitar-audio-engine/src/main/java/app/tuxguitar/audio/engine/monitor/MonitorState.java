package app.tuxguitar.audio.engine.monitor;

/** The state of the guitar's sound (spec 022 data-model "MonitorState"). */
public enum MonitorState {
	OFF,
	STARTING,
	ON,
	STOPPING,
	MUTED_FEEDBACK,
	MUTED_OVERLOAD,
	FAILED
}
