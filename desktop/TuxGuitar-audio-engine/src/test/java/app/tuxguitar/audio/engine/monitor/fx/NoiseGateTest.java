package app.tuxguitar.audio.engine.monitor.fx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.monitor.AmpSettings;
import app.tuxguitar.audio.engine.monitor.EffectKind;
import app.tuxguitar.audio.engine.monitor.PedalSettings;

/** The noise gate (spec 022 FR-011, research R4; task T017). */
public class NoiseGateTest {

	private static NoiseGate gate(double threshold, double release) {
		NoiseGate gate = new NoiseGate();
		gate.prepare(Signals.RATE, 128);
		gate.set(PedalSettings.of(EffectKind.GATE, true, threshold, release));
		return gate;
	}

	@Test
	public void itAttenuatesTheNoiseUnderItsThreshold() {
		float[] noise = Signals.noise(-70, Signals.RATE * 2, 1L);
		float[] output = noise.clone();
		Signals.run(gate(-60, 150), output);
		double attenuation = Signals.rmsDb(noise, Signals.RATE, 2 * Signals.RATE) - Signals.rmsDb(output, Signals.RATE, 2 * Signals.RATE);
		Assertions.assertTrue(attenuation >= 40, "attenuated by " + attenuation + " dB");
	}

	@Test
	public void aNoteOpensItWithinAMillisecond() {
		NoiseGate gate = gate(-60, 150);
		float[] noise = Signals.noise(-70, Signals.RATE, 2L);
		Signals.run(gate, noise);
		float[] note = Signals.sine(196, 0.1, 480);
		int opened = -1;
		float[] block = new float[1];
		for( int i = 0 ; i < note.length && opened < 0 ; i++ ) {
			block[0] = note[i];
			gate.process(block, block, 1);
			if( gate.gain() >= Math.pow(10, -1.0 / 20) ) {
				opened = i;
			}
		}
		Assertions.assertTrue(opened >= 0 && opened < 48, "open after " + opened + " samples");
	}

	@Test
	public void theReleaseFollowsItsKnobWithoutAClick() {
		for( double release : new double[] { 50, 150, 600 } ) {
			NoiseGate gate = gate(-60, release);
			float[] note = Signals.sine(196, 0.1, Signals.RATE / 2);
			Signals.run(gate, note);
			float[] tail = Signals.noise(-75, Signals.RATE * 2, 3L);
			float[] input = tail.clone();
			int began = -1;
			int closed = -1;
			float[] block = new float[1];
			for( int i = 0 ; i < tail.length && closed < 0 ; i++ ) {
				block[0] = tail[i];
				gate.process(block, block, 1);
				tail[i] = block[0];
				if( began < 0 && gate.gain() < 1.0 ) {
					began = i;
				}
				if( began >= 0 && gate.gain() <= 1e-3 ) {
					closed = i;
				}
			}
			double measured = (closed - began) * 1000.0 / Signals.RATE;
			Assertions.assertEquals(release, measured, release * 0.1, "release of " + release + " ms");
			double inputStep = 0;
			double outputStep = 0;
			for( int i = 1 ; i < closed ; i++ ) {
				inputStep = Math.max(inputStep, Math.abs(input[i] - input[i - 1]));
				outputStep = Math.max(outputStep, Math.abs(tail[i] - tail[i - 1]));
			}
			Assertions.assertTrue(outputStep <= inputStep * 1.05, "no click: " + outputStep + " against " + inputStep);
		}
	}

	@Test
	public void beforeAHighGainAmpTheNoiseStaysUnderMinusSixtyDecibels() {
		NoiseGate gate = gate(-55, 150);
		Amp amp = new Amp();
		amp.prepare(Signals.RATE, 128);
		amp.set(new AmpSettings(AmpSettings.Character.HIGH_GAIN, 7, 6, 6, 6, 6, true));
		Cabinet cabinet = new Cabinet();
		cabinet.prepare(Signals.RATE, 128);
		cabinet.setEnabled(true);
		OutputStage stage = new OutputStage(Signals.RATE, 128);
		stage.setVolume(0.7);
		stage.start();
		float[] noise = Signals.noise(-70, Signals.RATE * 2, 4L);
		float[] block = new float[128];
		for( int offset = 0 ; offset < noise.length ; offset += 128 ) {
			System.arraycopy(noise, offset, block, 0, 128);
			gate.process(block, block, 128);
			amp.process(block, block, 128);
			cabinet.process(block, block, 128);
			stage.process(block, 128);
			System.arraycopy(block, 0, noise, offset, 128);
		}
		double level = Signals.rmsDb(noise, Signals.RATE, 2 * Signals.RATE);
		Assertions.assertTrue(level < -60, "output noise " + level + " dBFS");
	}
}
