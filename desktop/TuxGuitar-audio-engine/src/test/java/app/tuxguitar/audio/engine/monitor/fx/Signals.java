package app.tuxguitar.audio.engine.monitor.fx;

/** Small signal helpers for the effect tests of spec 022: sines, levels and harmonic content. */
final class Signals {

	static final int RATE = 48000;

	private Signals() {
	}

	static float[] sine(double frequency, double amplitude, int frames) {
		float[] samples = new float[frames];
		for( int i = 0 ; i < frames ; i++ ) {
			samples[i] = (float) (amplitude * Math.sin(2 * Math.PI * frequency * i / RATE));
		}
		return samples;
	}

	static float[] noise(double rmsDbfs, int frames, long seed) {
		java.util.Random random = new java.util.Random(seed);
		double rms = Math.pow(10, rmsDbfs / 20);
		float[] samples = new float[frames];
		for( int i = 0 ; i < frames ; i++ ) {
			samples[i] = (float) (rms * random.nextGaussian());
		}
		return samples;
	}

	static double rmsDb(float[] samples, int from, int to) {
		double sum = 0;
		for( int i = from ; i < to ; i++ ) {
			sum += (double) samples[i] * samples[i];
		}
		return 10 * Math.log10(Math.max(1e-30, sum / (to - from)));
	}

	/** The amplitude of one frequency over a whole number of its periods (a single-bin DFT). */
	static double amplitude(float[] samples, int from, int to, double frequency) {
		double re = 0;
		double im = 0;
		for( int i = from ; i < to ; i++ ) {
			double phase = 2 * Math.PI * frequency * i / RATE;
			re += samples[i] * Math.cos(phase);
			im += samples[i] * Math.sin(phase);
		}
		return 2 * Math.sqrt(re * re + im * im) / (to - from);
	}

	/** Total harmonic distortion in % (harmonics 2 to 10 over the fundamental). */
	static double thdPercent(float[] samples, int from, int to, double fundamental) {
		double base = amplitude(samples, from, to, fundamental);
		double sum = 0;
		for( int h = 2 ; h <= 10 && h * fundamental < RATE / 2.0 ; h++ ) {
			double a = amplitude(samples, from, to, h * fundamental);
			sum += a * a;
		}
		return 100 * Math.sqrt(sum) / base;
	}

	/** The gain in dB of a sine through an effect (measured after it settled). */
	static double gainDb(app.tuxguitar.audio.engine.monitor.Effect effect, double frequency, double amplitude) {
		int frames = RATE;
		float[] in = sine(frequency, amplitude, frames);
		float[] out = new float[frames];
		for( int offset = 0 ; offset < frames ; offset += 128 ) {
			float[] block = new float[128];
			System.arraycopy(in, offset, block, 0, 128);
			effect.process(block, block, 128);
			System.arraycopy(block, 0, out, offset, 128);
		}
		int from = RATE / 2;
		int periods = (int) Math.floor((frames - from) * frequency / RATE);
		int to = from + (int) Math.round(periods * RATE / frequency);
		return 20 * Math.log10(amplitude(out, from, to, frequency) / amplitude);
	}

	static void run(app.tuxguitar.audio.engine.monitor.Effect effect, float[] samples) {
		float[] block = new float[128];
		for( int offset = 0 ; offset < samples.length ; offset += 128 ) {
			int n = Math.min(128, samples.length - offset);
			System.arraycopy(samples, offset, block, 0, n);
			effect.process(block, block, n);
			System.arraycopy(block, 0, samples, offset, n);
		}
	}

	static boolean finite(float[] samples) {
		for( float sample : samples ) {
			if( Float.isNaN(sample) || Float.isInfinite(sample) ) {
				return false;
			}
		}
		return true;
	}

	/** True when no sample is a non-zero subnormal. */
	static boolean noDenormals(float[] samples) {
		for( float sample : samples ) {
			if( sample != 0f && Math.abs(sample) < Float.MIN_NORMAL ) {
				return false;
			}
		}
		return true;
	}
}
