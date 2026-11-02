package app.tuxguitar.audio.engine.monitor.fx;

import app.tuxguitar.audio.engine.monitor.EffectKind;
import app.tuxguitar.audio.engine.monitor.Pedal;

/** Makes the pedal of each kind (spec 022 FR-005, research R4). */
public final class Pedals {

	private Pedals() {
	}

	public static Pedal create(EffectKind kind) {
		switch (kind) {
		case GATE:
			return new NoiseGate();
		case COMPRESSOR:
			return new Compressor();
		case OVERDRIVE:
			return new Overdrive();
		case DISTORTION:
			return new Distortion();
		case CHORUS:
			return new Chorus();
		case ECHO:
			return new Echo();
		default:
			return new Reverb();
		}
	}

	/** A pedal's level knob, 0 to 10, as a gain: 5 is unity, each step 3.6 dB (36 dB from 0 to 10). */
	static double level(double knob) {
		return Math.pow(10, (knob - 5) * 3.6 / 20);
	}

	/** A one-pole low-pass coefficient for that cut-off, kept under the Nyquist frequency. */
	static double onePole(double frequency, float sampleRate) {
		return 1.0 - Math.exp(-2 * Math.PI * Math.min(frequency, 0.45 * sampleRate) / sampleRate);
	}

	/** Under that size a state is set to zero, so that no denormal number slows the loop down. */
	static double flush(double value) {
		return Math.abs(value) < 1e-20 ? 0.0 : value;
	}
}
