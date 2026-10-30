package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/**
 * Spec 018 (T005; FR-001, FR-003, FR-004, FR-007, FR-008, FR-010, FR-011, SC-003): the field of big pixels of the demo fire, alone. The height of the
 * flame of a column counts above the two ember rows, as the tongues of spec 009 were measured.
 */
public class FireFieldTest {

	private static final Viewport SCREEN = new Viewport(1280, 720);
	private static final long CLOCK = 3_000_000_000L;
	private static final Viewport[] SHAPES = { new Viewport(1280, 720), new Viewport(800, 600), new Viewport(640, 480), new Viewport(1500, 500), new Viewport(2000, 1000),
			new Viewport(1100, 950), new Viewport(900, 400) };

	private static FireField field(Viewport viewport, QualityTier tier, FlameSetting setting, double level, long clock, boolean motion) {
		return new FireField(viewport, tier, new FlameState(setting, level, clock, motion));
	}

	private static double t(FireField field, int col) {
		return (col + 0.5) / field.columns();
	}

	private static double factor(FlameSetting setting) {
		return setting == FlameSetting.DISCREET ? FlameDrawing.DISCREET : 1.0;
	}

	@Test
	public void theCellsAreBigPixelsAndTwiceAsBigAtTheBalancedTier() {
		assertEquals(7.0, FireField.cell(SCREEN, QualityTier.FULL), 0.0, "1280 / 192, rounded");
		assertEquals(14.0, FireField.cell(SCREEN, QualityTier.BALANCED), 0.0);
		assertEquals(4.0, FireField.cell(new Viewport(640, 480), QualityTier.FULL), 0.0);
		assertEquals(4.0, FireField.cell(new Viewport(300, 200), QualityTier.FULL), 0.0, "never under 4 screen pixels");
	}

