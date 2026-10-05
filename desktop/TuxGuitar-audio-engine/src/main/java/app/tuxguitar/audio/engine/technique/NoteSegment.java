package app.tuxguitar.audio.engine.technique;

/**
 * The hop-by-hop measurements of one note, from just after its attack to its end: the fractional
 * MIDI pitch (NaN when unvoiced), the level and whether a new articulation was heard. Immutable
 * copy; the detectors work on it and keep no state.
 */
public final class NoteSegment {

	private final double[] midi;
	private final double[] levelDb;
	private final boolean[] onset;
	private final double hopSeconds;

	public NoteSegment(double[] midi, double[] levelDb, boolean[] onset, double hopSeconds) {
		if( midi.length != levelDb.length || midi.length != onset.length || !(hopSeconds > 0.0) ) {
			throw new IllegalArgumentException("the series must have the same length and the hop must be positive");
		}
		this.midi = midi.clone();
		this.levelDb = levelDb.clone();
		this.onset = onset.clone();
		this.hopSeconds = hopSeconds;
	}

	public int size() {
		return this.midi.length;
	}

	public double midiAt(int i) {
		return this.midi[i];
	}

	public double levelDbAt(int i) {
		return this.levelDb[i];
	}

	public boolean onsetAt(int i) {
		return this.onset[i];
	}

	public double getHopSeconds() {
		return this.hopSeconds;
	}

	public double getDurationSeconds() {
		return this.midi.length * this.hopSeconds;
	}
}
