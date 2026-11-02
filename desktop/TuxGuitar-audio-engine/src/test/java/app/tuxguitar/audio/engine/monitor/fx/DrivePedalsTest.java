package app.tuxguitar.audio.engine.monitor.fx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.monitor.EffectKind;
import app.tuxguitar.audio.engine.monitor.Pedal;
import app.tuxguitar.audio.engine.monitor.PedalSettings;

/** The compressor, the overdrive and the distortion (spec 022 FR-005, research R4; task T030). */
public class DrivePedalsTest {

	static Pedal pedal(EffectKind kind, double... values) {
		Pedal pedal = Pedals.create(kind);
		pedal.prepare(Signals.RATE, 128);
		pedal.set(PedalSettings.of(kind, true, values));
		return pedal;
	}

	private static double level(Pedal pedal, double frequency, double amplitude) {
		return Signals.gainDb(pedal, frequency, amplitude) + 20 * Math.log10(amplitude);
	}

	@Test
	public void theCompressorNarrowsAStepByMoreWithMoreSustainAndMakesUpItsLevel() {
		double[] narrowed = new double[2];
		int index = 0;
		for (double sustain : new double[] { 0, 10 }) {
			Pedal quiet = pedal(EffectKind.COMPRESSOR, sustain, 10, 5);
			Pedal loud = pedal(EffectKind.COMPRESSOR, sustain, 10, 5);
			double low = level(quiet, 440, 0.01);
			double high = level(loud, 440, 0.1);
			narrowed[index++] = 20 - (high - low);
		}
		Assertions.assertTrue(narrowed[0] < 3, "sustain 0 hardly compresses: the 20 dB step narrowed by " + narrowed[0] + " dB");
		Assertions.assertTrue(narrowed[1] >= 10, "sustain 10 narrows the 20 dB step by at least 10 dB: " + narrowed[1] + " dB");
		double madeUp = level(pedal(EffectKind.COMPRESSOR, 10, 10, 5), 440, 0.01) - 20 * Math.log10(0.01);
		Assertions.assertTrue(madeUp >= 6, "a quiet note comes out louder at sustain 10: " + madeUp + " dB");
	}

	private static double thd(EffectKind kind, double drive) {
		Pedal pedal = pedal(kind, drive, 5, 5);
		float[] samples = Signals.sine(220, 0.25, Signals.RATE);
		Signals.run(pedal, samples);
		return Signals.thdPercent(samples, Signals.RATE / 2, Signals.RATE / 2 + 110 * Signals.RATE / 220, 220);
	}

	@Test
	public void theHarmonicsGrowWithDriveAndGain() {
		for (EffectKind kind : new EffectKind[] { EffectKind.OVERDRIVE, EffectKind.DISTORTION }) {
			double low = thd(kind, 1);
			double high = thd(kind, 9);
			Assertions.assertTrue(high > low * 1.5 && high > 10, kind + ": " + low + " % at 1, " + high + " % at 9");
		}
		Assertions.assertTrue(thd(EffectKind.DISTORTION, 6) > thd(EffectKind.OVERDRIVE, 6), "the distortion distorts more than the overdrive");
	}

	@Test
	public void toneMovesThe3kHzContentAndLevelSpansThirtyDecibels() {
		for (EffectKind kind : new EffectKind[] { EffectKind.OVERDRIVE, EffectKind.DISTORTION }) {
			double dark = Signals.gainDb(pedal(kind, 3, 0, 5), 3000, 0.01) - Signals.gainDb(pedal(kind, 3, 0, 5), 300, 0.01);
			double bright = Signals.gainDb(pedal(kind, 3, 10, 5), 3000, 0.01) - Signals.gainDb(pedal(kind, 3, 10, 5), 300, 0.01);
			Assertions.assertTrue(bright - dark >= 6, kind + ": tone moves 3 kHz by " + (bright - dark) + " dB");
			double quiet = Signals.gainDb(pedal(kind, 5, 5, 0), 440, 0.1);
			double loud = Signals.gainDb(pedal(kind, 5, 5, 10), 440, 0.1);
			Assertions.assertTrue(loud - quiet >= 30, kind + ": level spans " + (loud - quiet) + " dB");
		}
		double quiet = Signals.gainDb(pedal(EffectKind.COMPRESSOR, 5, 10, 0), 440, 0.1);
		double loud = Signals.gainDb(pedal(EffectKind.COMPRESSOR, 5, 10, 10), 440, 0.1);
		Assertions.assertTrue(loud - quiet >= 30, "the compressor's level spans " + (loud - quiet) + " dB");
	}

	@Test
	public void finiteAndDeterministic() {
		for (EffectKind kind : new EffectKind[] { EffectKind.COMPRESSOR, EffectKind.OVERDRIVE, EffectKind.DISTORTION }) {
			for (double knob : new double[] { 0, 10 }) {
				double[] values = new double[kind.knobs().size()];
				for (int i = 0; i < values.length; i++) {
					values[i] = knob == 0 ? kind.knobs().get(i).min() : kind.knobs().get(i).max();
				}
				float[] first = Signals.noise(0, Signals.RATE, 7);
				float[] second = first.clone();
				Signals.run(pedal(kind, values), first);
				Signals.run(pedal(kind, values), second);
				Assertions.assertTrue(Signals.finite(first), kind + " at " + knob);
				Assertions.assertArrayEquals(first, second, kind + " at " + knob);
			}
		}
	}
}
