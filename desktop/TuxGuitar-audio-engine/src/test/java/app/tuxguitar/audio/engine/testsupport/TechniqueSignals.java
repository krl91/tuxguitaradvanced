package app.tuxguitar.audio.engine.testsupport;

import java.util.function.DoubleUnaryOperator;

/**
 * Deterministic synthetic signals for fast passages and playing techniques (spec 002, tasks T002 and
 * T003). Every technique generator has a plain counterpart, so a detector can be checked on both.
 * All signals are mono floats; none uses random numbers except through {@link SyntheticSignals}.
 */
public final class TechniqueSignals {

	private TechniqueSignals() {
	}

	/** A passage of plucks with the onsets where they were placed. */
	public static final class Passage {
		private final float[] signal;
		private final int[] onsetSamples;
		private final int[] midi;

		Passage(float[] signal, int[] onsetSamples, int[] midi) {
			this.signal = signal;
			this.onsetSamples = onsetSamples;
			this.midi = midi;
		}

		public float[] getSignal() {
			return this.signal;
		}

		/** Sample index of each pluck's start. */
		public int[] getOnsetSamples() {
			return this.onsetSamples.clone();
		}

		public int[] getMidi() {
			return this.midi.clone();
		}
	}

	// ---------------------------------------------------------------- fast passages

