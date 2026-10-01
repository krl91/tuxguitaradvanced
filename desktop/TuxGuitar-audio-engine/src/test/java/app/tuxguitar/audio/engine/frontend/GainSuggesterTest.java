package app.tuxguitar.audio.engine.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.LevelCheckStats;

public class GainSuggesterTest {

	private static LevelCheckStats stats(double playingRms, double playingPeak, double quietRms) {
		return new LevelCheckStats(true, playingRms, playingPeak, !Double.isNaN(quietRms), quietRms);
	}

	@Test
	public void aWeakInputGetsABoostCappedAtTheMaximum() {
		GainSuggestion suggestion = GainSuggester.suggest(stats(-45.0, -30.0, -80.0), 1.0);
		assertEquals(GainSuggestion.Reason.TOO_LOW, suggestion.getReason());
		assertEquals(InputGain.MAX_MULTIPLIER, suggestion.getMultiplier(), 1e-9);
	}

	@Test
	public void theSuggestionIsRelativeToTheGainThatWasAppliedDuringTheCheck() {
		// measured -34 dBFS while +3 dB was applied: the target (-22) is 12 dB above, so the total gain is +15 dB
		double applied = InputGain.fromDb(3.0);
		GainSuggestion suggestion = GainSuggester.suggest(stats(-34.0, -25.0, -80.0), applied);
		assertEquals(GainSuggestion.Reason.TOO_LOW, suggestion.getReason());
		assertEquals(InputGain.fromDb(15.0), suggestion.getMultiplier(), 1e-6);
	}

	@Test
	public void theBoostNeverPushesThePeakOverTheLimit() {
		GainSuggestion suggestion = GainSuggester.suggest(stats(-35.0, -12.0, -90.0), 1.0);
		// -22 would be +13 dB, but the peak (-12) would go to +1: limited to +9 dB
		assertEquals(InputGain.fromDb(GainSuggester.MAX_PEAK_DBFS + 12.0), suggestion.getMultiplier(), 1e-6);
	}

	@Test
	public void aHotInputGetsAReduction() {
		GainSuggestion suggestion = GainSuggester.suggest(stats(-8.0, -1.0, -70.0), 1.0);
		assertEquals(GainSuggestion.Reason.TOO_HIGH, suggestion.getReason());
		assertEquals(InputGain.MIN_MULTIPLIER, suggestion.getMultiplier(), 1e-9);
	}

	@Test
	public void aLevelThatIsAlreadyRightChangesNothing() {
		GainSuggestion suggestion = GainSuggester.suggest(stats(-23.0, -10.0, -70.0), 1.5);
		assertEquals(GainSuggestion.Reason.OK, suggestion.getReason());
		assertEquals(1.5, suggestion.getMultiplier(), 1e-9);
	}

	@Test
	public void aWeakNoisyInputIsToldThatABoostWouldAmplifyNoise() {
		GainSuggestion suggestion = GainSuggester.suggest(stats(-40.0, -28.0, -52.0), 1.0);
		assertEquals(GainSuggestion.Reason.NOISE_DOMINATED, suggestion.getReason());
		assertTrue(GainSuggester.isNoiseDominated(stats(-40.0, -28.0, -52.0)));
		assertFalse(GainSuggester.isNoiseDominated(stats(-40.0, -28.0, -80.0)));
		assertFalse(GainSuggester.isNoiseDominated(new LevelCheckStats(true, -40.0, -28.0, false, Double.NaN)));
	}

	@Test
	public void noPlayingMeansNoSuggestion() {
		assertNull(GainSuggester.suggest(null, 1.0));
		assertNull(GainSuggester.suggest(new LevelCheckStats(false, Double.NaN, Double.NaN, true, -70.0), 1.0));
	}
}
