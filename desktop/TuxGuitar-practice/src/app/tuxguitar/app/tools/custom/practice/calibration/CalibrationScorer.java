package app.tuxguitar.app.tools.custom.practice.calibration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Scores a candidate settings on the cut recording (spec 013, contract scoring-search): each sound is judged alone against its own event, and also
 * against the NEXT event (the last against the first), which it must not match: a setting that accepts everything then scores badly. The score is the
 * sounds recognised minus {@link #WRONG_WEIGHT} points for each sound accepted for the wrong event.
 */
public final class CalibrationScorer {

	/** Points taken off for a sound accepted for a wrong event: a starting value, adjustable after the owner's trial. */
	public static final double WRONG_WEIGHT = 2.0;

	/** Whether the sound {@code segment} makes a session expecting {@code event} accept, with these settings. */
	public interface Judgement {
		boolean accepts(int segment, int event, RecognitionSettings settings);

		/** Called before a candidate is scored, so that a judgement can do its heavy work for all the sounds at once; nothing by default. */
		default void prepare(RecognitionSettings settings) {
		}
	}

	private final Judgement judgement;
	private final boolean[] chord;
	private final AtomicLong analysesDone = new AtomicLong();

	public CalibrationScorer(Judgement judgement, boolean[] chord) {
		this.judgement = judgement;
		this.chord = chord.clone();
	}

	/** A scorer over the segments of a recording, judged by {@code judge}; the frames of an analysis are kept for every candidate that shares its analysis settings. */
	public static CalibrationScorer forRecording(final EventJudge judge, final List<float[]> segments, final int sampleRate) {
		boolean[] chords = new boolean[judge.eventCount()];
		for (int i = 0; i < chords.length; i++) {
			chords[i] = judge.isChord(i);
		}
		final Map<String, List<AnalysisFrame>> frames = new ConcurrentHashMap<String, List<AnalysisFrame>>();
		final CalibrationScorer[] self = new CalibrationScorer[1];
		final int count = chords.length;
		self[0] = new CalibrationScorer(new Judgement() {
			private List<AnalysisFrame> framesOf(int segment, int event, RecognitionSettings settings) {
				String key = segment + ">" + event + "@" + analysisKey(settings);
				List<AnalysisFrame> found = frames.get(key);
				if (found == null) {
					found = judge.analyse(segments.get(segment), sampleRate, event, settings);
					if (frames.putIfAbsent(key, found) == null) {
						self[0].analysesDone.incrementAndGet();
					}
				}
				return found;
			}

			/** The analyses of a candidate are independent: the missing ones are made together, on all the cores. The result does not depend on the order. */
			public void prepare(RecognitionSettings settings) {
				IntStream.range(0, 2 * count).parallel().forEach(k -> {
					int segment = k / 2;
					framesOf(segment, k % 2 == 0 ? segment : (segment + 1) % count, settings);
				});
			}

			public boolean accepts(int segment, int event, RecognitionSettings settings) {
				return judge.accepts(framesOf(segment, event, settings), event, settings);
			}
		}, chords);
		return self[0];
	}

	/** The settings that reach the offline analysis (the level and attack values and the pitch engine, as in {@code AudioEngine.engineValuesDiffer}). */
	static String analysisKey(RecognitionSettings settings) {
		return settings.get(RecognitionParameters.INPUT_LEVEL_MIN) + "|" + settings.get(RecognitionParameters.INPUT_LEVEL_MAX) + "|"
				+ settings.get(RecognitionParameters.LEVEL_CLIP_SAMPLE) + "|" + settings.get(RecognitionParameters.LEVEL_CLIP_SHARE) + "|"
				+ settings.get(RecognitionParameters.NOTE_ATTACK) + "|" + settings.get(RecognitionParameters.ATTACK_MIN_LEVEL) + "|"
				+ settings.get(RecognitionParameters.ENGINE_PITCH);
	}

	/** How many offline analyses were made so far (a candidate that changes no analysis setting makes none). */
	public long getAnalysesDone() {
		return this.analysesDone.get();
	}

	public CalibrationScore score(RecognitionSettings settings) {
		int count = this.chord.length;
		this.judgement.prepare(settings);
		int correct = 0;
		int wrong = 0;
		int notesAccepted = 0;
		int notesTotal = 0;
		int chordsAccepted = 0;
		int chordsTotal = 0;
		for (int i = 0; i < count; i++) {
			boolean own = this.judgement.accepts(i, i, settings);
			if (own) {
				correct++;
			}
			if (this.chord[i]) {
				chordsTotal++;
				chordsAccepted += own ? 1 : 0;
			} else {
				notesTotal++;
				notesAccepted += own ? 1 : 0;
			}
			if (count > 1 && this.judgement.accepts(i, (i + 1) % count, settings)) {
				wrong++;
			}
		}
		return new CalibrationScore(correct, wrong, new RecognitionRate(notesAccepted, notesTotal, chordsAccepted, chordsTotal));
	}
}
