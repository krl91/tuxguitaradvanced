package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.LevelCheckStats;
import app.tuxguitar.audio.engine.frontend.FrontEnd;
import app.tuxguitar.audio.engine.frontend.GainSuggester;
import app.tuxguitar.audio.engine.frontend.HighPass;
import app.tuxguitar.audio.engine.frontend.NoiseEstimator;
import app.tuxguitar.audio.engine.frontend.NoiseGate;
import app.tuxguitar.audio.engine.frontend.SpectralSubtractor;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;

/**
 * The front-end, noise-gate and gain families (spec 026, T018, inventory.md "FRONT_END", "NOISE_GATE", "GAIN"): their defaults are the former
 * constants of the high-pass, the noise estimate, the gate, the spectral subtraction and the gain suggestion, and the front end's parameters, the gain
 * suggestion's and the native tuning array carry the settings.
 */
public class FrontEndParametersTest {

	@Test
	public void theDefaultsAreTheFormerValues() {
		assertEquals(HighPass.CORNER_HZ, RecognitionParameters.FRONT_END_HIGH_PASS.getDefault());
		assertEquals(NoiseEstimator.DEFAULT_RISE_DB_PER_SECOND, RecognitionParameters.FRONT_END_NOISE_RISE.getDefault());
		assertEquals(NoiseEstimator.DEFAULT_FALL_RATE, RecognitionParameters.FRONT_END_NOISE_FALL.getDefault());
		assertEquals(NoiseEstimator.TRUSTED_AFTER_HOPS, RecognitionParameters.FRONT_END_NOISE_TRUSTED.getDefault().intValue());
		assertEquals(NoiseGate.OPEN_MARGIN_DB, RecognitionParameters.NOISE_GATE_OPEN.getDefault());
		assertEquals(NoiseGate.CLOSE_MARGIN_DB, RecognitionParameters.NOISE_GATE_CLOSE.getDefault());
		assertEquals(NoiseGate.REDUCTION_DB, RecognitionParameters.NOISE_GATE_REDUCTION.getDefault());
		assertEquals(NoiseGate.HOLD_HOPS, RecognitionParameters.NOISE_GATE_HOLD.getDefault().intValue());
		assertEquals(SpectralSubtractor.ALPHA, RecognitionParameters.NOISE_GATE_SUBTRACTION.getDefault());
		assertEquals(SpectralSubtractor.BETA, RecognitionParameters.NOISE_GATE_SUBTRACTION_FLOOR.getDefault());
		assertEquals(SpectralSubtractor.DEFAULT_LEARN_RATE, RecognitionParameters.NOISE_GATE_SUBTRACTION_LEARN.getDefault());
		assertEquals(GainSuggester.TARGET_PLAYING_RMS_DBFS, RecognitionParameters.GAIN_TARGET.getDefault());
		assertEquals(GainSuggester.MAX_PEAK_DBFS, RecognitionParameters.GAIN_MAX_PEAK.getDefault());
		assertEquals(GainSuggester.NO_CHANGE_DB, RecognitionParameters.GAIN_NO_CHANGE.getDefault());
		assertEquals(GainSuggester.MIN_SIGNAL_TO_NOISE_DB, RecognitionParameters.GAIN_MIN_SNR.getDefault());
		assertEquals(fronts(FrontEnd.Parameters.defaults()), fronts(FrontEnd.Parameters.of(RecognitionSettings.defaults())));
		assertEquals(gains(GainSuggester.Parameters.defaults()), gains(GainSuggester.Parameters.of(RecognitionSettings.defaults())));
	}

