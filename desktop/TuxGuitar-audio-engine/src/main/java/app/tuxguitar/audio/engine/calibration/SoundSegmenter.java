package app.tuxguitar.audio.engine.calibration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Cuts the calibration recording into one segment per sound, so that each sound can be judged alone against its own event (spec 013, contract
 * scoring-search). The sounds are found with the DEFAULT settings, once, whatever candidate is scored later: the cutting does not move with the search.
 * A recording that cannot be cut into the expected number of sounds, or that is silent or clipped, is refused with the reason and nothing is analysed.
 */
public final class SoundSegmenter {

	public enum Refusal {
		/** no sound above the input level minimum */
		SILENT,
		/** the signal reached the clipping thresholds */
		CLIPPED,
		/** more or fewer sounds than the piece has events */
		WRONG_COUNT
	}

	public static final class Result {
		private final Refusal refusal;
		private final int heard;
		private final int expected;
		private final List<float[]> segments;
		private final List<Integer> starts;

		private Result(Refusal refusal, int heard, int expected, List<float[]> segments, List<Integer> starts) {
			this.refusal = refusal;
			this.heard = heard;
			this.expected = expected;
			this.segments = segments;
			this.starts = starts;
		}

		public boolean isAccepted() {
			return this.refusal == null;
		}

		public Refusal getRefusal() {
			return this.refusal;
		}

		/** The sounds counted (after merging those less than the refractory time apart). */
		public int getHeard() {
			return this.heard;
		}

		public int getExpected() {
			return this.expected;
		}

		/** One array per sound, in order; null when the recording was refused. */
		public List<float[]> getSegments() {
			return this.segments;
		}

		/** The sample at which each segment starts in the recording; null when refused. */
		public List<Integer> getStarts() {
			return this.starts;
		}
	}

	private SoundSegmenter() {
	}

	public static Result segment(float[] samples, int sampleRate, int expectedSounds) {
		RecognitionSettings defaults = RecognitionSettings.defaults();
		double levelMinimum = defaults.get(RecognitionParameters.INPUT_LEVEL_MIN);
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(samples, sampleRate, defaults, false, null);
		boolean clipped = false;
		List<Long> onsets = new ArrayList<Long>();
		long refractory = (long) SoundDetection.REFRACTORY_MILLIS * 1_000_000L;
		long lastOnset = Long.MIN_VALUE;
		for (AnalysisFrame frame : frames) {
			if (frame.getLevelState() == AnalysisFrame.LevelState.CLIPPING) {
				clipped = true;
			}
			if (frame.isOnset() && frame.getRmsDbfs() >= levelMinimum) {
				long when = frame.getTimestampNanos();
				if (lastOnset == Long.MIN_VALUE || when - lastOnset >= refractory) {
					onsets.add(Long.valueOf(when));
				}
				lastOnset = when;
			}
		}
		if (onsets.isEmpty()) {
			return refused(Refusal.SILENT, 0, expectedSounds);
		}
		if (clipped) {
			return refused(Refusal.CLIPPED, onsets.size(), expectedSounds);
		}
		if (onsets.size() != expectedSounds) {
			return refused(Refusal.WRONG_COUNT, onsets.size(), expectedSounds);
		}
		List<Integer> starts = new ArrayList<Integer>();
		for (Long onset : onsets) {
			int end = (int) Math.min(samples.length, onset.longValue() * sampleRate / 1_000_000_000L);
			int start = Math.max(0, end - (int) ((long) SoundDetection.SEGMENT_LEAD_MILLIS * sampleRate / 1000L));
			if (!starts.isEmpty() && start <= starts.get(starts.size() - 1)) {
				start = starts.get(starts.size() - 1) + 1;
			}
			starts.add(Integer.valueOf(start));
		}
		List<float[]> segments = new ArrayList<float[]>();
		for (int i = 0; i < starts.size(); i++) {
			int from = starts.get(i);
			int to = (i + 1 < starts.size() ? starts.get(i + 1) : samples.length);
			float[] part = new float[Math.max(0, to - from)];
			System.arraycopy(samples, from, part, 0, part.length);
			segments.add(part);
		}
		return new Result(null, onsets.size(), expectedSounds, Collections.unmodifiableList(segments), Collections.unmodifiableList(starts));
	}

	private static Result refused(Refusal refusal, int heard, int expected) {
		return new Result(refusal, heard, expected, null, null);
	}
}
