package app.tuxguitar.audio.engine.calibration;

import java.util.Arrays;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.LevelMeasurement;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The measures shown at the quality question (spec 013, FR-005): the sounds heard out of the sounds expected, the highest level, the background noise as a
 * value in dBFS with a word, and whether the signal clipped. The player judges the noise: it is shown and never refuses a recording. The refusals are a
 * silent recording (its highest level under the input minimum), a clipped one, and a wrong number of sounds.
 */
public final class RecordingQuality {

	/** Background noise below this is called calm (display bands only, starting values adjustable after the trial). */
	public static final double CALM_BELOW_DBFS = -60.0;
	/** Background noise above this is called loud. */
	public static final double LOUD_ABOVE_DBFS = -45.0;

	public enum NoiseWord {
		CALM,
		MEDIUM,
		LOUD
	}

	private static final int BLOCK = 2048;
	private static final double FLOOR_DBFS = -120.0;

	private final int heard;
	private final int expected;
	private final double peakDbfs;
	private final double noiseDbfs;
	private final boolean clipped;

	public RecordingQuality(int heard, int expected, double peakDbfs, double noiseDbfs, boolean clipped) {
		this.heard = heard;
		this.expected = expected;
		this.peakDbfs = peakDbfs;
		this.noiseDbfs = noiseDbfs;
		this.clipped = clipped;
	}

	/** Measures a recording; {@code heard} is the count of the listening mode (or of the segmenter). */
	public static RecordingQuality measure(float[] samples, int sampleRate, int heard, int expected) {
		RecognitionSettings defaults = RecognitionSettings.defaults();
		LevelAnalyzer levels = AudioEngine.levelAnalyzer(defaults);
		double peak = FLOOR_DBFS;
		boolean clipped = false;
		int blocks = samples.length / BLOCK;
		double[] blockDbfs = new double[blocks];
		for (int b = 0; b < blocks; b++) {
			LevelMeasurement level = levels.analyze(samples, b * BLOCK, BLOCK);
			blockDbfs[b] = Math.max(FLOOR_DBFS, level.getRmsDbfs());
			peak = Math.max(peak, level.getPeakDbfs());
			if (level.getLevelState() == AnalysisFrame.LevelState.CLIPPING) {
				clipped = true;
			}
		}
		Arrays.sort(blockDbfs);
		// the background noise is the level of the quiet blocks: the 10th percentile of the levels of the blocks
		double noise = blocks == 0 ? FLOOR_DBFS : blockDbfs[(int) Math.min(blocks - 1, Math.floor(blocks * 0.10))];
		return new RecordingQuality(heard, expected, Math.max(FLOOR_DBFS, peak), noise, clipped);
	}

	public static NoiseWord wordFor(double noiseDbfs) {
		if (noiseDbfs < CALM_BELOW_DBFS) {
			return NoiseWord.CALM;
		}
		return noiseDbfs <= LOUD_ABOVE_DBFS ? NoiseWord.MEDIUM : NoiseWord.LOUD;
	}

	public int getHeard() {
		return this.heard;
	}

	public int getExpected() {
		return this.expected;
	}

	/** The highest level of the recording, in dBFS. */
	public double getPeakDbfs() {
		return this.peakDbfs;
	}

	/** The background noise, in dBFS. */
	public double getNoiseDbfs() {
		return this.noiseDbfs;
	}

	public NoiseWord getNoiseWord() {
		return wordFor(this.noiseDbfs);
	}

	public boolean isClipped() {
		return this.clipped;
	}

	/** Why the recording is refused, or null: silent, clipped, or a wrong number of sounds (in that order). The noise never refuses. */
	public SoundSegmenter.Refusal getRefusal() {
		double minimum = RecognitionSettings.defaults().get(RecognitionParameters.INPUT_LEVEL_MIN);
		if (this.heard == 0 || this.peakDbfs < minimum) {
			return SoundSegmenter.Refusal.SILENT;
		}
		if (this.clipped) {
			return SoundSegmenter.Refusal.CLIPPED;
		}
		return this.heard != this.expected ? SoundSegmenter.Refusal.WRONG_COUNT : null;
	}
}
