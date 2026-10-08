package app.tuxguitar.app.tools.custom.practice.model;

import java.util.Arrays;
import java.util.List;

import app.tuxguitar.song.helpers.tuning.TuningGroup;
import app.tuxguitar.song.helpers.tuning.TuningPreset;
import app.tuxguitar.song.models.TGString;
import app.tuxguitar.song.models.TGTrack;

/**
 * Tuning of the Practice Track as practice needs it (FR-008). Expected pitches always use
 * {@code offset + openStringMidi + fret}; the physical targets and the realization are guidance
 * only and never change expected pitches.
 */
public final class TrackTuning {

	public enum Realization {
		/** offset 0: play in the track tuning */
		NONE,
		/** offset k > 0: capo at fret k (capo-equivalent) */
		CAPO_EQUIVALENT,
		/** offset k < 0: tune every string down |k| semitone(s) */
		TUNE_DOWN
	}

	public static final int STRING_COUNT = 6;

	private final int[] openStringMidi;
	private final int offset;
	private final int[] physicalTargetMidi;
	private final boolean unusualSetup;

	/**
	 * @param openStringMidi open-string pitch per string, string 1 (highest) first
	 * @param offset the track offset (−24…+24)
	 * @param presetMinimumMidi lowest pitch per string across TuxGuitar's built-in 6-string guitar
	 *        presets, string 1 first; null when unavailable (then no notice is ever shown)
	 */
	public TrackTuning(int[] openStringMidi, int offset, int[] presetMinimumMidi) {
		this(openStringMidi, offset, presetMinimumMidi, true);
	}

	private TrackTuning(int[] openStringMidi, int offset, int[] presetMinimumMidi, boolean sixStringsOnly) {
		if( sixStringsOnly && openStringMidi.length != STRING_COUNT ) {
			throw new IllegalArgumentException("six strings expected");
		}
		if( openStringMidi.length == 0 ) {
			throw new IllegalArgumentException("a string is expected");
		}
		this.openStringMidi = openStringMidi.clone();
		this.offset = offset;
		this.physicalTargetMidi = new int[openStringMidi.length];
		boolean belowPresets = false;
		for( int i = 0 ; i < openStringMidi.length ; i ++ ) {
			this.physicalTargetMidi[i] = (offset >= 0 ? openStringMidi[i] : openStringMidi[i] + offset);
			if( presetMinimumMidi != null && i < presetMinimumMidi.length && this.physicalTargetMidi[i] < presetMinimumMidi[i] ) {
				belowPresets = true;
			}
		}
		this.unusualSetup = (offset < 0 && belowPresets);
	}

	/**
	 * The tuning of a track of any number of strings (a four-string bass, a seven-string guitar), as its definition says (Arcade mode, spec 003 FR-027).
	 * The wait and following modes keep {@link #of(TGTrack, int[])}, which is for six strings; there is no preset notice for other counts.
	 */
	public static TrackTuning ofAnyStrings(TGTrack track) {
		int[] open = new int[track.stringCount()];
		for( int i = 0 ; i < open.length ; i ++ ) {
			open[i] = track.getString(i + 1).getValue();
		}
		return new TrackTuning(open, track.getOffset(), null, false);
	}

	/** The number of strings of this tuning. */
	public int getStringCount() {
		return this.openStringMidi.length;
	}

	/** Reads the six open strings (string 1 first) and the offset of a six-string track. */
	public static TrackTuning of(TGTrack track, int[] presetMinimumMidi) {
		int[] open = new int[STRING_COUNT];
		for( int i = 0 ; i < STRING_COUNT ; i ++ ) {
			TGString string = track.getString(i + 1);
			open[i] = string.getValue();
		}
		return new TrackTuning(open, track.getOffset(), presetMinimumMidi);
	}

	/**
	 * Per-string minimum of the built-in "Guitar › 6-String" presets, string 1 first, or null
	 * when that group is missing. Pass {@code TuningManager.getTgTuningsGroup()}; user custom
	 * tunings are not part of it.
	 */
	public static int[] builtInSixStringMinimum(TuningGroup builtInRoot) {
		TuningGroup sixString = findGroup(findGroup(builtInRoot, "Guitar"), "6-String");
		if( sixString == null ) {
			return null;
		}
		int[] minimum = null;
		for( TuningPreset preset : sixString.getTunings() ) {
			int[] values = preset.getValues();
			if( values.length != STRING_COUNT ) {
				continue;
			}
			if( minimum == null ) {
				minimum = values.clone();
			} else {
				for( int i = 0 ; i < STRING_COUNT ; i ++ ) {
					minimum[i] = Math.min(minimum[i], values[i]);
				}
			}
		}
		return minimum;
	}

	private static TuningGroup findGroup(TuningGroup parent, String name) {
		if( parent == null ) {
			return null;
		}
		List<TuningGroup> groups = parent.getGroups();
		for( TuningGroup group : groups ) {
			if( name.equals(group.getName()) ) {
				return group;
			}
		}
		return null;
	}

	/** Open-string pitch per string, string 1 (highest) first. */
	public int[] getOpenStringMidi() {
		return this.openStringMidi.clone();
	}

	public int getOffset() {
		return this.offset;
	}

	public Realization getRealization() {
		return (this.offset == 0 ? Realization.NONE : (this.offset > 0 ? Realization.CAPO_EQUIVALENT : Realization.TUNE_DOWN));
	}

	/** Physical open-string targets for the tuner, string 1 first (guidance only). */
	public int[] getPhysicalTargetMidi() {
		return this.physicalTargetMidi.clone();
	}

	/** Informational notice only (FR-008); never blocks practice. */
	public boolean isUnusualSetup() {
		return this.unusualSetup;
	}

	/** Expected sounding pitch of a fret on a string (1-based string number). */
	public int expectedMidi(int stringNumber, int fret) {
		return this.offset + this.openStringMidi[stringNumber - 1] + fret;
	}

	@Override
	public String toString() {
		return "TrackTuning" + Arrays.toString(this.openStringMidi) + " offset " + this.offset;
	}
}
