package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevel;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;

/**
 * Spec 014 (T027, FR-006): the start window of an Arcade start asks the level, so that every launch offers the choice by hand (the Start button, the transport-bar
 * button and the quick start all go through this window). It is preselected with the level the player has (level 1 the first time, then the last chosen), shows the
 * five levels with their shares, and stores a change at once; a Follow or Wait start has no such row, and the constructor the existing tests use is unchanged.
 */
public class ArcadeStartLevelRowTest {

	private static final class Source implements PreRunSource {
		public StrumClipMeter.Reading reading() {
			return new StrumClipMeter.Reading(StrumVerdict.NO_STRUM, 0.0, Double.NaN);
		}

		public double levelDbfs() {
			return Double.NaN;
		}

		public boolean boostActive() {
			return false;
		}

		public void reset() {
		}

		public void close() {
		}
	}

	private static final class Tuner implements PreRunWindow.TunerOpener {
		public boolean available() {
			return true;
		}

		public void open() {
		}

		public void toFront() {
		}

		public void setClosedListener(Runnable listener) {
		}
	}

	private static final class Choice implements PreRunWindow.LevelChoice {
		ArcadeLevel level;
		final List<ArcadeLevel> stored = new ArrayList<ArcadeLevel>();

		Choice(ArcadeLevel level) {
			this.level = level;
		}

		public ArcadeLevel level() {
			return this.level;
		}

		public void choose(ArcadeLevel chosen) {
			this.level = chosen;
			this.stored.add(chosen);
		}
	}

	private static final PreRunWindow.Texts TEXTS = (key, arguments) -> key + (arguments.length == 0 ? "" : Arrays.toString(arguments));

	private final SwtContractToolkit toolkit = new SwtContractToolkit();

	private PreRunWindow arcade(Choice choice) {
		PreRunWindow window = new PreRunWindow(this.toolkit.factory(), null, new PreRunCheck(FollowSettings.Mode.ARCADE), new Source(), TEXTS, () -> 0L, new Tuner(), () -> {
		}, choice);
		window.refresh();
		return window;
	}

	@Test
	public void theArcadeStartWindowAsksTheLevelPreselectedWithTheOneThePlayerHas() {
		PreRunWindow first = arcade(new Choice(ArcadeLevel.of(1)));
		assertNotNull(first.levelSelect(), "the level row exists for an Arcade start");
		assertEquals(5, SwtContractToolkit.state(first.levelSelect()).items.size(), "the five levels");
		assertEquals(ArcadeLevel.of(1), first.levelSelect().getSelectedValue(), "level 1 the first time");
		PreRunWindow later = arcade(new Choice(ArcadeLevel.of(4)));
		assertEquals(ArcadeLevel.of(4), later.levelSelect().getSelectedValue(), "then the last level chosen");
	}

	@Test
	public void theFiveLevelsAreShownWithTheirSharesAndTheLabelOfTheRow() {
		PreRunWindow window = arcade(new Choice(ArcadeLevel.of(1)));
		for (int i = 0; i < 5; i++) {
			assertEquals("practice.arcade.level." + (i + 1), SwtContractToolkit.state(window.levelSelect()).items.get(i).getText());
		}
		assertEquals("practice.arcade.level.label", SwtContractToolkit.state(window.levelLabel()).properties.get("Text"));
		assertTrue(SwtContractToolkit.state(window.levelSelect()).properties.get("Visible") != Boolean.FALSE, "the row is shown from the start of the window");
	}

	@Test
	public void changingTheLevelStoresItAtOnce() {
		Choice choice = new Choice(ArcadeLevel.of(1));
		PreRunWindow window = arcade(choice);
		SwtContractToolkit.state(window.levelSelect()).selectedValue = ArcadeLevel.of(3);
		SwtContractToolkit.userSelects(window.levelSelect());
		assertEquals(List.of(ArcadeLevel.of(3)), choice.stored);
		assertEquals(ArcadeLevel.of(3), choice.level());
	}

	@Test
	public void theConstructorTheExistingTestsUseAndAStartWithoutAChoiceHaveNoLevelRow() {
		PreRunWindow plain = new PreRunWindow(this.toolkit.factory(), null, new PreRunCheck(FollowSettings.Mode.FOLLOW), new Source(), TEXTS, () -> 0L, new Tuner(), () -> {
		});
		plain.refresh();
		assertNull(plain.levelSelect(), "no row: the window is as before for Follow and Wait");
		PreRunWindow without = new PreRunWindow(this.toolkit.factory(), null, new PreRunCheck(FollowSettings.Mode.WAIT), new Source(), TEXTS, () -> 0L, new Tuner(), () -> {
		}, null);
		assertNull(without.levelSelect());
		assertFalse(without.hasLevelRow());
	}
}
