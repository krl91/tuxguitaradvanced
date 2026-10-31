package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSnapshot;

/**
 * The streak of right notes the score fire grows with (spec 019 FR-003, research R3, data model "ScoreStreak"), read from what the window already takes from
 * each snapshot, and never written back: the verdicts are the sessions' own (FR-008).
 * <ul>
 * <li>Wait: each new acceptance is a right note; a skip is a mistake; an automatic pass changes nothing; a wrong verdict is a mistake, counted once while it
 * is shown.</li>
 * <li>Follow: each result is counted once, when it leaves PENDING: MATCHED (on time, early or late) is right, WRONG_PITCH and MISSED are mistakes.</li>
 * </ul>
 * Each right note leaves a burst of sparks with the streak it made. UI thread only.
 */
public final class ScoreStreak {

	private int streak;
	private int judged;
	private int acceptedSeen;
	private Object passedSeen;
	private boolean wrongShown;
	private final Set<Integer> resultsSeen = new HashSet<Integer>();
	private final List<Integer> bursts = new ArrayList<Integer>();

	/** A new run: no streak, nothing seen, no burst. */
	public void reset() {
		this.streak = 0;
		this.judged = 0;
		this.acceptedSeen = 0;
		this.passedSeen = null;
		this.wrongShown = false;
		this.resultsSeen.clear();
		this.bursts.clear();
	}

	/** The right notes in a row now. */
	public int streak() {
		return this.streak;
	}

	/** The notes judged right or wrong in this run so far. */
	public int judged() {
		return this.judged;
	}

	/** The streak of each right note since the last call, oldest first; they are not given again. */
	public List<Integer> takeBursts() {
		List<Integer> taken = new ArrayList<Integer>(this.bursts);
		this.bursts.clear();
		return taken;
	}

	/** What the window reads from a Wait snapshot. */
	public void waitSnapshot(PracticeSnapshot snapshot) {
		waitSnapshot(snapshot.getAcceptedCount(), snapshot.getLastPassed(), snapshot.getLastPassedOutcome(), snapshot.getVerdict());
	}

	/**
	 * A Wait snapshot, by what the window reads from it.
	 *
	 * @param acceptedCount the acceptances of the run so far
	 * @param lastPassed the last event skipped or passed automatically, compared by identity; null for none
	 * @param lastPassedOutcome SKIPPED or AUTO_PASSED for it
	 * @param verdict the check or cross shown now
	 */
	public void waitSnapshot(int acceptedCount, Object lastPassed, PracticeSession.Outcome lastPassedOutcome, PracticeSession.Verdict verdict) {
		if (acceptedCount < this.acceptedSeen) {
			// the session started again (Restart): its count starts again, and so does the streak
			this.acceptedSeen = acceptedCount;
			this.streak = 0;
		}
		while (this.acceptedSeen < acceptedCount) {
			this.acceptedSeen++;
			right();
			this.wrongShown = false;
		}
		if (lastPassed != null && lastPassed != this.passedSeen) {
			this.passedSeen = lastPassed;
			if (lastPassedOutcome == PracticeSession.Outcome.SKIPPED) {
				wrong();
			}
			this.wrongShown = false;
		}
		boolean wrong = verdict == PracticeSession.Verdict.WRONG;
		if (wrong && !this.wrongShown) {
			wrong();
		}
		this.wrongShown = wrong;
	}

	/** A result of a Follow snapshot; a result seen again counts nothing. */
	public void followResult(int eventIndex, FollowSession.Status status) {
		if (status == FollowSession.Status.PENDING || this.resultsSeen.contains(eventIndex)) {
			return;
		}
		this.resultsSeen.add(eventIndex);
		if (status == FollowSession.Status.MATCHED) {
			right();
		} else if (status == FollowSession.Status.WRONG_PITCH || status == FollowSession.Status.MISSED) {
			wrong();
		}
	}

	/** Every result of a Follow snapshot. */
	public void followSnapshot(FollowSession.Snapshot snapshot) {
		for (FollowSession.EventResult result : snapshot.results()) {
			followResult(result.eventIndex(), result.status());
		}
	}

	private void right() {
		this.streak++;
		this.judged++;
		this.bursts.add(this.streak);
	}

	private void wrong() {
		this.streak = 0;
		this.judged++;
	}
}