	@Test
	public void atLevelZeroOnlyTheTwoBottomRowsGlowAndWeakly() {
		for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.BALANCED }) {
			FireField field = field(SCREEN, tier, FlameSetting.FULL, 0.0, CLOCK, true);
			for (int col = 0; col < field.columns(); col++) {
				assertEquals(0, field.tongueRows(col), "no tongue at level 0");
				for (int row = 0; row < FireField.EMBER_ROWS; row++) {
					int heat = field.heat(col, row);
					assertTrue(heat >= 1 && heat <= 3, tier + ": an ember row is lit and weak: " + heat);
				}
				assertEquals(0, field.heat(col, FireField.EMBER_ROWS), "nothing above the ember rows");
			}
		}
	}

	@Test
	public void theFlameOfEachColumnFollowsTheLevelTheSettingAndTheWeightSmoothly() {
		for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.BALANCED }) {
			for (FlameSetting setting : new FlameSetting[] { FlameSetting.FULL, FlameSetting.DISCREET }) {
				for (double level : new double[] { 0.25, 0.5, 0.51, 1.0 }) {
					FireField field = field(SCREEN, tier, setting, level, CLOCK, true);
					double cell = field.cell();
					for (int col = 0; col < field.columns(); col++) {
						double expected = level * factor(setting) * 0.55 * SCREEN.height() * field.weight(col);
						assertEquals(expected, field.height(col), 1e-9, tier + " " + setting + " " + level + ": column " + col + " (SC-003)");
						assertEquals((int) Math.ceil(expected / cell - 1e-9), field.tongueRows(col), "the top cell is cut to the height");
						double top = field.top(FireField.EMBER_ROWS + field.tongueRows(col) - 1);
						double cut = field.cellHeight(col, FireField.EMBER_ROWS + field.tongueRows(col) - 1);
						assertTrue(cut > 0.0 && cut <= cell + 1e-9);
						assertEquals(SCREEN.height() - FireField.EMBER_ROWS * cell - expected, top + cell - cut, 1e-9, "the flame ends at its height");
					}
				}
			}
		}
		FireField full = field(SCREEN, QualityTier.FULL, FlameSetting.FULL, 1.0, CLOCK, true);
		assertEquals(1.0, full.weight(0), 1e-12, "the outermost column reaches the 55 % exactly");
		assertEquals(1.0, full.weight(full.columns() - 1), 1e-12);
	}

	@Test
	public void theMiddleTenthIsAtMostTwentyTwoPercentOfTheCornersInHeightAndInHeat() {
		for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.BALANCED }) {
			for (FlameSetting setting : new FlameSetting[] { FlameSetting.FULL, FlameSetting.DISCREET }) {
				FireField field = field(SCREEN, tier, setting, 1.0, CLOCK, true);
				double cornerHeight = 0.0;
				int cornerHeat = 0;
				double middleHeight = 0.0;
				int middleHeat = 0;
				for (int col = 0; col < field.columns(); col++) {
					double d = Math.abs(2.0 * t(field, col) - 1.0);
					int hottest = 0;
					for (int row = 0; row < FireField.EMBER_ROWS + field.tongueRows(col); row++) {
						hottest = Math.max(hottest, field.heat(col, row));
					}
					if (d >= 0.9) {
						cornerHeight = Math.max(cornerHeight, field.height(col));
						cornerHeat = Math.max(cornerHeat, hottest);
					} else if (d <= 0.1) {
						middleHeight = Math.max(middleHeight, field.height(col));
						middleHeat = Math.max(middleHeat, hottest);
					}
				}
				assertTrue(cornerHeight > 0.0 && cornerHeat == FireColours.STEPS - 1, "the corners burn white");
				assertTrue(middleHeight <= 0.22 * cornerHeight, tier + " " + setting + ": middle " + middleHeight + " against " + cornerHeight);
				assertTrue(middleHeat <= 0.22 * cornerHeat, tier + " " + setting + ": middle heat " + middleHeat + " against " + cornerHeat);
			}
		}
	}

	@Test
	public void discreetIsSixtyPercentOfTheHeightOfFull() {
		FireField full = field(SCREEN, QualityTier.FULL, FlameSetting.FULL, 1.0, CLOCK, false);
		FireField discreet = field(SCREEN, QualityTier.FULL, FlameSetting.DISCREET, 1.0, CLOCK, false);
		for (int col = 0; col < full.columns(); col++) {
			assertEquals(0.6 * full.height(col), discreet.height(col), 1e-9, "column " + col);
		}
	}

	@Test
	public void theSameInputsGiveTheSameFieldAndOnlyMotionLetsTheClockFlicker() {
		FireField a = field(SCREEN, QualityTier.FULL, FlameSetting.FULL, 0.8, CLOCK, true);
		FireField b = field(SCREEN, QualityTier.FULL, FlameSetting.FULL, 0.8, CLOCK, true);
		FireField later = field(SCREEN, QualityTier.FULL, FlameSetting.FULL, 0.8, CLOCK + 170_000_000L, true);
		FireField still = field(SCREEN, QualityTier.FULL, FlameSetting.FULL, 0.8, CLOCK, false);
		FireField stillLater = field(SCREEN, QualityTier.FULL, FlameSetting.FULL, 0.8, CLOCK + 777_000_000L, false);
		assertEquals(dump(a), dump(b));
		assertFalse(dump(a).equals(dump(later)), "with motion the fire flickers");
		assertEquals(dump(still), dump(stillLater), "with reduced motion the clock changes nothing");
		for (int col = 0; col < a.columns(); col++) {
			assertEquals(a.height(col), later.height(col), 0.0, "the clock never changes the height: only the level does");
		}
	}

	@Test
	public void noHeatAboveTheProfileAndNothingOutsideThePictureWhateverTheShape() {
		for (Viewport shape : SHAPES) {
			for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.BALANCED }) {
				FireField field = field(shape, tier, FlameSetting.FULL, 1.0, CLOCK, true);
				assertTrue(field.cell() >= 4.0, "at least 4 screen pixels per cell");
				assertTrue(field.left(0) >= 0.0 && field.left(field.columns() - 1) + field.cell() <= shape.width() + 1e-9, shape.width() + "x" + shape.height() + ": inside");
				for (int col = 0; col < field.columns(); col++) {
					int rows = FireField.EMBER_ROWS + field.tongueRows(col);
					assertTrue(field.top(rows - 1) >= 0.0, "inside at the top");
					for (int row = 0; row < rows; row++) {
						assertTrue(field.heat(col, row) >= 1 && field.heat(col, row) <= FireColours.STEPS - 1, "lit under the profile");
					}
					assertEquals(0, field.heat(col, rows), "cold above it");
				}
			}
		}
	}

	private static String dump(FireField field) {
		StringBuilder out = new StringBuilder();
		for (int col = 0; col < field.columns(); col++) {
			for (int row = 0; row < FireField.EMBER_ROWS + field.tongueRows(col); row++) {
				out.append(Integer.toHexString(field.heat(col, row)));
			}
			out.append('|');
		}
		return out.toString();
	}
}
