package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Spec 019 (T005, FR-019): the values of a fire are always within their range; a missing or bad value is the default. */
public class FireTuningTest {

	@Test
	public void everyValueIsBroughtWithinItsRange() {
		Map<FireParameter, Double> low = new EnumMap<FireParameter, Double>(FireParameter.class);
		Map<FireParameter, Double> high = new EnumMap<FireParameter, Double>(FireParameter.class);
		for (FireParameter parameter : FireParameter.values()) {
			low.put(parameter, parameter.min() - 1000.0);
			high.put(parameter, parameter.max() + 1000.0);
		}
		FireTuning lowest = FireTuning.of(FireTuning.Group.SCORE, low);
		FireTuning highest = FireTuning.of(FireTuning.Group.SCORE, high);
		for (FireParameter parameter : FireParameter.values()) {
			if (parameter.kind() == FireParameter.Kind.COLOUR) {
				continue;
			}
			assertEquals(parameter.min(), lowest.value(parameter), 0.0, parameter.key());
			assertEquals(parameter.max(), highest.value(parameter), 0.0, parameter.key());
		}
	}

	@Test
	public void integersAreRoundedAndColoursKeptToRedGreenAndBlue() {
		Map<FireParameter, Double> values = new EnumMap<FireParameter, Double>(FireParameter.class);
		values.put(FireParameter.CELL_COLUMNS, 100.6);
		values.put(FireParameter.COLOUR_1, (double) 0x7F123456L);
		FireTuning tuning = FireTuning.of(FireTuning.Group.ARCADE, values);
		assertEquals(101.0, tuning.value(FireParameter.CELL_COLUMNS), 0.0);
		assertEquals(101, tuning.integer(FireParameter.CELL_COLUMNS));
		assertEquals(0x123456, tuning.colour(FireParameter.COLOUR_1));
	}

	@Test
	public void aMissingOrNotANumberValueIsTheDefault() {
		Map<FireParameter, Double> values = new EnumMap<FireParameter, Double>(FireParameter.class);
		values.put(FireParameter.OPACITY_MOST, Double.NaN);
		values.put(FireParameter.SPARKS_MOST, null);
		FireTuning tuning = FireTuning.of(FireTuning.Group.SCORE, values);
		for (FireParameter parameter : FireParameter.values()) {
			assertEquals(parameter.scoreDefault(), tuning.value(parameter), 0.0, parameter.key());
		}
		assertEquals(FireTuning.SCORE_DEFAULTS, tuning);
		assertEquals(FireTuning.SCORE_DEFAULTS, FireTuning.of(FireTuning.Group.SCORE, null));
	}

	@Test
	public void theTwoGroupsDifferOnlyWhereTheirDefaultsDiffer() {
		for (FireParameter parameter : FireParameter.values()) {
			boolean same = Double.compare(parameter.arcadeDefault(), parameter.scoreDefault()) == 0;
			assertEquals(same, Double.compare(FireTuning.ARCADE_DEFAULTS.value(parameter), FireTuning.SCORE_DEFAULTS.value(parameter)) == 0, parameter.key());
		}
		assertNotEquals(FireTuning.ARCADE_DEFAULTS, FireTuning.SCORE_DEFAULTS);
	}

	@Test
	public void equalValuesGiveEqualTunings() {
		FireTuning one = FireTuning.ARCADE_DEFAULTS.with(FireParameter.OPACITY_MOST, 0.5);
		FireTuning two = FireTuning.ARCADE_DEFAULTS.with(FireParameter.OPACITY_MOST, 0.5);
		assertEquals(one, two);
		assertEquals(one.hashCode(), two.hashCode());
		assertNotEquals(FireTuning.ARCADE_DEFAULTS, one);
		assertEquals(0.85, FireTuning.ARCADE_DEFAULTS.value(FireParameter.OPACITY_MOST), 0.0, "with() leaves the tuning it comes from as it was");
	}

	@Test
	public void theSparkCountOfTheTuningIsTheIntegerArithmeticOfBefore() {
		for (int streak = 0; streak <= 128; streak++) {
			int before = 20 + 5 * Math.min(streak, 12) / 2;
			assertEquals(before, FlameDrawing.sparkCount(FireTuning.ARCADE_DEFAULTS, streak), "streak " + streak);
		}
	}
}
