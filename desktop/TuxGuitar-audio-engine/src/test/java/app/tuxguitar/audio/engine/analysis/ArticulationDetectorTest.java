package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Synthetic cases of research spike S4. The detector may miss weak or fast re-plucks (FR-022
 * prefers misses), but must never report sustain, vibrato, beating, clicks or dropouts.
 */
public class ArticulationDetectorTest {

	private static final int RATE = 48000;
	private static final int WINDOW = 2048;
	private static final int HOP = 256;
	private static final double E3 = 164.81;
	private static final double TOLERANCE_SECONDS = 0.030;

	@Test
	public void reportsTheFirstPluckOnceDuringSustain() {
		float[] signal = withNoiseFloor(plucks(2.5, new double[] { 0.5 }, new double[] { 1.0 }));
		assertOnsets(detect(signal), new double[] { 0.5 }, new double[] { 0.5 });
	}

	@Test
	public void reportsAnEqualSamePitchRePluck() {
		float[] signal = withNoiseFloor(plucks(2.5, new double[] { 0.5, 1.3 }, new double[] { 1.0, 1.0 }));
		assertOnsets(detect(signal), new double[] { 0.5, 1.3 }, new double[] { 0.5, 1.3 });
	}

	@Test
	public void weakAndFastRePlucksMayBeMissedButNeverInvented() {
		float[] weak = withNoiseFloor(plucks(2.5, new double[] { 0.5, 1.3 }, new double[] { 1.0, 0.35 }));
		assertOnsets(detect(weak), new double[] { 0.5 }, new double[] { 0.5, 1.3 });

		double[] fast = { 0.5, 0.65, 0.8, 0.95, 1.1 };
		float[] repeats = withNoiseFloor(plucks(2.0, fast, new double[] { 1, 1, 1, 1, 1 }));
		assertOnsets(detect(repeats), new double[] { 0.5 }, fast);
	}

	@Test
	public void ignoresVibratoAmplitudeModulationAndBeating() {
		assertOnsets(detect(withNoiseFloor(modulatedTone(30, 5.5, 1.5))), new double[0], new double[] { 0.5 });
		assertOnsets(detect(withNoiseFloor(modulatedTone(50, 6.0, 3.0))), new double[0], new double[] { 0.5 });
		assertOnsets(detect(withNoiseFloor(beatingTone())), new double[0], new double[] { 0.5 });
	}

	@Test
	public void ignoresClicksAndDropoutsDuringSustain() {
		float[] click = plucks(2.5, new double[] { 0.5 }, new double[] { 1.0 });
		addNoiseBurst(click, 1.3, 0.015, -18.0);
		assertOnsets(detect(withNoiseFloor(click)), new double[] { 0.5 }, new double[] { 0.5 });

		float[] softClick = plucks(2.5, new double[] { 0.5 }, new double[] { 1.0 });
		addNoiseBurst(softClick, 1.3, 0.010, -30.0);
		assertOnsets(detect(withNoiseFloor(softClick)), new double[] { 0.5 }, new double[] { 0.5 });

		float[] dropout = plucks(2.5, new double[] { 0.5 }, new double[] { 1.0 });
		int from = (int) (1.3 * RATE);
		for( int i = from ; i < from + (int) (0.025 * RATE) ; i ++ ) {
			dropout[i] *= (float) Math.pow(10, -12.0 / 20.0);
		}
		assertOnsets(detect(withNoiseFloor(dropout)), new double[] { 0.5 }, new double[] { 0.5 });
	}

	/**
	 * Synthetic gate (1.5 dB): equal re-plucks on a ringing string only add 2-4 dB. On the real
	 * single-pluck clips the fast detector reports false attacks at every gate up to 4.5 dB
	 * (research.md R3, builtin-mic-articulation-fast-sweep.csv), so it is opt-in, not the default.
	 */
	private static ArticulationDetector fast() {
		return ArticulationDetector.fast(ArticulationDetector.DEFAULT_MIN_LEVEL_DB, 1.5);
	}

	/** Equal re-plucks every {@code spacing} seconds from 0.5 s; how many are found. */
	private static int foundRepeats(ArticulationDetector detector, double spacing, int count) {
		double[] times = new double[count];
		double[] gains = new double[count];
		for( int i = 0 ; i < count ; i ++ ) {
			times[i] = 0.5 + i * spacing;
			gains[i] = 1.0;
		}
		List<Double> onsets = detect(detector, withNoiseFloor(plucks(0.5 + count * spacing + 0.6, times, gains)));
		int found = 0;
		for( double time : times ) {
			found += (near(onsets, time) ? 1 : 0);
		}
		return found;
	}

	@Test
	public void theFastDetectorSeparatesIdenticalRepeatsAtEightNotesPerSecond() {
		assertTrue(foundRepeats(fast(), 0.125, 6) >= 5, "fast detector at 8 notes/s");
		// the MVP detector stays conservative (documented limit)
		assertTrue(foundRepeats(new ArticulationDetector(), 0.125, 6) < 6);
	}

