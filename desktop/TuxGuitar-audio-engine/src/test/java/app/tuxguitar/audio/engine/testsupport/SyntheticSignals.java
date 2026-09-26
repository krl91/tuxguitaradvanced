package app.tuxguitar.audio.engine.testsupport;

import java.util.Random;

/** Deterministic synthetic audio used by engine tests. */
public final class SyntheticSignals {

	private SyntheticSignals() {
	}

	public static float[] sine(double frequencyHz, int sampleRate, int sampleCount, double amplitude) {
		float[] signal = new float[sampleCount];
		for (int i = 0; i < signal.length; i++) {
			signal[i] = (float) (amplitude * Math.sin(2.0 * Math.PI * frequencyHz * i / sampleRate));
		}
		return signal;
	}

	/** Each harmonic amplitude corresponds to fundamental, second harmonic, and so on. */
	public static float[] harmonics(double fundamentalHz, int sampleRate, int sampleCount, double... amplitudes) {
		float[] signal = new float[sampleCount];
		for (int i = 0; i < signal.length; i++) {
			double sample = 0.0;
			for (int harmonic = 0; harmonic < amplitudes.length; harmonic++) {
				sample += amplitudes[harmonic] * Math.sin(2.0 * Math.PI * fundamentalHz * (harmonic + 1) * i / sampleRate);
			}
			signal[i] = (float) sample;
		}
		return signal;
	}

	public static float[] detunedSine(int midi, double cents, int sampleRate, int sampleCount, double amplitude) {
		return sine(midiFrequency(midi) * Math.pow(2.0, cents / 1200.0), sampleRate, sampleCount, amplitude);
	}

	public static float[] noise(int sampleCount, double amplitude, long seed) {
		Random random = new Random(seed);
		float[] signal = new float[sampleCount];
		for (int i = 0; i < signal.length; i++) {
			signal[i] = (float) ((random.nextDouble() * 2.0 - 1.0) * amplitude);
		}
		return signal;
	}

	public static float[] silence(int sampleCount) {
		return new float[sampleCount];
	}

	public static float[] hardClip(float[] source, double limit) {
		if (limit <= 0.0 || limit > 1.0) {
			throw new IllegalArgumentException("limit must be in (0, 1]");
		}
		float[] clipped = new float[source.length];
		for (int i = 0; i < source.length; i++) {
			clipped[i] = (float) Math.max(-limit, Math.min(limit, source[i]));
		}
		return clipped;
	}

	public static float[] karplusStrong(double frequencyHz, int sampleRate, int sampleCount, long seed) {
		int period = Math.max(2, (int) Math.round(sampleRate / frequencyHz));
		Random random = new Random(seed);
		float[] delay = new float[period];
		for (int i = 0; i < delay.length; i++) {
			delay[i] = (float) (random.nextDouble() * 2.0 - 1.0);
		}

		float[] signal = new float[sampleCount];
		for (int i = 0; i < signal.length; i++) {
			int current = i % period;
			int next = (current + 1) % period;
			signal[i] = delay[current];
			delay[current] = 0.996f * 0.5f * (delay[current] + delay[next]);
		}
		return signal;
	}

	public static float[] repeatedPlucks(double frequencyHz, int sampleRate, int sampleCount, int[] onsetSamples, long seed) {
		float[] signal = new float[sampleCount];
		for (int i = 0; i < onsetSamples.length; i++) {
			int onset = onsetSamples[i];
			if (onset < 0 || onset >= sampleCount) {
				throw new IllegalArgumentException("onset outside signal: " + onset);
			}
			float[] pluck = karplusStrong(frequencyHz, sampleRate, sampleCount - onset, seed + i);
			for (int j = 0; j < pluck.length; j++) {
				signal[onset + j] += pluck[j];
			}
		}
		return signal;
	}

	public static float[] interleave(float[]... channels) {
		if (channels.length == 0) {
			throw new IllegalArgumentException("at least one channel is required");
		}
		int frames = channels[0].length;
		for (float[] channel : channels) {
			if (channel.length != frames) {
				throw new IllegalArgumentException("all channels must have equal length");
			}
		}
		float[] interleaved = new float[frames * channels.length];
		for (int frame = 0; frame < frames; frame++) {
			for (int channel = 0; channel < channels.length; channel++) {
				interleaved[frame * channels.length + channel] = channels[channel][frame];
			}
		}
		return interleaved;
	}

	public static double midiFrequency(int midi) {
		return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
	}
}
