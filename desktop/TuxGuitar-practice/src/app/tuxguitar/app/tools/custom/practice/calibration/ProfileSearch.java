package app.tuxguitar.app.tools.custom.practice.calibration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

import app.tuxguitar.audio.engine.settings.ProfileParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Looks for the settings that recognise the recorded piece best (spec 013, FR-006, contract scoring-search): a deterministic coordinate descent. It starts
 * from the default settings, which are always the first candidate; for each profile parameter, in a fixed order, it tries up to four values around the
 * current one (two steps each side) and keeps the best (on a tie, the nearest the default); it repeats with a narrower step while a pass improves, and
 * stops at its budget or on a cancel. It leaves the default only for settings scoring at least one point above it.
 */
public final class ProfileSearch {

	public static final int BUDGET_CANDIDATES = 2000;
	/** about two minutes */
	public static final long BUDGET_MILLIS = 120_000L;
	/** the first step is an eighth of the range of the parameter; each pass halves it */
	private static final double FIRST_STEP_DIVISOR = 8.0;
	private static final int MAX_PASSES = 3;
	/** the gain, in points of score, below which the default is kept */
	public static final double MINIMUM_GAIN = 1.0;

	/** Scores one candidate on the recording. */
	public interface Scoring {
		CalibrationScore score(RecognitionSettings settings);
	}

	/** Told after each candidate: how many were scored and the budget. */
	public interface Progress {
		void progress(int candidates, int budget);
	}

	/** One line of the report: a profile parameter, the default value, the value found, and whether it changed. */
	public static final class Line {
		private final String key;
		private final double defaultValue;
		private final double foundValue;

		Line(String key, double defaultValue, double foundValue) {
			this.key = key;
			this.defaultValue = defaultValue;
			this.foundValue = foundValue;
		}

		public String getKey() {
			return this.key;
		}

		public double getDefaultValue() {
			return this.defaultValue;
		}

		public double getFoundValue() {
			return this.foundValue;
		}

		public boolean isChanged() {
			return this.defaultValue != this.foundValue;
		}
	}

	public static final class Result {
		private final RecognitionSettings best;
		private final CalibrationScore bestScore;
		private final CalibrationScore defaultScore;
		private final boolean betterThanDefault;
		private final boolean stoppedAtLimit;
		private final boolean cancelled;
		private final int candidates;
		private final List<Line> report;

		Result(RecognitionSettings best, CalibrationScore bestScore, CalibrationScore defaultScore, boolean betterThanDefault, boolean stoppedAtLimit,
				boolean cancelled, int candidates, List<Line> report) {
			this.best = best;
			this.bestScore = bestScore;
			this.defaultScore = defaultScore;
			this.betterThanDefault = betterThanDefault;
			this.stoppedAtLimit = stoppedAtLimit;
			this.cancelled = cancelled;
			this.candidates = candidates;
			this.report = Collections.unmodifiableList(report);
		}

		/** The settings found, or the default settings when nothing scored at least one point more. */
		public RecognitionSettings getBest() {
			return this.best;
		}

		public CalibrationScore getBestScore() {
			return this.bestScore;
		}

		public CalibrationScore getDefaultScore() {
			return this.defaultScore;
		}

		public boolean isBetterThanDefault() {
			return this.betterThanDefault;
		}

		/** The budget (time or candidates) ended the search before it converged; the best found so far is kept. */
		public boolean isStoppedAtLimit() {
			return this.stoppedAtLimit;
		}

		public boolean isCancelled() {
			return this.cancelled;
		}

		public int getCandidates() {
			return this.candidates;
		}

		public List<Line> getReport() {
			return this.report;
		}
	}

	private final Scoring scoring;
	private final int maxCandidates;
	private final long maxMillis;
	private final LongSupplier nanoClock;
	private volatile boolean cancelled;

	public ProfileSearch(Scoring scoring) {
		this(scoring, BUDGET_CANDIDATES, BUDGET_MILLIS, System::nanoTime);
	}

