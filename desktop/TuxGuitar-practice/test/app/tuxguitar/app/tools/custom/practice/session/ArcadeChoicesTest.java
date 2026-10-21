package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniencyPosition;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevel;

/**
 * Spec 014 (T006, FR-006, FR-012): what the player chose in Arcade is remembered between launches: the level (level 1 when nothing is saved) and the
 * leniency position (the fun one by default). A missing, empty, non-numeric, out-of-range or unknown value falls back without an error; an unreadable
 * file keeps the defaults.
 */
public class ArcadeChoicesTest {

	private static final class Memory implements ArcadeChoices.Persistence {
		final Map<String, String> values = new HashMap<String, String>();
		boolean unreadable;
		boolean unwritable;

		public String get(String key) {
			if (this.unreadable) {
				throw new IllegalStateException("unreadable");
			}
			return this.values.get(key);
		}

		public void set(String key, String value) {
			if (this.unwritable) {
				throw new IllegalStateException("read-only");
			}
			this.values.put(key, value);
		}
	}

	@Test
	public void nothingSavedGivesLevelOneAndTheFunPosition() {
		ArcadeChoices choices = new ArcadeChoices(new Memory());
		assertSame(ArcadeLevel.of(1), choices.level());
		assertSame(ArcadeLeniencyPosition.FUN, choices.leniency());
	}

	@Test
	public void aChoiceIsReadBackAfterItIsWrittenAndSurvivesANewObject() {
		Memory file = new Memory();
		ArcadeChoices first = new ArcadeChoices(file);
		first.setLevel(ArcadeLevel.of(3));
		first.setLeniency(ArcadeLeniencyPosition.VERY_PERMISSIVE);
		assertSame(ArcadeLevel.of(3), first.level());
		assertSame(ArcadeLeniencyPosition.VERY_PERMISSIVE, first.leniency());
		ArcadeChoices restarted = new ArcadeChoices(file);
		assertSame(ArcadeLevel.of(3), restarted.level());
		assertSame(ArcadeLeniencyPosition.VERY_PERMISSIVE, restarted.leniency());
	}

	@Test
	public void theTwoChoicesAreIndependent() {
		Memory file = new Memory();
		ArcadeChoices choices = new ArcadeChoices(file);
		choices.setLevel(ArcadeLevel.of(4));
		assertSame(ArcadeLeniencyPosition.FUN, choices.leniency(), "the position keeps its default");
		choices.setLeniency(ArcadeLeniencyPosition.LIKE_PRACTICE);
		assertSame(ArcadeLevel.of(4), choices.level(), "the level keeps its value");
	}

	@Test
	public void aMissingEmptyNonNumericOrOutOfRangeLevelFallsBack() {
		Memory file = new Memory();
		ArcadeChoices choices = new ArcadeChoices(file);
		file.values.put(ArcadeChoices.LEVEL_KEY, "");
		assertSame(ArcadeLevel.of(1), choices.level());
		file.values.put(ArcadeChoices.LEVEL_KEY, "three");
		assertSame(ArcadeLevel.of(1), choices.level());
		file.values.put(ArcadeChoices.LEVEL_KEY, "9");
		assertSame(ArcadeLevel.of(5), choices.level(), "out of range: the nearest level");
		file.values.put(ArcadeChoices.LEVEL_KEY, "-2");
		assertSame(ArcadeLevel.of(1), choices.level());
		file.values.put(ArcadeChoices.LEVEL_KEY, " 2 ");
		assertSame(ArcadeLevel.of(2), choices.level(), "spaces are ignored");
	}

	@Test
	public void anUnknownOrEmptyPositionFallsBackToFun() {
		Memory file = new Memory();
		ArcadeChoices choices = new ArcadeChoices(file);
		file.values.put(ArcadeChoices.LENIENCY_KEY, "");
		assertSame(ArcadeLeniencyPosition.FUN, choices.leniency());
		file.values.put(ArcadeChoices.LENIENCY_KEY, "RECKLESS");
		assertSame(ArcadeLeniencyPosition.FUN, choices.leniency());
		file.values.put(ArcadeChoices.LENIENCY_KEY, "like_practice");
		assertSame(ArcadeLeniencyPosition.LIKE_PRACTICE, choices.leniency(), "the name is read whatever its case");
	}

	@Test
	public void anUnreadableFileKeepsTheDefaultsAndAFailedWriteIsReportedWithoutAnError() {
		Memory file = new Memory();
		file.unreadable = true;
		ArcadeChoices choices = new ArcadeChoices(file);
		assertSame(ArcadeLevel.of(1), choices.level());
		assertSame(ArcadeLeniencyPosition.FUN, choices.leniency());
		file.unreadable = false;
		file.unwritable = true;
		assertEquals(false, choices.setLevel(ArcadeLevel.of(2)), "a write that fails says so and throws nothing");
		assertEquals(false, choices.setLeniency(ArcadeLeniencyPosition.LIKE_PRACTICE));
		assertSame(ArcadeLevel.of(1), choices.level(), "nothing was stored");
	}
}
