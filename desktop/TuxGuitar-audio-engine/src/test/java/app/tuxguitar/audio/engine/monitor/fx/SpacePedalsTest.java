package app.tuxguitar.audio.engine.monitor.fx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.monitor.EffectKind;
import app.tuxguitar.audio.engine.monitor.Pedal;
import app.tuxguitar.audio.engine.monitor.PedalSettings;

/** The chorus, the echo and the reverb (spec 022 FR-005, US2/AC2, research R4; task T031). */
public class SpacePedalsTest {

	private static Pedal pedal(EffectKind kind, double... values) {
		return DrivePedalsTest.pedal(kind, values);
	}

	private static float[] impulse(int frames) {
		float[] samples = new float[frames];
		samples[0] = 1f;
		return samples;
	}

	private static double windowRms(float[] samples, int centre, int half) {
		double sum = 0;
		for (int i = Math.max(0, centre - half); i < Math.min(samples.length, centre + half); i++) {
			sum += (double) samples[i] * samples[i];
		}
		return Math.sqrt(sum / (2 * half));
	}

	private static int peak(float[] samples, int from, int to) {
		int best = from;
		for (int i = from; i < to; i++) {
			if (Math.abs(samples[i]) > Math.abs(samples[best])) {
				best = i;
			}
		}
		return best;
	}

	@Test
	public void theEchoRepeatsAtItsTimeEachRepeatLowerAndDarkerWithALowerTone() {
		int delay = 100 * Signals.RATE / 1000;
		float[] wet = impulse(5 * delay);
		Signals.run(pedal(EffectKind.ECHO, 100, 5, 10, 100), wet);
		Assertions.assertEquals(delay, peak(wet, delay / 2, delay + delay / 2), 1, "the first repeat at the time");
		Assertions.assertEquals(2 * delay, peak(wet, delay + delay / 2, 2 * delay + delay / 2), 1, "the second repeat");
		double first = windowRms(wet, delay, 40);
		double second = windowRms(wet, 2 * delay, 40);
		double third = windowRms(wet, 3 * delay, 40);
		Assertions.assertTrue(second < first && third < second, first + " > " + second + " > " + third);
		double few = repeatRatio(2);
		double many = repeatRatio(8);
		Assertions.assertTrue(many > few + 0.2, "more repeats, slower decay: " + few + " then " + many);
		Assertions.assertTrue(roughness(0) < 0.7 * roughness(10), "a lower tone gives darker repeats");
	}

	private static double repeatRatio(double repeats) {
		int delay = 100 * Signals.RATE / 1000;
		float[] wet = impulse(4 * delay);
		Signals.run(pedal(EffectKind.ECHO, 100, repeats, 10, 100), wet);
		return windowRms(wet, 2 * delay, 40) / windowRms(wet, delay, 40);
	}

	/** The share of high frequencies in the third repeat of a burst of noise: the energy of the differences over the energy. */
	private static double roughness(double tone) {
		int delay = 100 * Signals.RATE / 1000;
		float[] wet = new float[4 * delay];
		System.arraycopy(Signals.noise(-12, 200, 3), 0, wet, 0, 200);
		Signals.run(pedal(EffectKind.ECHO, 100, 8, tone, 100), wet);
		double energy = 0;
		double differences = 0;
		for (int i = 3 * delay; i < 3 * delay + 400; i++) {
			energy += wet[i] * wet[i];
			differences += (wet[i] - wet[i - 1]) * (wet[i] - wet[i - 1]);
		}
		return differences / energy;
	}

	@Test
	public void theEchoTailGoesOnAfterTheInputStopsAndOneAndAHalfSecondsIsAllowed() {
		float[] samples = new float[Signals.RATE];
		System.arraycopy(Signals.noise(-20, Signals.RATE / 5, 5), 0, samples, 0, Signals.RATE / 5);
		Signals.run(pedal(EffectKind.ECHO, 300, 5, 5, 50), samples);
		Assertions.assertTrue(Signals.rmsDb(samples, Signals.RATE / 2, 4 * Signals.RATE / 5) > -60, "the tail after the input stopped");
		int longest = 1500 * Signals.RATE / 1000;
		float[] wet = impulse(longest + Signals.RATE / 10);
		Signals.run(pedal(EffectKind.ECHO, 1500, 3, 10, 100), wet);
		Assertions.assertEquals(longest, peak(wet, longest - 100, longest + 100), 1, "a repeat 1.5 s later");
	}

	@Test
	public void theChorusDelayStaysWithinSevenAndTwentyMilliseconds() {
		Chorus chorus = (Chorus) pedal(EffectKind.CHORUS, 5, 10, 50);
		float[] block = new float[128];
		double min = Double.MAX_VALUE;
		double max = 0;
		for (int b = 0; b < 10 * Signals.RATE / 128; b++) {
			chorus.process(block, block, 128);
			min = Math.min(min, chorus.delayMillis());
			max = Math.max(max, chorus.delayMillis());
		}
		Assertions.assertTrue(min >= 7 && max <= 20, min + " to " + max + " ms");
		Assertions.assertTrue(max - min > 5, "deep: " + min + " to " + max + " ms");
	}

	private static double decaySeconds(double size) {
		float[] wet = impulse(4 * Signals.RATE);
		Signals.run(pedal(EffectKind.REVERB, size, 5, 100), wet);
		double start = Signals.rmsDb(wet, 0, Signals.RATE / 20);
		int window = Signals.RATE / 50;
		for (int from = Signals.RATE / 20; from + window < wet.length; from += window) {
			if (Signals.rmsDb(wet, from, from + window) < start - 40) {
				return from / (double) Signals.RATE;
			}
		}
		return wet.length / (double) Signals.RATE;
	}

	@Test
	public void theReverbTailGrowsWithSize() {
		double small = decaySeconds(2);
		double large = decaySeconds(8);
		Assertions.assertTrue(large > 1.5 * small, small + " s then " + large + " s");
	}

	@Test
	public void mixZeroIsDry() {
		for (Pedal pedal : new Pedal[] { pedal(EffectKind.CHORUS, 1, 5, 0), pedal(EffectKind.ECHO, 300, 5, 5, 0), pedal(EffectKind.REVERB, 5, 5, 0) }) {
			float[] dry = Signals.sine(440, 0.3, Signals.RATE / 2);
			float[] wet = dry.clone();
			Signals.run(pedal, wet);
			for (int i = 0; i < dry.length; i++) {
				Assertions.assertEquals(dry[i], wet[i], 1e-6, pedal.getClass().getSimpleName() + " at " + i);
			}
		}
	}

	@Test
	public void everyPedalIsFiniteAfterAMinuteOfFullScaleNoise() {
		java.util.Random random = new java.util.Random(11);
		for (EffectKind kind : EffectKind.values()) {
			double[] values = new double[kind.knobs().size()];
			for (int i = 0; i < values.length; i++) {
				values[i] = kind.knobs().get(i).max();
			}
			Pedal pedal = pedal(kind, values);
			float[] block = new float[128];
			boolean finite = true;
			for (int b = 0; b < 60 * Signals.RATE / 128 && finite; b++) {
				for (int i = 0; i < 128; i++) {
					block[i] = (float) (2 * random.nextDouble() - 1);
				}
				pedal.process(block, block, 128);
				finite = Signals.finite(block);
			}
			Assertions.assertTrue(finite, kind + " stays finite");
		}
	}
}
