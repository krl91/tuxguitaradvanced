package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import app.tuxguitar.app.view.dialog.chord.TGChordDatabase;
import app.tuxguitar.app.view.dialog.chord.TGChordNamingConvention;

/**
 * The musical identity shown on the diagram of a chord (spec 003 FR-061, SC-018): a pure function of what the score writes, resolved before any renderer.
 * <ol>
 * <li>A non-blank name written in the score ({@code TGChord.getName()}) wins, exactly as written: {@link Source#WRITTEN}.</li>
 * <li>Otherwise a conventional name is derived from the written pitches, their bass and the key signature, with the chord templates of TuxGuitar's chord
 * database ({@code TGChordDatabase}) and its naming convention ({@code TGChordNamingConvention}), so that a name found here is a name the chord editor of
 * the application would also write: {@link Source#DERIVED}. The search is the one the editor does in its simplest form (the notes of the chord must be
 * the notes of a template, a fifth may be left out of a seventh chord, one added ninth, eleventh or thirteenth is allowed, the lowest note names an
 * inversion), made deterministic.</li>
 * <li>When no template describes the written notes honestly, the notes themselves, from the lowest up, are the identity: {@link Source#PITCH_SET}. A name
 * is never made up.</li>
 * </ol>
 * Nothing is read from the sound, from the detection or from the state of a run, and nothing is written to the score.
 */
public final class ChordNameResolver {

	public enum Source {
		WRITTEN, DERIVED, PITCH_SET
	}

	/** What the diagram shows: the text (for a pitch set, the list of notes only: the word that introduces it is the interface's) and where it comes from. */
	public static final class Display {
		private final String text;
		private final Source source;

		Display(String text, Source source) {
			this.text = text;
			this.source = source;
		}

		public String text() {
			return this.text;
		}

		public Source source() {
			return this.source;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Display && ((Display) other).text.equals(this.text) && ((Display) other).source == this.source;
		}

		@Override
		public int hashCode() {
			return this.text.hashCode() * 31 + this.source.ordinal();
		}

		@Override
		public String toString() {
			return this.source + ":" + this.text;
		}
	}

	private static final int MAJOR = 0;
	private static final int MINOR = 4;
	private static final int POWER = 15;
	private static final int FIFTH = 8;
	/** The offsets, in semitones from the tonic, of the added notes the database's naming convention knows: 9 (alteration 1), 11 (2) and 13 (3). */
	private static final int[] ADDED_SEMITONES = { 2, 5, 9 };

	private ChordNameResolver() {
	}

	/**
	 * @param writtenName the name written in the score, or null
	 * @param pitches the MIDI pitches the chord writes (new notes only), in any order, duplicates allowed
	 * @param keySignature the key signature of the measure (0 for C or unknown, 1 to 7 sharps, 8 to 14 flats)
	 */
	public static Display resolve(String writtenName, int[] pitches, int keySignature) {
		if (writtenName != null && !writtenName.trim().isEmpty()) {
			return new Display(writtenName.trim(), Source.WRITTEN);
		}
		if (pitches == null || pitches.length == 0) {
			return new Display("", Source.PITCH_SET);
		}
		int bass = Integer.MAX_VALUE;
		TreeSet<Integer> classes = new TreeSet<Integer>();
		for (int pitch : pitches) {
			bass = Math.min(bass, pitch);
			classes.add(Math.floorMod(pitch, 12));
		}
		int bassClass = Math.floorMod(bass, 12);
		String derived = derive(classes, bassClass, keySignature);
		if (derived != null) {
			return new Display(derived, Source.DERIVED);
		}
		return new Display(list(pitches, keySignature), Source.PITCH_SET);
	}

	/** The notes of the chord from the lowest up, each once, spelled for the key: {@code A–C–F♯}. */
	static String list(int[] pitches, int keySignature) {
		int[] sorted = pitches.clone();
		java.util.Arrays.sort(sorted);
		boolean sharp = keySignature < 8;
		TGChordNamingConvention convention = new TGChordNamingConvention();
		List<String> names = new ArrayList<String>();
		for (int pitch : sorted) {
			String name = convention.getTonic(Math.floorMod(pitch, 12), sharp).replace("#", "♯").replace('b', '♭');
			if (!names.contains(name)) {
				names.add(name);
			}
		}
		return String.join("–", names);
	}

