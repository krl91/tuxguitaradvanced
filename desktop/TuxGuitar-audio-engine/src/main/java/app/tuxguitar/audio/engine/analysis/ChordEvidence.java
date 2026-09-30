package app.tuxguitar.audio.engine.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Score-conditioned evidence for an expected chord in one {@link SpectrumFrame} (research R21,
 * plan stage D). It answers two questions with the analyzer's own comb rules, and decides
 * nothing: how strongly each expected pitch is present, and which other pitches remain once the
 * expected (and tolerated, still-sounding) pitches are explained. Acceptance policy — windows,
 * thresholds, what to do with pitches that cannot be verified independently — belongs to the
 * consumer. Immutable.
 */
public final class ChordEvidence {

	/**
	 * A partial "clearly sounds" when its peak (floor-removed spectrum) reaches this share of the
	 * strongest partial of the expected pitches (−26 dB). Provisional engineering parameter.
	 */
	public static final double PARTIAL_PRESENCE_RATIO = 0.05;

	/** Evidence for one expected pitch. */
	public static final class Pitch {
		private final int midi;
		private final double salience;
		private final double independentSalience;
		private final boolean harmonicOfLower;
		private final double meanAmplitude;
		private final double uniqueAmplitude;
		private final int uniquePartials;

		Pitch(int midi, double salience, double independentSalience, boolean harmonicOfLower,
				double meanAmplitude, double uniqueAmplitude, int uniquePartials) {
			this.midi = midi;
			this.salience = salience;
			this.independentSalience = independentSalience;
			this.harmonicOfLower = harmonicOfLower;
			this.meanAmplitude = meanAmplitude;
			this.uniqueAmplitude = uniqueAmplitude;
			this.uniquePartials = uniquePartials;
		}

		/** Weighted mean amplitude of all the pitch's partials (floor-removed spectrum). */
		public double getMeanAmplitude() {
			return this.meanAmplitude;
		}

		/**
		 * Weighted mean amplitude of the pitch's unique partials — those no other expected or
		 * tolerated pitch shares within the tolerance. Near zero when the pitch does not sound,
		 * whatever the other strings do; 0 when it has no unique partial.
		 */
		public double getUniqueAmplitude() {
			return this.uniqueAmplitude;
		}

		/** Number of unique partials; 0 means the pitch cannot be verified separately. */
		public int getUniquePartials() {
			return this.uniquePartials;
		}

		/**
		 * Share (0–1) of the unique partials that clearly sound: their peak reaches
		 * {@link ChordEvidence#PARTIAL_PRESENCE_RATIO} of the strongest partial of any expected
		 * pitch in the frame. 0 without unique partials.
		 */
		public double getUniquePresentFraction() {
			return this.uniquePresentFraction;
		}

		/** Number of unique partials that clearly sound (see {@link #getUniquePresentFraction()}). */
		public int getUniqueClearPartials() {
			return this.uniqueClearPartials;
		}

		private double uniquePresentFraction;
		private int uniqueClearPartials;

		public int getMidi() {
			return this.midi;
		}

		/** Harmonic-sum salience in the frame, as measured alone. */
		public double getSalience() {
			return this.salience;
		}

		/**
		 * Salience left once the lower expected pitches' harmonics are removed (spectral
		 * smoothness): the evidence that this pitch itself sounds.
		 */
		public double getIndependentSalience() {
			return this.independentSalience;
		}

		/**
		 * True when the pitch lies on a harmonic of a lower expected pitch (octave, octave +
		 * fifth, …): its presence cannot be established independently of that lower pitch.
		 */
		public boolean isHarmonicOfLower() {
			return this.harmonicOfLower;
		}
	}

	private final List<Pitch> pitches;
	private final List<PitchCandidate> residual;

	private ChordEvidence(List<Pitch> pitches, List<PitchCandidate> residual) {
		this.pitches = Collections.unmodifiableList(pitches);
		this.residual = Collections.unmodifiableList(residual);
	}

	/** Evidence per distinct expected pitch, ascending. */
	public List<Pitch> getPitches() {
		return this.pitches;
	}

