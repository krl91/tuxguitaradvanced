package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.List;
import java.util.Map;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.app.tools.custom.practice.session.FollowLeniencyPosition;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.FollowSummary;
import app.tuxguitar.app.tools.custom.practice.session.RefusalReason;
import app.tuxguitar.app.tools.custom.practice.session.TimingContext;
import app.tuxguitar.app.tools.custom.practice.session.TimingVerdict;

/**
 * What the palette says about a run of the tempo-following mode (spec 002 T098, T099): the feedback of the latest event, the state, the counts, the
 * rhythmic precision, the techniques apart and, at the end, the summary. It is text and nothing else, so that every language and every state can be
 * tested without a window; the palette only puts it on its labels.
 * <p>
 * The kinds of feedback stay apart: the timing and the pitch of an event are one line, the techniques another, and an event that was not judged is
 * said to be not judged, never missed.
 */
public final class FollowView {

	/** The localized text of a key with its arguments. */
	public interface Text {
		String get(String key, String... arguments);
	}

	/** What kind of symbol the latest feedback gets: a good result, a bad one, or neither. */
	public enum Tone {
		NONE,
		GOOD,
		BAD,
		NEUTRAL
	}

	private final Tone tone;
	private final String latest;
	private final String state;
	private final String next;
	private final String counts;
	private final String precision;
	private final String techniques;
	private final String summary;
	private final int latestEvent;
	private final int latestExtras;
	private final String reason;

	private FollowView(Tone tone, String latest, String reason, String state, String next, String counts, String precision, String techniques, String summary,
			int latestEvent, int latestExtras) {
		this.tone = tone;
		this.latest = latest;
		this.reason = reason;
		this.state = state;
		this.next = next;
		this.counts = counts;
		this.precision = precision;
		this.techniques = techniques;
		this.summary = summary;
		this.latestEvent = latestEvent;
		this.latestExtras = latestExtras;
	}

	/**
	 * @param events the events of the run, in the order of the snapshot's results
	 * @param shownEvent the index of the event whose feedback the palette showed last, or -1
	 * @param shownExtras the number of extra notes it showed last
	 */
	public static FollowView of(FollowSession.Snapshot snapshot, List<PracticeEvent> events, int shownEvent, int shownExtras, Text text) {
		return of(snapshot, events, shownEvent, shownExtras, text, null, -1);
	}

