package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.nativeengine.FrameRecords;
import app.tuxguitar.audio.engine.nativeengine.NativeLibrary;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Each onset value takes effect, alike in both engines (spec 026, T013, SC-001): on a synthetic take of plucks (equal, weak, fast and slow
 * re-plucks), every value of the families {@code onset} and {@code onset-fast} at one end of its range changes the onsets found with the
 * recommended values (the attack floor lowered, so that the softest plucks are judged by the flux), and the Java detector and the native one find the same onsets for both ends.
 */
public class OnsetTakesEffectTest {

	private static final int RATE = 48000;
	private static final int WINDOW = 2048;
	private static final int HOP = 256;
	private static final double E3 = 164.81;

	@BeforeAll
	public static void library() {
		String failure = NativeRecognitionLibrary.load();
		String directory = System.getProperty(NativeLibrary.DIRECTORY_PROPERTY);
		if( failure != null && directory != null && !directory.trim().isEmpty() ) {
			throw new AssertionError("the native library was not loaded from " + directory + ": " + failure);
		}
		Assumptions.assumeTrue(failure == null, "the native library is not loaded (-Dtuxguitar.native.library.dir): " + failure);
	}

	@Test
	public void everyOnsetValueChangesTheOnsetsAlikeInBothEngines() {
		check(Section.ONSET, false);
	}

	@Test
	public void everyFastOnsetValueChangesTheFastOnsetsAlikeInBothEngines() {
		check(Section.ONSET_FAST, true);
	}

	private static void check(Section section, boolean fast) {
		float[] take = take();
		// the attack floor at its lowest, so the soft plucks of the quiet passage reach the flux test
		RecognitionSettings defaults = RecognitionSettings.defaults().with(RecognitionParameters.ATTACK_MIN_LEVEL, RecognitionParameters.ATTACK_MIN_LEVEL.getMinimum());
		List<Integer> recommended = java(take, defaults, fast);
		assertEquals(recommended, nativeOnsets(take, defaults, fast), "the recommended values");
		assertTrue(recommended.size() > 3, "the take has onsets: " + recommended);
		List<String> withoutEffect = new ArrayList<String>();
		for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			boolean changed = false;
			for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
				RecognitionSettings settings = defaults.with(number, end);
				List<Integer> java = java(take, settings, fast);
				assertEquals(java, nativeOnsets(take, settings, fast), number.getKey() + " at " + end);
				changed |= !java.equals(recommended);
			}
			if( !changed ) {
				withoutEffect.add(number.getKey());
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change no onset");
	}

	/** The hops on which the Java detector reports an onset. */
	private static List<Integer> java(float[] mono, RecognitionSettings settings, boolean fast) {
		ArticulationDetector detector = (fast ? AudioEngine.fastArticulationDetector(settings) : AudioEngine.articulationDetector(settings));
		float[] window = new float[WINDOW];
		int filled = 0;
		int frame = 0;
		List<Integer> onsets = new ArrayList<Integer>();
		for( int offset = 0 ; offset + HOP <= mono.length ; offset += HOP ) {
			System.arraycopy(window, HOP, window, 0, WINDOW - HOP);
			System.arraycopy(mono, offset, window, WINDOW - HOP, HOP);
			filled = Math.min(WINDOW, filled + HOP);
			if( filled < WINDOW ) {
				continue;
			}
			if( detector.process(window) ) {
				onsets.add(frame);
			}
			frame ++;
		}
		return onsets;
	}

	/** The frames on which the native engine reports an onset. */
	private static List<Integer> nativeOnsets(float[] mono, RecognitionSettings settings, boolean fast) {
		float[] parameters = NativeParameters.of(settings);
		if( fast ) {
			parameters = NativeParameters.withFastOnset(parameters, settings);
		}
		float[] records = NativeRecognitionLibrary.analyze(mono, RATE, parameters, false, null, null, null, null);
		List<Integer> onsets = new ArrayList<Integer>();
		int offset = 0;
		int frame = 0;
		while( offset < records.length ) {
			if( FrameRecords.read(records, offset).isOnset() ) {
				onsets.add(frame);
			}
			offset += FrameRecords.length(records, offset);
			frame ++;
		}
		return onsets;
	}

	/**
	 * Short plucks (damped after a tenth of a second) re-picked at gaps from 30 to 200 ms, a long ringing note re-picked, then soft plucks
	 * at falling levels, over a noise floor; last a quiet passage of very soft plucks and soft
	 * pure tones over a far lower floor.
	 */
	private static float[] take() {
		List<double[]> plucks = new ArrayList<double[]>();
		double t = 0.4;
		for( double gap : new double[] { 0.03, 0.05, 0.08, 0.12, 0.2 } ) {
			plucks.add(new double[] { t, 1.0, 0.08 });
			plucks.add(new double[] { t + gap, 1.0, 0.08 });
			t += 0.8;
		}
		plucks.add(new double[] { t, 1.0, 2.0 });
		plucks.add(new double[] { t + 0.6, 0.7, 2.0 });
		plucks.add(new double[] { t + 1.2, 0.5, 2.0 });
		t += 2.4;
		for( double gain : new double[] { 0.3, 0.15, 0.08, 0.04, 0.02 } ) {
			plucks.add(new double[] { t, gain, 0.3 });
			t += 0.6;
		}
		// a quiet passage: very soft plucks over a much lower noise floor, where the flux margin decides
		double quiet = t;
		for( double gain : new double[] { 0.01, 0.005, 0.0025, 0.0012, 0.0006 } ) {
			plucks.add(new double[] { t, gain, 0.3 });
			t += 0.6;
		}
		// and soft pure tones: few spectral bins rise, so their flux is near the margin
		List<double[]> tones = new ArrayList<double[]>();
		for( double amplitude : new double[] { 0.004, 0.002, 0.001, 0.0005 } ) {
			tones.add(new double[] { t, amplitude });
			t += 0.6;
		}
		float[] signal = new float[(int) ((t + 0.5) * RATE)];
		for( double[] tone : tones ) {
			int onset = (int) (tone[0] * RATE);
			for( int i = 0 ; i < (int) (0.3 * RATE) ; i ++ ) {
				double attack = Math.min(1.0, i / (0.005 * RATE));
				signal[onset + i] += (float) (tone[1] * attack * Math.sin(2 * Math.PI * 220.0 * i / RATE));
			}
		}
		int seed = 11;
		for( double[] pluck : plucks ) {
			int onset = (int) (pluck[0] * RATE);
			float[] tone = SyntheticSignals.karplusStrong(E3 * (seed % 3 == 2 ? 1.5 : 1.0), RATE, signal.length - onset, seed ++);
			for( int i = 0 ; i < tone.length ; i ++ ) {
				double damping = Math.exp(-Math.max(0.0, i / (double) RATE - pluck[2]) / 0.02);
				signal[onset + i] += (float) (0.5 * pluck[1] * damping * tone[i]);
			}
		}
		float[] noise = SyntheticSignals.noise(signal.length, Math.pow(10, -40.0 / 20.0), 3L);
		int quietStart = (int) ((quiet - 0.3) * RATE);
		for( int i = 0 ; i < signal.length ; i ++ ) {
			signal[i] += (i < quietStart ? noise[i] : noise[i] * 0.0001f);
		}
		return signal;
	}
}
