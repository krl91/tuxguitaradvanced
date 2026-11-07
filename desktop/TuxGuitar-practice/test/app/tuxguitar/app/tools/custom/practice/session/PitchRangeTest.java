package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The practice values of the pitch family take effect at the next run (spec 026, T014): the attack candidates' neighbours stay within the
 * candidate range, and the sharp side of a note is never accepted beyond the maximum sharp deviation.
 */
public class PitchRangeTest {

	@Test
	public void theNeighboursStayWithinTheCandidateRange() {
		TreeSet<Integer> own = new TreeSet<Integer>(Arrays.asList(30, 98));
		// the recommended range: every neighbour from 28 to 100
		assertArrayEquals(new int[] { 28, 29, 30, 31, 32, 42, 86, 96, 97, 98, 99, 100 }, AttackCandidates.around(own, 28, 100));
		assertArrayEquals(new int[] { 30, 31, 32, 42, 86, 96, 97, 98 }, AttackCandidates.around(own, 30, 98));
		RecognitionSettings narrow = RecognitionSettings.defaults().with(RecognitionParameters.PITCH_ATTACK_CANDIDATES_LOW, 30)
				.with(RecognitionParameters.PITCH_ATTACK_CANDIDATES_HIGH, 98);
		assertArrayEquals(AttackCandidates.around(own, 30, 98), AttackCandidates.around(own, narrow));
		assertArrayEquals(AttackCandidates.around(own, 28, 100), AttackCandidates.around(own, RecognitionSettings.defaults()));
	}

	@Test
	public void theSharpSideStopsAtTheMaximumSharp() {
		RecognitionSettings settings = RecognitionSettings.defaults().with(RecognitionParameters.NOTE_FRET_SHARP, 2).with(RecognitionParameters.PITCH_MAX_SHARP, 40);
		assertEquals(40.0, PracticeParameters.of(settings).tolerance().sharpLimitCents(12));
		RecognitionSettings recommended = RecognitionSettings.defaults().with(RecognitionParameters.NOTE_FRET_SHARP, 2);
		assertEquals(49.0, PracticeParameters.of(recommended).tolerance().sharpLimitCents(12));
	}
}
