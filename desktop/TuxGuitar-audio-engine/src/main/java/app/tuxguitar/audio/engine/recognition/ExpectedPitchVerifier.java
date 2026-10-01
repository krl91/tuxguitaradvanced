package app.tuxguitar.audio.engine.recognition;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.audio.engine.recognition.TargetEvidence.Hypothesis;

/**
 * Asks "is the expected pitch playing?" instead of "which pitch is playing?" (spec 002, research
 * R3 lever L1). For the expected pitch, its semitone neighbours and its octave it measures the
 * periodicity of the newest audio at that pitch's period (normalised autocorrelation, searched
 * within {@link #SEARCH_CENTS} of the nominal period), and penalises a pitch whose own half period
 * is also periodic (the audio is then an octave higher). Deterministic, no state.
 *
 * <p>The window is about four periods (at most the audio given, at least {@link #MIN_WINDOW}
 * samples), so the evidence is available after at most the analysis window, and sooner for
 * higher strings.</p>
 */
public final class ExpectedPitchVerifier {

	public static final double SEARCH_CENTS = 20.0;
	public static final int PERIODS = 4;
	public static final int MIN_WINDOW = 512;

	private final int sampleRate;

	public ExpectedPitchVerifier(int sampleRate) {
		if( sampleRate <= 0 ) {
			throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
		}
		this.sampleRate = sampleRate;
	}

	/**
	 * @param audio the newest samples (the last ones are used)
	 * @param expectedMidi the pitches to verify (several for a chord)
	 * @return the evidence for each expected pitch, its neighbours and its octave
	 */
	public TargetEvidence verify(float[] audio, int... expectedMidi) {
		if( expectedMidi == null || expectedMidi.length == 0 ) {
			throw new IllegalArgumentException("at least one expected pitch is required");
		}
		List<Hypothesis> hypotheses = new ArrayList<Hypothesis>();
		int longest = 0;
		for( int expected : expectedMidi ) {
			for( int midi : new int[] { expected, expected - 1, expected + 1, expected + 12 } ) {
				if( !contains(hypotheses, midi) ) {
					int used = windowFor(midi, audio.length);
					longest = Math.max(longest, used);
					hypotheses.add(new Hypothesis(midi, score(audio, midi, used)));
				}
			}
		}
		return new TargetEvidence(hypotheses, 1000.0 * longest / this.sampleRate);
	}

	private int windowFor(int midi, int available) {
		int period = (int) Math.ceil(this.sampleRate / frequency(midi));
		return Math.min(available, Math.max(MIN_WINDOW, PERIODS * period + period));
	}

	private double score(float[] audio, int midi, int window) {
		double period = this.sampleRate / frequency(midi);
		double own = periodicity(audio, window, period);
		double half = periodicity(audio, window, period / 2.0);
		double penalty = Math.max(0.0, half);
		return clamp(own * (1.0 - penalty * penalty));
	}

	/** Best normalised correlation between the newest samples and themselves one period earlier. */
	private double periodicity(float[] audio, int window, double period) {
		int low = (int) Math.floor(period * Math.pow(2.0, -SEARCH_CENTS / 1200.0));
		int high = (int) Math.ceil(period * Math.pow(2.0, SEARCH_CENTS / 1200.0));
		double best = 0.0;
		for( int lag = Math.max(2, low) ; lag <= high ; lag ++ ) {
			int length = Math.min(PERIODS * (int) Math.round(period), window - lag);
			if( length < 32 ) {
				continue;
			}
			int start = audio.length - length;
			double cross = 0.0;
			double energyNow = 0.0;
			double energyBefore = 0.0;
			for( int i = start ; i < audio.length ; i ++ ) {
				double now = audio[i];
				double before = audio[i - lag];
				cross += now * before;
				energyNow += now * now;
				energyBefore += before * before;
			}
			double norm = Math.sqrt(energyNow * energyBefore);
			if( norm > 1e-9 ) {
				best = Math.max(best, cross / norm);
			}
		}
		return best;
	}

	private static boolean contains(List<Hypothesis> hypotheses, int midi) {
		for( Hypothesis hypothesis : hypotheses ) {
			if( hypothesis.getMidi() == midi ) {
				return true;
			}
		}
		return false;
	}

	private static double frequency(int midi) {
		return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
	}

	private static double clamp(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}
}