	/**
	 * The same, for a run of Follow (spec 016 FR-007): its summary names the position of the permissiveness and the time to play.
	 *
	 * @param position null for a run prepared without one: no such line
	 */
	public static FollowView of(FollowSession.Snapshot snapshot, List<PracticeEvent> events, int shownEvent, int shownExtras, Text text,
			FollowLeniencyPosition position, int playTimeMillis) {
		int latestEvent = -1;
		List<FollowSession.EventResult> results = snapshot.results();
		for (int i = 0; i < results.size(); i++) {
			switch (results.get(i).status()) {
				case MATCHED:
				case WRONG_PITCH:
				case MISSED:
				case NOT_JUDGED:
					latestEvent = i;
					break;
				default:
					break;
			}
		}
		int extras = snapshot.extras().size();
		Tone tone = Tone.NONE;
		String latest = " ";
		String reasonLine = "";
		if (extras > shownExtras && latestEvent == shownEvent) {
			tone = Tone.BAD;
			latest = text.get("practice.follow.event.EXTRA");
		} else if (latestEvent >= 0) {
			FollowSession.EventResult result = results.get(latestEvent);
			reasonLine = reasonText(result, events.get(latestEvent), text);
			switch (result.status()) {
				case MATCHED:
					tone = Tone.GOOD;
					latest = matchedText(result, text);
					break;
				case WRONG_PITCH:
					tone = Tone.BAD;
					latest = text.get("practice.follow.event.WRONG_PITCH");
					if (result.missingPitches().length > 0) {
						latest += " " + text.get("practice.follow.event.missing", names(result.missingPitches()));
					}
					break;
				case MISSED:
					tone = Tone.BAD;
					latest = text.get("practice.follow.event.MISSED");
					break;
				default:
					tone = Tone.NEUTRAL;
					latest = text.get("practice.follow.event.NOT_JUDGED");
					break;
			}
		} else if (extras > 0) {
			tone = Tone.BAD;
			latest = text.get("practice.follow.event.EXTRA");
		}

		String next = "";
		int open = snapshot.openEvent();
		if (open >= 0 && open < events.size() && (snapshot.state() == FollowSession.State.FOLLOWING || snapshot.state() == FollowSession.State.COUNT_IN)) {
			next = text.get("practice.follow.event.waiting", expectedText(events.get(open)));
		}
		StringBuilder state = new StringBuilder(text.get("practice.follow.state." + snapshot.state().name()));
		if (snapshot.state() == FollowSession.State.PAUSED) {
			StringBuilder reasons = new StringBuilder();
			for (app.tuxguitar.app.tools.custom.practice.session.PracticeSession.PauseReason reason : snapshot.pauseReasons()) {
				reasons.append(reasons.length() > 0 ? ", " : "").append(text.get("practice.pause." + reason.name()));
			}
			state.append(' ').append(reasons);
		}
		FollowSession.Counts counts = snapshot.counts();
		StringBuilder countsLine = new StringBuilder(text.get("practice.follow.counts", Integer.toString(counts.onTime()), Integer.toString(counts.early()),
				Integer.toString(counts.late()), Integer.toString(counts.wrongPitch()), Integer.toString(counts.missed()), Integer.toString(counts.extra())));
		if (counts.notJudged() > 0) {
			countsLine.append(" · ").append(text.get("practice.follow.counts.not-judged", Integer.toString(counts.notJudged())));
		}
		String precision = precisionText(snapshot, text);
		// a technique is judged when its note is over (a bend is observed for a second or more): the line is that of the latest event that has results
		String techniques = " ";
		for (int i = results.size() - 1; i >= 0; i--) {
			if (!results.get(i).techniques().isEmpty()) {
				techniques = TechniqueLine.of(results.get(i).techniques(), key -> text.get(key));
				break;
			}
		}
		String summary = "";
		if (snapshot.state() == FollowSession.State.COMPLETE || snapshot.state() == FollowSession.State.STOPPED) {
			summary = summaryText(FollowSummary.of(snapshot), text, position, playTimeMillis);
		}
		return new FollowView(tone, latest, reasonLine, state.toString(), next, countsLine.toString(), precision, techniques, summary, latestEvent, extras);
	}

	/**
	 * Why the event was not accepted on time (spec 016 FR-001), in the words of a guitarist; empty for an event accepted. A reason with a detail always
	 * shows it: the note heard and the one expected, the milliseconds, the certainty.
	 */
	public static String reasonText(FollowSession.EventResult result, PracticeEvent event, Text text) {
		RefusalReason reason = result.reason();
		long detail = result.reasonDetail();
		switch (reason) {
			case NONE:
				return "";
			case OTHER_PITCH:
				if (detail == Integer.MIN_VALUE) {
					return text.get("practice.follow.reason.OTHER_PITCH_UNNAMED", "", expectedText(event));
				}
				return text.get("practice.follow.reason.OTHER_PITCH", PracticePalette.noteName((int) detail), expectedText(event));
			case EARLY_OUTSIDE:
			case LATE_OUTSIDE:
			case UNCLEAR:
				return text.get("practice.follow.reason." + reason.name(), Long.toString(Math.abs(detail)));
			default:
				return text.get("practice.follow.reason." + reason.name());
		}
	}

	private static String matchedText(FollowSession.EventResult result, Text text) {
		if (result.timing() == TimingVerdict.EARLY || result.timing() == TimingVerdict.LATE) {
			return text.get("practice.follow.event." + result.timing().name(), Long.toString(Math.round(Math.abs(result.offsetNanos()) / 1e6)));
		}
		return text.get("practice.follow.event.ON_TIME");
	}

	private static String precisionText(FollowSession.Snapshot snapshot, Text text) {
		if (snapshot.precision() == TimingContext.RhythmPrecision.NORMAL) {
			return "";
		}
		if (snapshot.precision() == TimingContext.RhythmPrecision.NOT_READY) {
			return text.get("practice.follow.precision.NOT_READY");
		}
		StringBuilder line = new StringBuilder(text.get("practice.follow.precision.reduced"));
		for (TimingContext.PrecisionReason reason : snapshot.precisionReasons()) {
			line.append(". ").append(text.get("practice.follow.precision.reason." + reason.name()));
		}
		return line.toString();
	}

