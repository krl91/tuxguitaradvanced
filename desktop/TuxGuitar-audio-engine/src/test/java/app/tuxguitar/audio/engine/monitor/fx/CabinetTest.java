package app.tuxguitar.audio.engine.monitor.fx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The speaker cabinet curve (spec 022 FR-004, research R4; task T016). */
public class CabinetTest {

	private static Cabinet cabinet(boolean on) {
		Cabinet cabinet = new Cabinet();
		cabinet.prepare(Signals.RATE, 128);
		cabinet.setEnabled(on);
		return cabinet;
	}

	@Test
	public void itCutsTheLowsAndTheHighs() {
		double middle = Signals.gainDb(cabinet(true), 1000, 0.3);
		double low = Signals.gainDb(cabinet(true), 40, 0.3);
		double high = Signals.gainDb(cabinet(true), 10000, 0.3);
		Assertions.assertTrue(middle - low >= 12, "40 Hz is " + (middle - low) + " dB under 1 kHz");
		Assertions.assertTrue(middle - high >= 12, "10 kHz is " + (middle - high) + " dB under 1 kHz");
	}

	@Test
	public void switchedOffItIsAPassThrough() {
		float[] input = Signals.noise(-10, Signals.RATE, 4L);
		float[] output = input.clone();
		Signals.run(cabinet(false), output);
		Assertions.assertArrayEquals(input, output);
	}

	@Test
	public void itIsDeterministicAndFinite() {
		float[] first = Signals.noise(0, Signals.RATE, 5L);
		float[] second = first.clone();
		Signals.run(cabinet(true), first);
		Signals.run(cabinet(true), second);
		Assertions.assertArrayEquals(first, second);
		Assertions.assertTrue(Signals.finite(first));
	}
}
