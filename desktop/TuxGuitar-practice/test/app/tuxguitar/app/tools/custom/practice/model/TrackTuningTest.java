package app.tuxguitar.app.tools.custom.practice.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.TrackTuning.Realization;
import app.tuxguitar.song.helpers.tuning.TuningGroup;
import app.tuxguitar.song.helpers.tuning.TuningPreset;

public class TrackTuningTest {

	private static final int[] STANDARD = { 64, 59, 55, 50, 45, 40 };
	/** Per-string minima of today's built-in presets (research R3). */
	private static final int[] PRESET_MINIMUM = { 58, 53, 49, 44, 39, 34 };

	@Test
	public void expectedPitchIsOffsetPlusOpenStringPlusFret() {
		for( int offset : new int[] { 0, 2, -1, -8 } ) {
			TrackTuning tuning = new TrackTuning(STANDARD, offset, PRESET_MINIMUM);
			assertEquals(offset + 40 + 3, tuning.expectedMidi(6, 3));
			assertEquals(offset + 64 + 12, tuning.expectedMidi(1, 12));
		}
	}

	@Test
	public void physicalTargetsFollowTheOffsetRealization() {
		TrackTuning capo = new TrackTuning(STANDARD, 2, PRESET_MINIMUM);
		assertEquals(Realization.CAPO_EQUIVALENT, capo.getRealization());
		assertArrayEquals(STANDARD, capo.getPhysicalTargetMidi());

		TrackTuning down = new TrackTuning(STANDARD, -1, PRESET_MINIMUM);
		assertEquals(Realization.TUNE_DOWN, down.getRealization());
		assertArrayEquals(new int[] { 63, 58, 54, 49, 44, 39 }, down.getPhysicalTargetMidi());

		assertEquals(Realization.NONE, new TrackTuning(STANDARD, 0, PRESET_MINIMUM).getRealization());
	}

	@Test
	public void unusualSetupOnlyForNegativeOffsetsBelowPresetMinima() {
		assertFalse(new TrackTuning(STANDARD, -1, PRESET_MINIMUM).isUnusualSetup());
		assertFalse(new TrackTuning(STANDARD, -6, PRESET_MINIMUM).isUnusualSetup());
		assertTrue(new TrackTuning(STANDARD, -8, PRESET_MINIMUM).isUnusualSetup());
		assertFalse(new TrackTuning(STANDARD, 24, PRESET_MINIMUM).isUnusualSetup());
		assertFalse(new TrackTuning(STANDARD, -8, null).isUnusualSetup());
	}

	@Test
	public void builtInMinimaComeFromTheGuitarSixStringGroupOnly() {
		TuningGroup root = new TuningGroup();
		TuningGroup guitar = new TuningGroup(root, "Guitar");
		TuningGroup sixString = new TuningGroup(guitar, "6-String");
		TuningGroup bass = new TuningGroup(root, "Bass");
		root.addGroup(guitar);
		root.addGroup(bass);
		guitar.addGroup(sixString);
		sixString.addTuningPreset(new TuningPreset(sixString, "E Tuning", STANDARD));
		sixString.addTuningPreset(new TuningPreset(sixString, "B Tuning", new int[] { 59, 54, 50, 45, 40, 35 }));
		bass.addTuningPreset(new TuningPreset(bass, "6-String Bass", new int[] { 48, 43, 38, 33, 28, 23 }));

		assertArrayEquals(new int[] { 59, 54, 50, 45, 40, 35 }, TrackTuning.builtInSixStringMinimum(root));
		assertNull(TrackTuning.builtInSixStringMinimum(new TuningGroup()));
	}
}
