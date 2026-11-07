package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.LegatoDetector;
import app.tuxguitar.audio.engine.technique.SlideDetector;
import app.tuxguitar.audio.engine.technique.VibratoDetector;

/**
 * The technique family (spec 026, T026, inventory.md "TECHNIQUE_1" to "TECHNIQUE_5"): its defaults are the detectors' former parameters, the pitch
 * motion's outlier and minimum and the frame confidence of the sessions, and the values built from the settings carry them.
 */
public class TechniqueParametersTest {

	@Test
	public void theDefaultsAreTheFormerValues() {
		RecognitionSettings d = RecognitionSettings.defaults();
		assertEquals(bend(BendDetector.Parameters.defaults()), bend(TechniqueParameters.bend(d)));
		assertEquals(slide(SlideDetector.Parameters.defaults()), slide(TechniqueParameters.slide(d)));
		assertEquals(vibrato(VibratoDetector.Parameters.defaults()), vibrato(TechniqueParameters.vibrato(d)));
		assertEquals(legato(LegatoDetector.Parameters.defaults()), legato(TechniqueParameters.legato(d)));
		assertEquals(Arrays.asList(60.0, 30.0, 0.2, 0.6, 25.0, 30.0, 0.15, 0.5, 40.0, 8.0, 400.0, 8.0), bend(BendDetector.Parameters.defaults()));
		assertEquals(Arrays.asList(100.0, 40.0, 0.75, 6.0, 0.15, 0.5, 400.0, 8.0), slide(SlideDetector.Parameters.defaults()));
		assertEquals(Arrays.asList(25.0, 10.0, 2.5, 9.0, 0.35, 0.7, 400.0, 8.0), vibrato(VibratoDetector.Parameters.defaults()));
		assertEquals(Arrays.asList(0.1, 6.0, 3.0), legato(LegatoDetector.Parameters.defaults()));
		assertEquals(0.8, TechniqueParameters.frameConfidence(d));
	}

	@Test
	public void theValuesCarryTheSettings() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.TECHNIQUE_BEND_MIN_RANGE, 80)
				.with(RecognitionParameters.TECHNIQUE_BEND_EDGE, 12).with(RecognitionParameters.TECHNIQUE_SLIDE_STEPS, 9)
				.with(RecognitionParameters.TECHNIQUE_VIBRATO_MAX_RATE, 12).with(RecognitionParameters.TECHNIQUE_LEGATO_RISE, 4)
				.with(RecognitionParameters.TECHNIQUE_FRAME_CONFIDENCE, 0.9).with(RecognitionParameters.TECHNIQUE_MOTION_OUTLIER, 600)
				.with(RecognitionParameters.TECHNIQUE_MOTION_MIN_VOICED, 12).with(RecognitionParameters.TECHNIQUE_VIBRATO_MIN_DURATION, 0.5);
		assertEquals(80.0, TechniqueParameters.bend(s).minRangeCents);
		assertEquals(12, TechniqueParameters.bend(s).edgeHops);
		assertEquals(9, TechniqueParameters.slide(s).minMovingSteps);
		assertEquals(12.0, TechniqueParameters.vibrato(s).maxRateHz);
		assertEquals(0.5, TechniqueParameters.vibrato(s).minDurationSeconds);
		assertEquals(4.0, TechniqueParameters.legato(s).legatoRiseDb);
		assertEquals(0.9, TechniqueParameters.frameConfidence(s));
		for( double[] motion : new double[][] { { TechniqueParameters.bend(s).motionOutlierCents, TechniqueParameters.bend(s).motionMinVoiced },
				{ TechniqueParameters.slide(s).motionOutlierCents, TechniqueParameters.slide(s).motionMinVoiced },
				{ TechniqueParameters.vibrato(s).motionOutlierCents, TechniqueParameters.vibrato(s).motionMinVoiced } } ) {
			assertEquals(600.0, motion[0]);
			assertEquals(12.0, motion[1]);
		}
	}

	@Test
	public void theSectionsHoldTheirValues() {
		assertEquals(5, RecognitionParameters.of(Section.TECHNIQUE_1).size());
		assertEquals(5, RecognitionParameters.of(Section.TECHNIQUE_2).size());
		assertEquals(6, RecognitionParameters.of(Section.TECHNIQUE_3).size());
		assertEquals(6, RecognitionParameters.of(Section.TECHNIQUE_4).size());
		assertEquals(Arrays.asList(RecognitionParameters.TECHNIQUE_LEGATO_START_WINDOW, RecognitionParameters.TECHNIQUE_LEGATO_PICKED_RISE,
				RecognitionParameters.TECHNIQUE_LEGATO_RISE, RecognitionParameters.TECHNIQUE_FRAME_CONFIDENCE, RecognitionParameters.TECHNIQUE_MOTION_OUTLIER,
				RecognitionParameters.TECHNIQUE_MOTION_MIN_VOICED), RecognitionParameters.of(Section.TECHNIQUE_5));
	}

	private static List<Double> bend(BendDetector.Parameters p) {
		return Arrays.asList(p.minRangeCents, p.plainRangeCents, p.minDurationSeconds, p.minVoicedShare, p.minWrittenMoveCents, p.onToleranceCents,
				p.onToleranceShare, p.movedShare, p.startToleranceCents, (double) p.edgeHops, p.motionOutlierCents, (double) p.motionMinVoiced);
	}

	private static List<Double> slide(SlideDetector.Parameters p) {
		return Arrays.asList(p.minRangeCents, p.plainRangeCents, p.minMonotony, (double) p.minMovingSteps, p.minDurationSeconds, p.minVoicedShare,
				p.motionOutlierCents, (double) p.motionMinVoiced);
	}

	private static List<Double> vibrato(VibratoDetector.Parameters p) {
		return Arrays.asList(p.minDepthCents, p.plainDepthCents, p.minRateHz, p.maxRateHz, p.minDurationSeconds, p.minVoicedShare, p.motionOutlierCents,
				(double) p.motionMinVoiced);
	}

	private static List<Double> legato(LegatoDetector.Parameters p) {
		return Arrays.asList(p.startWindowSeconds, p.pickedRiseDb, p.legatoRiseDb);
	}
}
