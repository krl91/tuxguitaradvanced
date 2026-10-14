package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * The chord guard of the default suite (spec 010 T027, R8 missing 1): open chords as plucked-string sums, judged by the production rules
 * ({@link DatasetJudge#accepts}: {@code ChordMatcher} and {@code PolyphonicAcceptance}). The signals come from the engine's test support
 * ({@code SyntheticSignals}, visible through the test-jar the practice module already depends on: nothing was copied). The asserted figures are the ones
 * measured before this feature (recorded in {@code specs/010-pre-run-check/benchmark/before-after.txt}), a baseline and not an ideal: the guard fails if
 * they get worse.
 */
public class SyntheticChordGuardTest {

	private static final int RATE = 44100;
	private static final long[] SEEDS = { 11L, 23L, 37L };

	private static final String[] NAMES = { "A", "C", "E", "D", "G" };
	private static final int[][] CHORDS = { { 45, 52, 57, 61, 64 }, { 48, 52, 55, 60, 64 }, { 40, 47, 52, 56, 59, 64 }, { 50, 57, 62, 66 }, { 43, 47, 50, 55, 59, 67 } };

	/** A strum of the pitches at the given RMS level (dBFS), three tenths of a second after the start. */
	static RecordedAudio strum(int[] pitches, double rmsDbfs, long seed) {
		float[] signal = SyntheticSignals.strum(pitches, RATE, RATE * 5 / 2, 600, 1.0, seed);
		double sum = 0;
		for (float s : signal) {
			sum += (double) s * s;
		}
		double rms = Math.sqrt(sum / signal.length);
		double gain = Math.pow(10.0, rmsDbfs / 20.0) / rms;
		for (int i = 0; i < signal.length; i++) {
			signal[i] = (float) (signal[i] * gain);
		}
		return RecordedAudio.of(signal, RATE);
	}

	/** @return {accepted, total} of the right chords. */
	static int[] rightChords() {
		int accepted = 0;
		int total = 0;
		for (int c = 0; c < CHORDS.length; c++) {
			int ok = 0;
			for (long seed : SEEDS) {
				total++;
				if (DatasetJudge.accepts(strum(CHORDS[c], -20.0, seed), CHORDS[c])) {
					accepted++;
					ok++;
				}
			}
					}
		return new int[] { accepted, total };
	}

	/** @return {accepted, total} of the chords played with one string missing while it is expected. */
	static int[] missingStringChords() {
		int accepted = 0;
		int total = 0;
		for (int c = 0; c < CHORDS.length; c++) {
			int[] chord = CHORDS[c];
			int ok = 0;
			for (int missing = 0; missing < chord.length; missing++) {
				int[] played = new int[chord.length - 1];
				for (int i = 0, k = 0; i < chord.length; i++) {
					if (i != missing) {
						played[k++] = chord[i];
					}
				}
				for (long seed : SEEDS) {
					total++;
					if (DatasetJudge.accepts(strum(played, -20.0, seed), chord)) {
						accepted++;
						ok++;
					}
				}
			}
		}
		return new int[] { accepted, total };
	}

	/** The figures measured before this feature (2026-10-14, build of commit 2e9643ae8): 15 of 15 right chords accepted; 43 of 78 chords with one string missing accepted. */
	static final int BASELINE_RIGHT_ACCEPTED = 15;
	static final int BASELINE_MISSING_ACCEPTED_AT_MOST = 43;

	@Test
	public void everyOpenChordPlayedRightIsStillAccepted() {
		int[] right = rightChords();
		assertTrue(right[0] >= BASELINE_RIGHT_ACCEPTED, "right chords accepted: " + right[0] + " of " + right[1] + ", baseline " + BASELINE_RIGHT_ACCEPTED);
	}

	@Test
	public void aChordWithAStringMissingIsNotAcceptedMoreOftenThanBefore() {
		int[] missing = missingStringChords();
		assertTrue(missing[0] <= BASELINE_MISSING_ACCEPTED_AT_MOST, "chords with a string missing accepted: " + missing[0] + " of " + missing[1] + ", baseline at most " + BASELINE_MISSING_ACCEPTED_AT_MOST
				+ " (the matcher accepts some of them today: the guard only keeps it from getting more permissive)");
	}
}
