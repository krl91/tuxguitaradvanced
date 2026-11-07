package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.OrderedPair;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The wait family (spec 026, T021, inventory.md "WAIT_1", "WAIT_2"): its defaults are the former values of the Wait session (its constants and
 * the speed check's lead found inline) and of the practice parameters (the early stability, the weak input), and the values built from the
 * settings carry them.
 */
public class WaitParametersTest {

	private static final long MS = 1_000_000L;

	@Test
	public void theDefaultsAreTheFormerValues() {
		PracticeSession.WaitParameters d = PracticeSession.WaitParameters.defaults();
		assertEquals(values(d), values(PracticeSession.WaitParameters.of(RecognitionSettings.defaults())));
		assertEquals(Arrays.asList(1000.0 * MS, 120.0 * MS, 0.8, 1500.0 * MS, 1000.0 * MS, 2500.0 * MS, 0.5, 150.0 * MS, 200.0 * MS, 20.0 * MS, -30.0),
				values(d));
		assertEquals(PracticeSession.VERDICT_HOLD_NANOS, d.verdictHoldNanos);
		assertEquals(PracticeParameters.WEAK_FAST_DBFS, d.weakFastDbfs);
		PracticeParameters p = PracticeParameters.of(RecognitionSettings.defaults());
		assertEquals(PracticeParameters.EARLY_STABILITY_MARGIN, p.earlyStabilityMargin());
		assertEquals(PracticeParameters.EARLY_STABILITY_NANOS, p.earlyStabilityNanos());
	}

	@Test
	public void theValuesCarryTheSettings() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.WAIT_VERDICT_HOLD, 2000)
				.with(RecognitionParameters.WAIT_NEIGHBOUR_VETO, 300).with(RecognitionParameters.WAIT_NEIGHBOUR_CONFIDENCE, 0.9)
				.with(RecognitionParameters.WAIT_TECHNIQUE_WINDOW, 3000).with(RecognitionParameters.WAIT_BEND_WINDOW_MIN, 500)
				.with(RecognitionParameters.WAIT_BEND_WINDOW_MAX, 3500).with(RecognitionParameters.WAIT_BEND_MARGIN, 1.0)
				.with(RecognitionParameters.WAIT_TECHNIQUE_LEAD, 300).with(RecognitionParameters.WAIT_SPEED_WINDOW, 400)
				.with(RecognitionParameters.WAIT_SPEED_LEAD, 50).with(RecognitionParameters.WAIT_EARLY_MARGIN, 1.0)
				.with(RecognitionParameters.WAIT_EARLY_HOPS, 20).with(RecognitionParameters.WAIT_WEAK_FAST, -40.0);
		assertEquals(Arrays.asList(2000.0 * MS, 300.0 * MS, 0.9, 3000.0 * MS, 500.0 * MS, 3500.0 * MS, 1.0, 300.0 * MS, 400.0 * MS, 50.0 * MS, -40.0),
				values(PracticeParameters.of(s).waitParameters()));
		assertEquals(1.0, PracticeParameters.of(s).earlyStabilityMargin());
		assertEquals(20 * PracticeParameters.HOP_NANOS, PracticeParameters.of(s).earlyStabilityNanos());
	}

	@Test
	public void theSectionsHoldTheirValuesAndTheBendWindowStaysOrdered() {
		assertEquals(Arrays.asList(RecognitionParameters.WAIT_VERDICT_HOLD, RecognitionParameters.WAIT_NEIGHBOUR_VETO,
				RecognitionParameters.WAIT_NEIGHBOUR_CONFIDENCE, RecognitionParameters.WAIT_TECHNIQUE_WINDOW, RecognitionParameters.WAIT_BEND_WINDOW_MIN,
				RecognitionParameters.WAIT_BEND_WINDOW_MAX, RecognitionParameters.WAIT_BEND_MARGIN), RecognitionParameters.of(Section.WAIT_1));
		assertEquals(Arrays.asList(RecognitionParameters.WAIT_TECHNIQUE_LEAD, RecognitionParameters.WAIT_SPEED_WINDOW, RecognitionParameters.WAIT_SPEED_LEAD,
				RecognitionParameters.WAIT_EARLY_MARGIN, RecognitionParameters.WAIT_EARLY_HOPS, RecognitionParameters.WAIT_WEAK_FAST),
				RecognitionParameters.of(Section.WAIT_2));
		boolean declared = false;
		for( OrderedPair pair : RecognitionParameters.ORDERED_PAIRS ) {
			declared |= pair.getLower() == RecognitionParameters.WAIT_BEND_WINDOW_MIN && pair.getUpper() == RecognitionParameters.WAIT_BEND_WINDOW_MAX;
		}
		assertTrue(declared, "bend-window-min <= bend-window-max is declared");
	}

	private static List<Double> values(PracticeSession.WaitParameters p) {
		return Arrays.asList((double) p.verdictHoldNanos, (double) p.neighbourVetoNanos, p.neighbourConfidence, (double) p.techniqueWindowNanos,
				(double) p.bendWindowMinNanos, (double) p.bendWindowMaxNanos, p.bendMarginSemitones, (double) p.techniqueLeadNanos,
				(double) p.speedWindowNanos, (double) p.speedLeadNanos, p.weakFastDbfs);
	}
}
