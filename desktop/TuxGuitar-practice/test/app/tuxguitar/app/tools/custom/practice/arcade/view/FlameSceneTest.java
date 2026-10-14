package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/**
 * The stage flames in the scene (spec 009 FR-001, FR-005, FR-007, FR-008, FR-011, FR-013, contract invariants 1 to 4 and 6): how many tongues at each tier
 * and setting, their height by level, the weak middle and the limited corners, nothing when the setting is off, flames before everything else, and no
 * random source.
 */
public class FlameSceneTest {

	private static final Viewport SCREEN = new Viewport(1280, 720);
	private static final long CLOCK = 3_000_000_000L;

	private static SceneFixture fixture() {
		SongFixture song = new SongFixture(3);
		song.quarter(1, 0, 3, 5);
		song.quarter(1, 2, 2, 7);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static Scene build(SceneFixture f, QualityTier tier, FlameState flames, boolean mono) {
		return f.scene(mono).build(f.onset(0) - 700 * SceneFixture.MS, SCREEN, null, 100, tier, flames);
	}

	private static Scene build(SceneFixture f, QualityTier tier, FlameState flames) {
		return build(f, tier, flames, false);
	}

	private static FlameState state(FlameSetting setting, double level) {
		return new FlameState(setting, level, CLOCK, true);
	}

	private static List<String> lines(Scene scene) {
		List<String> lines = new ArrayList<String>();
		for (SceneItem item : scene.items()) {
			lines.add(item.toString());
		}
		return lines;
	}

	private static double[] box(SceneItem quad) {
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			minX = Math.min(minX, quad.value(2 * i));
			maxX = Math.max(maxX, quad.value(2 * i));
			minY = Math.min(minY, quad.value(2 * i + 1));
			maxY = Math.max(maxY, quad.value(2 * i + 1));
		}
		return new double[] { minX, minY, maxX, maxY };
	}

	/** A tongue layer as a reader of the scene sees it: the strips that share a code in {@code tgString} are one piece, {minX, minY, maxX, maxY, most opaque strip}. */
	private static java.util.Map<Integer, double[]> pieces(Scene scene) {
		java.util.Map<Integer, double[]> pieces = new java.util.TreeMap<Integer, double[]>();
		for (SceneItem strip : scene.withRole(Role.FLAME_TONGUE)) {
			double[] b = box(strip);
			double[] p = pieces.get(strip.tgString());
			if (p == null) {
				pieces.put(strip.tgString(), new double[] { b[0], b[1], b[2], b[3], strip.alpha() });
			} else {
				p[0] = Math.min(p[0], b[0]);
				p[1] = Math.min(p[1], b[1]);
				p[2] = Math.max(p[2], b[2]);
				p[3] = Math.max(p[3], b[3]);
				p[4] = Math.max(p[4], strip.alpha());
			}
		}
		return pieces;
	}

	private static double tallest(Scene scene) {
		double tallest = 0.0;
		for (double[] p : pieces(scene).values()) {
			tallest = Math.max(tallest, p[3] - p[1]);
		}
		return tallest;
	}

	/** The number of tongue layers: one per code. */
	private static int layerCount(Scene scene) {
		return pieces(scene).size();
	}

	@Test
	public void theTonguesAndTheirLayersFollowTheTierAndTheSetting() {
		SceneFixture f = fixture();
		assertEquals(28 * 3, layerCount(build(f, QualityTier.FULL, state(FlameSetting.FULL, 1.0))), "full: 28 tongues of 3 layers");
		assertEquals(14 * 2, layerCount(build(f, QualityTier.BALANCED, state(FlameSetting.FULL, 1.0))), "balanced: 14 tongues of 2 layers");
		assertEquals(14 * 3, layerCount(build(f, QualityTier.FULL, state(FlameSetting.DISCREET, 1.0))), "discreet: half the tongues");
		assertEquals(7 * 2, layerCount(build(f, QualityTier.BALANCED, state(FlameSetting.DISCREET, 1.0))));
		Scene lowest = build(f, QualityTier.PERFORMANCE, state(FlameSetting.FULL, 1.0));
		assertTrue(lowest.withRole(Role.FLAME_TONGUE).isEmpty() && lowest.withRole(Role.FLAME_EMBER).isEmpty(), "the lowest tier draws no flame at all");
	}

	@Test
	public void theBaseGlowExistsOnlyAtTheFullTier() {
		SceneFixture f = fixture();
		int full = build(f, QualityTier.FULL, state(FlameSetting.FULL, 1.0)).withRole(Role.FLAME_EMBER).size();
		int balanced = build(f, QualityTier.BALANCED, state(FlameSetting.FULL, 1.0)).withRole(Role.FLAME_EMBER).size();
		assertTrue(balanced > 0, "embers at the balanced tier");
		assertTrue(full > balanced, "the full tier adds the base glow to the embers");
	}

