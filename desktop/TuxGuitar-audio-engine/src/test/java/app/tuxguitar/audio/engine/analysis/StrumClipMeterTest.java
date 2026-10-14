package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * The strum meter (spec 010 T006, contract invariants 5, 10, 15, 16 and 18): the verdict on the first strum after a check begins, on synthetic signals.
 * It arms on a hop that reaches -40 dBFS and rises 12 dB above the quietest of the 60 hops before it, counts the samples at the clip level and the mean level
 * over 300 ms (counted in samples), and says clipping at 0.5 % of them.
 */
public class StrumClipMeterTest {

	private static final int HOP = 256;

	private static StrumClipMeter meter(int sampleRate) {
		StrumClipMeter meter = new StrumClipMeter(StrumClipMeter.Parameters.defaults());
		meter.configure(sampleRate);
		return meter;
	}

	private static void feed(StrumClipMeter meter, float[] signal) {
		for (int offset = 0; offset + HOP <= signal.length; offset += HOP) {
			meter.hop(signal, offset, HOP);
		}
	}

	/** {@code silenceHops} hops of digital silence, then a sine of the given amplitude for {@code seconds}; the onset is on a hop boundary. */
	private static float[] sine(int sampleRate, int silenceHops, double amplitude, double seconds) {
		int silence = silenceHops * HOP;
		int length = silence + (int) Math.round(seconds * sampleRate);
		length -= length % HOP;
		float[] signal = new float[length];
		for (int i = silence; i < length; i++) {
			signal[i] = (float) (amplitude * Math.sin(2.0 * Math.PI * 220.0 * i / sampleRate));
		}
		return signal;
	}

	/** Puts {@code count} samples at full scale in the window that starts at the onset, spread along it. */
	private static void spikes(float[] signal, int onset, int count) {
		for (int i = 0; i < count; i++) {
			signal[onset + 100 + i * 190] = 1.0f;
		}
	}

	private static double dbfs(double rms) {
		return 20.0 * Math.log10(rms);
	}

	@Test
	public void silenceAndAConstantLowNoiseAreNoStrum() {
		StrumClipMeter meter = meter(48000);
		feed(meter, new float[48000 * 2]);
		assertEquals(StrumVerdict.NO_STRUM, meter.verdict(), "digital silence");
		Random random = new Random(1);
		float[] noise = new float[48000 * 2];
		for (int i = 0; i < noise.length; i++) {
			noise[i] = (float) (random.nextGaussian() * Math.pow(10.0, -60.0 / 20.0));
		}
		meter = meter(48000);
		feed(meter, noise);
		assertEquals(StrumVerdict.NO_STRUM, meter.verdict(), "a constant noise at -60 dBFS never rises 12 dB over itself");
	}