	/**
	 * Plucks at a constant rate, one per entry of {@code midiNotes}, starting at {@code leadInSamples}.
	 * Each pluck keeps ringing under the next ones (as real strings do), so identical repeated notes
	 * and semitone neighbours test the separation of notes, not just of silences.
	 */
	public static Passage fastPassage(int[] midiNotes, int sampleRate, double notesPerSecond, int leadInSamples, int tailSamples, double amplitude, long seed) {
		if( midiNotes.length == 0 || notesPerSecond <= 0.0 ) {
			throw new IllegalArgumentException("notes and a positive rate are required");
		}
		int spacing = (int) Math.round(sampleRate / notesPerSecond);
		int length = leadInSamples + spacing * (midiNotes.length - 1) + tailSamples;
		float[] signal = new float[length];
		int[] onsets = new int[midiNotes.length];
		for( int i = 0 ; i < midiNotes.length ; i ++ ) {
			onsets[i] = leadInSamples + i * spacing;
			float[] pluck = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(midiNotes[i]), sampleRate, length - onsets[i], seed + i);
			for( int j = 0 ; j < pluck.length ; j ++ ) {
				signal[onsets[i] + j] += (float) (amplitude * pluck[j]);
			}
		}
		return new Passage(signal, onsets, midiNotes.clone());
	}

	// ---------------------------------------------------------------- pitched tone with a frequency track

	/**
	 * A decaying harmonic tone whose fundamental follows {@code frequencyAtSecond} (phase-continuous,
	 * so a change of pitch has no attack). Harmonic amplitudes are relative to the fundamental.
	 */
	public static float[] tone(int sampleRate, int sampleCount, DoubleUnaryOperator frequencyAtSecond, double decaySeconds, double amplitude, double... harmonicAmplitudes) {
		float[] signal = new float[sampleCount];
		double phase = 0.0;
		for( int i = 0 ; i < sampleCount ; i ++ ) {
			double t = i / (double) sampleRate;
			phase += 2.0 * Math.PI * frequencyAtSecond.applyAsDouble(t) / sampleRate;
			double envelope = amplitude * Math.exp(-t / decaySeconds);
			double sample = 0.0;
			for( int h = 0 ; h < harmonicAmplitudes.length ; h ++ ) {
				sample += harmonicAmplitudes[h] * Math.sin(phase * (h + 1));
			}
			signal[i] = (float) (envelope * sample);
		}
		return signal;
	}

	private static final double[] GUITAR_LIKE = { 1.0, 0.5, 0.3, 0.15 };

	// ---------------------------------------------------------------- legato

	/** Two plucks: the plain counterpart of a legato pair. */
	public static float[] pickedPair(int fromMidi, int toMidi, int sampleRate, int secondNoteSample, int sampleCount, long seed) {
		float[] signal = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(fromMidi), sampleRate, sampleCount, seed);
		float[] second = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(toMidi), sampleRate, sampleCount - secondNoteSample, seed + 1);
		return SyntheticSignals.mix(signal, second, secondNoteSample);
	}

	/**
	 * A legato step (hammer-on when {@code toMidi} is higher, pull-off when lower): one pluck, then the
	 * pitch changes at {@code stepSecond} without any new attack and the level keeps decaying.
	 */
	public static float[] legatoStep(int fromMidi, int toMidi, int sampleRate, double stepSecond, int sampleCount) {
		double from = SyntheticSignals.midiFrequency(fromMidi);
		double to = SyntheticSignals.midiFrequency(toMidi);
		return tone(sampleRate, sampleCount, t -> (t < stepSecond ? from : to), 0.8, 0.5, GUITAR_LIKE);
	}

	// ---------------------------------------------------------------- slide, bend, vibrato

	/** A slide: the pitch glides (linearly in semitones) from one note to another, no new attack. */
	public static float[] slide(int fromMidi, int toMidi, int sampleRate, double startSecond, double glideSeconds, int sampleCount) {
		return tone(sampleRate, sampleCount, t -> {
			double progress = Math.max(0.0, Math.min(1.0, (t - startSecond) / glideSeconds));
			return SyntheticSignals.midiFrequency(fromMidi) * Math.pow(2.0, progress * (toMidi - fromMidi) / 12.0);
		}, 0.8, 0.5, GUITAR_LIKE);
	}

	/**
	 * A bend: the pitch follows {@code semitonePath} = pairs (seconds, semitones above {@code midi}),
	 * linear between the points and constant after the last one.
	 */
	public static float[] bend(int midi, int sampleRate, int sampleCount, double... semitonePath) {
		if( semitonePath.length < 2 || semitonePath.length % 2 != 0 ) {
			throw new IllegalArgumentException("pairs (seconds, semitones) are required");
		}
		double base = SyntheticSignals.midiFrequency(midi);
		return tone(sampleRate, sampleCount, t -> base * Math.pow(2.0, semitonesAt(semitonePath, t) / 12.0), 0.8, 0.5, GUITAR_LIKE);
	}

	private static double semitonesAt(double[] path, double t) {
		if( t <= path[0] ) {
			return path[1];
		}
		for( int i = 2 ; i < path.length ; i += 2 ) {
			if( t <= path[i] ) {
				double part = (t - path[i - 2]) / (path[i] - path[i - 2]);
				return path[i - 1] + part * (path[i + 1] - path[i - 1]);
			}
		}
		return path[path.length - 1];
	}

	/**
	 * Vibrato: a steady note whose pitch oscillates by {@code depthCents} (peak) at {@code rateHz}.
	 * {@code harmonicAmplitudes} defaults to a guitar-like spectrum; pass {@code 1.0} for a pure sine.
	 */
	public static float[] vibrato(int midi, int sampleRate, int sampleCount, double rateHz, double depthCents, double... harmonicAmplitudes) {
		double base = SyntheticSignals.midiFrequency(midi);
		return tone(sampleRate, sampleCount, t -> base * Math.pow(2.0, depthCents * Math.sin(2.0 * Math.PI * rateHz * t) / 1200.0), 0.8, 0.5,
				harmonicAmplitudes.length == 0 ? GUITAR_LIKE : harmonicAmplitudes);
	}

	/** The plain counterpart: the same note with a steady pitch. */
	public static float[] steadyNote(int midi, int sampleRate, int sampleCount, double... harmonicAmplitudes) {
		return vibrato(midi, sampleRate, sampleCount, 5.0, 0.0, harmonicAmplitudes);
	}

	// ---------------------------------------------------------------- palm mute, tremolo picking, harmonics

	/** A plucked note that dies quickly and sounds darker, as under the palm. */
	public static float[] palmMutedPluck(int midi, int sampleRate, int sampleCount, long seed) {
		float[] open = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(midi), sampleRate, sampleCount, seed);
		float[] muted = new float[sampleCount];
		double smoothed = 0.0;
		for( int i = 0 ; i < sampleCount ; i ++ ) {
			smoothed += 0.25 * (open[i] - smoothed);
			double t = i / (double) sampleRate;
			muted[i] = (float) (smoothed * Math.exp(-t / 0.06));
		}
		return muted;
	}

	/** Tremolo picking: the same note picked {@code picksPerSecond} times a second for {@code seconds}. */
	public static float[] tremoloPicking(int midi, int sampleRate, int sampleCount, double picksPerSecond, double seconds, long seed) {
		int spacing = (int) Math.round(sampleRate / picksPerSecond);
		int picks = (int) Math.floor(seconds * picksPerSecond);
		int[] onsets = new int[picks];
		for( int i = 0 ; i < picks ; i ++ ) {
			onsets[i] = i * spacing;
		}
		return SyntheticSignals.repeatedPlucks(SyntheticSignals.midiFrequency(midi), sampleRate, sampleCount, onsets, seed);
	}

	/** A natural harmonic: almost a pure sine, with only a trace of upper partials. */
	public static float[] naturalHarmonic(int soundingMidi, int sampleRate, int sampleCount) {
		double frequency = SyntheticSignals.midiFrequency(soundingMidi);
		return tone(sampleRate, sampleCount, t -> frequency, 1.2, 0.5, 1.0, 0.03);
	}

	// ---------------------------------------------------------------- input conditions

	/** Mains hum: a fundamental and two harmonics (for example 50 Hz). */
	public static float[] hum(int sampleRate, int sampleCount, double fundamentalHz, double amplitude) {
		return SyntheticSignals.harmonics(fundamentalHz, sampleRate, sampleCount, amplitude, amplitude * 0.5, amplitude * 0.25);
	}

	/** {@code gain} times the signal (a weak or boosted input). */
	public static float[] scaled(float[] source, double gain) {
		float[] scaled = new float[source.length];
		for( int i = 0 ; i < source.length ; i ++ ) {
			scaled[i] = (float) (source[i] * gain);
		}
		return scaled;
	}

	// ---------------------------------------------------------------- measurement helper for tests

	/** Energy of the signal at one frequency over [from, to) (Goertzel), for checking what a generator produced. */
	public static double energyAt(float[] signal, int from, int to, int sampleRate, double frequencyHz) {
		double omega = 2.0 * Math.PI * frequencyHz / sampleRate;
		double coefficient = 2.0 * Math.cos(omega);
		double s1 = 0.0;
		double s2 = 0.0;
		for( int i = from ; i < to ; i ++ ) {
			double s0 = signal[i] + coefficient * s1 - s2;
			s2 = s1;
			s1 = s0;
		}
		return (s1 * s1 + s2 * s2 - coefficient * s1 * s2) / Math.max(1, to - from);
	}
}
