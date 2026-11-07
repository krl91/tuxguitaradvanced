package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;

/**
 * The onset families (spec 026, T013, inventory.md "ONSET" and "ONSET_FAST"): their defaults are the former constants, so the detectors and the
 * native array built from the defaults are those of before; their pairs stay ordered; from the defaults, no value at either end moves another.
 */
public class OnsetParametersTest {

	@Test
	public void theDefaultsAreTheFormerConstants() {
		assertEquals(ArticulationDetector.DEFAULT_FLUX_MEDIAN_FACTOR, RecognitionParameters.ONSET_FLUX_FACTOR.getDefault());
		assertEquals(ArticulationDetector.DEFAULT_FLUX_OFFSET, RecognitionParameters.ONSET_FLUX_OFFSET.getDefault());
		assertEquals(ArticulationDetector.DEFAULT_REFRACTORY_HOPS, RecognitionParameters.ONSET_REFRACTORY.getDefault());
		assertEquals(19.0, RecognitionParameters.ONSET_PRE_FIRST.getDefault());
		assertEquals(2.0, RecognitionParameters.ONSET_PRE_LAST.getDefault());
		assertEquals(4.0, RecognitionParameters.ONSET_POST_FIRST.getDefault());
		assertEquals(ArticulationDetector.DECISION_DELAY_HOPS, RecognitionParameters.ONSET_POST_LAST.getDefault());
		assertEquals(ArticulationDetector.DEFAULT_FLUX_MEDIAN_FACTOR, RecognitionParameters.ONSET_FAST_FLUX_FACTOR.getDefault());
		assertEquals(ArticulationDetector.DEFAULT_FLUX_OFFSET, RecognitionParameters.ONSET_FAST_FLUX_OFFSET.getDefault());
		assertEquals(ArticulationDetector.FAST_REFRACTORY_HOPS, RecognitionParameters.ONSET_FAST_REFRACTORY.getDefault());
		assertEquals(8.0, RecognitionParameters.ONSET_FAST_PRE_FIRST.getDefault());
		assertEquals(2.0, RecognitionParameters.ONSET_FAST_PRE_LAST.getDefault());
		assertEquals(3.0, RecognitionParameters.ONSET_FAST_POST_FIRST.getDefault());
		assertEquals(ArticulationDetector.FAST_DECISION_DELAY_HOPS, RecognitionParameters.ONSET_FAST_POST_LAST.getDefault());
	}

	@Test
	public void theDetectorsAndTheNativeArrayOfTheDefaultsAreThoseOfBefore() {
		RecognitionSettings defaults = RecognitionSettings.defaults();
		ArticulationDetector normal = AudioEngine.articulationDetector(defaults);
		assertEquals(Arrays.asList(1.5, 5.0, 12.0, 19.0, 2.0, 4.0, 13.0), windows(normal));
		ArticulationDetector fast = AudioEngine.fastArticulationDetector(defaults);
		ArticulationDetector former = ArticulationDetector.fast(normal.getMinLevelDb(), normal.getGateDb());
		assertEquals(windows(former), windows(fast));
		assertEquals(former.getMinLevelDb(), fast.getMinLevelDb());
		assertEquals(former.getGateDb(), fast.getGateDb());
		float[] base = NativeParameters.of(defaults);
		assertTrue(Arrays.equals(NativeParameters.withFastOnset(base), NativeParameters.withFastOnset(base, defaults)));
	}

