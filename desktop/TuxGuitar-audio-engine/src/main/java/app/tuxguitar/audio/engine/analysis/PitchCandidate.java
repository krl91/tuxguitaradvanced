package app.tuxguitar.audio.engine.analysis;

/**
 * One pitch that the polyphonic analysis finds prominent in a frame. Immutable. A candidate is
 * spectral evidence, not a claim that the pitch was played: consumers compare candidates with
 * the pitches they expect.
 */
public final class PitchCandidate {

	private final int midi;
	private final double salience;
	private final double relativeSalience;
	private final double levelDb;

	/**
	 * @param midi nearest equal-tempered MIDI note (A4 = 440 Hz)
	 * @param salience harmonic-sum salience (linear amplitude units)
	 * @param relativeSalience salience divided by that of the frame's strongest candidate (0–1)
	 * @param levelDb level of the fundamental's spectral peak in dBFS (a full-scale sine is 0 dB)
	 */
	public PitchCandidate(int midi, double salience, double relativeSalience, double levelDb) {
		this.midi = midi;
		this.salience = salience;
		this.relativeSalience = relativeSalience;
		this.levelDb = levelDb;
	}

	public int getMidi() {
		return this.midi;
	}

	public double getSalience() {
		return this.salience;
	}

	public double getRelativeSalience() {
		return this.relativeSalience;
	}

	public double getLevelDb() {
		return this.levelDb;
	}

	@Override
	public String toString() {
		return "PitchCandidate[" + this.midi + ", " + String.format(java.util.Locale.ROOT, "%.2f", this.relativeSalience) + "]";
	}
}
