package app.tuxguitar.audio.engine.pitch;

/** Equal-tempered interpretation of one pitch estimate. Immutable. */
public final class PitchReading {

	private static final PitchReading NO_READING = new PitchReading(-1, "", -1, Double.NaN, false);

	private final int midi;
	private final String noteName;
	private final int octave;
	private final double cents;
	private final boolean reliable;

	public PitchReading(int midi, String noteName, int octave, double cents, boolean reliable) {
		this.midi = midi;
		this.noteName = noteName;
		this.octave = octave;
		this.cents = cents;
		this.reliable = reliable;
	}

	public static PitchReading noReading() {
		return NO_READING;
	}

	public boolean hasPitch() {
		return this.midi >= 0 && Double.isFinite(this.cents);
	}

	public int getMidi() {
		return this.midi;
	}

	public String getNoteName() {
		return this.noteName;
	}

	public int getOctave() {
		return this.octave;
	}

	public double getCents() {
		return this.cents;
	}

	public boolean isReliable() {
		return this.reliable;
	}
}
