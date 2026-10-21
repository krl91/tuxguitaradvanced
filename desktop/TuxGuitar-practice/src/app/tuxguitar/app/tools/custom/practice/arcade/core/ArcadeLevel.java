package app.tuxguitar.app.tools.custom.practice.arcade.core;

/**
 * The five levels of Arcade (spec 014, FR-006): the same piece with only a share of its sounds to play, from a few notes (level 1) to the whole piece (level 5).
 * Each level keeps a share of the notes and a share of the chords, in percent; level 1 keeps no chord. Which sounds are kept is {@link ArcadeLevelFilter}'s.
 */
public enum ArcadeLevel {

	LEVEL_1(1, 30, 0),
	LEVEL_2(2, 30, 30),
	LEVEL_3(3, 50, 50),
	LEVEL_4(4, 75, 75),
	LEVEL_5(5, 100, 100);

	public static final ArcadeLevel LOWEST = LEVEL_1;
	public static final ArcadeLevel HIGHEST = LEVEL_5;

	private final int number;
	private final int notePercent;
	private final int chordPercent;

	ArcadeLevel(int number, int notePercent, int chordPercent) {
		this.number = number;
		this.notePercent = notePercent;
		this.chordPercent = chordPercent;
	}

	/** The level of this number, brought to the nearest level when it is below 1 or above 5. */
	public static ArcadeLevel of(int number) {
		return values()[Math.max(1, Math.min(5, number)) - 1];
	}

	/** 1 to 5. */
	public int number() {
		return this.number;
	}

	/** The share of the notes kept, in percent. */
	public int notePercent() {
		return this.notePercent;
	}

	/** The share of the chords kept, in percent. */
	public int chordPercent() {
		return this.chordPercent;
	}

	/** Level 5: nothing is removed and the run is the one of before this feature. */
	public boolean keepsEverything() {
		return this.notePercent == 100 && this.chordPercent == 100;
	}
}
