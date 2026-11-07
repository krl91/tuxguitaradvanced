package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.nativeengine.FrameRecords;
import app.tuxguitar.audio.engine.nativeengine.NativeLibrary;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.recognition.ExpectedPitchVerifier;
import app.tuxguitar.audio.engine.recognition.TargetEvidence;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * The engine values of the pitch family take effect alike in both engines (spec 026, T014, SC-001): the YIN threshold changes which frames of a
 * noisy tone are pitched, the target search and periods change the expected-pitch evidence, and the Java and native engines agree at both ends of
 * each range (within the tolerances of the committed parity tests: 1 cent, one pitched decision in a thousand frames, a score to 1e-4).
 */
public class PitchTakesEffectTest {

	private static final int RATE = 48000;
	private static final int WINDOW = 2048;
	private static final int HOP = 256;

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
	public void theYinThresholdChangesThePitchedFramesAlikeInBothEngines() {
		float[] mono = noisyTone();
		RecognitionSettings defaults = RecognitionSettings.defaults();
		int recommended = pitchedJava(mono, defaults);
		RecognitionParameter.Number threshold = RecognitionParameters.PITCH_YIN_THRESHOLD;
		boolean changed = false;
		for( double end : new double[] { threshold.getMinimum(), threshold.getMaximum() } ) {
			RecognitionSettings settings = defaults.with(threshold, end);
			changed |= pitchedJava(mono, settings) != recommended;
			comparePitch(mono, settings, "threshold " + end);
		}
		comparePitch(mono, defaults, "recommended");
		assertTrue(changed, "the threshold changes the pitched frames: " + recommended);
	}

