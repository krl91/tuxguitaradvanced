package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.InputAssessor;
import app.tuxguitar.audio.engine.input.InputAssessment;

/**
 * When a level check that failed starts again by itself (spec 025, FR-001, research R1): the check ends on "no signal" when nothing was played for
 * ten seconds and was run again only for a new input, so a learner who did not play at once stayed greyed. It starts again when the guitar is heard,
 * after "no signal" or "saturating" (the learner lowered the gain and plays again); an input lost or an access refused waits for the learner's fix.
 */
final class LevelRecheck {

	/** The confidence from which the check itself counts a frame as playing. */
	private static final double PLAYING_CONFIDENCE = new InputAssessor.Parameters().playingConfidence;

	private LevelRecheck() {
	}

	static boolean shouldCheck(InputAssessment assessment, boolean checking, boolean runGoing, AnalysisFrame frame) {
		if( checking || runGoing || assessment.getStatus() != InputAssessment.Status.UNUSABLE ) {
			return false;
		}
		InputAssessment.Reason reason = assessment.getReason();
		if( reason != InputAssessment.Reason.NO_SIGNAL && reason != InputAssessment.Reason.CLIPPING ) {
			return false;
		}
		return !Double.isNaN(frame.getF0Hz()) && frame.getConfidence() >= PLAYING_CONFIDENCE;
	}
}
