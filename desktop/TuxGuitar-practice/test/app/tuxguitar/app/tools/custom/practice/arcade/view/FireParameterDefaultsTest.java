package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Spec 019 (T004, FR-015, FR-017, FR-021): the catalogue of the fire's values, whose Arcade defaults are the constants the fire used before. */
public class FireParameterDefaultsTest {

	private static final String[] KEYS = { "opacity.most", "cell.columns", "cell.smallest", "colour.steps", "colour.1", "colour.2", "colour.3", "colour.4",
			"colour.5", "height.corners", "height.middle", "height.curve", "body.heat", "body.falloff", "body.ramp", "flicker.strength", "flicker.base",
			"motion.speed", "motion.rise", "sway.width", "sway.speed", "sway.rows", "noise.large", "noise.medium", "noise.fine", "noise.scale", "noise.aspect",
			"ember.rows", "ember.glow", "ember.flicker", "discreet.share", "growth.from", "growth.minJudged", "growth.full", "growth.rise", "growth.fall",
			"growth.maxStep", "sparks.base", "sparks.perStreak", "sparks.streakCap", "sparks.most", "sparks.life", "sparks.rise", "sparks.riseSpread",
			"sparks.drift", "sparks.zone", "sparks.start", "sparks.fade" };

	private static double arcade(String key) {
		FireParameter parameter = FireParameter.of(key);
		assertNotNull(parameter, key);
		return parameter.arcadeDefault();
	}

	private static double score(String key) {
		return FireParameter.of(key).scoreDefault();
	}

	@Test
	public void everyRowOfTheDataModelIsAParameter() {
		Set<String> expected = new LinkedHashSet<String>(Arrays.asList(KEYS));
		Set<String> actual = new LinkedHashSet<String>();
		for (FireParameter parameter : FireParameter.values()) {
			actual.add(parameter.key());
		}
		assertEquals(expected, actual, "the catalogue is the table of the data model, no more and no less");
		assertEquals(48, FireParameter.values().length);
	}

	@Test
	public void bothDefaultsLieWithinTheRange() {
		for (FireParameter parameter : FireParameter.values()) {
			assertTrue(parameter.min() <= parameter.max(), parameter.key());
			assertTrue(parameter.min() <= parameter.arcadeDefault() && parameter.arcadeDefault() <= parameter.max(), parameter.key() + " Arcade");
			assertTrue(parameter.min() <= parameter.scoreDefault() && parameter.scoreDefault() <= parameter.max(), parameter.key() + " score");
		}
		assertEquals(16.0, arcade("growth.full"), 0.0, "never read, but within its range");
		assertFalse(FireParameter.GROWTH_FULL.inArcade(), "the streak of full height is the score fire's only");
		assertTrue(FireParameter.GROWTH_FULL.inScore());
	}

	@Test
	public void theArcadeDefaultsAreTheConstantsOfBefore() {
		assertEquals(FireColours.MOST, arcade("opacity.most"), 0.0);
		assertEquals(FireField.COLUMNS, arcade("cell.columns"), 0.0);
		assertEquals(FireField.SMALLEST, arcade("cell.smallest"), 0.0);
		assertEquals(FireColours.STEPS, arcade("colour.steps"), 0.0);
		assertEquals(FlameDrawing.CORNER_HEIGHT, arcade("height.corners"), 0.0);
		assertEquals(FlameDrawing.MIDDLE, arcade("height.middle"), 0.0);
		assertEquals(FlameDrawing.DISCREET, arcade("discreet.share"), 0.0);
		assertEquals(FireField.EMBER_ROWS, arcade("ember.rows"), 0.0);
		assertEquals(FlameDrawing.MOST_SPARKS, arcade("sparks.most"), 0.0);
		assertEquals(FlameDrawing.SPARK_LIFE / 1e9, arcade("sparks.life"), 0.0);
		int[] scale = { 0x5A0A04, 0xB7210F, 0xF26A12, 0xFFD23C, 0xFFF6D8 };
		for (int i = 0; i < scale.length; i++) {
			assertEquals(scale[i], arcade("colour." + (i + 1)), 0.0, "colour " + (i + 1));
		}
		assertEquals(2.0, arcade("height.curve"), 0.0, "the d * d of the profile");
		assertEquals(2.0, arcade("noise.scale"), 0.0, "the octave divisors 2, 1, 0.5");
		assertEquals(2.0, arcade("noise.aspect"), 0.0, "the octave divisors 4, 2, 1 of the height");
		assertEquals(1.0, arcade("sparks.start"), 0.0, "Arcade's sparks start at the top of the flames, as before");
		assertEquals(0.5, arcade("growth.from"), 0.0, "Arcade's flames follow the share from one half");
	}

	@Test
	public void theScoreDefaults() {
		assertEquals(1.0 / 3.0, score("height.corners"), 0.0, "FR-004");
		assertEquals(16.0, score("growth.full"), 0.0);
		assertEquals(1.0, score("growth.fall"), 0.0, "FR-003: back to embers within about one second");
		assertEquals(0.0, score("growth.from"), 0.0);
		assertEquals(0.0, score("growth.minJudged"), 0.0);
		assertEquals(0.0, score("sparks.start"), 0.0, "from the bottom of the fire (owner)");
	}

	@Test
	public void theCostCeilings() {
		assertEquals(384.0, FireParameter.of("cell.columns").max(), 0.0);
		assertEquals(3.0, FireParameter.of("cell.smallest").min(), 0.0);
		assertEquals(1000.0, FireParameter.of("sparks.most").max(), 0.0);
		assertEquals(200.0, FireParameter.of("sparks.base").max(), 0.0);
		assertEquals(50.0, FireParameter.of("sparks.perStreak").max(), 0.0);
		assertEquals(64.0, FireParameter.of("colour.steps").max(), 0.0);
	}
}