	@Test
	public void aClickAtMinusFiftyDecibelsThatRisesTwentyDecibelsOverSilenceButStaysUnderTheFloorNeverArms() {
		float[] signal = new float[48000 * 2];
		double amplitude = Math.pow(10.0, -50.0 / 20.0) * Math.sqrt(2.0);
		for (int i = 20 * HOP; i < 21 * HOP; i++) {
			signal[i] = (float) (amplitude * Math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0));
		}
		for (int i = 90 * HOP; i < 92 * HOP; i++) {
			signal[i] = (float) (amplitude * Math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0));
		}
		StrumClipMeter meter = meter(48000);
		feed(meter, signal);
		assertEquals(StrumVerdict.NO_STRUM, meter.verdict(), "the floor of -40 dBFS keeps a click or noise from arming the meter (invariant 15)");
		// and the strum that follows still counts
		float[] strum = sine(48000, 10, 0.3, 1.0);
		feed(meter, strum);
		assertEquals(StrumVerdict.GOOD, meter.verdict());
	}

	@Test
	public void aStrumWithNoSampleAtTheClipLevelIsGoodAndIsNeverClipping() {
		StrumClipMeter meter = meter(48000);
		feed(meter, sine(48000, 40, 0.5, 1.0));
		assertEquals(StrumVerdict.GOOD, meter.verdict());
		assertEquals(0.0, meter.clippedShare(), 0.0);
		assertTrue(meter.rmsDbfs() > -9.5 && meter.rmsDbfs() < -8.5, "a sine of amplitude 0.5 is -9.03 dBFS RMS: " + meter.rmsDbfs());
	}

	@Test
	public void theShareOfHalfAPercentIsTheLimitAtEveryRateAndTheWindowIsCountedInSamples() {
		// 300 ms is 14,400 samples at 48 kHz (0.5 % is 72) and 13,230 at 44.1 kHz (0.5 % is 66.15, so 67): not a whole number of hops of 256
		int[][] cases = { { 48000, 71, 72 }, { 44100, 66, 67 } };
		for (int[] c : cases) {
			int rate = c[0];
			for (int count : new int[] { c[1], c[2] }) {
				float[] signal = sine(rate, 40, 0.3, 1.0);
				spikes(signal, 40 * HOP, count);
				StrumClipMeter meter = meter(rate);
				feed(meter, signal);
				StrumVerdict expected = count == c[2] ? StrumVerdict.CLIPPING : StrumVerdict.GOOD;
				assertEquals(expected, meter.verdict(), rate + " Hz, " + count + " samples at full scale in 300 ms");
				assertEquals(count / (double) Math.round(0.3 * rate), meter.clippedShare(), 1e-12, "the share counts the window in samples: round(0.3 x fs)");
			}
		}
	}

	@Test
	public void theSameStrumGivesTheSameVerdictAt44100AndAt48000() {
		for (double amplitude : new double[] { 0.05, 0.3, 0.8 }) {
			StrumVerdict at48 = null;
			for (int rate : new int[] { 44100, 48000 }) {
				StrumClipMeter meter = meter(rate);
				feed(meter, sine(rate, 40, amplitude, 1.0));
				if (at48 == null) {
					at48 = meter.verdict();
				}
				assertEquals(at48, meter.verdict(), "amplitude " + amplitude + " at " + rate + " Hz");
			}
		}
	}

	@Test
	public void aMeanLevelUnderMinusFortyDecibelsAfterAnAttackThatReachedTheFloorIsTooWeak() {
		int rate = 48000;
		float[] signal = new float[rate * 2];
		double loud = Math.pow(10.0, -36.0 / 20.0) * Math.sqrt(2.0);
		for (int i = 40 * HOP; i < 41 * HOP; i++) { // one hop at -36 dBFS reaches the floor and arms the meter
			signal[i] = (float) (loud * Math.sin(2.0 * Math.PI * 220.0 * i / rate));
		}
		double quiet = Math.pow(10.0, -70.0 / 20.0) * Math.sqrt(2.0);
		for (int i = 41 * HOP; i < signal.length; i++) {
			signal[i] = (float) (quiet * Math.sin(2.0 * Math.PI * 220.0 * i / rate));
		}
		StrumClipMeter meter = meter(rate);
		feed(meter, signal);
		assertEquals(StrumVerdict.TOO_WEAK, meter.verdict());
		assertTrue(meter.rmsDbfs() < -40.0, "mean " + meter.rmsDbfs());
	}

	@Test
	public void aLoudStrumThatDoesNotClipIsTooLoudAndAClippingOneIsClippingBeforeAnythingElse() {
		int rate = 48000;
		double rms5 = Math.pow(10.0, -5.0 / 20.0);
		StrumClipMeter meter = meter(rate);
		feed(meter, sine(rate, 40, rms5 * Math.sqrt(2.0), 1.0));
		assertEquals(StrumVerdict.TOO_LOUD, meter.verdict(), "a sine of -5 dBFS RMS peaks at 0.8: loud, no clipping");
		assertEquals(0.0, meter.clippedShare(), 0.0);
		float[] loudAndClipping = sine(rate, 40, rms5 * Math.sqrt(2.0), 1.0);
		spikes(loudAndClipping, 40 * HOP, 100);
		meter = meter(rate);
		feed(meter, loudAndClipping);
		assertEquals(StrumVerdict.CLIPPING, meter.verdict(), "clipping, then too loud, then too weak (a clipping window is never weaker than -23 dBFS: 0.5 % of full scale)");
		assertTrue(dbfs(Math.sqrt(0.005)) > -23.1);
	}

	@Test
	public void theVerdictIsNoStrumUntilThreeHundredMillisecondsAfterTheAttackWereCounted() {
		int rate = 48000;
		float[] signal = sine(rate, 40, 0.3, 1.0);
		StrumClipMeter meter = meter(rate);
		int onsetHop = 40;
		int window = (int) Math.round(0.3 * rate);
		int hopsBefore = onsetHop + (window - 1) / HOP; // the hops that hold the window except the last sample of its last hop
		for (int hop = 0; hop < hopsBefore; hop++) {
			meter.hop(signal, hop * HOP, HOP);
		}
		assertEquals(StrumVerdict.NO_STRUM, meter.verdict(), "299 ms after the attack");
		for (int hop = hopsBefore; hop < hopsBefore + 3; hop++) {
			meter.hop(signal, hop * HOP, HOP);
		}
		assertEquals(StrumVerdict.GOOD, meter.verdict(), "the window of round(0.3 x fs) samples is complete");
	}

	@Test
	public void aSecondAttackAfterTheVerdictChangesNothingUntilReset() {
		int rate = 48000;
		float[] clipping = sine(rate, 40, 0.3, 1.0);
		spikes(clipping, 40 * HOP, 100);
		StrumClipMeter meter = meter(rate);
		feed(meter, clipping);
		assertEquals(StrumVerdict.CLIPPING, meter.verdict());
		feed(meter, new float[rate]);
		feed(meter, sine(rate, 0, 0.3, 1.0));
		assertEquals(StrumVerdict.CLIPPING, meter.verdict(), "the verdict of a window never changes after it is given");
		meter.reset();
		assertEquals(StrumVerdict.NO_STRUM, meter.verdict(), "a reset hides the old verdict at once, before the analysis thread has seen the flag");
		feed(meter, new float[HOP * 40]);
		feed(meter, sine(rate, 0, 0.3, 1.0));
		assertEquals(StrumVerdict.GOOD, meter.verdict(), "and the next strum counts");
	}

	@Test
	public void configureDuringACheckResetsItAndAHopBeforeAnyConfigureDoesNothing() {
		StrumClipMeter meter = new StrumClipMeter(StrumClipMeter.Parameters.defaults());
		feed(meter, sine(48000, 40, 0.3, 1.0)); // no configure yet: no rate, no window
		assertEquals(StrumVerdict.NO_STRUM, meter.verdict());
		meter.configure(48000);
		float[] signal = sine(48000, 40, 0.3, 1.0);
		for (int hop = 0; hop < 60; hop++) {
			meter.hop(signal, hop * HOP, HOP);
		}
		meter.configure(44100); // the engine reconfigured in the middle of the window
		assertEquals(StrumVerdict.NO_STRUM, meter.verdict());
		feed(meter, sine(44100, 40, 0.3, 1.0));
		assertEquals(StrumVerdict.GOOD, meter.verdict(), "the strum after the reconfiguration counts at the new rate");
	}

	@Test
	public void aStrumThatBeginsOnTheFirstHopCannotArmButTheNextOneDoesAndFewerThanSixtyHopsBeforeAreEnough() {
		int rate = 48000;
		float[] loudFromTheStart = sine(rate, 0, 0.3, 1.0);
		StrumClipMeter meter = meter(rate);
		feed(meter, loudFromTheStart);
		assertEquals(StrumVerdict.NO_STRUM, meter.verdict(), "the first hop has no hop before it, the others never rise over a constant level");
		meter = meter(rate);
		Random random = new Random(3);
		float[] signal = sine(rate, 10, 0.3, 1.0); // ten quiet hops only
		for (int i = 0; i < 10 * HOP; i++) {
			signal[i] = (float) (random.nextGaussian() * Math.pow(10.0, -70.0 / 20.0));
		}
		feed(meter, signal);
		assertEquals(StrumVerdict.GOOD, meter.verdict(), "with fewer than 60 hops before it, the reference is the quietest of the hops that exist");
	}

	@Test
	public void aThreadThatFeedsAndAnotherThatReadsAndResetsNeverSeeATornValueNorThrow() throws Exception {
		final StrumClipMeter meter = meter(48000);
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		final float[] clipping = sine(48000, 40, 0.3, 1.0);
		spikes(clipping, 40 * HOP, 100);
		final float[] good = sine(48000, 40, 0.3, 1.0);
		final boolean[] stop = { false };
		Thread feeder = new Thread(() -> {
			try {
				for (int round = 0; round < 60 && failure.get() == null; round++) {
					feed(meter, round % 2 == 0 ? clipping : good);
					feed(meter, new float[HOP * 100]);
				}
			} catch (Throwable error) {
				failure.set(error);
			}
		});
		Thread reader = new Thread(() -> {
			try {
				while (!stop[0]) {
					StrumClipMeter.Reading reading = meter.snapshot();
					assertNotNull(reading);
					if (reading.verdict() == StrumVerdict.CLIPPING) {
						assertTrue(reading.clippedShare() >= 0.005 - 1e-12, "a clipping verdict with a share of " + reading.clippedShare());
					}
					if (reading.verdict() == StrumVerdict.GOOD) {
						assertTrue(reading.clippedShare() < 0.005 && reading.rmsDbfs() > -40.0, "a good verdict with " + reading.clippedShare() + " / " + reading.rmsDbfs());
					}
					if (Math.random() < 0.05) {
						meter.reset();
					}
				}
			} catch (Throwable error) {
				failure.set(error);
			}
		});
		feeder.start();
		reader.start();
		feeder.join();
		stop[0] = true;
		reader.join();
		assertEquals(null, failure.get(), String.valueOf(failure.get()));
	}
}
