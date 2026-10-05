package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueTarget;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.audio.engine.technique.DetectableTechnique;
import app.tuxguitar.audio.engine.technique.NoteSegment;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.audio.engine.technique.TechniqueEvidence;

/**
 * Turns the written techniques of an event and the measurements of its note into verdicts
 * (contracts/technique-detection.md). A technique is "not verifiable" when technique detection is
 * switched off (FR-032), when the engine has no detector for it, or when its benchmark has not
 * justified showing verdicts yet; display-only techniques are never judged. The verdict never changes
 * the pitch verdict or the progression (FR-030): this class only reads.
 */
public final class TechniqueJudge {

	private final TechniqueCatalog catalog;
	private final boolean enabled;

	public TechniqueJudge(TechniqueCatalog catalog, boolean enabled) {
		this.catalog = catalog;
		this.enabled = enabled;
	}

	/** The engine technique that detects a written one, or null when there is no detector. */
	static DetectableTechnique detectable(PlayingTechnique technique) {
		switch( technique ) {
			case HAMMER_ON:
			case PULL_OFF:
				return DetectableTechnique.LEGATO;
			case SLIDE:
				return DetectableTechnique.SLIDE;
			case BEND:
			case PRE_BEND:
			case RELEASE:
				return DetectableTechnique.BEND;
			case VIBRATO:
				return DetectableTechnique.VIBRATO;
			default:
				return null;
		}
	}

	/** True when the result for this technique can only be "not verifiable" (nothing needs measuring). */
	public boolean isNeverVerdict(PlayingTechnique technique) {
		if( !this.enabled || !technique.isJudged() ) {
			return true;
		}
		DetectableTechnique detectable = detectable(technique);
		TechniqueCatalog.Entry entry = (detectable == null ? null : this.catalog.entry(detectable));
		return entry == null || !entry.isVerified();
	}

	/** One result per judged written technique; display-only techniques have none. */
	public List<TechniqueResult> judge(List<TechniqueTarget> targets, NoteSegment segment) {
		List<TechniqueResult> results = new ArrayList<TechniqueResult>();
		for( TechniqueTarget target : targets ) {
			PlayingTechnique technique = target.getTechnique();
			if( !technique.isJudged() ) {
				continue;
			}
			if( isNeverVerdict(technique) || segment == null ) {
				results.add(TechniqueResult.notVerifiable(technique));
				continue;
			}
			TechniqueEvidence evidence = this.catalog.entry(detectable(technique)).getDetector().evaluate(segment);
			results.add(new TechniqueResult(technique, verdictOf(evidence.getVerdict()), evidence.getConfidence(), evidence.getMeasurements()));
		}
		return results;
	}

	private static TechniqueVerdict verdictOf(TechniqueEvidence.Verdict verdict) {
		switch( verdict ) {
			case DETECTED:
				return TechniqueVerdict.DETECTED;
			case NOT_DETECTED:
				return TechniqueVerdict.NOT_DETECTED;
			default:
				return TechniqueVerdict.UNCERTAIN;
		}
	}
}