	/**
	 * Pitches left once the expected and tolerated pitches are explained, strongest first
	 * (at most three, not expected or tolerated themselves). Relative salience is against the
	 * strongest residual pitch.
	 */
	public List<PitchCandidate> getResidual() {
		return this.residual;
	}

	/**
	 * @param expected MIDI pitches that should sound (duplicates ignored)
	 * @param tolerated MIDI pitches that may still sound from earlier notes (never required)
	 */
	public static ChordEvidence evaluate(SpectrumFrame frame, int[] expected, int[] tolerated) {
		PolyphonicAnalyzer.Parameters parameters = frame.getParameters();
		HarmonicComb comb = new HarmonicComb(parameters, frame.getBinHz());
		int length = frame.getPeakLength();
		double[] peaks = new double[length];
		frame.copyPeaks(peaks);
		int[] distinct = Arrays.stream(expected).distinct().sorted().toArray();

		int[] sounding = new int[distinct.length + tolerated.length];
		System.arraycopy(distinct, 0, sounding, 0, distinct.length);
		System.arraycopy(tolerated, 0, sounding, distinct.length, tolerated.length);
		List<Pitch> pitches = new ArrayList<Pitch>();
		double[] working = new double[length];
		for (int i = 0; i < distinct.length; i++) {
			int midi = distinct[i];
			System.arraycopy(peaks, 0, working, 0, length);
			boolean harmonicOfLower = false;
			for (int j = 0; j < i; j++) {
				comb.suppress(working, length, distinct[j]);
				harmonicOfLower |= comb.isHarmonicOf(midi, distinct[j]);
			}
			double[] mean = comb.meanPartialAmplitude(peaks, length, midi, null);
			double[] unique = comb.meanPartialAmplitude(peaks, length, midi, sounding);
			pitches.add(new Pitch(midi, comb.salience(peaks, length, midi, 0), comb.salience(working, length, midi, 0), harmonicOfLower,
					mean[0], unique[0], (int) unique[1]));
		}
		double strongestPartial = 0.0;
		for (int midi : distinct) {
			for (double peak : comb.partialPeaks(peaks, length, midi, null)) {
				strongestPartial = Math.max(strongestPartial, peak);
			}
		}
		for (Pitch pitch : pitches) {
			double[] uniquePeaks = comb.partialPeaks(peaks, length, pitch.getMidi(), sounding);
			int clear = 0;
			for (double peak : uniquePeaks) {
				if (strongestPartial > 0.0 && peak >= PARTIAL_PRESENCE_RATIO * strongestPartial) {
					clear++;
				}
			}
			pitch.uniquePresentFraction = (uniquePeaks.length > 0 ? (double) clear / uniquePeaks.length : 0.0);
			pitch.uniqueClearPartials = clear;
		}

		System.arraycopy(peaks, 0, working, 0, length);
		boolean[] excluded = new boolean[128];
		for (int midi : distinct) {
			comb.suppress(working, length, midi);
			excluded[midi] = true;
		}
		for (int midi : tolerated) {
			comb.suppress(working, length, midi);
			excluded[midi] = true;
		}
		List<PitchCandidate> residual = new ArrayList<PitchCandidate>();
		double floor = Math.pow(10.0, parameters.floorDb / 20.0);
		double strongest = 0.0;
		while (residual.size() < 3) {
			int best = -1;
			double bestSalience = 0.0;
			for (int midi = parameters.minCandidateMidi; midi <= parameters.maxCandidateMidi; midi++) {
				if (!excluded[midi]) {
					double value = comb.salience(working, length, midi, 0);
					if (value > bestSalience) {
						bestSalience = value;
						best = midi;
					}
				}
			}
			if (best < 0 || bestSalience < floor) {
				break;
			}
			best = comb.lowestExplainingSubharmonic(working, length, best, bestSalience, parameters.subharmonicRatio, excluded,
					parameters.minCandidateMidi);
			bestSalience = Math.max(bestSalience, comb.salience(working, length, best, 0));
			if (strongest == 0.0) {
				strongest = bestSalience;
			}
			residual.add(new PitchCandidate(best, bestSalience, bestSalience / strongest, frame.getBandDb(best)));
			excluded[best] = true;
			comb.suppress(working, length, best);
		}
		return new ChordEvidence(pitches, residual);
	}
}
