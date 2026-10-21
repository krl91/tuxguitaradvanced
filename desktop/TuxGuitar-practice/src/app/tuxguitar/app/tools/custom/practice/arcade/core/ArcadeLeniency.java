package app.tuxguitar.app.tools.custom.practice.arcade.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The permissiveness of the Arcade recognition (spec 014, FR-001 to FR-005): a game wants a quick and forgiving verdict, the practice modes a right one. A
 * position relaxes some of the recognition settings, and widens the timing window of the player, only for an Arcade run:
 * <ul>
 * <li>it is <b>one way</b>: for each relaxed setting the result is the more lenient of the player's effective value (the default, or what a device profile
 * made of it) and the position's, so a player or a profile that is already more lenient is never made stricter;</li>
 * <li>it stays inside the range every setting already guarantees, and never touches the level thresholds of the input, the attack level or any other setting;</li>
 * <li>{@link ArcadeLeniencyPosition#LIKE_PRACTICE} changes nothing at all.</li>
 * </ul>
 * The values of the two permissive positions are provisional until the measure of the plan (research R6, task T012) fixes them within the limits of the spec.
 */
public final class ArcadeLeniency {

	/** One relaxed setting: its value at each permissive position, and which way is the more lenient one. */
	private static final class Row {
		final RecognitionParameter.Number parameter;
		final boolean higherIsMoreLenient;
		final double fun;
		final double veryPermissive;

		Row(RecognitionParameter.Number parameter, boolean higherIsMoreLenient, double fun, double veryPermissive) {
			this.parameter = parameter;
			this.higherIsMoreLenient = higherIsMoreLenient;
			this.fun = fun;
			this.veryPermissive = veryPermissive;
		}

		double at(ArcadeLeniencyPosition position) {
			return position == ArcadeLeniencyPosition.FUN ? this.fun : this.veryPermissive;
		}
	}

	/** The acceptance in cents grows, the confidence, the stability and confirmation times, the chord evidence and the minimum of partials shrink. */
	private static final List<Row> ROWS = Collections.unmodifiableList(rows());

	private static List<Row> rows() {
		List<Row> rows = new ArrayList<Row>();
		rows.add(new Row(RecognitionParameters.NOTE_ACCEPT, true, 42.0, 45.0));
		rows.add(new Row(RecognitionParameters.NOTE_CONFIDENCE, false, 0.70, 0.60));
		rows.add(new Row(RecognitionParameters.NOTE_STABILITY, false, 75.0, 55.0));
		rows.add(new Row(RecognitionParameters.CHORD_EVIDENCE, false, 0.28, 0.22));
		rows.add(new Row(RecognitionParameters.CHORD_MIN_PARTIALS, false, 2.0, 1.0));
		rows.add(new Row(RecognitionParameters.CHORD_CONFIRM, false, 32.0, 24.0));
		return rows;
	}

	private ArcadeLeniency() {
	}

	/** The settings of an Arcade run: the effective ones, relaxed to the position, one way. */
	public static RecognitionSettings apply(ArcadeLeniencyPosition position, RecognitionSettings effective) {
		if (position == ArcadeLeniencyPosition.LIKE_PRACTICE) {
			return effective;
		}
		RecognitionSettings relaxed = effective;
		for (Row row : ROWS) {
			double current = relaxed.get(row.parameter);
			double wanted = row.at(position);
			double value = row.higherIsMoreLenient ? Math.max(current, wanted) : Math.min(current, wanted);
			if (value != current) {
				relaxed = relaxed.with(row.parameter, Double.valueOf(value));
			}
		}
		return relaxed;
	}

	/** The width of the on-time window of an Arcade run, in milliseconds each side at 120 beats a minute: the player's, widened, never beyond the existing maximum. */
	public static int windowMillis(ArcadeLeniencyPosition position, int chosenMillis) {
		if (position == ArcadeLeniencyPosition.LIKE_PRACTICE) {
			return chosenMillis;
		}
		double factor = position == ArcadeLeniencyPosition.FUN ? 1.25 : 1.5;
		return Math.max(chosenMillis, Math.min(TimingTolerance.MAX_WIDTH_MILLIS, (int) Math.round(chosenMillis * factor)));
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
