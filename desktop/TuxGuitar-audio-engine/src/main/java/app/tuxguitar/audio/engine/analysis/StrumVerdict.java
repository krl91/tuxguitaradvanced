package app.tuxguitar.audio.engine.analysis;

/**
 * What the strum meter says about the level of the first strum after a check begins (spec 010). When more than one applies the one reported is, in this
 * order, {@link #CLIPPING}, {@link #TOO_LOUD}, {@link #TOO_WEAK}; {@link #NO_STRUM} until a whole window after an attack was counted.
 */
public enum StrumVerdict {
	NO_STRUM, CLIPPING, TOO_LOUD, TOO_WEAK, GOOD
}
