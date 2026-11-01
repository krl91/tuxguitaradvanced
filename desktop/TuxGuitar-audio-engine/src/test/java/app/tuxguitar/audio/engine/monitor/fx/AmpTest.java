package app.tuxguitar.audio.engine.monitor.fx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.monitor.AmpSettings;

/** The amp (spec 022 FR-004, research R4; task T016). */
public class AmpTest {

	private static Amp amp(AmpSettings settings) {
		Amp amp = new Amp();
		amp.prepare(Signals.RATE, 128);
		amp.set(settings);
		return amp;
	}

	private static double thd(AmpSettings.Character character) {
		Amp amp = amp(new AmpSettings(character, 5, 5, 5, 5, 6, true));
		float[] samples = Signals.sine(220, 0.1, Signals.RATE);
		Signals.run(amp, samples);
		int from = Signals.RATE / 2;
		int to = from + 110 * Signals.RATE / 220;
		return Signals.thdPercent(samples, from, to, 220);
	}

	@Test
	public void eachCharacterHasItsDistortion() {
		double clean = thd(AmpSettings.Character.CLEAN);
		double crunch = thd(AmpSettings.Character.CRUNCH);
		double high = thd(AmpSettings.Character.HIGH_GAIN);
		Assertions.assertTrue(clean < 3, "clean " + clean + " %");
		Assertions.assertTrue(crunch >= 3 && crunch <= 30, "crunch " + crunch + " %");
		Assertions.assertTrue(high > 30, "high gain " + high + " %");
	}

	private static double toneGain(double bass, double middle, double treble, double frequency) {
		Amp amp = amp(new AmpSettings(AmpSettings.Character.CLEAN, 0, bass, middle, treble, 6, true));
		return Signals.gainDb(amp, frequency, 0.01);
	}

	@Test
	public void theToneKnobsMoveTheirBandsBySixDecibelsEachWay() {
		double[][] cases = { { 100, 0 }, { 700, 1 }, { 3000, 2 } };
		for( double[] tone : cases ) {
			double frequency = tone[0];
			int knob = (int) tone[1];
			double[] values = new double[3];
			for( int position = 0 ; position < 3 ; position++ ) {
				double[] knobs = { 5, 5, 5 };
				knobs[knob] = position * 5;
				values[position] = toneGain(knobs[0], knobs[1], knobs[2], frequency);
			}
			Assertions.assertTrue(values[1] - values[0] >= 6, "knob " + knob + " at 0 cuts " + frequency + " Hz: " + (values[1] - values[0]));
			Assertions.assertTrue(values[2] - values[1] >= 6, "knob " + knob + " at 10 lifts " + frequency + " Hz: " + (values[2] - values[1]));
		}
	}

	@Test
	public void theOutputIsFiniteForEveryKnobCombination() {
		for( AmpSettings.Character character : AmpSettings.Character.values() ) {
			for( int mask = 0 ; mask < 32 ; mask++ ) {
				double[] k = new double[5];
				for( int i = 0 ; i < 5 ; i++ ) {
					k[i] = ((mask >> i) & 1) == 0 ? 0 : 10;
				}
				Amp amp = amp(new AmpSettings(character, k[0], k[1], k[2], k[3], k[4], true));
				float[] loud = Signals.noise(-3, Signals.RATE / 4, mask);
				for( int i = 0 ; i < loud.length ; i += 97 ) {
					loud[i] = (i & 1) == 0 ? 1f : -1f;
				}
				Signals.run(amp, loud);
				Assertions.assertTrue(Signals.finite(loud), character + " mask " + mask);
				if( mask == 31 ) {
					float[] silence = new float[Signals.RATE * 10];
					Signals.run(amp, silence);
					Assertions.assertTrue(Signals.noDenormals(silence), character + ": no denormals after 10 s of silence");
				}
			}
		}
	}

	@Test
	public void twoRunsGiveTheSameSamples() {
		for( AmpSettings.Character character : AmpSettings.Character.values() ) {
			AmpSettings settings = new AmpSettings(character, 7, 6, 4, 7, 6, true);
			float[] first = Signals.noise(-12, Signals.RATE, 9L);
			float[] second = first.clone();
			Signals.run(amp(settings), first);
			Signals.run(amp(settings), second);
			Assertions.assertArrayEquals(first, second, character.name());
		}
	}
}
