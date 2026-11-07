package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.recognition.ExpectedPitchVerifier;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;

/**
 * The pitch family (spec 026, T014, inventory.md "PITCH"): its defaults are the former constants, the native engine gets the YIN threshold in the
 * {@code float[21]} and the target search in the tuning array, the candidate range stays ordered, and no value at either end moves another.
 */
public class PitchParametersTest {

	@Test
	public void theDefaultsAreTheFormerConstants() {
		assertEquals(NativeParameters.DEFAULT_YIN_THRESHOLD, RecognitionParameters.PITCH_YIN_THRESHOLD.getDefault());
		assertEquals(ExpectedPitchVerifier.SEARCH_CENTS, RecognitionParameters.PITCH_TARGET_SEARCH.getDefault());
		assertEquals(ExpectedPitchVerifier.PERIODS, RecognitionParameters.PITCH_TARGET_PERIODS.getDefault());
		assertEquals(TolerancePolicy.DEFAULT_MAX_SHARP_CENTS, RecognitionParameters.PITCH_MAX_SHARP.getDefault());
		assertEquals(28.0, RecognitionParameters.PITCH_ATTACK_CANDIDATES_LOW.getDefault());
		assertEquals(100.0, RecognitionParameters.PITCH_ATTACK_CANDIDATES_HIGH.getDefault());
		assertEquals(Arrays.asList(RecognitionParameters.PITCH_YIN_THRESHOLD, RecognitionParameters.PITCH_TARGET_SEARCH, RecognitionParameters.PITCH_TARGET_PERIODS,
				RecognitionParameters.PITCH_MAX_SHARP, RecognitionParameters.PITCH_ATTACK_CANDIDATES_LOW, RecognitionParameters.PITCH_ATTACK_CANDIDATES_HIGH),
				RecognitionParameters.of(Section.PITCH));
	}

	@Test
	public void theNativeEngineGetsTheValues() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.PITCH_YIN_THRESHOLD, 0.35)
				.with(RecognitionParameters.PITCH_TARGET_SEARCH, 33).with(RecognitionParameters.PITCH_TARGET_PERIODS, 6);
		assertEquals(0.35f, NativeParameters.of(s)[NativeParameters.YIN_THRESHOLD], 1e-6f);
		double[] tuning = NativeParameters.tuning(s);
		assertEquals(33.0, tuning[NativeParameters.T_TARGET_SEARCH]);
		assertEquals(6.0, tuning[NativeParameters.T_TARGET_PERIODS]);
		double[] defaults = NativeParameters.tuning(RecognitionSettings.defaults());
		assertEquals(ExpectedPitchVerifier.SEARCH_CENTS, defaults[NativeParameters.T_TARGET_SEARCH]);
		assertEquals(ExpectedPitchVerifier.PERIODS, defaults[NativeParameters.T_TARGET_PERIODS]);
		assertEquals((float) NativeParameters.DEFAULT_YIN_THRESHOLD, NativeParameters.of(RecognitionSettings.defaults())[NativeParameters.YIN_THRESHOLD]);
	}

	@Test
	public void theCandidateRangeStaysOrdered() {
		List<String> pairs = new ArrayList<String>();
		for( OrderedPair pair : RecognitionParameters.ORDERED_PAIRS ) {
			pairs.add(pair.toString());
		}
		assertTrue(pairs.contains("recognition.pitch.attack-candidates-low <= recognition.pitch.attack-candidates-high"), pairs.toString());
	}

	@Test
	public void theMaximumSharpBoundsTheAcceptedSharpSide() {
		TolerancePolicy policy = new TolerancePolicy(10.0, 35.0, 10.0, 40.0);
		assertEquals(40.0, policy.sharpLimitCents(12));
		assertEquals(35.0, policy.sharpLimitCents(0));
		assertEquals(TolerancePolicy.DEFAULT_MAX_SHARP_CENTS, new TolerancePolicy(10.0, 35.0, 10.0).sharpLimitCents(12));
	}

	@Test
	public void fromTheDefaultsNoValueAtEitherEndMovesAnother() {
		RecognitionSettings defaults = RecognitionSettings.defaults();
		for( RecognitionParameter parameter : RecognitionParameters.of(Section.PITCH) ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
				RecognitionSettings changed = defaults.with(number, end);
				for( RecognitionParameter other : RecognitionParameters.ALL ) {
					if( other != number ) {
						assertEquals(defaults.getValue(other), changed.getValue(other), number.getKey() + " at " + end + " moved " + other.getKey());
					}
				}
			}
		}
	}
}
