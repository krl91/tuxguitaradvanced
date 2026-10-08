package app.tuxguitar.app.tools.custom.practice.arcade.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.FollowSummary;

/**
 * The score of an Arcade run, computed from the snapshots of the tempo-following session (spec 003 FR-020 to FR-023, research R5, R6): the verdict of
 * every event, the points, the streak and the best streak. It has no clock, no thread and no input: it never makes the session wait (the session
 * judges every event on its own window), and it never changes a verdict of the session.
 * <p>
 * {@link #update} is a pure function of the snapshot: the same snapshot gives the same view, whatever was asked before. The events are taken in the
 * order of the sequence; an event the session has not resolved yet (WAITING) is skipped, and an event that the session upgrades after the fact (a note
 * heard just after its window closed) changes the view of the next snapshot, never an earlier one.
 */
public final class ArcadeRun {

	/** What became of one event, its points and the streak after it. Immutable. */
	public static final class EventState {
		private final int eventIndex;
		private final ArcadeVerdict verdict;
		private final long offsetNanos;
		private final int points;
		private final int streakAfter;
		private final List<TechniqueResult> techniques;
		private final int[] missingPitches;

		public EventState(int eventIndex, ArcadeVerdict verdict, long offsetNanos, int points, int streakAfter, List<TechniqueResult> techniques,
				int[] missingPitches) {
			this.eventIndex = eventIndex;
			this.verdict = verdict;
			this.offsetNanos = offsetNanos;
			this.points = points;
			this.streakAfter = streakAfter;
			this.techniques = techniques;
			this.missingPitches = missingPitches.clone();
		}

		public int eventIndex() {
			return this.eventIndex;
		}

		public ArcadeVerdict verdict() {
			return this.verdict;
		}

		/** Offset of the note from its time, negative when early; 0 when there was no note. */
		public long offsetNanos() {
			return this.offsetNanos;
		}

		public int points() {
			return this.points;
		}

		public int streakAfter() {
			return this.streakAfter;
		}

		/** The techniques of the event, apart: they never change the verdict, the points or the streak. */
		public List<TechniqueResult> techniques() {
			return this.techniques;
		}

		/** The pitches (MIDI) of a chord that the session named as not heard, in its order; empty otherwise. Shown by name, at chord level only. */
		public int[] missingPitches() {
			return this.missingPitches.clone();
		}
	}

	/** The state of a run at a snapshot. Immutable. */
	public static final class View {
		private final List<EventState> states;
		private final int score;
		private final int streak;
		private final int bestStreak;
		private final int extras;
		private final int judged;

		public View(List<EventState> states, int score, int streak, int bestStreak, int extras, int judged) {
			this.states = Collections.unmodifiableList(states);
			this.score = score;
			this.streak = streak;
			this.bestStreak = bestStreak;
			this.extras = extras;
			this.judged = judged;
		}

		/** One state per event of the sequence, in its order (WAITING ones included). */
		public List<EventState> states() {
			return this.states;
		}

		/** The score so far: kept in memory, not shown while playing (FR-021). */
		public int score() {
			return this.score;
		}

		/** The streak of good notes now, shown while playing. */
		public int streak() {
			return this.streak;
		}

		public int bestStreak() {
			return this.bestStreak;
		}

		/** Notes with a pitch that matched no event; they cost nothing. */
		public int extras() {
			return this.extras;
		}

		/** The events judged so far: right, wrong or missed. */
		public int judged() {
			return this.judged;
		}
	}

	private final PracticeSequence sequence;
	private final ArcadeScoring scoring;

	public ArcadeRun(PracticeSequence sequence, ArcadeScoring scoring) {
		this.sequence = sequence;
		this.scoring = scoring;
	}

	public View update(FollowSession.Snapshot snapshot) {
		List<PracticeEvent> events = this.sequence.getEvents();
		List<FollowSession.EventResult> results = snapshot.results();
		List<EventState> states = new ArrayList<EventState>();
		int score = 0;
		int streak = 0;
		int best = 0;
		int judged = 0;
		for (int i = 0; i < results.size() && i < events.size(); i++) {
			FollowSession.EventResult result = results.get(i);
			ArcadeVerdict verdict = ArcadeVerdict.of(result, events.get(i));
			int points = 0;
			if (this.scoring.isJudged(verdict)) {
				judged++;
				points = this.scoring.eventScore(verdict, streak);
				score += points;
				streak = this.scoring.extendsStreak(verdict) ? streak + 1 : 0;
				best = Math.max(best, streak);
			}
			states.add(new EventState(i, verdict, verdict == ArcadeVerdict.EARLY || verdict == ArcadeVerdict.LATE || verdict == ArcadeVerdict.ON_TIME ? result.offsetNanos() : 0L,
					points, streak, result.techniques(), result.missingPitches()));
		}
		return new View(states, score, streak, best, snapshot.extras().size(), judged);
	}

	/** The result at the end of the run, from its last snapshot. */
	public ArcadeResult result(FollowSession.Snapshot last) {
		View view = update(last);
		return new ArcadeResult(view.score(), this.scoring.maximum(view.judged()), view.bestStreak(), FollowSummary.of(last));
	}
}
