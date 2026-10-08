package app.tuxguitar.app.tools.custom.practice.arcade.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The scoring rules of Arcade (spec 003 T010, FR-022, research R5): a fixed table, the same for the same play. */
public class ArcadeScoringTest {

	private final ArcadeScoring scoring = ArcadeScoring.standard();

	@Test
	public void eachVerdictHasItsPointsAndOnlyJudgedEventsScore() {
		assertEquals(100, scoring.points(ArcadeVerdict.ON_TIME));
		assertEquals(60, scoring.points(ArcadeVerdict.EARLY));
		assertEquals(60, scoring.points(ArcadeVerdict.LATE));
		for (ArcadeVerdict verdict : new ArcadeVerdict[] { ArcadeVerdict.WRONG, ArcadeVerdict.INCOMPLETE, ArcadeVerdict.MISSED, ArcadeVerdict.NOT_JUDGED,
				ArcadeVerdict.AUTO_PASSED, ArcadeVerdict.WAITING }) {
			assertEquals(0, scoring.points(verdict), verdict.name());
		}
	}

	@Test
	public void theStreakIsExtendedByGoodNotesResetByBadOnesAndLeftAloneByTheOthers() {
		for (ArcadeVerdict verdict : new ArcadeVerdict[] { ArcadeVerdict.ON_TIME, ArcadeVerdict.EARLY, ArcadeVerdict.LATE }) {
			assertTrue(scoring.extendsStreak(verdict), verdict.name());
			assertFalse(scoring.resetsStreak(verdict), verdict.name());
		}
		for (ArcadeVerdict verdict : new ArcadeVerdict[] { ArcadeVerdict.WRONG, ArcadeVerdict.INCOMPLETE, ArcadeVerdict.MISSED }) {
			assertTrue(scoring.resetsStreak(verdict), verdict.name());
			assertFalse(scoring.extendsStreak(verdict), verdict.name());
		}
		for (ArcadeVerdict verdict : new ArcadeVerdict[] { ArcadeVerdict.NOT_JUDGED, ArcadeVerdict.AUTO_PASSED, ArcadeVerdict.WAITING }) {
			assertFalse(scoring.extendsStreak(verdict), verdict.name());
			assertFalse(scoring.resetsStreak(verdict), verdict.name());
		}
	}

	@Test
	public void theMultiplierStepsAtTenAndTwentyFive() {
		assertEquals(1.0, scoring.multiplier(0), 0.0);
		assertEquals(1.0, scoring.multiplier(9), 0.0);
		assertEquals(1.5, scoring.multiplier(10), 0.0);
		assertEquals(1.5, scoring.multiplier(24), 0.0);
		assertEquals(2.0, scoring.multiplier(25), 0.0);
		assertEquals(2.0, scoring.multiplier(500), 0.0);
	}

	@Test
	public void judgedVerdictsAreThoseThatCountTowardsTheMaximum() {
		for (ArcadeVerdict verdict : ArcadeVerdict.values()) {
			boolean judged = (verdict == ArcadeVerdict.ON_TIME || verdict == ArcadeVerdict.EARLY || verdict == ArcadeVerdict.LATE || verdict == ArcadeVerdict.WRONG
					|| verdict == ArcadeVerdict.INCOMPLETE || verdict == ArcadeVerdict.MISSED);
			assertEquals(judged, scoring.isJudged(verdict), verdict.name());
		}
	}

	@Test
	public void theMaximumScoreIsTheScoreOfAPerfectRunOfTheSameJudgedEvents() {
		assertEquals(0, scoring.maximum(0));
		assertEquals(100, scoring.maximum(1));
		assertEquals(1000, scoring.maximum(10), "ten events at x1");
		assertEquals(1000 + 150, scoring.maximum(11), "the eleventh is at x1.5");
		// 10 x 100, 15 x 150, then x200
		assertEquals(1000 + 15 * 150 + 5 * 200, scoring.maximum(30));
	}

	@Test
	public void pointsOfOneEventAreRoundedAfterTheMultiplier() {
		assertEquals(60, scoring.eventScore(ArcadeVerdict.LATE, 0));
		assertEquals(90, scoring.eventScore(ArcadeVerdict.LATE, 10));
		assertEquals(120, scoring.eventScore(ArcadeVerdict.LATE, 25));
		assertEquals(0, scoring.eventScore(ArcadeVerdict.MISSED, 40));
	}

	@Test
	public void theCoreHasNoInputOrOutputOfAnyKind() throws java.io.IOException {
		java.nio.file.Path core = java.nio.file.Paths.get("src/app/tuxguitar/app/tools/custom/practice/arcade/core");
		try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(core)) {
			for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files::iterator) {
				String source = new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
				for (String forbidden : new String[] { "java.io.", "java.nio.", "TGConfigManager", "FileOutputStream", "Files.write", "System.out" }) {
					assertFalse(source.contains(forbidden), file.getFileName() + " must not use " + forbidden + " (nothing about a run is kept, FR-026)");
				}
			}
		}
	}
}
