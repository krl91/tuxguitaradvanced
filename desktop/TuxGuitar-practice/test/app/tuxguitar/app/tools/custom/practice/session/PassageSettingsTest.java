package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.util.TGContext;

/**
 * Preparation-window passage settings (owner decision 2026-09-28, FR-091): Off or 1/2/4
 * measures (default 2), threshold 25/50/75 % (default 50), kept for the application run only.
 */
public class PassageSettingsTest {

	@Test
	public void offersOffAndOneTwoFourMeasuresAnd255075Percent() {
		assertArrayEquals(new int[] { 0, 1, 2, 4 }, PassageSettings.MEASURE_OPTIONS);
		assertArrayEquals(new int[] { 25, 50, 75 }, PassageSettings.THRESHOLD_PERCENT_OPTIONS);
	}

	@Test
	public void defaultsToTwoMeasuresAndFiftyPercentWithTwoRetries() {
		PassageSettings settings = PassageSettings.getInstance(new TGContext());

		assertEquals(2, settings.getMeasures());
		assertEquals(50, settings.getThresholdPercent());
		assertFalse(settings.isOff());
		PassagePolicy policy = settings.toPolicy();
		assertEquals(2, policy.getMeasuresPerPassage());
		assertEquals(0.5, policy.getMaxErrorRate(), 1e-9);
		assertEquals(2, policy.getMaxRetries());
	}

	@Test
	public void theChosenValuesMakeThePolicy() {
		PassageSettings settings = PassageSettings.getInstance(new TGContext());
		settings.setMeasures(4);
		settings.setThresholdPercent(25);

		PassagePolicy policy = settings.toPolicy();

		assertEquals(4, policy.getMeasuresPerPassage());
		assertEquals(0.25, policy.getMaxErrorRate(), 1e-9);
		settings.setThresholdPercent(75);
		settings.setMeasures(1);
		assertEquals(0.75, settings.toPolicy().getMaxErrorRate(), 1e-9);
		assertEquals(1, settings.toPolicy().getMeasuresPerPassage());
	}

	@Test
	public void offGivesNoPolicyAndKeepsTheThresholdForLater() {
		PassageSettings settings = PassageSettings.getInstance(new TGContext());
		settings.setThresholdPercent(75);
		settings.setMeasures(PassageSettings.OFF);

		assertTrue(settings.isOff());
		assertNull(settings.toPolicy());
		settings.setMeasures(2);
		assertEquals(0.75, settings.toPolicy().getMaxErrorRate(), 1e-9);
	}

	@Test
	public void rejectsValuesThatAreNotOffered() {
		PassageSettings settings = PassageSettings.getInstance(new TGContext());

		assertThrows(IllegalArgumentException.class, () -> settings.setMeasures(3));
		assertThrows(IllegalArgumentException.class, () -> settings.setThresholdPercent(60));
		assertEquals(2, settings.getMeasures());
		assertEquals(50, settings.getThresholdPercent());
	}

	@Test
	public void theChoiceLastsForTheApplicationRunOnly() {
		TGContext run = new TGContext();
		PassageSettings.getInstance(run).setMeasures(PassageSettings.OFF);

		// the same run (e.g. the practice window closed and reopened) keeps it
		assertSame(PassageSettings.getInstance(run), PassageSettings.getInstance(run));
		assertTrue(PassageSettings.getInstance(run).isOff());
		// a new run starts from the defaults: nothing is saved
		assertEquals(2, PassageSettings.getInstance(new TGContext()).getMeasures());
	}

	// ---------------------------------------------------------------- T102: persistent Configuration values

	private static final class Memory implements RecognitionSettingsStore.Persistence {
		private java.util.Map<String, String> values = new java.util.HashMap<String, String>();

		public java.util.Map<String, String> read() {
			return new java.util.HashMap<String, String>(this.values);
		}

		public void write(java.util.Map<String, String> values) {
			this.values = new java.util.HashMap<String, String>(values);
		}
	}

	@Test
	public void theRunChoiceStartsFromTheSavedConfiguration() {
		Memory file = new Memory();
		RecognitionSettingsStore saved = new RecognitionSettingsStore(file);
		saved.update(saved.get().with(RecognitionParameters.PASSAGE_MEASURES, "4").with(RecognitionParameters.PASSAGE_THRESHOLD, "75")
				.with(RecognitionParameters.PASSAGE_RETRIES, 5));

		// next launch
		PassageSettings settings = new PassageSettings(new RecognitionSettingsStore(file));

		assertEquals(4, settings.getMeasures());
		assertEquals(75, settings.getThresholdPercent());
		assertEquals(5, settings.toPolicy().getMaxRetries());
	}

	@Test
	public void confirmingTheConfigurationResetsTheRunChoice() {
		RecognitionSettingsStore store = new RecognitionSettingsStore(new Memory());
		PassageSettings settings = new PassageSettings(store);
		settings.setMeasures(1);
		settings.setThresholdPercent(25);

		store.update(store.get().with(RecognitionParameters.PASSAGE_MEASURES, "0"));

		assertTrue(settings.isOff());
		assertNull(settings.toPolicy());
		assertEquals(50, settings.getThresholdPercent());
	}

	@Test
	public void theRunChoiceIsNotSaved() {
		Memory file = new Memory();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		PassageSettings settings = new PassageSettings(store);
		settings.setMeasures(PassageSettings.OFF);

		assertEquals(2, new PassageSettings(new RecognitionSettingsStore(file)).getMeasures());
		assertEquals(2, store.get().getPassageMeasures());
	}

	@Test
	public void theRetryLimitComesFromTheConfiguration() {
		RecognitionSettingsStore store = new RecognitionSettingsStore(new Memory());
		PassageSettings settings = new PassageSettings(store);
		assertEquals(2, settings.toPolicy().getMaxRetries());
		store.update(store.get().with(RecognitionParameters.PASSAGE_RETRIES, 0));
		assertEquals(0, settings.toPolicy().getMaxRetries());
		store.update(store.get().with(RecognitionParameters.PASSAGE_RETRIES, 99));
		assertEquals(5, settings.toPolicy().getMaxRetries());
	}
}
