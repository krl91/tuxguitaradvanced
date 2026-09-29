package app.tuxguitar.app.tools.custom.tunernext;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.song.helpers.tuning.TuningGroup;
import app.tuxguitar.song.helpers.tuning.TuningPreset;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGMusicKeyUtils;

/**
 * A six-string guitar tuning guided by the tuner: six open-string target pitches, string 1
 * (highest) first, and where they come from. Immutable. Tuner guidance only: it never changes
 * the song or the pitches practice expects (FR-060, FR-061).
 */
public final class TunerTuning {

	public enum Source {
		PRESET,
		CUSTOM,
		TRACK
	}

	public static final int STRING_COUNT = 6;
	public static final int MIN_PITCH = TGMusicKeyUtils.MIN_MIDI_NOTE;
	public static final int MAX_PITCH = TGMusicKeyUtils.MAX_MIDI_NOTE;
	public static final String STANDARD_NAME = "E Tuning";
	private static final int[] STANDARD_VALUES = { 64, 59, 55, 50, 45, 40 };

	private final Source source;
	private final String name;
	private final int[] targets;
	private final int offset;

	private TunerTuning(Source source, String name, int[] targets, int offset) {
		if( targets.length != STRING_COUNT ) {
			throw new IllegalArgumentException("six strings expected");
		}
		for( int target : targets ) {
			if( target < MIN_PITCH || target > MAX_PITCH ) {
				throw new IllegalArgumentException("target pitch outside " + MIN_PITCH + ".." + MAX_PITCH);
			}
		}
		this.source = source;
		this.name = name;
		this.targets = targets.clone();
		this.offset = offset;
	}

	public static TunerTuning preset(String name, int[] values) {
		return new TunerTuning(Source.PRESET, name, values, 0);
	}

	/** One target pitch per string, between {@link #MIN_PITCH} and {@link #MAX_PITCH} (FR-053). */
	public static TunerTuning custom(int[] values) {
		return new TunerTuning(Source.CUSTOM, null, values, 0);
	}

	/**
	 * A track's physical open-string targets (FR-052, FR-008): the open strings for offset ≥ 0
	 * (capo-equivalent), open string + offset for a negative offset (tune down).
	 */
	public static TunerTuning track(TGTrack track) {
		int offset = track.getOffset();
		int[] targets = new int[STRING_COUNT];
		for( int i = 0 ; i < STRING_COUNT ; i ++ ) {
			int open = track.getString(i + 1).getValue();
			targets[i] = (offset >= 0 ? open : open + offset);
		}
		return new TunerTuning(Source.TRACK, track.getName(), targets, offset);
	}

	public static TunerTuning standard() {
		return preset(STANDARD_NAME, STANDARD_VALUES);
	}

	/** Six-string presets of TuxGuitar's built-in "Guitar › 6-String" group (user tunings excluded). */
	public static List<TunerTuning> builtInPresets(TuningGroup builtInRoot) {
		List<TunerTuning> presets = new ArrayList<TunerTuning>();
		TuningGroup sixString = findGroup(findGroup(builtInRoot, "Guitar"), "6-String");
		if( sixString != null ) {
			for( TuningPreset preset : sixString.getTunings() ) {
				if( preset.getValues().length == STRING_COUNT ) {
					presets.add(preset(preset.getName(), preset.getValues()));
				}
			}
		}
		if( presets.isEmpty() ) {
			presets.add(standard());
		}
		return presets;
	}

	private static TuningGroup findGroup(TuningGroup parent, String name) {
		if( parent != null ) {
			for( TuningGroup group : parent.getGroups() ) {
				if( name.equals(group.getName()) ) {
					return group;
				}
			}
		}
		return null;
	}

	public Source getSource() {
		return this.source;
	}

	/** Preset or track name; null for a custom tuning. */
	public String getName() {
		return this.name;
	}

	/** Target pitches, string 1 (highest) first. */
	public int[] getTargets() {
		return this.targets.clone();
	}

	/** The track offset for a track tuning (display only); 0 otherwise. */
	public int getOffset() {
		return this.offset;
	}

	public boolean sameTargets(int[] other) {
		return java.util.Arrays.equals(this.targets, other);
	}
}
