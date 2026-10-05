package app.tuxguitar.audio.engine.technique;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hammer-on and pull-off: the note of the segment begins without a new articulation and without the
 * level jump of a pick. The segment starts at the moment the new pitch is heard. A hammer-on may
 * raise the level a little (the string is struck by the finger), a pull-off must not raise it.
 */
public final class LegatoDetector implements TechniqueDetector {

	public static final class Parameters {
		/** The start of the note in which an articulation means a picked note. */
		public double startWindowSeconds = 0.1;
		/** A level rise above this at the start says the string was picked. */
		public double pickedRiseDb = 6.0;
		/** A level rise below this at the start is compatible with legato. */
		public double legatoRiseDb = 3.0;

		public static Parameters defaults() {
			return new Parameters();
		}
	}

	private final Parameters parameters;

	public LegatoDetector(Parameters parameters) {
		this.parameters = parameters;
	}

	@Override
	public DetectableTechnique technique() {
		return DetectableTechnique.LEGATO;
	}

	@Override
	public TechniqueEvidence evaluate(NoteSegment segment) {
		Map<String, Double> m = new LinkedHashMap<String, Double>();
		int window = Math.min(segment.size(), Math.max(2, (int) Math.round(this.parameters.startWindowSeconds / segment.getHopSeconds())));
		if( window < 2 ) {
			return new TechniqueEvidence(DetectableTechnique.LEGATO, TechniqueEvidence.Verdict.UNCERTAIN, 0.0, m);
		}
		int onsets = 0;
		double lowest = Double.POSITIVE_INFINITY;
		double highest = Double.NEGATIVE_INFINITY;
		for( int i = 0 ; i < window ; i ++ ) {
			onsets += (segment.onsetAt(i) ? 1 : 0);
			lowest = Math.min(lowest, segment.levelDbAt(i));
			highest = Math.max(highest, segment.levelDbAt(i));
		}
		double rise = highest - lowest;
		m.put("onsets", (double) onsets);
		m.put("levelRiseDb", rise);
		if( onsets > 0 || rise >= this.parameters.pickedRiseDb ) {
			return new TechniqueEvidence(DetectableTechnique.LEGATO, TechniqueEvidence.Verdict.NOT_DETECTED, onsets > 0 ? 0.8 : 0.6, m);
		}
		if( rise <= this.parameters.legatoRiseDb ) {
			return new TechniqueEvidence(DetectableTechnique.LEGATO, TechniqueEvidence.Verdict.DETECTED, 0.6, m);
		}
		return new TechniqueEvidence(DetectableTechnique.LEGATO, TechniqueEvidence.Verdict.UNCERTAIN, 0.3, m);
	}
}
