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

	/**
	 * The chord a whole tone lower or higher explains the partials this many times better (harmonic sum) than the expected one: the expected one is not
	 * heard (spec 023, experiment A, {@code benchmark/chord-experiments.md}).
	 */
	public static final double NEIGHBOUR_MARGIN = 1.5;

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

	/** Evidence computed elsewhere than by {@link #evaluate} (the native engine, spec 023), from its pitches and residual. */
	public static ChordEvidence of(List<Pitch> pitches, List<PitchCandidate> residual) {
		return new ChordEvidence(new ArrayList<Pitch>(pitches), new ArrayList<PitchCandidate>(residual));
	}

	/** One expected pitch's evidence computed elsewhere than by {@link #evaluate} (the native engine, spec 023). */
	public static Pitch pitch(int midi, double salience, double independentSalience, boolean harmonicOfLower, double meanAmplitude, double uniqueAmplitude,
			int uniquePartials, double uniquePresentFraction, int uniqueClearPartials) {
		Pitch pitch = new Pitch(midi, salience, independentSalience, harmonicOfLower, meanAmplitude, uniqueAmplitude, uniquePartials);
		pitch.uniquePresentFraction = uniquePresentFraction;
		pitch.uniqueClearPartials = uniqueClearPartials;
		return pitch;
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
				if (strongestPartial > 0.0 && peak >= parameters.partialPresenceRatio * strongestPartial) {
					clear++;
				}
			}
			pitch.uniquePresentFraction = (uniquePeaks.length > 0 ? (double) clear / uniquePeaks.length : 0.0);
			pitch.uniqueClearPartials = clear;
		}
		for (int i = 0; i < pitches.size(); i++) {
			if (heardBySecondAndThirdPartials(comb, parameters, peaks, length, sounding, pitches.get(i).getMidi(), strongestPartial)) {
				Pitch pitch = pitches.get(i);
				pitch.uniqueClearPartials = Math.max(pitch.uniqueClearPartials, 2);
				pitch.uniquePresentFraction = Math.max(pitch.uniquePresentFraction, 1.0 / 3.0);
			}
		}
		if (aWholeToneAwayExplainsBetter(comb, parameters, peaks, length, distinct)) {
			for (Pitch pitch : pitches) {
				pitch.uniquePresentFraction = 0.0;
				pitch.uniqueClearPartials = 0;
			}
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

	/**
	 * A low string through a microphone gives little of its fundamental, while its second and third partials sound: the pitch is heard when its fundamental
	 * is under the presence ratio and both those partials are above it, neither of them a partial of another pitch that sounds, expected or tolerated (spec
	 * 023, experiment C).
	 */
	private static boolean heardBySecondAndThirdPartials(HarmonicComb comb, PolyphonicAnalyzer.Parameters parameters, double[] peaks, int length, int[] sounding,
			int midi, double strongestPartial) {
		if (strongestPartial <= 0.0) {
			return false;
		}
		double f0 = HarmonicComb.frequency(midi);
		double threshold = parameters.partialPresenceRatio * strongestPartial;
		if (comb.peakAround(peaks, length, f0, comb.toleranceRatio()) >= threshold || comb.peakAround(peaks, length, f0 * 2.0, comb.toleranceRatio()) < threshold
				|| comb.peakAround(peaks, length, f0 * 3.0, comb.toleranceRatio()) < threshold) {
			return false;
		}
		for (double h : new double[] { 2.0, 3.0 }) {
			double f = f0 * h;
			for (int other : sounding) {
				if (other == midi) {
					continue;
				}
				double g = HarmonicComb.frequency(other);
				long k = Math.round(f / g);
				if (k >= 1 && Math.abs(1200.0 * Math.log(f / (k * g)) / Math.log(2.0)) <= parameters.harmonicToleranceCents) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The harmonic sum of the expected pitches against that of the same chord a whole tone lower and higher. The chord a whole tone away shares a partial of
	 * each pitch with the expected one (the ninth of the lower note is the eighth of the higher), which is enough for the unique partials when a distortion
	 * gives every partial; its harmonic sum tells it, as its fundamentals and low partials are the ones that sound (spec 023, experiment A).
	 */
	private static boolean aWholeToneAwayExplainsBetter(HarmonicComb comb, PolyphonicAnalyzer.Parameters parameters, double[] peaks, int length, int[] distinct) {
		double own = 0.0;
		for (int midi : distinct) {
			own += comb.salience(peaks, length, midi, 0);
		}
		for (int shift : new int[] { -2, 2 }) {
			boolean inRange = true;
			for (int midi : distinct) {
				inRange &= (midi + shift >= parameters.minCandidateMidi && midi + shift <= parameters.maxCandidateMidi);
			}
			if (!inRange) {
				continue;
			}
			double neighbour = 0.0;
			for (int midi : distinct) {
				neighbour += comb.salience(peaks, length, midi + shift, 0);
			}
			if (neighbour > parameters.neighbourMargin * own) {
				return true;
			}
		}
		return false;
	}
}
