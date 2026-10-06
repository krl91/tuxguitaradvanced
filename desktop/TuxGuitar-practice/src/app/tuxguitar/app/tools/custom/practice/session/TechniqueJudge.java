package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueTarget;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.app.tools.custom.practice.model.PitchPath;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.BendPath;
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
			TechniqueEvidence evidence = evidenceOf(target, segment);
			results.add(new TechniqueResult(technique, verdictOf(evidence.getVerdict()), evidence.getConfidence(), evidence.getMeasurements()));
		}
		return results;
	}

	/** A written bend, pre-bend or release is judged against its path (one part of it each); every other technique by its detector alone. */
	private TechniqueEvidence evidenceOf(TechniqueTarget target, NoteSegment segment) {
		PlayingTechnique technique = target.getTechnique();
		Object detector = this.catalog.entry(detectable(technique)).getDetector();
		if( detector instanceof BendDetector && target.getPitchPath() != null && target.getBaseMidi() != TechniqueTarget.NONE ) {
			PitchPath path = target.getPitchPath();
			double[] pairs = new double[2 * path.getPoints().size()];
			for( int i = 0 ; i < path.getPoints().size() ; i ++ ) {
				pairs[2 * i] = path.getPoints().get(i).getRelativeTime();
				pairs[2 * i + 1] = path.getPoints().get(i).getSemitones();
			}
			BendDetector.Part part = (technique == PlayingTechnique.PRE_BEND ? BendDetector.Part.START
					: technique == PlayingTechnique.RELEASE ? BendDetector.Part.FALL : BendDetector.Part.RISE);
			return ((BendDetector) detector).evaluate(segment, target.getBaseMidi(), BendPath.of(pairs), part);
		}
		return this.catalog.entry(detectable(technique)).getDetector().evaluate(segment);
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
