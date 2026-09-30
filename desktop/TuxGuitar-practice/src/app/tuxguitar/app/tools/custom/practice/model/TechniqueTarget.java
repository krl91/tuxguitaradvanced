package app.tuxguitar.app.tools.custom.practice.model;

/**
 * A written technique of one expected event, with what is needed to judge it (data-model.md
 * "TechniqueTarget"). Immutable. Optional parts are {@link #NONE} / null when not relevant.
 */
public final class TechniqueTarget {

	/** Value of the optional integer parts when absent. */
	public static final int NONE = -1;

	private final PlayingTechnique technique;
	private final int targetMidi;
	private final PitchPath pitchPath;
	private final int soundingMidi;
	private final int partnerEventIndex;

	private TechniqueTarget(PlayingTechnique technique, int targetMidi, PitchPath pitchPath, int soundingMidi, int partnerEventIndex) {
		if( technique == null ) {
			throw new IllegalArgumentException("technique is required");
		}
		this.technique = technique;
		this.targetMidi = targetMidi;
		this.pitchPath = pitchPath;
		this.soundingMidi = soundingMidi;
		this.partnerEventIndex = partnerEventIndex;
	}

	/** A technique with no extra data (vibrato, palm mute, tremolo picking...). */
	public static TechniqueTarget of(PlayingTechnique technique) {
		return new TechniqueTarget(technique, NONE, null, NONE, NONE);
	}

	/** A technique linking this event to another (destination of a hammer-on, pull-off or slide). */
	public static TechniqueTarget withPartner(PlayingTechnique technique, int targetMidi, int partnerEventIndex) {
		return new TechniqueTarget(technique, targetMidi, null, NONE, partnerEventIndex);
	}

	/** A bend: the pitch follows a written path. */
	public static TechniqueTarget withPath(PlayingTechnique technique, int targetMidi, PitchPath pitchPath) {
		if( pitchPath == null ) {
			throw new IllegalArgumentException("a pitch path is required");
		}
		return new TechniqueTarget(technique, targetMidi, pitchPath, NONE, NONE);
	}

	/** A harmonic: the pitch that sounds is not the fretted pitch. */
	public static TechniqueTarget withSoundingPitch(PlayingTechnique technique, int soundingMidi) {
		return new TechniqueTarget(technique, NONE, null, soundingMidi, NONE);
	}

	public PlayingTechnique getTechnique() {
		return this.technique;
	}

	public int getTargetMidi() {
		return this.targetMidi;
	}

	/** Null unless the technique follows a written pitch path. */
	public PitchPath getPitchPath() {
		return this.pitchPath;
	}

	public int getSoundingMidi() {
		return this.soundingMidi;
	}

	public int getPartnerEventIndex() {
		return this.partnerEventIndex;
	}
}
