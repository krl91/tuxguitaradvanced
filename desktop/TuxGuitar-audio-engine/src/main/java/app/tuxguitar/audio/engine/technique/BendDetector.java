package app.tuxguitar.audio.engine.technique;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bend (and release, pre-bend): the pitch of the note moves by tens of cents without oscillating. The
 * amount it reaches is reported (cents); comparing it with the written bend is the judge's business.
 */
public final class BendDetector implements TechniqueDetector {

	public static final class Parameters {
		public double minRangeCents = 60.0;
		public double plainRangeCents = 30.0;
		public double minDurationSeconds = 0.2;
		public double minVoicedShare = 0.6;
		/** A written move smaller than this (cents) is not judged: a quarter of a quarter tone. */
		public double minWrittenMoveCents = 25.0;
		/** The pitch reached is on target within the larger of this (cents) and {@link #onToleranceShare} of the written move. */
		public double onToleranceCents = 30.0;
		public double onToleranceShare = 0.15;
		/** Under target but at least this share of the written move: uncertain; less: not detected. */
		public double movedShare = 0.5;
		/** A pre-bend starts on pitch within this (cents) of the bent pitch. */
		public double startToleranceCents = 40.0;
		/** Hops whose median gives the start and the end pitch. */
		public int edgeHops = 8;

		public static Parameters defaults() {
			return new Parameters();
		}
	}

	private final Parameters parameters;

	public BendDetector(Parameters parameters) {
		this.parameters = parameters;
	}

	@Override
	public DetectableTechnique technique() {
		return DetectableTechnique.BEND;
	}

	@Override
	public TechniqueEvidence evaluate(NoteSegment segment) {
		PitchMotion motion = new PitchMotion(segment);
		Map<String, Double> m = new LinkedHashMap<String, Double>();
		m.put("rangeCents", motion.range);
		m.put("driftCents", motion.drift);
		double voicedShare = motion.voicedShare(segment.size());
		m.put("voicedShare", voicedShare);
		if( segment.getDurationSeconds() < this.parameters.minDurationSeconds || voicedShare < this.parameters.minVoicedShare || motion.voiced < PitchMotion.MIN_VOICED ) {
			return new TechniqueEvidence(DetectableTechnique.BEND, TechniqueEvidence.Verdict.UNCERTAIN, 0.0, m);
		}
		if( motion.range >= this.parameters.minRangeCents ) {
			return new TechniqueEvidence(DetectableTechnique.BEND, TechniqueEvidence.Verdict.DETECTED, Math.min(1.0, motion.range / (2.0 * this.parameters.minRangeCents)), m);
		}
		if( motion.range < this.parameters.plainRangeCents ) {
			return new TechniqueEvidence(DetectableTechnique.BEND, TechniqueEvidence.Verdict.NOT_DETECTED, Math.min(1.0, 1.5 - motion.range / this.parameters.plainRangeCents), m);
		}
		return new TechniqueEvidence(DetectableTechnique.BEND, TechniqueEvidence.Verdict.UNCERTAIN, 0.3, m);
	}

