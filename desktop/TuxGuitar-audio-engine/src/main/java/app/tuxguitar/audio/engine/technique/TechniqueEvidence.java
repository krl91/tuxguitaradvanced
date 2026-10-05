package app.tuxguitar.audio.engine.technique;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a detector found in one note segment (spec 002, contracts/technique-detection.md). A detector
 * may only say detected, not detected or uncertain: "not verifiable" is the judge's word, for a
 * technique nobody could check. Immutable.
 */
public final class TechniqueEvidence {

	public enum Verdict {
		DETECTED,
		NOT_DETECTED,
		UNCERTAIN
	}

	private final DetectableTechnique technique;
	private final Verdict verdict;
	private final double confidence;
	private final Map<String, Double> measurements;

	public TechniqueEvidence(DetectableTechnique technique, Verdict verdict, double confidence, Map<String, Double> measurements) {
		if( technique == null || verdict == null ) {
			throw new IllegalArgumentException("technique and verdict are required");
		}
		if( !(confidence >= 0.0 && confidence <= 1.0) ) {
			throw new IllegalArgumentException("confidence must be in [0, 1]");
		}
		this.technique = technique;
		this.verdict = verdict;
		this.confidence = confidence;
		this.measurements = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(measurements));
	}

	public DetectableTechnique getTechnique() {
		return this.technique;
	}

	public Verdict getVerdict() {
		return this.verdict;
	}

	/** 0 (a guess) to 1 (far from every threshold). */
	public double getConfidence() {
		return this.confidence;
	}

	/** Named numbers behind the verdict (depth in cents, rate in Hz...), in a stable order. */
	public Map<String, Double> getMeasurements() {
		return this.measurements;
	}
}
