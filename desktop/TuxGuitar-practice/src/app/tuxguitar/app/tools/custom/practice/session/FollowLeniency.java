package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The pitch permissiveness of Follow (spec 016, FR-005, FR-006, contracts/follow-leniency.md). Like {@code ArcadeLeniency}, it is <b>one way</b>: for each row
 * the result is the more lenient of the effective value (the default, or what a device profile made of it) and the position's. No level threshold is a row, so
 * silence is never accepted; the acceptance in cents never goes above 45, below the halfway to the next semitone.
 * <p>
 * The values were measured (task T032, research R9): the most lenient that keep every right note and no wrong one of the reference recording. Very
 * permissive is at the bounds of the settings; the acceptance in cents stays at 35, since 40 loses a right note.
 */
public final class FollowLeniency {

	/** One relaxed setting: its value at each permissive position, and which way is the more lenient one. */
	private static final class Row {
		final RecognitionParameter.Number parameter;
		final boolean higherIsMoreLenient;
		final RecognitionParameter.Number permissive;
		final RecognitionParameter.Number veryPermissive;

		Row(RecognitionParameter.Number parameter, boolean higherIsMoreLenient, RecognitionParameter.Number permissive,
				RecognitionParameter.Number veryPermissive) {
			this.parameter = parameter;
			this.higherIsMoreLenient = higherIsMoreLenient;
			this.permissive = permissive;
			this.veryPermissive = veryPermissive;
		}

		/** The position's value, read from the settings (spec 026, {@code recognition.follow-leniency.*}). */
		double at(FollowLeniencyPosition position, RecognitionSettings settings) {
			return settings.get(position == FollowLeniencyPosition.PERMISSIVE ? this.permissive : this.veryPermissive);
		}
	}

	private static final List<Row> ROWS = Collections.unmodifiableList(rows());

	private static List<Row> rows() {
		List<Row> rows = new ArrayList<Row>();
		rows.add(new Row(RecognitionParameters.NOTE_ACCEPT, true, RecognitionParameters.FOLLOW_LENIENCY_PERMISSIVE_ACCEPT,
				RecognitionParameters.FOLLOW_LENIENCY_VERY_PERMISSIVE_ACCEPT));
		rows.add(new Row(RecognitionParameters.NOTE_CONFIDENCE, false, RecognitionParameters.FOLLOW_LENIENCY_PERMISSIVE_CONFIDENCE,
				RecognitionParameters.FOLLOW_LENIENCY_VERY_PERMISSIVE_CONFIDENCE));
		rows.add(new Row(RecognitionParameters.NOTE_STABILITY, false, RecognitionParameters.FOLLOW_LENIENCY_PERMISSIVE_STABILITY,
				RecognitionParameters.FOLLOW_LENIENCY_VERY_PERMISSIVE_STABILITY));
		rows.add(new Row(RecognitionParameters.CHORD_EVIDENCE, false, RecognitionParameters.FOLLOW_LENIENCY_PERMISSIVE_CHORD_EVIDENCE,
				RecognitionParameters.FOLLOW_LENIENCY_VERY_PERMISSIVE_CHORD_EVIDENCE));
		rows.add(new Row(RecognitionParameters.CHORD_MIN_PARTIALS, false, RecognitionParameters.FOLLOW_LENIENCY_PERMISSIVE_CHORD_PARTIALS,
				RecognitionParameters.FOLLOW_LENIENCY_VERY_PERMISSIVE_CHORD_PARTIALS));
		rows.add(new Row(RecognitionParameters.CHORD_CONFIRM, false, RecognitionParameters.FOLLOW_LENIENCY_PERMISSIVE_CHORD_CONFIRM,
				RecognitionParameters.FOLLOW_LENIENCY_VERY_PERMISSIVE_CHORD_CONFIRM));
		return rows;
	}

	private FollowLeniency() {
	}

	/** The settings of a Follow run: the effective ones, relaxed to the position, one way. */
	public static RecognitionSettings apply(FollowLeniencyPosition position, RecognitionSettings effective) {
		if (position == FollowLeniencyPosition.STRICT) {
			return effective;
		}
		RecognitionSettings relaxed = effective;
		for (Row row : ROWS) {
			double current = relaxed.get(row.parameter);
			double wanted = row.at(position, effective);
			double value = row.higherIsMoreLenient ? Math.max(current, wanted) : Math.min(current, wanted);
			if (value != current) {
				relaxed = relaxed.with(row.parameter, Double.valueOf(value));
			}
		}
		return relaxed;
	}

	/** The relaxed settings, for a report. */
	public static List<RecognitionParameter.Number> relaxedParameters() {
		List<RecognitionParameter.Number> parameters = new ArrayList<RecognitionParameter.Number>();
		for (Row row : ROWS) {
			parameters.add(row.parameter);
		}
		return parameters;
	}
}