	/** The summary of the run: every verdict apart, then the techniques apart (T099). */
	public static String summaryText(FollowSummary summary, Text text) {
		StringBuilder lines = new StringBuilder(text.get("practice.follow.summary.title"));
		line(lines, text.get("practice.follow.summary.events", Integer.toString(summary.events())));
		line(lines, text.get("practice.follow.summary.on-time", Integer.toString(summary.onTime())));
		line(lines, text.get("practice.follow.summary.early", Integer.toString(summary.early())));
		line(lines, text.get("practice.follow.summary.late", Integer.toString(summary.late())));
		line(lines, text.get("practice.follow.summary.wrong", Integer.toString(summary.wrong())));
		line(lines, text.get("practice.follow.summary.missed", Integer.toString(summary.missed())));
		line(lines, text.get("practice.follow.summary.extra", Integer.toString(summary.extra())));
		if (summary.notJudged() > 0) {
			line(lines, text.get("practice.follow.summary.not-judged", Integer.toString(summary.notJudged())));
		}
		if (summary.autoPassed() > 0) {
			line(lines, text.get("practice.follow.summary.auto-passed", Integer.toString(summary.autoPassed())));
		}
		for (RefusalReason reason : RefusalReason.values()) {
			if (summary.reasonCount(reason) > 0) {
				line(lines, text.get("practice.follow.summary.reason." + reason.name(), Integer.toString(summary.reasonCount(reason))));
			}
		}
		if (summary.techniqueTotal() > 0) {
			StringBuilder techniques = new StringBuilder();
			for (Map.Entry<PlayingTechnique, Map<TechniqueVerdict, Integer>> technique : summary.techniques().entrySet()) {
				for (Map.Entry<TechniqueVerdict, Integer> verdict : technique.getValue().entrySet()) {
					techniques.append(techniques.length() > 0 ? TechniqueLine.SEPARATOR : "").append(text.get("practice.technique.name." + technique.getKey().name()))
							.append(' ').append(text.get("practice.technique.verdict." + verdict.getKey().name())).append(" ×").append(verdict.getValue());
				}
			}
			line(lines, text.get("practice.follow.summary.techniques", techniques.toString()));
		}
		line(lines, text.get("practice.follow.summary.memory"));
		return lines.toString();
	}

	/** The summary, and for a run of Follow (spec 016 FR-007) one more line: the position of the permissiveness and the time to play; none without a position. */
	public static String summaryText(FollowSummary summary, Text text, FollowLeniencyPosition position, int playTimeMillis) {
		String lines = summaryText(summary, text);
		if (position == null) {
			return lines;
		}
		return lines + "\n" + text.get("practice.follow.summary.settings", text.get("practice.follow.leniency." + position.name().toLowerCase(java.util.Locale.ROOT)),
				Integer.toString(playTimeMillis));
	}

	private static void line(StringBuilder lines, String text) {
		lines.append('\n').append(text);
	}

	private static String expectedText(PracticeEvent event) {
		return event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD ? PracticePalette.chordText(event) : PracticePalette.noteName(event.getExpectedMidi());
	}

	private static String names(int[] pitches) {
		StringBuilder text = new StringBuilder();
		for (int midi : pitches) {
			text.append(text.length() > 0 ? " " : "").append(PracticePalette.noteName(midi));
		}
		return text.toString();
	}

	public Tone tone() {
		return this.tone;
	}

	/** The feedback of the latest event (or extra note): the big symbol and its words. */
	public String latest() {
		return this.latest;
	}

	/** Why the latest event was not accepted on time (spec 016), or an empty text. */
	public String reason() {
		return this.reason;
	}

	public String state() {
		return this.state;
	}

	/** The next event to play, or an empty text. */
	public String next() {
		return this.next;
	}

	public String counts() {
		return this.counts;
	}

	/** "Reduced rhythmic precision" with its reasons, the clocks not ready, or an empty text. */
	public String precision() {
		return this.precision;
	}

	/** The techniques of the latest event, apart from the pitch and the timing. */
	public String techniques() {
		return this.techniques;
	}

	/** The summary, once the run is complete or stopped; empty before. */
	public String summary() {
		return this.summary;
	}

	/** The index of the event whose feedback is shown. */
	public int latestEvent() {
		return this.latestEvent;
	}

	/** The number of extra notes counted when this view was made. */
	public int latestExtras() {
		return this.latestExtras;
	}
}