	@Test
	public void theFastDetectorDecidesEarlierAndKeepsTheMvpRejections() {
		assertTrue(fast().getDecisionDelayHops() < new ArticulationDetector().getDecisionDelayHops());
		ArticulationDetector detector = fast();
		assertOnsets(detect(detector, withNoiseFloor(modulatedTone(30, 5.5, 1.5))), new double[0], new double[] { 0.5 });
		assertOnsets(detect(fast(), withNoiseFloor(modulatedTone(50, 6.0, 3.0))), new double[0], new double[] { 0.5 });
		assertOnsets(detect(fast(), withNoiseFloor(beatingTone())), new double[0], new double[] { 0.5 });
		float[] click = plucks(2.5, new double[] { 0.5 }, new double[] { 1.0 });
		addNoiseBurst(click, 1.3, 0.015, -18.0);
		assertOnsets(detect(fast(), withNoiseFloor(click)), new double[] { 0.5 }, new double[] { 0.5 });
		float[] dropout = plucks(2.5, new double[] { 0.5 }, new double[] { 1.0 });
		int from = (int) (1.3 * RATE);
		for( int i = from ; i < from + (int) (0.025 * RATE) ; i ++ ) {
			dropout[i] *= (float) Math.pow(10, -12.0 / 20.0);
		}
		assertOnsets(detect(fast(), withNoiseFloor(dropout)), new double[] { 0.5 }, new double[] { 0.5 });
	}

	@Test
	public void resetForgetsHistory() {
		ArticulationDetector detector = new ArticulationDetector();
		float[] signal = withNoiseFloor(plucks(1.0, new double[] { 0.5 }, new double[] { 1.0 }));
		List<Double> first = detect(detector, signal);
		detector.reset();
		assertEquals(first, detect(detector, signal));
	}

	private static List<Double> detect(float[] signal) {
		return detect(new ArticulationDetector(), signal);
	}

	/** Transient times (centre of the transient hop's 1024-sample frame) in seconds. */
	private static List<Double> detect(ArticulationDetector detector, float[] signal) {
		List<Double> onsets = new ArrayList<Double>();
		float[] window = new float[WINDOW];
		for( int hop = 0 ; (hop + 1) * HOP <= signal.length ; hop ++ ) {
			System.arraycopy(window, HOP, window, 0, WINDOW - HOP);
			System.arraycopy(signal, hop * HOP, window, WINDOW - HOP, HOP);
			if( detector.process(window) ) {
				int transientHop = hop - detector.getDecisionDelayHops();
				onsets.add(((transientHop + 1) * HOP - 512) / (double) RATE);
			}
		}
		return onsets;
	}

	/** Every expected onset is found; every reported onset is near an allowed one. */
	private static void assertOnsets(List<Double> reported, double[] required, double[] allowed) {
		for( double expected : required ) {
			assertTrue(near(reported, expected), "missing onset at " + expected + " s, reported " + reported);
		}
		for( double onset : reported ) {
			boolean ok = false;
			for( double candidate : allowed ) {
				ok |= Math.abs(onset - candidate) <= TOLERANCE_SECONDS;
			}
			assertTrue(ok, "false articulation at " + onset + " s, reported " + reported);
		}
	}

	private static boolean near(List<Double> onsets, double time) {
		for( double onset : onsets ) {
			if( Math.abs(onset - time) <= TOLERANCE_SECONDS ) {
				return true;
			}
		}
		return false;
	}

	private static float[] plucks(double seconds, double[] times, double[] gains) {
		float[] signal = new float[(int) (seconds * RATE)];
		for( int p = 0 ; p < times.length ; p ++ ) {
			int onset = (int) (times[p] * RATE);
			float[] pluck = SyntheticSignals.karplusStrong(E3, RATE, signal.length - onset, 11 + p);
			for( int i = 0 ; i < pluck.length ; i ++ ) {
				signal[onset + i] += (float) (0.5 * gains[p] * pluck[i]);
			}
		}
		return signal;
	}

	/** Harmonic tone from 0.5 s with pitch vibrato (± cents) and amplitude modulation (± dB). */
	private static float[] modulatedTone(double cents, double rateHz, double amDb) {
		float[] signal = new float[(int) (2.5 * RATE)];
		double phase = 0;
		for( int i = (int) (0.5 * RATE) ; i < signal.length ; i ++ ) {
			double t = i / (double) RATE;
			double frequency = E3 * Math.pow(2, cents * Math.sin(2 * Math.PI * rateHz * t) / 1200.0);
			phase += 2 * Math.PI * frequency / RATE;
			double gain = Math.pow(10, amDb * Math.sin(2 * Math.PI * rateHz * t) / 20.0);
			signal[i] = (float) (0.2 * gain * (Math.sin(phase) + 0.5 * Math.sin(2 * phase) + 0.25 * Math.sin(3 * phase)));
		}
		return signal;
	}

	/** Two close partials beating at 2 Hz (about ±4 dB) from 0.5 s. */
	private static float[] beatingTone() {
		float[] signal = new float[(int) (2.5 * RATE)];
		for( int i = (int) (0.5 * RATE) ; i < signal.length ; i ++ ) {
			double t = i / (double) RATE;
			signal[i] = (float) (0.2 * Math.sin(2 * Math.PI * E3 * t) + 0.09 * Math.sin(2 * Math.PI * (E3 + 2.0) * t));
		}
		return signal;
	}

	private static void addNoiseBurst(float[] signal, double at, double seconds, double relativeDb) {
		Random random = new Random(7);
		double amplitude = 0.5 * Math.pow(10, relativeDb / 20.0);
		int from = (int) (at * RATE);
		for( int i = from ; i < from + (int) (seconds * RATE) ; i ++ ) {
			signal[i] += (float) (amplitude * (random.nextDouble() * 2 - 1));
		}
	}

	private static float[] withNoiseFloor(float[] signal) {
		float[] noise = SyntheticSignals.noise(signal.length, Math.pow(10, -40.0 / 20.0), 3L);
		for( int i = 0 ; i < signal.length ; i ++ ) {
			signal[i] += noise[i];
		}
		return signal;
	}
}
