package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The level of the stage flames (spec 009 FR-002 to FR-004, FR-010, SC-003, contract invariant 7): the real share of the maximum mapped from 0.5..1 to
 * 0..1, nothing until four events of the run are judged, a quick rise and a slow fall, with steps that never leap, a level that can be held and a new
 * object that starts at zero.
 */
public class FlameLevelTest {

	private static final long PICTURE = 1_000_000_000L / 60;

	private long now = 5_000_000_000L;

	private void frames(FlameLevel level, double share, int judged, int count) {
		for (int i = 0; i < count; i++) {
			this.now += PICTURE;
			level.update(share, judged, this.now);
		}
	}

	private FlameLevel started() {
		FlameLevel level = new FlameLevel();
		level.update(0.0, 0, this.now);
		return level;
	}

	/** The seconds until the level is at least {@code target}, or -1 after ten seconds. */
	private double secondsToReach(FlameLevel level, double share, int judged, double target) {
		double seconds = 0.0;
		while (seconds < 10.0) {
			frames(level, share, judged, 1);
			seconds += PICTURE / 1e9;
			if (level.level() >= target - 1e-9) {
				return seconds;
			}
		}
		return -1.0;
	}

	@Test
	public void aNewLevelIsZeroAndFewerThanFourJudgedEventsKeepItAtZeroWhateverTheShare() {
		FlameLevel level = started();
		assertEquals(0.0, level.level(), 0.0);
		for (int judged = 0; judged < 4; judged++) {
			frames(level, 1.0, judged, 120);
			assertEquals(0.0, level.level(), 0.0, judged + " judged events: embers only");
		}
		frames(level, 1.0, 4, 120);
		assertEquals(1.0, level.level(), 1e-9, "the fourth judged event releases the flames");
	}

	@Test
	public void theShareIsTheRealRatioMappedFromHalfToOneAndAShareOfZeroGivesZero() {
		double[][] cases = { { 0.0, 0.0 }, { 0.3, 0.0 }, { 0.5, 0.0 }, { 0.6, 0.2 }, { 0.8, 0.6 }, { 0.9, 0.8 }, { 1.0, 1.0 } };
		for (double[] c : cases) {
			FlameLevel level = started();
			frames(level, c[0], 10, 600);
			assertEquals(c[1], level.level(), 1e-9, "share " + c[0]);
		}
	}

	@Test
	public void theLevelRisesFromZeroToOneInAboutHalfASecondAndFallsInAboutThreeSeconds() {
		FlameLevel level = started();
		double rise = secondsToReach(level, 1.0, 10, 1.0);
		assertTrue(rise >= 0.5 * 0.8 && rise <= 0.5 * 1.2, "rise in " + rise + " s, within 20 % of 0.5 s");
		double fall = 0.0;
		while (level.level() > 1e-9 && fall < 10.0) {
			frames(level, 0.0, 10, 1);
			fall += PICTURE / 1e9;
		}
		assertTrue(fall >= 3.0 * 0.8 && fall <= 3.0 * 1.2, "fall in " + fall + " s, within 20 % of 3 s");
	}

	@Test
	public void atSixtyPicturesASecondTheStepIsAtMostFourPercentRisingAndOnePercentFalling() {
		FlameLevel level = started();
		double previous = 0.0;
		for (int i = 0; i < 90; i++) {
			frames(level, 1.0, 10, 1);
			assertTrue(level.level() - previous <= 0.04 + 1e-9, "rising step " + (level.level() - previous));
			previous = level.level();
		}
		for (int i = 0; i < 240; i++) {
			frames(level, 0.0, 10, 1);
			assertTrue(previous - level.level() <= 0.01 + 1e-9, "falling step " + (previous - level.level()));
			previous = level.level();
		}
	}

	@Test
	public void aLongGapBetweenTwoPicturesNeverMakesAStepLargerThanTheRateTimesTheElapsedTime() {
		FlameLevel level = started();
		this.now += 10_000_000_000L;
		level.update(1.0, 10, this.now);
		assertTrue(level.level() <= 2.0 * 0.05 + 1e-9, "the step is limited to 50 ms of rise: " + level.level());
		double before = level.level();
		this.now += 2_000_000L;
		level.update(1.0, 10, this.now);
		assertTrue(level.level() - before <= 2.0 * 0.002 + 1e-9, "and never more than rate x elapsed");
	}

	@Test
	public void aHeldLevelStaysAndGoesOnFromTheSameValueWithoutAJump() {
		FlameLevel level = started();
		frames(level, 1.0, 10, 15);
		double held = level.level();
		assertTrue(held > 0.0 && held < 1.0);
		this.now += PICTURE;
		level.hold(this.now);
		frames(level, 0.0, 10, 300);
		assertEquals(held, level.level(), 0.0, "held while the target changes and time goes on");
		this.now += 7_000_000_000L;
		level.resume(this.now);
		assertEquals(held, level.level(), 0.0, "no jump at the resume");
		frames(level, 1.0, 10, 1);
		assertTrue(Math.abs(level.level() - held) <= 0.04 + 1e-9, "the first picture after the resume is a normal step");
	}

	@Test
	public void eachNewLevelStartsAtZeroWhateverTheEarlierOneReached() {
		FlameLevel first = started();
		frames(first, 1.0, 10, 120);
		assertEquals(1.0, first.level(), 1e-9);
		assertEquals(0.0, new FlameLevel().level(), 0.0);
	}
}
