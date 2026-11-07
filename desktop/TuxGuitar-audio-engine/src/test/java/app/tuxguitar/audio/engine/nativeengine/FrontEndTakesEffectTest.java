package app.tuxguitar.audio.engine.nativeengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.frontend.FrontEnd;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Each front-end and noise-gate value takes effect, alike in both engines (spec 026, T018, SC-001): with the noise reduction and the subtraction on,
 * every value of the two sections at one end of its range changes the hops of a take (a hum and a background noise, notes that fade into it), and the
 * native front end gives the hops of the Java one within 1e-4 relative, as {@code FrontEndParityTest} does for the recommended values.
 */
public class FrontEndTakesEffectTest {

	private static final int RATE = 44100;

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void everyFrontEndValueChangesTheHopsAlikeInBothEngines() {
		float[] take = take();
		float[] recommended = hops(take, RecognitionSettings.defaults(), "the recommended values");
		List<String> withoutEffect = new ArrayList<String>();
		for( Section section : new Section[] { Section.FRONT_END, Section.NOISE_GATE } ) {
			for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
				RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
				boolean changed = false;
				for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
					RecognitionSettings settings = RecognitionSettings.defaults().with(number, end);
					changed |= differs(recommended, hops(take, settings, number.getKey() + " at " + end));
				}
				if( !changed ) {
					withoutEffect.add(number.getKey());
				}
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change no hop");
	}

	/** The Java front end's hops, checked against the native ones. */
	private static float[] hops(float[] take, RecognitionSettings settings, String label) {
		int hop = ParityEngines.HOP;
		FrontEnd front = new FrontEnd();
		front.configure(RATE, hop, FrontEnd.Parameters.of(settings));
		front.setNoiseReduction(true);
		front.setSubtraction(true);
		int count = take.length / hop;
		float[] expected = new float[count * hop];
		float[] block = new float[hop];
		for( int h = 0 ; h < count ; h ++ ) {
			System.arraycopy(take, h * hop, block, 0, hop);
			front.process(block, hop);
			System.arraycopy(block, 0, expected, h * hop, hop);
		}
		float[] actual = NativeRecognitionLibrary.frontEndTuned(take, RATE, NativeParameters.of(settings, 1.0, true, true), NativeParameters.tuning(settings));
		assertEquals(expected.length, actual.length, label);
		for( int i = 0 ; i < expected.length ; i ++ ) {
			double tolerance = 1e-4 * Math.max(Math.abs(expected[i]), 1e-3);
			if( Math.abs(expected[i] - actual[i]) > tolerance ) {
				fail(label + ": sample " + i + " (hop " + i / hop + ") Java " + expected[i] + " native " + actual[i]);
			}
		}
		return expected;
	}

	private static boolean differs(float[] a, float[] b) {
		for( int i = 0 ; i < a.length ; i ++ ) {
			if( Math.abs(a[i] - b[i]) > 1e-6 * Math.max(Math.abs(a[i]), 1e-3) ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A 30 Hz hum throughout; a loud background noise until 1.5 s that then drops, with a soft note just after the drop (the noise estimate falls
	 * toward it); a strum at 2.5 s and a softer one at 4.5 s fading into the quiet noise; a noise growing during the last second.
	 */
	private static float[] take() {
		float[] signal = new float[7 * RATE];
		float[] hum = SyntheticSignals.sine(30.0, RATE, signal.length, 0.01);
		float[] noise = SyntheticSignals.noise(signal.length, Math.pow(10, -55.0 / 20.0), 12L);
		for( int i = 0 ; i < signal.length ; i ++ ) {
			double scale = (i < 3 * RATE / 2 ? 20.0 : (i > 6 * RATE ? 1.0 + 9.0 * (i - 6 * RATE) / (double) RATE : 1.0));
			signal[i] = (float) (hum[i] + noise[i] * scale);
		}
		SyntheticSignals.mix(signal, SyntheticSignals.strum(new int[] { 57 }, RATE, RATE / 2, 0, 0.02, 91L), 3 * RATE / 2 + RATE / 50);
		SyntheticSignals.mix(signal, SyntheticSignals.strum(new int[] { 40, 47, 52, 56, 59, 64 }, RATE, (int) (1.6 * RATE), RATE / 100, 0.3, 71L),
				(int) (2.5 * RATE));
		SyntheticSignals.mix(signal, SyntheticSignals.strum(new int[] { 45, 52, 57, 61, 64 }, RATE, (int) (1.4 * RATE), RATE / 100, 0.05, 81L),
				(int) (4.5 * RATE));
		return signal;
	}
}
