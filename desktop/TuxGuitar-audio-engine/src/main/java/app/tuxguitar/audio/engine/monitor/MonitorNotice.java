package app.tuxguitar.audio.engine.monitor;

/** What the engine tells the learner without changing state (spec 022: input clipping, the chain made lighter when the computer cannot keep up). */
public enum MonitorNotice {
	INPUT_CLIPPING,
	LIGHTER_REVERB_OFF,
	LIGHTER_CHORUS_OFF,
	LIGHTER_OVERSAMPLING_2X,
	LIGHTER_OVERSAMPLING_1X,
	LIGHTER_AMP_ALONE
}