	/**
	 * The bend judged against its written path. {@code segment} runs from the acceptance of the note (its
	 * attack) to the end of the observation window. The pitch reached (95th percentile of the voiced hops)
	 * is compared with the written peak and, for a release, the pitch at the end with the written end; a
	 * pre-bend is checked on the pitch the note starts at. Measurements, in cents: {@code riseCents},
	 * {@code targetRiseCents}, {@code fallCents}, {@code targetFallCents}, {@code startErrorCents} (each
	 * only when the path has that part) and {@code outcome} (-1 under the target, 0 on pitch, +1 over). The
	 * verdict is the weakest part: detected only when every written part was made, so a bend is never
	 * called made before it has reached its target; a part that is under the target is uncertain when the
	 * pitch moved by at least {@link Parameters#movedShare} of the written move, not detected otherwise.
	 *
	 * @param baseMidi the fretted pitch the path is relative to
	 */
	public TechniqueEvidence evaluate(NoteSegment segment, double baseMidi, BendPath path) {
		Map<String, Double> m = new LinkedHashMap<String, Double>();
		double[] voiced = new double[segment.size()];
		int count = 0;
		// outliers (an octave error, a neighbouring string) are further than the written range plus three semitones
		double low = baseMidi + path.lowestSemitones() - 3.0;
		double high = baseMidi + path.peakSemitones() + 3.0;
		for( int i = 0 ; i < segment.size() ; i ++ ) {
			double midi = segment.midiAt(i);
			if( Double.isFinite(midi) && midi >= low && midi <= high ) {
				voiced[count ++] = midi;
			}
		}
		m.put("voicedShare", segment.size() == 0 ? 0.0 : (double) count / segment.size());
		if( count < PitchMotion.MIN_VOICED ) {
			return new TechniqueEvidence(DetectableTechnique.BEND, TechniqueEvidence.Verdict.UNCERTAIN, 0.0, m);
		}
		int edge = Math.min(this.parameters.edgeHops, count);
		double startObserved = median(Arrays.copyOfRange(voiced, 0, edge));
		double endObserved = median(Arrays.copyOfRange(voiced, count - edge, count));
		double[] sorted = Arrays.copyOf(voiced, count);
		Arrays.sort(sorted);
		double peakObserved = sorted[(int) Math.min(count - 1, Math.floor(0.95 * count))];

		double writtenStart = path.startSemitones() * 100.0;
		double writtenPeak = path.peakSemitones() * 100.0;
		double writtenEnd = path.endSemitones() * 100.0;
		int rank = 2;
		int outcome = 0;
		boolean outcomeSet = false;
		double confidence = 1.0;

		double riseWritten = writtenPeak - writtenStart;
		if( riseWritten >= this.parameters.minWrittenMoveCents ) {
			double riseObserved = (peakObserved - startObserved) * 100.0;
			m.put("riseCents", riseObserved);
			m.put("targetRiseCents", riseWritten);
			int[] part = judgePart(riseObserved, riseWritten);
			rank = Math.min(rank, part[0]);
			outcome = part[1];
			outcomeSet = true;
		}
		if( writtenStart >= this.parameters.minWrittenMoveCents ) {
			double startError = (startObserved - (baseMidi + path.startSemitones())) * 100.0;
			m.put("startErrorCents", startError);
			if( Math.abs(startError) > this.parameters.startToleranceCents ) {
				boolean atFretted = (startObserved - baseMidi) * 100.0 <= this.parameters.movedShare * writtenStart;
				rank = Math.min(rank, atFretted ? 0 : 1);
				if( !outcomeSet ) {
					outcome = (startError < 0 ? -1 : 1);
					outcomeSet = true;
				}
			}
		}
		double fallWritten = writtenPeak - writtenEnd;
		if( fallWritten >= this.parameters.minWrittenMoveCents ) {
			double fallObserved = (peakObserved - endObserved) * 100.0;
			m.put("fallCents", fallObserved);
			m.put("targetFallCents", fallWritten);
			int[] part = judgePart(fallObserved, fallWritten);
			rank = Math.min(rank, part[0]);
			if( !outcomeSet ) {
				outcome = part[1];
				outcomeSet = true;
			}
		}
		m.put("outcome", (double) outcome);
		if( !outcomeSet && !m.containsKey("startErrorCents") ) {
			// nothing was written that can be measured
			return new TechniqueEvidence(DetectableTechnique.BEND, TechniqueEvidence.Verdict.UNCERTAIN, 0.0, m);
		}
		TechniqueEvidence.Verdict verdict = (rank == 2 ? TechniqueEvidence.Verdict.DETECTED : rank == 1 ? TechniqueEvidence.Verdict.UNCERTAIN : TechniqueEvidence.Verdict.NOT_DETECTED);
		return new TechniqueEvidence(DetectableTechnique.BEND, verdict, rank == 1 ? 0.3 : confidence * 0.8, m);
	}

	/** Rank (2 made, 1 uncertain, 0 not made) and outcome (-1 under, 0 on pitch, 1 over) of one written move. */
	private int[] judgePart(double observed, double written) {
		double tolerance = Math.max(this.parameters.onToleranceCents, this.parameters.onToleranceShare * written);
		double error = observed - written;
		if( error > tolerance ) {
			return new int[] { 2, 1 };
		}
		if( error >= -tolerance ) {
			return new int[] { 2, 0 };
		}
		return new int[] { observed >= this.parameters.movedShare * written ? 1 : 0, -1 };
	}

	private static double median(double[] values) {
		double[] sorted = values.clone();
		Arrays.sort(sorted);
		return sorted[sorted.length / 2];
	}
}