	public ProfileSearch(Scoring scoring, int maxCandidates, long maxMillis, LongSupplier nanoClock) {
		this.scoring = scoring;
		this.maxCandidates = maxCandidates;
		this.maxMillis = maxMillis;
		this.nanoClock = nanoClock;
	}

	/** Ends the search before the next candidate is scored. */
	public void cancel() {
		this.cancelled = true;
	}

	public Result run(RecognitionSettings start, Progress progress) {
		long began = this.nanoClock.getAsLong();
		int count = 0;
		CalibrationScore defaultScore = this.scoring.score(start);
		count++;
		if (progress != null) {
			progress.progress(count, this.maxCandidates);
		}
		RecognitionSettings best = start;
		CalibrationScore bestScore = defaultScore;
		boolean stopped = false;
		boolean wasCancelled = false;
		search:
		for (int pass = 0; pass < MAX_PASSES; pass++) {
			boolean improved = false;
			for (RecognitionParameter.Number parameter : ProfileParameters.LIST) {
				RecognitionSettings chosen = null;
				CalibrationScore chosenScore = null;
				double chosenValue = 0;
				double base = start.get(parameter);
				for (double value : values(parameter, best.get(parameter), pass)) {
					if (this.cancelled) {
						wasCancelled = true;
						break search;
					}
					if (count >= this.maxCandidates || (this.nanoClock.getAsLong() - began) / 1_000_000L >= this.maxMillis) {
						stopped = true;
						break search;
					}
					RecognitionSettings candidate = best.with(parameter, value);
					CalibrationScore score = this.scoring.score(candidate);
					count++;
					if (progress != null) {
						progress.progress(count, this.maxCandidates);
					}
					if (chosen == null || better(score, value, chosenScore, chosenValue, base)) {
						chosen = candidate;
						chosenScore = score;
						chosenValue = value;
					}
				}
				if (chosen != null && better(chosenScore, chosenValue, bestScore, best.get(parameter), base)) {
					best = chosen;
					bestScore = chosenScore;
					improved = true;
				}
			}
			if (!improved) {
				break;
			}
		}
		boolean leaves = best != start && bestScore.getObjective() >= defaultScore.getObjective() + MINIMUM_GAIN;
		RecognitionSettings result = leaves ? best : start;
		CalibrationScore resultScore = leaves ? bestScore : defaultScore;
		List<Line> report = new ArrayList<Line>();
		for (RecognitionParameter.Number parameter : ProfileParameters.LIST) {
			report.add(new Line(parameter.getKey(), start.get(parameter), result.get(parameter)));
		}
		return new Result(result, resultScore, defaultScore, leaves, stopped, wasCancelled, count, report);
	}

	/** A higher score; on equal scores, the value nearer the default. */
	private static boolean better(CalibrationScore candidate, double candidateValue, CalibrationScore current, double currentValue, double defaultValue) {
		if (candidate.getObjective() != current.getObjective()) {
			return candidate.getObjective() > current.getObjective();
		}
		return Math.abs(candidateValue - defaultValue) < Math.abs(currentValue - defaultValue);
	}

	/** Up to four values around {@code current}, two steps each side, the step narrowing with the pass; inside the bounds, whole where the parameter is. */
	private static List<Double> values(RecognitionParameter.Number parameter, double current, int pass) {
		double range = parameter.getMaximum() - parameter.getMinimum();
		double step = range / (FIRST_STEP_DIVISOR * (1 << pass));
		if (parameter.isInteger()) {
			step = Math.max(1.0, Math.rint(step));
		}
		List<Double> values = new ArrayList<Double>();
		for (int k = -2; k <= 2; k++) {
			if (k == 0) {
				continue;
			}
			double value = Math.max(parameter.getMinimum(), Math.min(parameter.getMaximum(), current + k * step));
			if (parameter.isInteger()) {
				value = Math.rint(value);
			}
			if (value != current && !values.contains(value)) {
				values.add(value);
			}
		}
		return values;
	}
}