	@Test
	public void theFrontEndTheGainSuggestionAndTheNativeArrayCarryTheSettings() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.FRONT_END_HIGH_PASS, 80.0)
				.with(RecognitionParameters.FRONT_END_NOISE_RISE, 2.0).with(RecognitionParameters.FRONT_END_NOISE_FALL, 0.5)
				.with(RecognitionParameters.FRONT_END_NOISE_TRUSTED, 50).with(RecognitionParameters.NOISE_GATE_OPEN, 12.0)
				.with(RecognitionParameters.NOISE_GATE_CLOSE, 3.0).with(RecognitionParameters.NOISE_GATE_REDUCTION, 30.0)
				.with(RecognitionParameters.NOISE_GATE_HOLD, 40).with(RecognitionParameters.NOISE_GATE_SUBTRACTION, 2.5)
				.with(RecognitionParameters.NOISE_GATE_SUBTRACTION_FLOOR, 0.1).with(RecognitionParameters.NOISE_GATE_SUBTRACTION_LEARN, 0.3)
				.with(RecognitionParameters.GAIN_TARGET, -18.0).with(RecognitionParameters.GAIN_MAX_PEAK, -6.0)
				.with(RecognitionParameters.GAIN_NO_CHANGE, 3.0).with(RecognitionParameters.GAIN_MIN_SNR, 30.0);
		List<Double> front = Arrays.asList(80.0, 2.0, 0.5, 50.0, 12.0, 3.0, 30.0, 40.0, 2.5, 0.1, 0.3);
		assertEquals(front, fronts(FrontEnd.Parameters.of(s)));
		assertEquals(Arrays.asList(-18.0, -6.0, 3.0, 30.0), gains(GainSuggester.Parameters.of(s)));
		double[] tuning = NativeParameters.tuning(s);
		int[] indices = { NativeParameters.T_FRONT_HIGH_PASS, NativeParameters.T_FRONT_NOISE_RISE, NativeParameters.T_FRONT_NOISE_FALL,
				NativeParameters.T_FRONT_NOISE_TRUSTED, NativeParameters.T_GATE_OPEN, NativeParameters.T_GATE_CLOSE, NativeParameters.T_GATE_REDUCTION,
				NativeParameters.T_GATE_HOLD, NativeParameters.T_SUBTRACTION, NativeParameters.T_SUBTRACTION_FLOOR, NativeParameters.T_SUBTRACTION_LEARN };
		for( int i = 0 ; i < indices.length ; i ++ ) {
			assertEquals(front.get(i), tuning[indices[i]], "tuning index " + indices[i]);
		}
		assertEquals(NativeParameters.TUNING_LENGTH, tuning.length);
	}

	@Test
	public void theGainValuesChangeTheSuggestion() {
		// playing at -30 dBFS RMS, peaks at -12, background at -55: the recommended values propose +8 dB, without the noise warning
		LevelCheckStats stats = new LevelCheckStats(true, -30.0, -12.0, true, -55.0);
		GainSuggester.Parameters recommended = GainSuggester.Parameters.defaults();
		assertEquals(GainSuggester.suggest(stats, 1.0).getMultiplier(), GainSuggester.suggest(stats, 1.0, recommended).getMultiplier());
		double base = GainSuggester.suggest(stats, 1.0, recommended).getMultiplier();
		RecognitionSettings d = RecognitionSettings.defaults();
		assertNotEquals(base, GainSuggester.suggest(stats, 1.0, GainSuggester.Parameters.of(d.with(RecognitionParameters.GAIN_TARGET, -26.0))).getMultiplier());
		assertNotEquals(base, GainSuggester.suggest(stats, 1.0, GainSuggester.Parameters.of(d.with(RecognitionParameters.GAIN_MAX_PEAK, -12.0))).getMultiplier());
		assertEquals(1.0, GainSuggester.suggest(stats, 1.0, GainSuggester.Parameters.of(d.with(RecognitionParameters.GAIN_TARGET, -27.0)
				.with(RecognitionParameters.GAIN_NO_CHANGE, 6.0)))
				.getMultiplier(), "a change under the no-change margin keeps the gain");
		assertNotEquals(1.0, GainSuggester.suggest(stats, 1.0, GainSuggester.Parameters.of(d.with(RecognitionParameters.GAIN_TARGET, -27.0))).getMultiplier());
		assertTrue(!GainSuggester.isNoiseDominated(stats, recommended));
		assertTrue(GainSuggester.isNoiseDominated(stats, GainSuggester.Parameters.of(d.with(RecognitionParameters.GAIN_MIN_SNR, 30.0))));
	}

	@Test
	public void theSectionsHoldTheirValuesAndTheGateMarginsStayOrdered() {
		assertEquals(Arrays.asList(RecognitionParameters.FRONT_END_HIGH_PASS, RecognitionParameters.FRONT_END_NOISE_RISE,
				RecognitionParameters.FRONT_END_NOISE_FALL, RecognitionParameters.FRONT_END_NOISE_TRUSTED), RecognitionParameters.of(Section.FRONT_END));
		assertEquals(Arrays.asList(RecognitionParameters.NOISE_GATE_OPEN, RecognitionParameters.NOISE_GATE_CLOSE, RecognitionParameters.NOISE_GATE_REDUCTION,
				RecognitionParameters.NOISE_GATE_HOLD, RecognitionParameters.NOISE_GATE_SUBTRACTION, RecognitionParameters.NOISE_GATE_SUBTRACTION_FLOOR,
				RecognitionParameters.NOISE_GATE_SUBTRACTION_LEARN), RecognitionParameters.of(Section.NOISE_GATE));
		assertEquals(Arrays.asList(RecognitionParameters.GAIN_TARGET, RecognitionParameters.GAIN_MAX_PEAK, RecognitionParameters.GAIN_NO_CHANGE,
				RecognitionParameters.GAIN_MIN_SNR), RecognitionParameters.of(Section.GAIN));
		boolean declared = false;
		for( OrderedPair pair : RecognitionParameters.ORDERED_PAIRS ) {
			declared |= pair.getLower() == RecognitionParameters.NOISE_GATE_CLOSE && pair.getUpper() == RecognitionParameters.NOISE_GATE_OPEN;
		}
		assertTrue(declared, "close <= open is declared");
		// every value of the two front-end sections at either end builds a front end that processes a hop
		for( Section section : new Section[] { Section.FRONT_END, Section.NOISE_GATE } ) {
			for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
				RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
				for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
					FrontEnd front = new FrontEnd();
					front.configure(44100, SpectralSubtractor.HOP, FrontEnd.Parameters.of(RecognitionSettings.defaults().with(number, end)));
					front.setNoiseReduction(true);
					front.setSubtraction(true);
					front.process(new float[SpectralSubtractor.HOP], SpectralSubtractor.HOP);
				}
			}
		}
	}

	private static List<Double> fronts(FrontEnd.Parameters p) {
		return Arrays.asList(p.highPassHz, p.noiseRiseDbPerSecond, p.noiseFallRate, (double) p.noiseTrustedHops, p.gateOpenDb, p.gateCloseDb,
				p.gateReductionDb, (double) p.gateHoldHops, p.subtractionAlpha, p.subtractionBeta, p.subtractionLearnRate);
	}

	private static List<Double> gains(GainSuggester.Parameters p) {
		return Arrays.asList(p.targetPlayingRmsDbfs, p.maxPeakDbfs, p.noChangeDb, p.minSignalToNoiseDb);
	}
}
