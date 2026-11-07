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
 * The follow family (spec 026, T022, inventory.md "FOLLOW_1" to "FOLLOW_3"): its defaults are the former values of the Follow session (its constants and
 * the two numbers found inline), and the values built from the settings carry them.
 */
public class FollowParametersTest {

	private static final double MS = 1_000_000.0;

	@Test
	public void theDefaultsAreTheFormerValues() {
		assertEquals(Arrays.asList(400 * MS, 40 * MS, 40 * MS, 100 * MS, 1500 * MS, 8.0, 3000 * MS, 150 * MS, 30 * MS, 100 * MS, 100 * MS, 1200 * MS, 1500 * MS,
				1000 * MS, 2500 * MS, 0.5, 400 * MS), values(FollowSession.FollowParameters.defaults()));
		assertEquals(values(FollowSession.FollowParameters.defaults()), values(PracticeParameters.of(RecognitionSettings.defaults()).followParameters()));
	}

	@Test
	public void theValuesCarryTheSettings() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.FOLLOW_EVIDENCE_HORIZON, 500)
				.with(RecognitionParameters.FOLLOW_SILENCE, 50).with(RecognitionParameters.FOLLOW_SHORT_NOTE, 60).with(RecognitionParameters.FOLLOW_SAME_SOUND, 70)
				.with(RecognitionParameters.FOLLOW_RINGING, 2000).with(RecognitionParameters.FOLLOW_SCAN_BACK, 3).with(RecognitionParameters.FOLLOW_ONSET_MEMORY, 4000)
				.with(RecognitionParameters.FOLLOW_REFINE_BEFORE, 160).with(RecognitionParameters.FOLLOW_REFINE_AFTER, 40)
				.with(RecognitionParameters.FOLLOW_AFTER_CLOSE, 110).with(RecognitionParameters.FOLLOW_TAIL, 120).with(RecognitionParameters.FOLLOW_HISTORY, 1300)
				.with(RecognitionParameters.FOLLOW_TECHNIQUE_WINDOW, 1600).with(RecognitionParameters.FOLLOW_BEND_WINDOW_MIN, 900)
				.with(RecognitionParameters.FOLLOW_BEND_WINDOW_MAX, 3000).with(RecognitionParameters.FOLLOW_BEND_MARGIN, 0.7)
				.with(RecognitionParameters.FOLLOW_EVIDENCE_LEAD, 450);
		assertEquals(Arrays.asList(500 * MS, 50 * MS, 60 * MS, 70 * MS, 2000 * MS, 3.0, 4000 * MS, 160 * MS, 40 * MS, 110 * MS, 120 * MS, 1300 * MS, 1600 * MS,
				900 * MS, 3000 * MS, 0.7, 450 * MS), values(FollowSession.FollowParameters.of(s)));
	}

	@Test
	public void theSectionsHoldTheirValuesAndTheBendWindowStaysOrdered() {
		assertEquals(Arrays.asList(RecognitionParameters.FOLLOW_EVIDENCE_HORIZON, RecognitionParameters.FOLLOW_SILENCE, RecognitionParameters.FOLLOW_SHORT_NOTE,
				RecognitionParameters.FOLLOW_SAME_SOUND, RecognitionParameters.FOLLOW_RINGING, RecognitionParameters.FOLLOW_SCAN_BACK,
				RecognitionParameters.FOLLOW_ONSET_MEMORY), RecognitionParameters.of(Section.FOLLOW_1));
		assertEquals(Arrays.asList(RecognitionParameters.FOLLOW_REFINE_BEFORE, RecognitionParameters.FOLLOW_REFINE_AFTER, RecognitionParameters.FOLLOW_AFTER_CLOSE,
				RecognitionParameters.FOLLOW_TAIL, RecognitionParameters.FOLLOW_HISTORY), RecognitionParameters.of(Section.FOLLOW_2));
		assertEquals(Arrays.asList(RecognitionParameters.FOLLOW_TECHNIQUE_WINDOW, RecognitionParameters.FOLLOW_BEND_WINDOW_MIN,
				RecognitionParameters.FOLLOW_BEND_WINDOW_MAX, RecognitionParameters.FOLLOW_BEND_MARGIN, RecognitionParameters.FOLLOW_EVIDENCE_LEAD),
				RecognitionParameters.of(Section.FOLLOW_3));
		boolean declared = false;
		for( OrderedPair pair : RecognitionParameters.ORDERED_PAIRS ) {
			declared |= pair.getLower() == RecognitionParameters.FOLLOW_BEND_WINDOW_MIN && pair.getUpper() == RecognitionParameters.FOLLOW_BEND_WINDOW_MAX;
		}
		assertTrue(declared, "bend-window-min <= bend-window-max is declared");
	}

	private static List<Double> values(FollowSession.FollowParameters p) {
		return Arrays.asList((double) p.evidenceHorizonNanos, (double) p.silenceNanos, (double) p.shortNoteNanos, (double) p.sameSoundNanos,
				(double) p.ringingNanos, (double) p.scanBack, (double) p.onsetMemoryNanos, (double) p.refineBeforeNanos, (double) p.refineAfterNanos,
				(double) p.afterCloseNanos, (double) p.tailNanos, (double) p.historyNanos, (double) p.techniqueWindowNanos, (double) p.bendWindowMinNanos,
				(double) p.bendWindowMaxNanos, p.bendMarginSemitones, (double) p.evidenceLeadNanos);
	}
}
