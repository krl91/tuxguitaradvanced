package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniencyPosition;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevel;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSummary;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;

/**
 * What the result window says (spec 003 FR-023, FR-026, T054): the score, its share of the maximum, the best streak, the counts per judgement and, apart, the
 * techniques. Pure: the same result gives the same lines, in the language of the texts it is given. A judgement that did not happen is not listed (no line
 * for "not judged" when every event was judged); the techniques never enter the score and are said to be apart.
 */
public final class ArcadeResultText {

	/** A line of the result: a label and a value. */
	public static final class Line {
		private final String label;
		private final String value;

		Line(String label, String value) {
			this.label = label;
			this.value = value;
		}

		public String label() {
			return this.label;
		}

		public String value() {
			return this.value;
		}

		@Override
		public String toString() {
			return this.label + ": " + this.value;
		}
	}

	private ArcadeResultText() {
	}

	public static List<Line> of(ArcadeResult result, ArcadeTexts texts) {
		List<Line> lines = new ArrayList<Line>();
		FollowSummary summary = result.summary();
		lines.add(new Line(texts.get("arcade.result.score"), Integer.toString(result.score())));
		lines.add(new Line(texts.get("arcade.result.percent"), texts.get("arcade.result.percent.value", Integer.toString(result.percent()))));
		lines.add(new Line(texts.get("arcade.result.best"), Integer.toString(result.bestStreak())));
		lines.add(new Line(texts.get("arcade.result.ON_TIME"), Integer.toString(summary.onTime())));
		lines.add(new Line(texts.get("arcade.result.EARLY"), Integer.toString(summary.early())));
		lines.add(new Line(texts.get("arcade.result.LATE"), Integer.toString(summary.late())));
		lines.add(new Line(texts.get("arcade.result.WRONG"), Integer.toString(summary.wrong())));
		lines.add(new Line(texts.get("arcade.result.MISSED"), Integer.toString(summary.missed())));
		if (summary.notJudged() > 0) {
			lines.add(new Line(texts.get("arcade.result.NOT_JUDGED"), Integer.toString(summary.notJudged())));
		}
		if (summary.extra() > 0) {
			lines.add(new Line(texts.get("arcade.result.EXTRA"), Integer.toString(summary.extra())));
		}
		return lines;
	}

	/**
	 * The techniques, apart from the score: one line for each technique that was written, with what was heard of it, in the words the practice window already
	 * uses ({@code practice.technique.*}); empty when none was written. They never change the score, the streak or a verdict.
	 */
	/** The level and the leniency position the run was played at (spec 014, FR-010): shown first, so that two runs can be compared fairly. */
	public static List<Line> settings(ArcadeLevel level, ArcadeLeniencyPosition position, ArcadeTexts texts) {
		List<Line> lines = new ArrayList<Line>();
		lines.add(new Line(texts.get("arcade.result.level"),
				texts.get("arcade.result.level.value", Integer.toString(level.number()), Integer.toString(level.notePercent()), Integer.toString(level.chordPercent()))));
		lines.add(new Line(texts.get("arcade.result.leniency"), texts.get("arcade.leniency." + position.name().toLowerCase(java.util.Locale.ROOT))));
		return lines;
	}

	public static List<Line> techniques(ArcadeResult result, ArcadeTexts texts) {
		List<Line> lines = new ArrayList<Line>();
		for (Map.Entry<PlayingTechnique, Map<TechniqueVerdict, Integer>> entry : result.summary().techniques().entrySet()) {
			StringBuilder counts = new StringBuilder();
			for (Map.Entry<TechniqueVerdict, Integer> verdict : entry.getValue().entrySet()) {
				if (verdict.getValue() > 0) {
					counts.append(counts.length() == 0 ? "" : ", ").append(verdict.getValue()).append(' ').append(texts.get("practice.technique.verdict." + verdict.getKey().name()));
				}
			}
			lines.add(new Line(texts.get("practice.technique.name." + entry.getKey().name()), counts.toString()));
		}
		return lines;
	}
}
