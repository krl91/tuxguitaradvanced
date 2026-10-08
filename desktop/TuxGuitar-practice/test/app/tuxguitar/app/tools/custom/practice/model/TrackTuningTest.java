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

	// ------------------------------------------------------------------ any number of strings (spec 003 T002)

	private static app.tuxguitar.song.models.TGTrack trackOf(int... openStrings) {
		SongFixture fixture = new SongFixture(1);
		app.tuxguitar.song.models.TGTrack track = fixture.track();
		track.getStrings().clear();
		for( int i = 0 ; i < openStrings.length ; i ++ ) {
			app.tuxguitar.song.models.TGString string = fixture.factory().newString();
			string.setNumber(i + 1);
			string.setValue(openStrings[i]);
			track.getStrings().add(string);
		}
		return track;
	}

	@Test
	public void aFourStringBassHasItsOwnTuningStringOneFirst() {
		app.tuxguitar.song.models.TGTrack bass = trackOf(43, 38, 33, 28);

		TrackTuning tuning = TrackTuning.ofAnyStrings(bass);

		assertEquals(4, tuning.getStringCount());
		assertArrayEquals(new int[] { 43, 38, 33, 28 }, tuning.getOpenStringMidi());
		assertEquals(28 + 5, tuning.expectedMidi(4, 5));
		assertEquals(43, tuning.expectedMidi(1, 0));
		assertFalse(tuning.isUnusualSetup());
	}

	@Test
	public void aSevenStringGuitarAndTheTrackOffsetAreRead() {
		app.tuxguitar.song.models.TGTrack seven = trackOf(64, 59, 55, 50, 45, 40, 35);
		seven.setOffset(-2);

		TrackTuning tuning = TrackTuning.ofAnyStrings(seven);

		assertEquals(7, tuning.getStringCount());
		assertEquals(35 - 2 + 3, tuning.expectedMidi(7, 3));
		assertEquals(Realization.TUNE_DOWN, tuning.getRealization());
		assertEquals(7, tuning.getPhysicalTargetMidi().length);
	}

	@Test
	public void theSixStringRulesOfTheOtherModesAreUnchanged() {
		app.tuxguitar.song.models.TGTrack guitar = trackOf(64, 59, 55, 50, 45, 40);

		assertArrayEquals(TrackTuning.of(guitar, PRESET_MINIMUM).getOpenStringMidi(), TrackTuning.ofAnyStrings(guitar).getOpenStringMidi());
		assertEquals(6, TrackTuning.ofAnyStrings(guitar).getStringCount());
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new TrackTuning(new int[] { 43, 38, 33, 28 }, 0, null));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new TrackTuning(new int[0], 0, null));
		assertEquals(6, TrackTuning.STRING_COUNT);
	}

	@Test
	public void aSequenceCanBeBuiltForABass() {
		SongFixture fixture = new SongFixture(1);
		app.tuxguitar.song.models.TGTrack track = fixture.track();
		track.getStrings().clear();
		int[] bass = { 43, 38, 33, 28 };
		for( int i = 0 ; i < bass.length ; i ++ ) {
			app.tuxguitar.song.models.TGString string = fixture.factory().newString();
			string.setNumber(i + 1);
			string.setValue(bass[i]);
			track.getStrings().add(string);
		}
		fixture.quarter(0, 0, 4, 3);
		fixture.quarter(0, 1, 2, 0);

		PracticeSequence sequence = PracticeSequenceBuilder.build(fixture.song, track, TrackTuning.ofAnyStrings(track));

		assertEquals(2, sequence.getEvents().size());
		assertEquals(28 + 3, sequence.getEvents().get(0).getExpectedMidi());
		assertEquals(38, sequence.getEvents().get(1).getExpectedMidi());
		assertEquals(4, sequence.getTrackTuning().getStringCount());
	}
}
