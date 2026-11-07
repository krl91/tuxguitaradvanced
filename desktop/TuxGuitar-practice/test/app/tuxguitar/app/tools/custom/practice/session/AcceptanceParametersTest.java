package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The acceptance family (spec 026, T020, inventory.md "ACCEPTANCE_1" to "ACCEPTANCE_3"): its defaults are the former values of the chord
 * acceptance (its parameters, its constants and the two numbers found inline), and the parameters built from the settings carry them.
 */
public class AcceptanceParametersTest {

	@Test
	public void theDefaultsAreTheFormerValues() {
		assertEquals(values(PolyphonicAcceptance.Parameters.defaults()), values(PolyphonicAcceptance.Parameters.of(RecognitionSettings.defaults())));
		assertEquals(PolyphonicAcceptance.DEFAULT_OFFSET_MARGIN, RecognitionParameters.ACCEPTANCE_OFFSET_MARGIN.getDefault());
		assertEquals(PolyphonicAcceptance.DEFAULT_DECAY, RecognitionParameters.ACCEPTANCE_DECAY.getDefault().intValue());
		assertEquals(PolyphonicAcceptance.DEFAULT_GUARD_NANOS, RecognitionParameters.ACCEPTANCE_GUARD.getDefault().longValue() * 1_000_000L);
		assertEquals(PolyphonicAcceptance.DEFAULT_MIN_TOTAL, RecognitionParameters.ACCEPTANCE_MIN_TOTAL.getDefault());
		assertEquals(PolyphonicAcceptance.DEFAULT_FLOOR_HOPS, RecognitionParameters.ACCEPTANCE_FLOOR_HOPS.getDefault().intValue());
		assertEquals(PolyphonicAcceptance.DEFAULT_SILENCE_STRENGTH, RecognitionParameters.ACCEPTANCE_SILENCE.getDefault());
		assertEquals(PolyphonicAcceptance.DEFAULT_MIN_ONSET_TOTAL, RecognitionParameters.ACCEPTANCE_MIN_ONSET.getDefault());
		assertEquals(3.0, RecognitionParameters.ACCEPTANCE_ONSET_FLOOR.getDefault());
		assertEquals(0.25, RecognitionParameters.ACCEPTANCE_RELEASE.getDefault());
		assertEquals(PolyphonicAcceptance.DEFAULT_ONSET_FLOOR, RecognitionParameters.ACCEPTANCE_ONSET_FLOOR.getDefault());
		assertEquals(PolyphonicAcceptance.DEFAULT_RELEASE, RecognitionParameters.ACCEPTANCE_RELEASE.getDefault());
	}

	@Test
	public void theParametersCarryTheSettings() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.ACCEPTANCE_MIN_SHARE, 0.5)
				.with(RecognitionParameters.ACCEPTANCE_MIN_BALANCE, 0.2).with(RecognitionParameters.ACCEPTANCE_MIN_PEAK, 0.8)
				.with(RecognitionParameters.ACCEPTANCE_HOPS, 9).with(RecognitionParameters.ACCEPTANCE_NEIGHBOUR, 1.5)
				.with(RecognitionParameters.ACCEPTANCE_ATTACK_STRENGTH, 0.4).with(RecognitionParameters.ACCEPTANCE_REARM, 300);
		assertEquals(Arrays.asList(0.5, 0.2, 0.8, 9.0, 1.5, 0.4, 300_000_000.0), values(PolyphonicAcceptance.Parameters.of(s)));
	}

	@Test
	public void theSectionsHoldTheirValues() {
		assertEquals(Arrays.asList(RecognitionParameters.ACCEPTANCE_MIN_SHARE, RecognitionParameters.ACCEPTANCE_MIN_BALANCE,
				RecognitionParameters.ACCEPTANCE_MIN_PEAK, RecognitionParameters.ACCEPTANCE_HOPS, RecognitionParameters.ACCEPTANCE_NEIGHBOUR,
				RecognitionParameters.ACCEPTANCE_ATTACK_STRENGTH, RecognitionParameters.ACCEPTANCE_REARM), RecognitionParameters.of(Section.ACCEPTANCE_1));
		assertEquals(Arrays.asList(RecognitionParameters.ACCEPTANCE_OFFSET_MARGIN, RecognitionParameters.ACCEPTANCE_DECAY,
				RecognitionParameters.ACCEPTANCE_GUARD, RecognitionParameters.ACCEPTANCE_MIN_TOTAL, RecognitionParameters.ACCEPTANCE_FLOOR_HOPS,
				RecognitionParameters.ACCEPTANCE_SILENCE, RecognitionParameters.ACCEPTANCE_MIN_ONSET), RecognitionParameters.of(Section.ACCEPTANCE_2));
		assertEquals(Arrays.asList(RecognitionParameters.ACCEPTANCE_ONSET_FLOOR, RecognitionParameters.ACCEPTANCE_RELEASE),
				RecognitionParameters.of(Section.ACCEPTANCE_3));
	}

	private static List<Double> values(PolyphonicAcceptance.Parameters p) {
		return Arrays.asList(p.minShare, p.minBalance, p.minPeakRatio, (double) p.hops, p.neighbourRatio, p.minAttackStrength, (double) p.rearmNanos);
	}
}
