package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/** Spec 013 (T016): the recording is cut into one segment per sound, or refused with the reason and no analysis tried (FR-004, FR-006). */
public class SoundSegmenterTest {

	private static final int RATE = 48000;

	/** plucks of a low string at the given seconds, in a recording of the given length */
	private static float[] plucks(double[] seconds, double length) {
		int[] onsets = new int[seconds.length];
		for (int i = 0; i < seconds.length; i++) {
			onsets[i] = (int) (seconds[i] * RATE);
		}
		return SyntheticSignals.repeatedPlucks(110.0, RATE, (int) (length * RATE), onsets, 7L);
	}

	@Test
	public void fourPlucksGiveFourSegmentsEachStartingJustBeforeItsOwnAttack() {
		double[] at = { 1.0, 3.0, 5.0, 7.0 };
		SoundSegmenter.Result result = SoundSegmenter.segment(plucks(at, 9.0), RATE, 4);
		assertTrue(result.isAccepted(), String.valueOf(result.getRefusal()));
		assertEquals(4, result.getSegments().size());
		assertEquals(4, result.getHeard());
		int total = 0;
		for (int i = 0; i < 4; i++) {
			int onset = (int) (at[i] * RATE);
			int start = result.getStarts().get(i);
			assertTrue(start <= onset, "segment " + i + " starts before the attack: " + start + " vs " + onset);
			assertTrue(start > onset - RATE / 5, "and not long before it: " + start + " vs " + onset);
			if (i > 0) {
				assertTrue(start > (int) (at[i - 1] * RATE), "and after the attack of the sound before");
				assertEquals(result.getStarts().get(i) - result.getStarts().get(i - 1), result.getSegments().get(i - 1).length, "a segment runs to the start of the next");
			}
			total += result.getSegments().get(i).length;
		}
		assertEquals(9 * RATE - result.getStarts().get(0), total, "and the last one to the end of the recording");
	}

	@Test
	public void fewerOrMorePlucksThanExpectedAreRefusedWithTheNumbers() {
		SoundSegmenter.Result few = SoundSegmenter.segment(plucks(new double[] { 1.0, 3.0, 5.0 }, 7.0), RATE, 4);
		assertFalse(few.isAccepted());
		assertEquals(SoundSegmenter.Refusal.WRONG_COUNT, few.getRefusal());
		assertEquals(3, few.getHeard());
		assertEquals(4, few.getExpected());
		assertNull(few.getSegments(), "nothing to analyse");
		SoundSegmenter.Result many = SoundSegmenter.segment(plucks(new double[] { 1.0, 3.0, 5.0, 7.0, 9.0 }, 11.0), RATE, 4);
		assertEquals(SoundSegmenter.Refusal.WRONG_COUNT, many.getRefusal());
		assertEquals(5, many.getHeard());
	}

	@Test
	public void twoAttacksLessThanTheRefractoryTimeApartCountAsOneSound() {
		SoundSegmenter.Result result = SoundSegmenter.segment(plucks(new double[] { 1.0, 1.10, 3.0 }, 5.0), RATE, 2);
		assertTrue(result.isAccepted(), "100 ms apart: one sound (starting value 250 ms), heard " + result.getHeard());
		assertEquals(2, result.getHeard());
	}

	@Test
	public void aSilentRecordingIsRefusedAsSilent() {
		SoundSegmenter.Result result = SoundSegmenter.segment(SyntheticSignals.silence(5 * RATE), RATE, 4);
		assertEquals(SoundSegmenter.Refusal.SILENT, result.getRefusal());
		assertEquals(0, result.getHeard());
	}

	@Test
	public void aClippedRecordingIsRefusedAsClipped() {
		float[] loud = plucks(new double[] { 1.0, 3.0, 5.0, 7.0 }, 9.0);
		for (int i = 0; i < loud.length; i++) {
			loud[i] *= 60f;
		}
		SoundSegmenter.Result result = SoundSegmenter.segment(SyntheticSignals.hardClip(loud, 1.0), RATE, 4);
		assertEquals(SoundSegmenter.Refusal.CLIPPED, result.getRefusal());
	}

	@Test
	public void theNoiseOfARoomAloneIsNotCountedAsSounds() {
		SoundSegmenter.Result result = SoundSegmenter.segment(SyntheticSignals.noise(6 * RATE, 0.0005, 3L), RATE, 4);
		assertFalse(result.isAccepted());
		assertEquals(0, result.getHeard(), "a faint hiss is below the input level minimum");
	}
}
