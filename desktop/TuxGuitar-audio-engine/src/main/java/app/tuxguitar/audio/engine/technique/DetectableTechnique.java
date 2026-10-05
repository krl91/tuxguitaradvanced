package app.tuxguitar.audio.engine.technique;

/**
 * The techniques the engine has a detector for. The practice module maps its written techniques
 * onto these (hammer-on and pull-off are both {@link #LEGATO}); the engine does not depend on it.
 */
public enum DetectableTechnique {
	LEGATO,
	SLIDE,
	BEND,
	VIBRATO
}
