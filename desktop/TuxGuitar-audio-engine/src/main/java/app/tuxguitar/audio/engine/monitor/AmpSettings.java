package app.tuxguitar.audio.engine.monitor;

/** The amp and its cabinet (spec 022 FR-004, data-model "AmpSettings"). Immutable; every knob goes from 0 to 10. */
public final class AmpSettings {

	public enum Character {
		CLEAN("clean"),
		CRUNCH("crunch"),
		HIGH_GAIN("highgain");

		private final String textName;

		Character(String textName) {
			this.textName = textName;
		}

		public String textName() {
			return this.textName;
		}

		public static Character ofText(String name) {
			for( Character character : values() ) {
				if( character.textName.equals(name) ) {
					return character;
				}
			}
			return null;
		}
	}

	private final Character character;
	private final double gain;
	private final double bass;
	private final double middle;
	private final double treble;
	private final double volume;
	private final boolean cabinet;

	public AmpSettings(Character character, double gain, double bass, double middle, double treble, double volume, boolean cabinet) {
		if( character == null ) {
			throw new IllegalArgumentException("the amp needs a character");
		}
		this.character = character;
		this.gain = clamp(gain);
		this.bass = clamp(bass);
		this.middle = clamp(middle);
		this.treble = clamp(treble);
		this.volume = clamp(volume);
		this.cabinet = cabinet;
	}

	/** A clean amp, every knob at 5, the cabinet on. */
	public static AmpSettings defaults() {
		return new AmpSettings(Character.CLEAN, 5, 5, 5, 5, 5, true);
	}

	static double clamp(double value) {
		if( Double.isNaN(value) ) {
			return 0;
		}
		return Math.max(0, Math.min(10, value));
	}

	public Character character() {
		return this.character;
	}

	public double gain() {
		return this.gain;
	}

	public double bass() {
		return this.bass;
	}

	public double middle() {
		return this.middle;
	}

	public double treble() {
		return this.treble;
	}

	public double volume() {
		return this.volume;
	}

	public boolean cabinet() {
		return this.cabinet;
	}

	public AmpSettings withCharacter(Character value) {
		return new AmpSettings(value, this.gain, this.bass, this.middle, this.treble, this.volume, this.cabinet);
	}

	public AmpSettings withGain(double value) {
		return new AmpSettings(this.character, value, this.bass, this.middle, this.treble, this.volume, this.cabinet);
	}

	public AmpSettings withBass(double value) {
		return new AmpSettings(this.character, this.gain, value, this.middle, this.treble, this.volume, this.cabinet);
	}

	public AmpSettings withMiddle(double value) {
		return new AmpSettings(this.character, this.gain, this.bass, value, this.treble, this.volume, this.cabinet);
	}

	public AmpSettings withTreble(double value) {
		return new AmpSettings(this.character, this.gain, this.bass, this.middle, value, this.volume, this.cabinet);
	}

	public AmpSettings withVolume(double value) {
		return new AmpSettings(this.character, this.gain, this.bass, this.middle, this.treble, value, this.cabinet);
	}

	public AmpSettings withCabinet(boolean value) {
		return new AmpSettings(this.character, this.gain, this.bass, this.middle, this.treble, this.volume, value);
	}

	@Override
	public boolean equals(Object other) {
		if( !(other instanceof AmpSettings) ) {
			return false;
		}
		AmpSettings amp = (AmpSettings) other;
		return this.character == amp.character && this.gain == amp.gain && this.bass == amp.bass && this.middle == amp.middle
				&& this.treble == amp.treble && this.volume == amp.volume && this.cabinet == amp.cabinet;
	}

	@Override
	public int hashCode() {
		int hash = this.character.hashCode();
		for( double value : new double[] { this.gain, this.bass, this.middle, this.treble, this.volume } ) {
			hash = hash * 31 + Double.hashCode(value);
		}
		return hash * 31 + (this.cabinet ? 1 : 0);
	}
}
