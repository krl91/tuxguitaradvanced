package app.tuxguitar.app.tools.custom.practice.session;

import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;

/**
 * The record of one run of Follow (spec 016 FR-003, contracts/run-record.md): a CSV text, per event, of what was expected, what was heard and the
 * reason of its verdict, with the settings of the run in its header. Pure: it builds the text from a finished run and writes nothing; the window
 * writes it, only when the learner asked for it. Times are in milliseconds from the start of the timeline (the target of the first event less its
 * time on the timeline), with a dot for the decimals.
 */
public final class FollowRunRecord {

	public static final String FIRST_LINE = "# follow-run-record 1";
	public static final String COLUMNS = "index,kind,expected,target_ms,status,timing,offset_ms,reason,detail,attack_ms,heard,confidence,level_dbfs";

	/** The settings and the alignment of the run, for the header. */
	public static final class Header {
		final String score;
		final int track;
		final int percent;
		final int windowMillis;
		final int playTimeMillis;
		final String pitchPosition;
		final String input;
		final String output;
		final String format;
		final String calibration;
		final double offsetMillis;
		final String audio;
		final int sampleRate;
		final double firstSampleMillis;
		final boolean audioTruncated;

		/**
		 * @param windowMillis the timing window of the run, in ms each side at 120 beats a minute
		 * @param playTimeMillis the time to play of the run, or a negative number when the run judged with the timing window (before spec 016 story 3)
		 * @param pitchPosition the pitch position of the run, or an empty text
		 * @param calibration NONE, CALIBRATED or MANUAL
		 * @param audio the name of the audio copy, or null when there is none
		 * @param firstSampleMillis the time of the first recorded sample on the timeline (negative during the count-in)
		 */
		public Header(String score, int track, int percent, int windowMillis, int playTimeMillis, String pitchPosition, String input, String output, String format,
				String calibration, double offsetMillis, String audio, int sampleRate, double firstSampleMillis, boolean audioTruncated) {
			this.score = score;
			this.track = track;
			this.percent = percent;
			this.windowMillis = windowMillis;
			this.playTimeMillis = playTimeMillis;
			this.pitchPosition = pitchPosition;
			this.input = input;
			this.output = output;
			this.format = format;
			this.calibration = calibration;
			this.offsetMillis = offsetMillis;
			this.audio = audio;
			this.sampleRate = sampleRate;
			this.firstSampleMillis = firstSampleMillis;
			this.audioTruncated = audioTruncated;
		}
	}

	private FollowRunRecord() {
	}

	/**
	 * @param results the results of the finished run, in the order of the events
	 * @param targetMillis the target of each event on the timeline
	 * @param heard what was heard for each event (see {@link FollowSession#heard(int)}), with its attack on the timeline; null when nothing
	 */
	public static String text(Header header, List<PracticeEvent> events, List<FollowSession.EventResult> results, IntFunction<Double> targetMillis,
			IntFunction<FollowSession.Heard> heard, long timelineOriginWall) {
		StringBuilder text = new StringBuilder();
		text.append(FIRST_LINE).append('\n');
		text.append("# score=").append(clean(header.score)).append(",track=").append(header.track).append(",percent=").append(header.percent)
					.append(",window_ms=").append(header.windowMillis)
				.append(",play_time_ms=").append(header.playTimeMillis >= 0 ? Integer.toString(header.playTimeMillis) : "")
				.append(",pitch_position=").append(clean(header.pitchPosition)).append('\n');
		text.append("# route=").append(clean(header.input)).append('|').append(clean(header.output)).append('|').append(clean(header.format))
				.append(",calibration=").append(clean(header.calibration)).append(",offset_ms=").append(number(header.offsetMillis)).append('\n');
		text.append("# audio=").append(header.audio != null ? clean(header.audio) : "none").append(",sample_rate=").append(header.sampleRate)
				.append(",first_sample_ms=").append(number(header.firstSampleMillis)).append(",audio_truncated=").append(header.audioTruncated).append('\n');
		text.append(COLUMNS).append('\n');
		for (int i = 0; i < results.size(); i++) {
			FollowSession.EventResult result = results.get(i);
			PracticeEvent event = events.get(i);
			double target = targetMillis.apply(i);
			FollowSession.Heard h = heard.apply(i);
			boolean timed = result.timing() != null && result.timing() != TimingVerdict.MISSED;
			String attack = "";
			String pitch = "";
			String confidence = "";
			String level = "";
			if (h != null) {
				attack = number((h.attackWall() - timelineOriginWall) / 1e6);
				pitch = (h.midi() != Integer.MIN_VALUE ? Integer.toString(h.midi()) : "");
				confidence = String.format(Locale.ROOT, "%.2f", h.confidence());
				level = (Double.isFinite(h.levelDbfs()) ? number(h.levelDbfs()) : "");
			} else if (timed) {
				attack = number(target + result.offsetNanos() / 1e6);
			}
			if (pitch.isEmpty() && result.status() == FollowSession.Status.MATCHED) {
				pitch = expected(event);
			}
			text.append(i).append(',').append(event.getKind().name()).append(',').append(expected(event)).append(',').append(number(target)).append(',')
					.append(result.status().name()).append(',').append(result.timing() != null ? result.timing().name() : "").append(',')
					.append(timed ? number(result.offsetNanos() / 1e6) : "").append(',').append(result.reason().name()).append(',')
					.append(detail(result)).append(',')
					.append(attack).append(',').append(pitch).append(',').append(confidence).append(',').append(level).append('\n');
		}
		return text.toString();
	}

	/** The detail of the reason, for the reasons that have one (the pitch heard, the offset, the certainty); empty otherwise. */
	private static String detail(FollowSession.EventResult result) {
		switch (result.reason()) {
			case OTHER_PITCH:
				return result.reasonDetail() != Integer.MIN_VALUE ? Long.toString(result.reasonDetail()) : "";
			case EARLY_OUTSIDE:
			case LATE_OUTSIDE:
			case UNCLEAR:
				return Long.toString(result.reasonDetail());
			default:
				return "";
		}
	}

	private static String expected(PracticeEvent event) {
		if (event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD) {
			StringBuilder pitches = new StringBuilder();
			for (int midi : event.getRequiredPitches()) {
				pitches.append(pitches.length() > 0 ? " " : "").append(midi);
			}
			return pitches.toString();
		}
		return event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ? Integer.toString(event.getExpectedMidi()) : "";
	}

	/** A dot for the decimals, whatever the language of the computer. */
	static String number(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	/** A text of the header without the separators of the file. */
	private static String clean(String text) {
		return text == null ? "" : text.replace(',', ' ').replace('|', ' ').replace('\n', ' ').replace('\r', ' ');
	}
}