	@Test
	public void atLevelZeroOnlyEmbersBurnAndTheHeightGrowsInProportionToTheLevel() {
		SceneFixture f = fixture();
		for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.BALANCED }) {
			for (FlameSetting setting : new FlameSetting[] { FlameSetting.DISCREET, FlameSetting.FULL }) {
				Scene zero = build(f, tier, new FlameState(setting, 0.0, CLOCK, false));
				assertTrue(zero.withRole(Role.FLAME_TONGUE).isEmpty(), tier + " " + setting + ": no tongue at level 0");
				assertFalse(zero.withRole(Role.FLAME_EMBER).isEmpty(), "but embers");
				double top = tallest(build(f, tier, new FlameState(setting, 1.0, CLOCK, false)));
				double previous = 0.0;
				for (double level : new double[] { 0.25, 0.5, 0.75, 1.0 }) {
					double height = tallest(build(f, tier, new FlameState(setting, level, CLOCK, false)));
					assertEquals(level * top, height, top * 1e-6, tier + " " + setting + ": proportional at level " + level);
					assertTrue(height > previous, "and ordered");
					previous = height;
				}
			}
		}
	}

	@Test
	public void theCornersAreAtMostFiftyFiveAndTheMiddleAtMostTwentyTwoPercentOfThem() {
		SceneFixture f = fixture();
		for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.BALANCED }) {
			for (FlameSetting setting : new FlameSetting[] { FlameSetting.DISCREET, FlameSetting.FULL }) {
				for (boolean motion : new boolean[] { false, true }) {
					Scene scene = build(f, tier, new FlameState(setting, 1.0, CLOCK, motion));
					double corner = 0.0;
					double middle = Double.MAX_VALUE;
					double middleAlpha = 0.0;
					double cornerAlpha = 0.0;
					for (double[] b : pieces(scene).values()) {
						double centre = (b[0] + b[2]) / 2.0;
						double d = Math.abs(centre - SCREEN.width() / 2.0) / (SCREEN.width() / 2.0);
						if (d >= 0.7) {
							corner = Math.max(corner, b[3] - b[1]);
							cornerAlpha = Math.max(cornerAlpha, b[4]);
						}
						if (d <= 0.1) {
							middle = Math.min(middle, b[3] - b[1]);
							middleAlpha = Math.max(middleAlpha, b[4]);
						}
					}
					assertTrue(corner <= 0.55 * SCREEN.height() + 1e-9, tier + " " + setting + ": corner " + corner + " is at most 55 % of the picture height");
					assertTrue(corner > 0.0);
					assertTrue(middle <= 0.22 * corner + 1e-9, tier + " " + setting + ": the middle height " + middle + " is at most 22 % of " + corner);
					assertTrue(middleAlpha <= 0.22 * cornerAlpha + 1e-9, "and so is the opacity");
				}
			}
		}
	}

	@Test
	public void discreetIsSixtyPercentOfTheHeightAndOfTheOpacityOfFull() {
		SceneFixture f = fixture();
		Scene full = build(f, QualityTier.FULL, new FlameState(FlameSetting.FULL, 1.0, CLOCK, false));
		Scene discreet = build(f, QualityTier.FULL, new FlameState(FlameSetting.DISCREET, 1.0, CLOCK, false));
		assertEquals(0.6 * tallest(full), tallest(discreet), 1e-6);
		double fullAlpha = 0.0;
		double discreetAlpha = 0.0;
		for (double[] p : pieces(full).values()) {
			fullAlpha = Math.max(fullAlpha, p[4]);
		}
		for (double[] p : pieces(discreet).values()) {
			discreetAlpha = Math.max(discreetAlpha, p[4]);
		}
		assertEquals(0.6 * fullAlpha, discreetAlpha, 1e-6);
	}

	@Test
	public void offOrNoStateGivesExactlyTheSceneWithoutTheFeature() {
		SceneFixture f = fixture();
		for (QualityTier tier : QualityTier.values()) {
			Scene without = f.scene(false).build(f.onset(0) - 700 * SceneFixture.MS, SCREEN, null, 100, tier);
			assertEquals(lines(without), lines(build(f, tier, null)), tier + ": a null state");
			assertEquals(lines(without), lines(build(f, tier, state(FlameSetting.OFF, 1.0))), tier + ": the setting off, even at full level");
			assertTrue(without.withRole(Role.FLAME_TONGUE).isEmpty() && without.withRole(Role.FLAME_EMBER).isEmpty());
		}
	}

	@Test
	public void theFlamesComeBeforeEveryOtherItemAndAreOnlyDecoration() {
		SceneFixture f = fixture();
		for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.BALANCED }) {
			Scene scene = build(f, tier, state(FlameSetting.FULL, 1.0));
			boolean flamesDone = false;
			int flames = 0;
			for (SceneItem item : scene.items()) {
				boolean flame = item.role() == Role.FLAME_TONGUE || item.role() == Role.FLAME_EMBER;
				assertTrue(!flame || !flamesDone, "no flame after another kind of item: " + item);
				assertTrue(!flame || item.role().isDecoration());
				if (flame) {
					flames++;
				} else {
					flamesDone = true;
				}
			}
			assertTrue(flames > 0);
			// everything else is the scene without the feature
			List<String> others = new ArrayList<String>(lines(scene).subList(flames, scene.items().size()));
			assertEquals(lines(f.scene(false).build(f.onset(0) - 700 * SceneFixture.MS, SCREEN, null, 100, tier)), others, "the rest of the picture is untouched");
		}
	}

	@Test
	public void theSameInputsGiveTheSameItemsAndTheClockMovesOnlyTheSway() {
		SceneFixture f = fixture();
		FlameState a = new FlameState(FlameSetting.FULL, 0.8, CLOCK, true);
		assertEquals(lines(build(f, QualityTier.FULL, a)), lines(build(f, QualityTier.FULL, new FlameState(FlameSetting.FULL, 0.8, CLOCK, true))));
		Scene moved = build(f, QualityTier.FULL, new FlameState(FlameSetting.FULL, 0.8, CLOCK + 170_000_000L, true));
		Scene base = build(f, QualityTier.FULL, a);
		assertNotEquals(lines(base), lines(moved), "a later clock moves the tongues");
		assertEquals(base.items().size(), moved.items().size());
		for (int i = 0; i < base.items().size(); i++) {
			assertEquals(base.items().get(i).role(), moved.items().get(i).role());
			assertEquals(base.items().get(i).shape(), moved.items().get(i).shape());
		}
	}

	@Test
	public void withReducedMotionTheClockChangesNothingAndOnlyTheLevelMovesTheHeight() {
		SceneFixture f = fixture();
		List<String> one = lines(build(f, QualityTier.FULL, new FlameState(FlameSetting.FULL, 0.8, CLOCK, false)));
		List<String> other = lines(build(f, QualityTier.FULL, new FlameState(FlameSetting.FULL, 0.8, CLOCK + 777_000_000L, false)));
		assertEquals(one, other, "frozen sway: the same whatever the clock");
		assertNotEquals(one, lines(build(f, QualityTier.FULL, new FlameState(FlameSetting.FULL, 0.4, CLOCK, false))), "but the level still changes the picture");
	}

	@Test
	public void theFlamesKeepTheirColoursInTheMonochromeLookAndInTheLightTheme() {
		SceneFixture f = fixture();
		FlameState flames = state(FlameSetting.FULL, 1.0);
		List<String> colour = new ArrayList<String>();
		List<String> mono = new ArrayList<String>();
		for (SceneItem item : build(f, QualityTier.FULL, flames, false).items()) {
			if (item.role() == Role.FLAME_TONGUE || item.role() == Role.FLAME_EMBER) {
				colour.add(item.toString());
			}
		}
		for (SceneItem item : build(f, QualityTier.FULL, flames, true).items()) {
			if (item.role() == Role.FLAME_TONGUE || item.role() == Role.FLAME_EMBER) {
				mono.add(item.toString());
			}
		}
		assertFalse(colour.isEmpty());
		assertEquals(colour, mono, "no monochrome variant of the flames (FR-011)");
		boolean warm = false;
		for (SceneItem item : build(f, QualityTier.FULL, flames, true).withRole(Role.FLAME_TONGUE)) {
			warm |= ((item.colour() >> 16) & 0xFF) > ((item.colour() & 0xFF) + 40);
		}
		assertTrue(warm, "red and orange, not grey");
	}

	@Test
	public void aStateOutsideTheRangeIsClamped() {
		SceneFixture f = fixture();
		assertEquals(lines(build(f, QualityTier.FULL, state(FlameSetting.FULL, 1.0))), lines(build(f, QualityTier.FULL, state(FlameSetting.FULL, 7.5))));
		assertEquals(lines(build(f, QualityTier.FULL, state(FlameSetting.FULL, 0.0))), lines(build(f, QualityTier.FULL, state(FlameSetting.FULL, -3.0))));
		assertEquals(14 * 2, layerCount(build(f, QualityTier.BALANCED, state(FlameSetting.FULL, 1.0))));
	}
}
