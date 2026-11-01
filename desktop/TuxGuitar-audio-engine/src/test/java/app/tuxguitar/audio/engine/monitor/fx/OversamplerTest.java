package app.tuxguitar.audio.engine.monitor.fx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The oversampler around the clipping stages (spec 022 research R4; task T015). */
public class OversamplerTest {

	/** Up, an optional soft clip at the high rate (the curve the clipping stages use), down. */
	private static float[] through(int factor, float[] input, boolean clip) {
		Oversampler oversampler = new Oversampler(factor, 128);
		float[] high = new float[128 * factor];
		float[] block = new float[128];
		float[] output = new float[input.length];
		for( int offset = 0 ; offset < input.length ; offset += 128 ) {
			System.arraycopy(input, offset, block, 0, 128);
			int count = oversampler.up(block, 128, high);
			if( clip ) {
				for( int i = 0 ; i < count ; i++ ) {
					high[i] = (float) Math.tanh(3 * high[i]);
				}
			}
			oversampler.down(high, count, block);
			System.arraycopy(block, 0, output, offset, 128);
		}
		return output;
	}

	@Test
	public void theRoundTripKeepsASine() {
		for( int factor : new int[] { 2, 4 } ) {
			float[] output = through(factor, Signals.sine(1000, 0.5, Signals.RATE), false);
			double gain = 20 * Math.log10(Signals.amplitude(output, Signals.RATE / 2, Signals.RATE, 1000) / 0.5);
			Assertions.assertEquals(0.0, gain, 0.1, factor + "x round trip");
		}
		Oversampler none = new Oversampler(1, 128);
		Assertions.assertEquals(1, none.factor());
	}

	/** The energy of everything below 20 kHz that is not a harmonic of 5 kHz. */
	private static double aliasDb(float[] output) {
		int from = Signals.RATE / 2;
		int to = Signals.RATE;
		double sum = 0;
		for( int bin = 100 ; bin <= 20000 ; bin += 100 ) {
			if( bin % 5000 == 0 ) {
				continue;
			}
			double a = Signals.amplitude(output, from, to, bin);
			sum += a * a;
		}
		return 10 * Math.log10(sum);
	}

	@Test
	public void fourTimesOversamplingCutsTheAliasingOfAClippedSine() {
		float[] input = Signals.sine(5000, 0.9, Signals.RATE);
		double plain = aliasDb(through(1, input, true));
		double oversampled = aliasDb(through(4, input, true));
		Assertions.assertTrue(plain - oversampled >= 40, "aliasing " + plain + " dB plain, " + oversampled + " dB with 4x");
	}
}
