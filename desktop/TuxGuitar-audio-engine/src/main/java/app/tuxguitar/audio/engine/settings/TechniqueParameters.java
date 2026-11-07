package app.tuxguitar.audio.engine.settings;

import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.LegatoDetector;
import app.tuxguitar.audio.engine.technique.SlideDetector;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.audio.engine.technique.VibratoDetector;

/**
 * The technique detectors' values read from the settings (spec 026, T026, sections {@code TECHNIQUE_1} to {@code TECHNIQUE_5}); the defaults give the
 * detectors' former parameters. Taken into account when a run builds its catalogue.
 */
public final class TechniqueParameters {

	private TechniqueParameters() {
	}

	public static BendDetector.Parameters bend(RecognitionSettings s) {
		BendDetector.Parameters p = BendDetector.Parameters.defaults();
		p.minRangeCents = s.get(RecognitionParameters.TECHNIQUE_BEND_MIN_RANGE);
		p.plainRangeCents = s.get(RecognitionParameters.TECHNIQUE_BEND_PLAIN_RANGE);
		p.minDurationSeconds = s.get(RecognitionParameters.TECHNIQUE_BEND_MIN_DURATION);
		p.minVoicedShare = s.get(RecognitionParameters.TECHNIQUE_BEND_VOICED);
		p.minWrittenMoveCents = s.get(RecognitionParameters.TECHNIQUE_BEND_WRITTEN_MOVE);
		p.onToleranceCents = s.get(RecognitionParameters.TECHNIQUE_BEND_ON_TOLERANCE);
		p.onToleranceShare = s.get(RecognitionParameters.TECHNIQUE_BEND_ON_SHARE);
		p.movedShare = s.get(RecognitionParameters.TECHNIQUE_BEND_MOVED_SHARE);
		p.startToleranceCents = s.get(RecognitionParameters.TECHNIQUE_BEND_START_TOLERANCE);
		p.edgeHops = (int) s.get(RecognitionParameters.TECHNIQUE_BEND_EDGE);
		p.motionOutlierCents = s.get(RecognitionParameters.TECHNIQUE_MOTION_OUTLIER);
		p.motionMinVoiced = (int) s.get(RecognitionParameters.TECHNIQUE_MOTION_MIN_VOICED);
		return p;
	}

	public static SlideDetector.Parameters slide(RecognitionSettings s) {
		SlideDetector.Parameters p = SlideDetector.Parameters.defaults();
		p.minRangeCents = s.get(RecognitionParameters.TECHNIQUE_SLIDE_MIN_RANGE);
		p.plainRangeCents = s.get(RecognitionParameters.TECHNIQUE_SLIDE_PLAIN_RANGE);
		p.minMonotony = s.get(RecognitionParameters.TECHNIQUE_SLIDE_MONOTONY);
		p.minMovingSteps = (int) s.get(RecognitionParameters.TECHNIQUE_SLIDE_STEPS);
		p.minDurationSeconds = s.get(RecognitionParameters.TECHNIQUE_SLIDE_MIN_DURATION);
		p.minVoicedShare = s.get(RecognitionParameters.TECHNIQUE_SLIDE_VOICED);
		p.motionOutlierCents = s.get(RecognitionParameters.TECHNIQUE_MOTION_OUTLIER);
		p.motionMinVoiced = (int) s.get(RecognitionParameters.TECHNIQUE_MOTION_MIN_VOICED);
		return p;
	}

	public static VibratoDetector.Parameters vibrato(RecognitionSettings s) {
		VibratoDetector.Parameters p = VibratoDetector.Parameters.defaults();
		p.minDepthCents = s.get(RecognitionParameters.TECHNIQUE_VIBRATO_DEPTH);
		p.plainDepthCents = s.get(RecognitionParameters.TECHNIQUE_VIBRATO_PLAIN_DEPTH);
		p.minRateHz = s.get(RecognitionParameters.TECHNIQUE_VIBRATO_MIN_RATE);
		p.maxRateHz = s.get(RecognitionParameters.TECHNIQUE_VIBRATO_MAX_RATE);
		p.minDurationSeconds = s.get(RecognitionParameters.TECHNIQUE_VIBRATO_MIN_DURATION);
		p.minVoicedShare = s.get(RecognitionParameters.TECHNIQUE_VIBRATO_VOICED);
		p.motionOutlierCents = s.get(RecognitionParameters.TECHNIQUE_MOTION_OUTLIER);
		p.motionMinVoiced = (int) s.get(RecognitionParameters.TECHNIQUE_MOTION_MIN_VOICED);
		return p;
	}

	public static LegatoDetector.Parameters legato(RecognitionSettings s) {
		LegatoDetector.Parameters p = LegatoDetector.Parameters.defaults();
		p.startWindowSeconds = s.get(RecognitionParameters.TECHNIQUE_LEGATO_START_WINDOW);
		p.pickedRiseDb = s.get(RecognitionParameters.TECHNIQUE_LEGATO_PICKED_RISE);
		p.legatoRiseDb = s.get(RecognitionParameters.TECHNIQUE_LEGATO_RISE);
		return p;
	}

	/** The catalogue of a run, its detectors built from the settings. */
	public static TechniqueCatalog catalog(RecognitionSettings s) {
		return TechniqueCatalog.of(vibrato(s), bend(s), slide(s), legato(s));
	}

	/** The confidence a pitch frame needs to be kept for a technique, in Wait and Follow (0 to 1). */
	public static double frameConfidence(RecognitionSettings s) {
		return s.get(RecognitionParameters.TECHNIQUE_FRAME_CONFIDENCE);
	}
}
