package app.tuxguitar.app.tools.custom.practice.arcade.view;

/** The name of a MIDI pitch ("E2", "F#3"), the same in every language. */
public final class PitchNames {

	private static final String[] NAMES = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };

	private PitchNames() {
	}

	public static String of(int midi) {
		int octave = Math.floorDiv(midi, 12) - 1;
		return NAMES[Math.floorMod(midi, 12)] + octave;
	}
}