	/** One reading of the notes by a template of the database, with what makes it more or less likely. */
	private static final class Candidate {
		final int tonic;
		final int template;
		/** 0 for none, 1, 2, 3 for an added ninth, eleventh, thirteenth. */
		final int added;
		final boolean fifthLeftOut;
		final int score;

		Candidate(int tonic, int template, int added, boolean fifthLeftOut, int score) {
			this.tonic = tonic;
			this.template = template;
			this.added = added;
			this.fifthLeftOut = fifthLeftOut;
			this.score = score;
		}
	}

	private static TreeSet<Integer> classesOf(int tonic, int[] required, boolean withoutFifth, int addedSemitones) {
		TreeSet<Integer> set = new TreeSet<Integer>();
		for (int value : required) {
			if (withoutFifth && value == FIFTH) {
				continue;
			}
			set.add(Math.floorMod(tonic + value - 1, 12));
		}
		if (addedSemitones > 0) {
			set.add(Math.floorMod(tonic + addedSemitones, 12));
		}
		return set;
	}

	private static String derive(TreeSet<Integer> classes, int bassClass, int keySignature) {
		if (classes.size() < 2) {
			return null;
		}
		Candidate best = null;
		for (int tonic = 0; tonic < 12; tonic++) {
			for (int template = 0; template < TGChordDatabase.length(); template++) {
				int[] required = TGChordDatabase.get(template).getRequiredNotes();
				// the order of the database is the order of how usual the chords are, with the major chord and the power chord counted as usual
				int usual = (template == MAJOR || template == MINOR || template == POWER) ? 0 : template * 2;
				int base = 1000 - usual + (tonic == bassClass ? 120 : 0);
				if (classes.equals(classesOf(tonic, required, false, 0))) {
					best = better(best, new Candidate(tonic, template, 0, false, base));
				}
				if (required.length == 4 && classes.equals(classesOf(tonic, required, true, 0)) && classes.size() >= 3) {
					best = better(best, new Candidate(tonic, template, 0, true, base - 300));
				}
				if (required.length <= 4 && template != POWER) {
					for (int a = 0; a < ADDED_SEMITONES.length; a++) {
						if (classes.equals(classesOf(tonic, required, false, ADDED_SEMITONES[a])) && !classesOf(tonic, required, false, 0).contains(Math.floorMod(tonic + ADDED_SEMITONES[a], 12))) {
							best = better(best, new Candidate(tonic, template, a + 1, false, base - 500 - a));
						}
					}
				}
			}
		}
		if (best == null) {
			return null;
		}
		boolean minorish = isMinorish(best.template);
		boolean sharp = sharpFor(best.tonic, minorish, keySignature);
		int[] required = TGChordDatabase.get(best.template).getRequiredNotes();
		boolean hasSeventh = false;
		for (int value : required) {
			hasSeventh |= value == 10 || value == 11 || value == 12;
		}
		// a bass that is not the tonic names an inversion, with the convention of the database; its backslash is written as the usual slash
		String name = new TGChordNamingConvention().createChordName(best.tonic, best.template, best.added, 0, best.added != 0 && !hasSeventh, 0, 0, 0, bassClass, sharp);
		return name.replace(" \\", "/");
	}

	private static Candidate better(Candidate current, Candidate candidate) {
		if (current == null || candidate.score > current.score) {
			return candidate;
		}
		if (candidate.score == current.score) {
			// the same likelihood: the lowest tonic, then the lowest template, a deterministic choice
			if (candidate.tonic < current.tonic || (candidate.tonic == current.tonic && candidate.template < current.template)) {
				return candidate;
			}
		}
		return current;
	}

	/** The templates with a minor third, a diminished fifth or the like: the chords whose tonic is spelled as in a minor key. */
	private static boolean isMinorish(int template) {
		String name = TGChordDatabase.get(template).getName();
		return name.startsWith("m") || name.startsWith("dim");
	}

	/**
	 * The spelling of the notes: the key signature decides when it says something (sharps in a sharp key, flats in a flat key); in the key of C, or when
	 * nothing is known, the usual spelling of guitar charts: Db, Eb, Ab and Bb for a major chord, C#, F# and G# for a minor one, F# for a major one.
	 */
	static boolean sharpFor(int tonic, boolean minorish, int keySignature) {
		if (keySignature >= 8) {
			return false;
		}
		if (keySignature >= 1) {
			return true;
		}
		switch (tonic) {
			case 1:
				return minorish;
			case 3:
				return minorish;
			case 6:
				return true;
			case 8:
				return minorish;
			case 10:
				return false;
			default:
				return true;
		}
	}
}
