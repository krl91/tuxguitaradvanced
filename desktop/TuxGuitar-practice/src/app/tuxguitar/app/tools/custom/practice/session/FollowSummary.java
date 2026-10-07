package app.tuxguitar.app.tools.custom.practice.session;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;

/**
 * The summary of a run of the tempo-following mode (spec 002 T099, FR-056): how many events were on time, early, late, wrong, missed or not judged,
 * how many extra notes were played, and, apart, what the techniques said. It is computed from a snapshot, in memory, and is never written anywhere:
 * not in the song, not in a file, not kept for the next run.
 * <p>
 * The three kinds of verdict stay apart as they are in the session: the techniques are never added to the timing counts, and the events that were not
 * judged (a pause, a clock not ready, a window not seen whole) are counted on their own, neither right nor wrong. The sum of the events is the
 * number of events of the run: {@code onTime + early + late + wrong + missed + notJudged + autoPassed + pending}.
 */
public final class FollowSummary {

	private final int events;
	private final int onTime;
	private final int early;
	private final int late;
	private final int wrong;
	private final int missed;
	private final int notJudged;
	private final int autoPassed;
	private final int pending;
	private final int extra;
	private final Map<TechniqueVerdict, Integer> techniqueVerdicts;
	private final Map<PlayingTechnique, Map<TechniqueVerdict, Integer>> techniques;

	private FollowSummary(int events, int onTime, int early, int late, int wrong, int missed, int notJudged, int autoPassed, int pending, int extra,
			Map<TechniqueVerdict, Integer> techniqueVerdicts, Map<PlayingTechnique, Map<TechniqueVerdict, Integer>> techniques) {
		this.events = events;
		this.onTime = onTime;
		this.early = early;
		this.late = late;
		this.wrong = wrong;
		this.missed = missed;
		this.notJudged = notJudged;
		this.autoPassed = autoPassed;
		this.pending = pending;
		this.extra = extra;
		this.techniqueVerdicts = techniqueVerdicts;
		this.techniques = techniques;
	}

	/** The summary of the snapshot: every event once, whatever its status. */
	public static FollowSummary of(FollowSession.Snapshot snapshot) {
		int onTime = 0;
		int early = 0;
		int late = 0;
		int wrong = 0;
		int missed = 0;
		int notJudged = 0;
		int autoPassed = 0;
		int pending = 0;
		Map<TechniqueVerdict, Integer> verdicts = new EnumMap<TechniqueVerdict, Integer>(TechniqueVerdict.class);
		Map<PlayingTechnique, Map<TechniqueVerdict, Integer>> byTechnique = new EnumMap<PlayingTechnique, Map<TechniqueVerdict, Integer>>(PlayingTechnique.class);
		for (TechniqueVerdict verdict : TechniqueVerdict.values()) {
			verdicts.put(verdict, 0);
		}
		List<FollowSession.EventResult> results = snapshot.results();
		for (FollowSession.EventResult result : results) {
			switch (result.status()) {
				case MATCHED:
					if (result.timing() == TimingVerdict.EARLY) {
						early++;
					} else if (result.timing() == TimingVerdict.LATE) {
						late++;
					} else {
						onTime++;
					}
					break;
				case WRONG_PITCH:
					wrong++;
					break;
				case MISSED:
					missed++;
					break;
				case NOT_JUDGED:
					notJudged++;
					break;
				case AUTO_PASSED:
					autoPassed++;
					break;
				default:
					pending++;
					break;
			}
			for (TechniqueResult technique : result.techniques()) {
				verdicts.merge(technique.getVerdict(), 1, Integer::sum);
				byTechnique.computeIfAbsent(technique.getTechnique(), key -> new EnumMap<TechniqueVerdict, Integer>(TechniqueVerdict.class))
						.merge(technique.getVerdict(), 1, Integer::sum);
			}
		}
		return new FollowSummary(results.size(), onTime, early, late, wrong, missed, notJudged, autoPassed, pending, snapshot.extras().size(),
				Collections.unmodifiableMap(verdicts), Collections.unmodifiableMap(byTechnique));
	}

	/** The events of the run, extra notes excluded (they belong to no event). */
	public int events() {
		return this.events;
	}

	public int onTime() {
		return this.onTime;
	}

	public int early() {
		return this.early;
	}

	public int late() {
		return this.late;
	}

	public int wrong() {
		return this.wrong;
	}

	public int missed() {
		return this.missed;
	}

	public int notJudged() {
		return this.notJudged;
	}

	/** Events passed without judging (a dead note, a harmonic...): neither right nor wrong. */
	public int autoPassed() {
		return this.autoPassed;
	}

	/** Events the run never reached (it was stopped first). */
	public int pending() {
		return this.pending;
	}

	/** Notes with a pitch that matched no event. */
	public int extra() {
		return this.extra;
	}

	/** The right notes, on time, early or late. */
	public int rightNotes() {
		return this.onTime + this.early + this.late;
	}

	/** The events that were judged: right, wrong or missed. */
	public int judged() {
		return rightNotes() + this.wrong + this.missed;
	}

	/** What the techniques said, over all the events, by verdict; every verdict is there, with 0 when none. */
	public Map<TechniqueVerdict, Integer> techniqueVerdicts() {
		return this.techniqueVerdicts;
	}

	/** What the techniques said, by technique. */
	public Map<PlayingTechnique, Map<TechniqueVerdict, Integer>> techniques() {
		return this.techniques;
	}

	public int techniqueCount(TechniqueVerdict verdict) {
		return this.techniqueVerdicts.get(verdict);
	}

	/** How many technique results there are in all. */
	public int techniqueTotal() {
		int total = 0;
		for (int count : this.techniqueVerdicts.values()) {
			total += count;
		}
		return total;
	}
}
