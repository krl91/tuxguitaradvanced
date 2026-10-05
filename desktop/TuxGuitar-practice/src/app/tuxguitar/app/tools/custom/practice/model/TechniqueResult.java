package app.tuxguitar.app.tools.custom.practice.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What practice says about one written technique of one event (data-model.md "TechniqueResult").
 * It never affects the pitch verdict or the progression. Immutable.
 */
public final class TechniqueResult {

	private final PlayingTechnique technique;
	private final TechniqueVerdict verdict;
	private final double confidence;
	private final Map<String, Double> measurements;

	public TechniqueResult(PlayingTechnique technique, TechniqueVerdict verdict, double confidence, Map<String, Double> measurements) {
		if( technique == null || verdict == null ) {
			throw new IllegalArgumentException("technique and verdict are required");
		}
		this.technique = technique;
		this.verdict = verdict;
		this.confidence = confidence;
		this.measurements = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(measurements));
	}

	public static TechniqueResult notVerifiable(PlayingTechnique technique) {
		return new TechniqueResult(technique, TechniqueVerdict.NOT_VERIFIABLE, 0.0, Collections.<String, Double>emptyMap());
	}

	public PlayingTechnique getTechnique() {
		return this.technique;
	}

	public TechniqueVerdict getVerdict() {
		return this.verdict;
	}

	public double getConfidence() {
		return this.confidence;
	}

	/** Named numbers behind the verdict; empty when not verifiable. */
	public Map<String, Double> getMeasurements() {
		return this.measurements;
	}
}