	@Test
	public void theTargetSearchAndPeriodsChangeTheEvidenceAlikeInBothEngines() {
		float[] mono = SyntheticSignals.harmonics(110.0 * Math.pow(2, 0.3 / 12.0), RATE, RATE * 2, 0.3, 0.15, 0.08);
		int[] expected = { 45, 46, 44, 57 };
		RecognitionSettings defaults = RecognitionSettings.defaults();
		List<TargetEvidence> recommended = evidenceJava(mono, defaults, expected);
		assertSame(recommended, evidenceNative(mono, defaults, expected), 1e-4, "the recommended values");
		for( RecognitionParameter.Number number : new RecognitionParameter.Number[] { RecognitionParameters.PITCH_TARGET_SEARCH,
				RecognitionParameters.PITCH_TARGET_PERIODS } ) {
			boolean changed = false;
			for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
				RecognitionSettings settings = defaults.with(number, end);
				List<TargetEvidence> java = evidenceJava(mono, settings, expected);
				assertSame(java, evidenceNative(mono, settings, expected), 1e-4, number.getKey() + " at " + end);
				changed |= difference(java, recommended) > 1e-3;
			}
			assertTrue(changed, number.getKey() + " changes the evidence");
		}
	}

	/** The evidences agree frame by frame: same window, same hypotheses, scores within {@code tolerance}. */
	private static void assertSame(List<TargetEvidence> a, List<TargetEvidence> b, double tolerance, String label) {
		assertEquals(a.size(), b.size(), label);
		assertTrue(difference(a, b) <= tolerance, label + ": the largest difference is " + difference(a, b));
	}

	/** The largest difference of window (in ms) or score between two evidence lists; infinite when their shapes differ. */
	private static double difference(List<TargetEvidence> a, List<TargetEvidence> b) {
		if( a.size() != b.size() ) {
			return Double.POSITIVE_INFINITY;
		}
		double largest = 0.0;
		for( int i = 0 ; i < a.size() ; i ++ ) {
			TargetEvidence x = a.get(i);
			TargetEvidence y = b.get(i);
			if( x.getHypotheses().size() != y.getHypotheses().size() ) {
				return Double.POSITIVE_INFINITY;
			}
			largest = Math.max(largest, Math.abs(x.getWindowMillis() - y.getWindowMillis()));
			for( int h = 0 ; h < x.getHypotheses().size() ; h ++ ) {
				if( x.getHypotheses().get(h).getMidi() != y.getHypotheses().get(h).getMidi() ) {
					return Double.POSITIVE_INFINITY;
				}
				largest = Math.max(largest, Math.abs(x.getHypotheses().get(h).getScore() - y.getHypotheses().get(h).getScore()));
			}
		}
		return largest;
	}

	private static int pitchedJava(float[] mono, RecognitionSettings settings) {
		int pitched = 0;
		for( double f0 : javaPitch(mono, settings) ) {
			pitched += Double.isNaN(f0) ? 0 : 1;
		}
		return pitched;
	}

	private static void comparePitch(float[] mono, RecognitionSettings settings, String label) {
		List<Double> java = javaPitch(mono, settings);
		List<Double> nativePitch = new ArrayList<Double>();
		for( AnalysisFrame frame : nativeFrames(mono, settings, null) ) {
			nativePitch.add(frame.getF0Hz());
		}
		assertEquals(java.size(), nativePitch.size(), label);
		int decisions = 0;
		for( int i = 0 ; i < java.size() ; i ++ ) {
			double a = java.get(i);
			double b = nativePitch.get(i);
			if( Double.isNaN(a) != Double.isNaN(b) ) {
				decisions ++;
			} else if( !Double.isNaN(a) ) {
				assertTrue(Math.abs(1200.0 * Math.log(b / a) / Math.log(2.0)) <= 1.0, label + " frame " + i + ": " + a + " / " + b);
			}
		}
		assertTrue(decisions <= Math.max(1, java.size() / 1000), label + ": " + decisions + " of " + java.size() + " pitched decisions differ");
	}

	/** The Java engine's pitch per frame (NaN: unpitched), from the estimator the catalog builds for these settings. */
	private static List<Double> javaPitch(float[] mono, RecognitionSettings settings) {
		PitchEstimator estimator = PitchEngineCatalog.resolve(PitchEngineCatalog.FASTYIN).create(RATE, settings);
		List<Double> pitch = new ArrayList<Double>();
		float[] window = new float[WINDOW];
		int filled = 0;
		for( int offset = 0 ; offset + HOP <= mono.length ; offset += HOP ) {
			System.arraycopy(window, HOP, window, 0, WINDOW - HOP);
			System.arraycopy(mono, offset, window, WINDOW - HOP, HOP);
			filled = Math.min(WINDOW, filled + HOP);
			if( filled < WINDOW ) {
				continue;
			}
			PitchEstimate estimate = estimator.estimate(window);
			pitch.add(estimate.isPitched() ? estimate.getF0Hz() : Double.NaN);
		}
		return pitch;
	}

	private static List<TargetEvidence> evidenceJava(float[] mono, RecognitionSettings settings, int[] expected) {
		ExpectedPitchVerifier verifier = ExpectedPitchVerifier.of(RATE, settings);
		List<TargetEvidence> evidence = new ArrayList<TargetEvidence>();
		float[] window = new float[WINDOW];
		int filled = 0;
		for( int offset = 0 ; offset + HOP <= mono.length ; offset += HOP ) {
			System.arraycopy(window, HOP, window, 0, WINDOW - HOP);
			System.arraycopy(mono, offset, window, WINDOW - HOP, HOP);
			filled = Math.min(WINDOW, filled + HOP);
			if( filled >= WINDOW ) {
				evidence.add(verifier.verify(window, expected));
			}
		}
		return evidence;
	}

	private static List<TargetEvidence> evidenceNative(float[] mono, RecognitionSettings settings, int[] expected) {
		List<TargetEvidence> evidence = new ArrayList<TargetEvidence>();
		for( AnalysisFrame frame : nativeFrames(mono, settings, expected) ) {
			assertNotNull(frame.getTargetEvidence());
			evidence.add(frame.getTargetEvidence());
		}
		return evidence;
	}

	private static List<AnalysisFrame> nativeFrames(float[] mono, RecognitionSettings settings, int[] expected) {
		float[] records = NativeRecognitionLibrary.analyzeTuned(mono, RATE, NativeParameters.of(settings), NativeParameters.tuning(settings), false, null,
				expected, null, null);
		List<AnalysisFrame> frames = new ArrayList<AnalysisFrame>();
		int offset = 0;
		while( offset < records.length ) {
			frames.add(FrameRecords.read(records, offset));
			offset += FrameRecords.length(records, offset);
		}
		return frames;
	}

	/** A plucked A2 under strong noise: many frames sit near the YIN decision. */
	private static float[] noisyTone() {
		float[] tone = SyntheticSignals.karplusStrong(110.0, RATE, RATE * 4, 5L);
		float[] noise = SyntheticSignals.noise(tone.length, 0.05, 9L);
		for( int i = 0 ; i < tone.length ; i ++ ) {
			tone[i] = 0.3f * tone[i] + noise[i];
		}
		return tone;
	}
}
