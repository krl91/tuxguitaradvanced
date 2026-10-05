package app.tuxguitar.audio.engine.technique;

import java.util.EnumMap;
import java.util.Map;

/**
 * The single table of what the engine can detect: the detector of each technique, whether its result
 * may be shown as a verdict ("verified"), and the benchmark that justifies the flag (spec 002,
 * contracts/technique-detection.md rule 2). A technique stays unverified, so practice reports it as
 * "not verifiable", until a benchmark on recordings meets the criteria of SC-005 (at least 85 % of the
 * technique notes detected and under 10 % of the plain notes wrongly detected).
 */
public final class TechniqueCatalog {

	/** One row of the table. */
	public static final class Entry {
		private final TechniqueDetector detector;
		private final boolean verified;
		private final String benchmark;

		Entry(TechniqueDetector detector, boolean verified, String benchmark) {
			this.detector = detector;
			this.verified = verified;
			this.benchmark = benchmark;
		}

		public TechniqueDetector getDetector() {
			return this.detector;
		}

		/** True only when a benchmark justified showing this detector's verdicts. */
		public boolean isVerified() {
			return this.verified;
		}

		/** The measured result that justifies {@link #isVerified()} (or explains why it is false). */
		public String getBenchmark() {
			return this.benchmark;
		}
	}

	private final Map<DetectableTechnique, Entry> entries = new EnumMap<DetectableTechnique, Entry>(DetectableTechnique.class);

	private TechniqueCatalog() {
	}

	/** The catalogue as benchmarked today: every detector exists, none is verified yet. */
	public static TechniqueCatalog defaults() {
		TechniqueCatalog catalog = new TechniqueCatalog();
		catalog.entries.put(DetectableTechnique.VIBRATO, new Entry(new VibratoDetector(VibratoDetector.Parameters.defaults()), false,
				"IDMT-SMT-Guitar dataset 2: vibrato detected on 59 % of 116 vibrato notes (target 85 %), wrongly on 0.5 % of 3548 plain notes"));
		catalog.entries.put(DetectableTechnique.BEND, new Entry(new BendDetector(BendDetector.Parameters.defaults()), false,
				"IDMT-SMT-Guitar dataset 2: bend detected on 82 % of 51 bent notes (target 85 %), wrongly on 3.7 % of 3548 plain notes"));
		catalog.entries.put(DetectableTechnique.SLIDE, new Entry(new SlideDetector(SlideDetector.Parameters.defaults()), false,
				"IDMT-SMT-Guitar dataset 2: slide detected on 48 % of 93 slid notes (target 85 %), wrongly on 1.3 % of 3548 plain notes"));
		catalog.entries.put(DetectableTechnique.LEGATO, new Entry(new LegatoDetector(LegatoDetector.Parameters.defaults()), false,
				"no recording labelled hammer-on or pull-off is available: only synthetic signals and one written pair of the reference recording"));
		return catalog;
	}

	/** A catalogue with the given entries, for tests. */
	public static TechniqueCatalog of(TechniqueDetector detector, boolean verified, String benchmark) {
		TechniqueCatalog catalog = new TechniqueCatalog();
		catalog.entries.put(detector.technique(), new Entry(detector, verified, benchmark));
		return catalog;
	}

	/** The row of a technique, or null when the engine has no detector for it. */
	public Entry entry(DetectableTechnique technique) {
		return this.entries.get(technique);
	}
}
