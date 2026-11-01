package app.tuxguitar.audio.engine.monitor.fx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The biquad filters (spec 022 research R4, the cookbook forms; task T015). */
public class BiquadTest {

	private static Biquad filter() {
		Biquad biquad = new Biquad();
		biquad.prepare(Signals.RATE, 128);
		return biquad;
	}

	@Test
	public void theResponsesMatchTheirDesign() {
		Biquad low = filter();
		low.setLowPass(1000, Math.sqrt(0.5));
		Assertions.assertEquals(-3.01, Signals.gainDb(low, 1000, 0.5), 0.5, "low-pass at its cutoff");
		low.reset();
		Assertions.assertEquals(0.0, Signals.gainDb(low, 100, 0.5), 0.5, "low-pass far below");
		low.reset();
		Assertions.assertTrue(Signals.gainDb(low, 8000, 0.5) < -24, "low-pass two octaves above falls 12 dB per octave");

		Biquad high = filter();
		high.setHighPass(200, Math.sqrt(0.5));
		Assertions.assertEquals(-3.01, Signals.gainDb(high, 200, 0.5), 0.5, "high-pass at its cutoff");
		high.reset();
		Assertions.assertEquals(0.0, Signals.gainDb(high, 4000, 0.5), 0.5, "high-pass far above");

		Biquad peak = filter();
		peak.setPeak(700, 1.0, 9.0);
		Assertions.assertEquals(9.0, Signals.gainDb(peak, 700, 0.1), 0.5, "peak at its centre");
		peak.reset();
		Assertions.assertEquals(0.0, Signals.gainDb(peak, 12000, 0.1), 0.5, "peak far away");

		Biquad lowShelf = filter();
		lowShelf.setLowShelf(200, -8.0);
		Assertions.assertEquals(-8.0, Signals.gainDb(lowShelf, 25, 0.5), 0.5, "low shelf below");
		lowShelf.reset();
		Assertions.assertEquals(0.0, Signals.gainDb(lowShelf, 8000, 0.5), 0.5, "low shelf above");

		Biquad highShelf = filter();
		highShelf.setHighShelf(3000, 6.0);
		Assertions.assertEquals(6.0, Signals.gainDb(highShelf, 16000, 0.1), 0.5, "high shelf above");
		highShelf.reset();
		Assertions.assertEquals(0.0, Signals.gainDb(highShelf, 100, 0.1), 0.5, "high shelf below");
	}

	@Test
	public void itStaysStableForExtremeSettings() {
		for( double frequency : new double[] { 10, 20, 23000, 23990 } ) {
			for( double gain : new double[] { -24, 24 } ) {
				Biquad[] filters = { filter(), filter(), filter(), filter(), filter() };
				filters[0].setLowPass(frequency, 10);
				filters[1].setHighPass(frequency, 0.1);
				filters[2].setPeak(frequency, 8, gain);
				filters[3].setLowShelf(frequency, gain);
				filters[4].setHighShelf(frequency, gain);
				for( Biquad biquad : filters ) {
					float[] noise = Signals.noise(0, Signals.RATE * 2, 3L);
					Signals.run(biquad, noise);
					Assertions.assertTrue(Signals.finite(noise), "finite at " + frequency + " Hz, " + gain + " dB");
					float[] silence = new float[Signals.RATE * 10];
					Signals.run(biquad, silence);
					Assertions.assertTrue(Signals.noDenormals(silence), "no denormals after silence");
				}
			}
		}
	}

	@Test
	public void aNewSettingRampsWithoutAJump() {
		Biquad biquad = filter();
		biquad.setLowPass(200, Math.sqrt(0.5));
		float[] warm = Signals.sine(150, 0.5, Signals.RATE / 2);
		Signals.run(biquad, warm);
		biquad.setLowPass(8000, Math.sqrt(0.5));
		float[] next = Signals.sine(150, 0.5, Signals.RATE / 2 + 128);
		float[] block = new float[128];
		System.arraycopy(next, Signals.RATE / 2, block, 0, 128);
		biquad.process(block, block, 128);
		double previous = warm[warm.length - 1];
		double maxStep = 0;
		for( float sample : block ) {
			maxStep = Math.max(maxStep, Math.abs(sample - previous));
			previous = sample;
		}
		double sineStep = 2 * Math.PI * 150 * 0.5 / Signals.RATE;
		Assertions.assertTrue(maxStep < 3 * sineStep, "a step of " + maxStep + " against the sine's own " + sineStep);
	}
}
