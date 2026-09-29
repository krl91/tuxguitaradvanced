package app.tuxguitar.app.tools.custom.tunernext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.song.helpers.tuning.TuningGroup;
import app.tuxguitar.song.helpers.tuning.TuningPreset;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;

/** FR-052, FR-053, FR-055: which tuning the guided tuner uses. */
public class TunerTuningTest {

	private static final int[] STANDARD = { 64, 59, 55, 50, 45, 40 };

	@Test
	public void explicitChoiceWinsThenPracticeTrackThenConfirmedTrackThenStandardE() {
		TunerSessionState state = new TunerSessionState();
		TunerTuning practice = TunerTuning.preset("practice", new int[] { 64, 59, 55, 50, 45, 38 });
		TunerTuning confirmed = TunerTuning.preset("confirmed", new int[] { 62, 57, 53, 48, 43, 38 });

		assertEquals(TunerTuning.STANDARD_NAME, state.effectiveTuning(null, null).getName());
		assertArrayEquals(STANDARD, state.effectiveTuning(null, null).getTargets());
		assertSame(confirmed, state.effectiveTuning(null, confirmed));
		assertSame(practice, state.effectiveTuning(practice, confirmed));

		TunerTuning chosen = TunerTuning.custom(new int[] { 63, 58, 54, 49, 44, 39 });
		state.choose(chosen);
		assertSame(chosen, state.effectiveTuning(practice, confirmed));
	}

	@Test
	public void trackTuningUsesPhysicalOpenStringTargets() {
		TGSong song = new TGSongManager().newSong();
		TGTrack track = song.getTrack(0);

		track.setOffset(2);
		assertArrayEquals(STANDARD, TunerTuning.track(track).getTargets());
		assertEquals(TunerTuning.Source.TRACK, TunerTuning.track(track).getSource());

		track.setOffset(-1);
		assertArrayEquals(new int[] { 63, 58, 54, 49, 44, 39 }, TunerTuning.track(track).getTargets());
		assertEquals(-1, TunerTuning.track(track).getOffset());
	}

	@Test
	public void customTuningHasOnePitchPerStringWithinTuxGuitarsRange() {
		assertArrayEquals(new int[] { 12, 20, 30, 40, 50, 127 }, TunerTuning.custom(new int[] { 12, 20, 30, 40, 50, 127 }).getTargets());
		assertThrows(IllegalArgumentException.class, () -> TunerTuning.custom(new int[] { 64, 59, 55, 50, 45 }));
		assertThrows(IllegalArgumentException.class, () -> TunerTuning.custom(new int[] { 64, 59, 55, 50, 45, 11 }));
		assertThrows(IllegalArgumentException.class, () -> TunerTuning.custom(new int[] { 128, 59, 55, 50, 45, 40 }));
	}

	@Test
	public void presetsComeFromTheBuiltInSixStringGuitarGroup() {
		TuningGroup root = new TuningGroup();
		TuningGroup guitar = new TuningGroup(root, "Guitar");
		TuningGroup sixString = new TuningGroup(guitar, "6-String");
		root.addGroup(guitar);
		guitar.addGroup(sixString);
		sixString.addTuningPreset(new TuningPreset(sixString, "E Tuning", STANDARD));
		sixString.addTuningPreset(new TuningPreset(sixString, "Dropped D", new int[] { 64, 59, 55, 50, 45, 38 }));

		List<TunerTuning> presets = TunerTuning.builtInPresets(root);
		assertEquals(2, presets.size());
		assertEquals("Dropped D", presets.get(1).getName());
		assertEquals(1, TunerTuning.builtInPresets(new TuningGroup()).size());
	}
}