	@Test
	public void theNativeArrayCarriesTheSettings() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.ONSET_FLUX_FACTOR, 2.5).with(RecognitionParameters.ONSET_FLUX_OFFSET, 7.5)
				.with(RecognitionParameters.ONSET_REFRACTORY, 20).with(RecognitionParameters.ONSET_PRE_FIRST, 15).with(RecognitionParameters.ONSET_PRE_LAST, 3)
				.with(RecognitionParameters.ONSET_POST_FIRST, 5).with(RecognitionParameters.ONSET_POST_LAST, 11)
				.with(RecognitionParameters.ONSET_FAST_FLUX_FACTOR, 3.0).with(RecognitionParameters.ONSET_FAST_FLUX_OFFSET, 9.0)
				.with(RecognitionParameters.ONSET_FAST_REFRACTORY, 10).with(RecognitionParameters.ONSET_FAST_PRE_FIRST, 6)
				.with(RecognitionParameters.ONSET_FAST_PRE_LAST, 3).with(RecognitionParameters.ONSET_FAST_POST_FIRST, 2)
				.with(RecognitionParameters.ONSET_FAST_POST_LAST, 7);
		float[] p = NativeParameters.of(s);
		assertEquals(Arrays.asList(2.5, 7.5, 20.0, 15.0, 3.0, 5.0, 11.0), floats(p));
		assertEquals(Arrays.asList(2.5, 7.5, 20.0, 15.0, 3.0, 5.0, 11.0), windows(AudioEngine.articulationDetector(s)));
		float[] fast = NativeParameters.withFastOnset(p, s);
		assertEquals(Arrays.asList(3.0, 9.0, 10.0, 6.0, 3.0, 2.0, 7.0), floats(fast));
		assertEquals(Arrays.asList(3.0, 9.0, 10.0, 6.0, 3.0, 2.0, 7.0), windows(AudioEngine.fastArticulationDetector(s)));
	}

	@Test
	public void theTwoSectionsHoldTheirSevenValues() {
		assertEquals(Arrays.asList(RecognitionParameters.ONSET_FLUX_FACTOR, RecognitionParameters.ONSET_FLUX_OFFSET, RecognitionParameters.ONSET_REFRACTORY,
				RecognitionParameters.ONSET_PRE_FIRST, RecognitionParameters.ONSET_PRE_LAST, RecognitionParameters.ONSET_POST_FIRST,
				RecognitionParameters.ONSET_POST_LAST), RecognitionParameters.of(Section.ONSET));
		assertEquals(Arrays.asList(RecognitionParameters.ONSET_FAST_FLUX_FACTOR, RecognitionParameters.ONSET_FAST_FLUX_OFFSET,
				RecognitionParameters.ONSET_FAST_REFRACTORY, RecognitionParameters.ONSET_FAST_PRE_FIRST, RecognitionParameters.ONSET_FAST_PRE_LAST,
				RecognitionParameters.ONSET_FAST_POST_FIRST, RecognitionParameters.ONSET_FAST_POST_LAST), RecognitionParameters.of(Section.ONSET_FAST));
	}

	@Test
	public void theWindowsStayOrdered() {
		List<String> pairs = new ArrayList<String>();
		for( OrderedPair pair : RecognitionParameters.ORDERED_PAIRS ) {
			pairs.add(pair.toString());
		}
		assertTrue(pairs.contains("recognition.onset.pre-last <= recognition.onset.pre-first"), pairs.toString());
		assertTrue(pairs.contains("recognition.onset.post-first <= recognition.onset.post-last"), pairs.toString());
		assertTrue(pairs.contains("recognition.onset-fast.pre-last <= recognition.onset-fast.pre-first"), pairs.toString());
		assertTrue(pairs.contains("recognition.onset-fast.post-first <= recognition.onset-fast.post-last"), pairs.toString());
		// every pair the detectors could not build is repaired: the detector of any such settings is built
		RecognitionSettings inverted = RecognitionSettings.defaults().with(RecognitionParameters.ONSET_PRE_FIRST, 2)
				.with(RecognitionParameters.ONSET_POST_LAST, 4).with(RecognitionParameters.ONSET_FAST_PRE_FIRST, 2)
				.with(RecognitionParameters.ONSET_FAST_POST_LAST, 3).with(RecognitionParameters.ONSET_PRE_LAST, 19)
				.with(RecognitionParameters.ONSET_POST_FIRST, 13);
		AudioEngine.articulationDetector(inverted);
		AudioEngine.fastArticulationDetector(inverted);
	}

	@Test
	public void fromTheDefaultsNoValueAtEitherEndMovesAnother() {
		RecognitionSettings defaults = RecognitionSettings.defaults();
		for( Section section : new Section[] { Section.ONSET, Section.ONSET_FAST } ) {
			for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
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

	private static List<Double> windows(ArticulationDetector d) {
		return Arrays.asList(d.getFluxMedianFactor(), d.getFluxOffset(), (double) d.getRefractoryHops(), (double) d.getPreFirst(), (double) d.getPreLast(),
				(double) d.getPostFirst(), (double) d.getDecisionDelayHops());
	}

	private static List<Double> floats(float[] p) {
		int[] indices = { NativeParameters.ONSET_FLUX_FACTOR, NativeParameters.ONSET_FLUX_OFFSET, NativeParameters.ONSET_REFRACTORY, NativeParameters.ONSET_PRE_FIRST,
				NativeParameters.ONSET_PRE_LAST, NativeParameters.ONSET_POST_FIRST, NativeParameters.ONSET_POST_LAST };
		List<Double> values = new ArrayList<Double>();
		for( int index : indices ) {
			values.add((double) p[index]);
		}
		return values;
	}
}
