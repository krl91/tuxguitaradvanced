package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AttackAnalyzer;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;

/**
 * The attack family (spec 026, T016, inventory.md "ATTACK_1" and "ATTACK_2"): its defaults are the former values of the attack analyser and of the
 * attack evidence's offset measure, the analyser's parameters and the native tuning array carry the settings, and the FFT size stays.
 */
public class AttackParametersTest {

	@Test
	public void theDefaultsAreTheFormerValues() {
		AttackAnalyzer.Parameters former = AttackAnalyzer.Parameters.defaults();
		assertEquals(former.lagHops, RecognitionParameters.ATTACK_LAG.getDefault().intValue());
		assertEquals(former.maxHz, RecognitionParameters.ATTACK_MAX_FREQUENCY.getDefault());
		assertEquals(former.partials, RecognitionParameters.ATTACK_PARTIALS.getDefault().intValue());
		assertEquals(former.beta, RecognitionParameters.ATTACK_BETA.getDefault());
		assertEquals(former.sigmaBins, RecognitionParameters.ATTACK_SIGMA.getDefault());
		assertEquals(12, AttackEvidence.DEFAULT_PARTIALS);
		assertEquals(60.0, AttackEvidence.DEFAULT_SEARCH_CENTS);
		assertEquals(0.03, AttackEvidence.DEFAULT_MIN_PEAK_SHARE);
		assertEquals(AttackEvidence.DEFAULT_PARTIALS, RecognitionParameters.ATTACK_EVIDENCE_PARTIALS.getDefault().intValue());
		assertEquals(AttackEvidence.DEFAULT_SEARCH_CENTS, RecognitionParameters.ATTACK_EVIDENCE_SEARCH.getDefault());
		assertEquals(AttackEvidence.DEFAULT_MIN_PEAK_SHARE, RecognitionParameters.ATTACK_EVIDENCE_PEAK.getDefault());
		assertEquals(values(former), values(AttackAnalyzer.Parameters.of(RecognitionSettings.defaults())));
	}

	@Test
	public void theAnalyserAndTheNativeArrayCarryTheSettings() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.ATTACK_LAG, 30).with(RecognitionParameters.ATTACK_MAX_FREQUENCY, 7000)
				.with(RecognitionParameters.ATTACK_PARTIALS, 9).with(RecognitionParameters.ATTACK_BETA, 0.8).with(RecognitionParameters.ATTACK_SIGMA, 2.0);
		assertEquals(Arrays.asList(4096.0, 30.0, 7000.0, 9.0, 0.8, 2.0), values(AttackAnalyzer.Parameters.of(s)));
		double[] tuning = NativeParameters.tuning(s);
		assertEquals(30.0, tuning[NativeParameters.T_ATTACK_LAG]);
		assertEquals(7000.0, tuning[NativeParameters.T_ATTACK_MAX_FREQUENCY]);
		assertEquals(9.0, tuning[NativeParameters.T_ATTACK_PARTIALS]);
		assertEquals(0.8, tuning[NativeParameters.T_ATTACK_BETA]);
		assertEquals(2.0, tuning[NativeParameters.T_ATTACK_SIGMA]);
	}

	@Test
	public void theTwoSectionsHoldTheirValues() {
		assertEquals(Arrays.asList(RecognitionParameters.ATTACK_LAG, RecognitionParameters.ATTACK_MAX_FREQUENCY, RecognitionParameters.ATTACK_PARTIALS,
				RecognitionParameters.ATTACK_BETA, RecognitionParameters.ATTACK_SIGMA), RecognitionParameters.of(Section.ATTACK_1));
		assertEquals(Arrays.asList(RecognitionParameters.ATTACK_EVIDENCE_PARTIALS, RecognitionParameters.ATTACK_EVIDENCE_SEARCH,
				RecognitionParameters.ATTACK_EVIDENCE_PEAK), RecognitionParameters.of(Section.ATTACK_2));
	}

	private static List<Double> values(AttackAnalyzer.Parameters p) {
		return Arrays.asList((double) p.fftSize, (double) p.lagHops, p.maxHz, (double) p.partials, p.beta, p.sigmaBins);
	}
}
