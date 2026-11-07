package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The chord acceptance measures the offsets of the new partials with the settings of the run (spec 026, T016, section {@code ATTACK_2}); built
 * without settings, with the recommended values.
 */
public class PolyphonicAcceptanceSettingsTest {

	@Test
	public void theAcceptanceReadsTheOffsetMeasureOfItsSettings() {
		RecognitionSettings settings = RecognitionSettings.defaults().with(RecognitionParameters.ATTACK_EVIDENCE_SEARCH, 30);
		assertSame(settings, new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults(), settings).getSettings());
		assertEquals(60.0, new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()).getSettings().get(RecognitionParameters.ATTACK_EVIDENCE_SEARCH));
	}
}
