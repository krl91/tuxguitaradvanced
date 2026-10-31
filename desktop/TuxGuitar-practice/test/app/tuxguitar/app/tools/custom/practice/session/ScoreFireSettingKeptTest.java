package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireParameter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;

/** Spec 019 (T009, FR-007, SC-007): the score fire's choice and values survive a restart, and a fresh install starts with Off. */
public class ScoreFireSettingKeptTest {

	@Test
	public void aFreshInstallStartsWithOff() {
		assertEquals(FlameSetting.OFF, new FireTuningStore(new FireTuningStoreTest.Memory()).scoreSetting());
	}

	@Test
	public void theChoiceAndTheValuesAreKeptForTheNextLaunch() {
		FireTuningStoreTest.Memory configuration = new FireTuningStoreTest.Memory();
		new FireTuningStore(configuration).setScoreSetting(FlameSetting.FULL);
		assertEquals(FlameSetting.FULL, new FireTuningStore(configuration).scoreSetting(), "a new store over the same configuration: a restart");
		new FireTuningStore(configuration).setScoreSetting(FlameSetting.DISCREET);
		assertEquals(FlameSetting.DISCREET, new FireTuningStore(configuration).scoreSetting());
		new FireTuningStore(configuration).save(FireTuning.SCORE_DEFAULTS.with(FireParameter.GROWTH_FULL, 24));
		assertEquals(24, new FireTuningStore(configuration).load(FireTuning.Group.SCORE).integer(FireParameter.GROWTH_FULL));
	}
}
