package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireParameter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;

/** Spec 019 (T007, FR-019, research R5): the values of both fires are kept, only those that differ from the default, and never with an error. */
public class FireTuningStoreTest {

	static final class Memory implements ArcadeChoices.Persistence {
		final Map<String, String> values = new HashMap<String, String>();

		public String get(String key) {
			return this.values.get(key);
		}

		public void set(String key, String value) {
			this.values.put(key, value);
		}

		int written() {
			int count = 0;
			for (String value : this.values.values()) {
				if (value != null && !value.isEmpty()) {
					count++;
				}
			}
			return count;
		}
	}

	@Test
	public void whatIsSavedIsLoadedAgainAndOnlyTheChangedValuesAreWritten() {
		Memory memory = new Memory();
		FireTuningStore store = new FireTuningStore(memory);
		FireTuning arcade = FireTuning.ARCADE_DEFAULTS.with(FireParameter.OPACITY_MOST, 0.4).with(FireParameter.COLOUR_3, 0x102030);
		FireTuning score = FireTuning.SCORE_DEFAULTS.with(FireParameter.SPARKS_BASE, 77);
		assertTrue(store.save(arcade));
		assertTrue(store.save(score));
		assertEquals(3, memory.written(), "three values differ from their default");
		assertEquals("#102030", memory.values.get("arcade.colour.3"));
		assertEquals(arcade, store.load(FireTuning.Group.ARCADE));
		assertEquals(score, store.load(FireTuning.Group.SCORE));
		assertTrue(store.save(FireTuning.ARCADE_DEFAULTS));
		assertEquals(1, memory.written(), "a value back at its default is cleared");
	}

	@Test
	public void resetBringsOneGroupBackToItsDefaults() {
		Memory memory = new Memory();
		FireTuningStore store = new FireTuningStore(memory);
		store.save(FireTuning.ARCADE_DEFAULTS.with(FireParameter.CELL_COLUMNS, 96));
		store.save(FireTuning.SCORE_DEFAULTS.with(FireParameter.CELL_COLUMNS, 64));
		assertTrue(store.reset(FireTuning.Group.SCORE));
		assertEquals(FireTuning.SCORE_DEFAULTS, store.load(FireTuning.Group.SCORE));
		assertEquals(96, store.load(FireTuning.Group.ARCADE).integer(FireParameter.CELL_COLUMNS), "the other group is not touched");
	}

	@Test
	public void aBadValueGivesTheDefaultOrTheValueWithinRange() {
		Memory memory = new Memory();
		memory.values.put("score.opacity.most", "abc");
		memory.values.put("score.cell.columns", "5000");
		memory.values.put("score.colour.1", "102030");
		memory.values.put("score.sparks.life", "NaN");
		FireTuning tuning = new FireTuningStore(memory).load(FireTuning.Group.SCORE);
		assertEquals(FireParameter.OPACITY_MOST.scoreDefault(), tuning.value(FireParameter.OPACITY_MOST), 0.0);
		assertEquals(384, tuning.integer(FireParameter.CELL_COLUMNS));
		assertEquals((int) FireParameter.COLOUR_1.scoreDefault(), tuning.colour(FireParameter.COLOUR_1), "a colour without # is not read");
		assertEquals(FireParameter.SPARKS_LIFE.scoreDefault(), tuning.value(FireParameter.SPARKS_LIFE), 0.0);
	}

	@Test
	public void aStoreThatFailsGivesTheDefaultsAndNeverAnError() {
		ArcadeChoices.Persistence broken = new ArcadeChoices.Persistence() {
			public String get(String key) {
				throw new IllegalStateException("unreadable");
			}

			public void set(String key, String value) {
				throw new IllegalStateException("unwritable");
			}
		};
		FireTuningStore store = new FireTuningStore(broken);
		assertEquals(FireTuning.ARCADE_DEFAULTS, store.load(FireTuning.Group.ARCADE));
		assertFalse(store.save(FireTuning.ARCADE_DEFAULTS.with(FireParameter.OPACITY_MOST, 0.3)));
		assertFalse(store.reset(FireTuning.Group.SCORE));
		assertEquals(FlameSetting.OFF, store.scoreSetting());
		assertFalse(store.setScoreSetting(FlameSetting.FULL));
	}

	@Test
	public void theScoreFireIsOffUntilChosen() {
		Memory memory = new Memory();
		FireTuningStore store = new FireTuningStore(memory);
		assertEquals(FlameSetting.OFF, store.scoreSetting());
		memory.values.put(FireTuningStore.SETTING_KEY, "LOUD");
		assertEquals(FlameSetting.OFF, store.scoreSetting(), "an unknown value is Off");
		assertTrue(store.setScoreSetting(FlameSetting.FULL));
		assertEquals(FlameSetting.FULL, store.scoreSetting());
	}
}
