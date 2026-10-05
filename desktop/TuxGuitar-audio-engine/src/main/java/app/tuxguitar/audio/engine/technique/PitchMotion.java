package app.tuxguitar.audio.engine.technique;

import java.util.Arrays;

/**
 * What the pitch of a note segment does, in cents around its median: the shared measurements of the
 * pitch-based detectors (vibrato, bend, slide). Outliers further than {@link #OUTLIER_CENTS} from the
 * median (octave errors, a neighbouring string) are discarded before anything is measured.
 */
final class PitchMotion {

	static final double OUTLIER_CENTS = 400.0;
	/** Fewer voiced samples than this cannot be measured. */
	static final int MIN_VOICED = 8;

	final int voiced;
	final double hop;
	/** Robust peak-to-peak (5th to 95th percentile) of the pitch around its trend, in cents; 0 when not measurable. */
	final double wobbleDepth;
	/** Cycles per second of the pitch around its trend (the modulation rate); 0 when not measurable. */
	final double wobbleRate;
	/** Pitch at the end minus pitch at the start, in cents (medians of thirds of the voiced samples). */
	final double drift;
	/** Share of the moving steps that go in the direction with the most of them, 0–1. */
	final double monotony;
	/** Robust peak-to-peak (5th to 95th percentile) of the pitch itself, in cents. */
	final double range;
	/** Number of steps between consecutive voiced samples that move the pitch by more than one cent (on the smoothed pitch). */
	final int movingSteps;

	PitchMotion(NoteSegment segment) {
		this.hop = segment.getHopSeconds();
		int n = segment.size();
		double[] voicedMidi = new double[n];
		int count = 0;
		for( int i = 0 ; i < n ; i ++ ) {
			if( Double.isFinite(segment.midiAt(i)) ) {
				voicedMidi[count ++] = segment.midiAt(i);
			}
		}
		double[] sorted = Arrays.copyOf(voicedMidi, count);
		Arrays.sort(sorted);
		double median = (count == 0 ? 0.0 : sorted[count / 2]);
		double[] kept = new double[n];
		int keptCount = 0;
		for( int i = 0 ; i < n ; i ++ ) {
			double cents = (Double.isFinite(segment.midiAt(i)) ? (segment.midiAt(i) - median) * 100.0 : Double.NaN);
			if( Double.isFinite(cents) && Math.abs(cents) <= OUTLIER_CENTS ) {
				kept[keptCount ++] = cents;
			}
		}
		this.voiced = keptCount;
		if( keptCount < MIN_VOICED ) {
			this.wobbleDepth = 0.0;
			this.wobbleRate = 0.0;
			this.drift = 0.0;
			this.monotony = 0.0;
			this.range = 0.0;
			this.movingSteps = 0;
			return;
		}
		double[] series = Arrays.copyOf(kept, keptCount);
		this.drift = thirdsDrift(series);
		this.range = spread(series);
		int window = Math.max(3, (int) Math.round(0.25 / this.hop));
		double[] detrended = new double[keptCount];
		for( int i = 0 ; i < keptCount ; i ++ ) {
			int lo = Math.max(0, i - window / 2);
			int hi = Math.min(keptCount - 1, i + window / 2);
			double sum = 0.0;
			for( int j = lo ; j <= hi ; j ++ ) {
				sum += series[j];
			}
			detrended[i] = series[i] - sum / (hi - lo + 1);
		}
		this.wobbleDepth = spread(detrended);
		// a cycle counts only when the pitch goes from below -h to above +h: jitter around zero is not a modulation
		double h = 0.25 * this.wobbleDepth / 2.0;
		int cycles = 0;
		boolean low = false;
		for( int i = 0 ; i < keptCount ; i ++ ) {
			if( detrended[i] < -h ) {
				low = true;
			} else if( detrended[i] > h && low ) {
				cycles ++;
				low = false;
			}
		}
		this.wobbleRate = cycles / (keptCount * this.hop);
		// monotony on a lightly smoothed pitch: the estimator's jitter is not a change of direction
		int smooth = 5;
		double[] smoothed = new double[keptCount];
		for( int i = 0 ; i < keptCount ; i ++ ) {
			int lo = Math.max(0, i - smooth / 2);
			int hi = Math.min(keptCount - 1, i + smooth / 2);
			double sum = 0.0;
			for( int j = lo ; j <= hi ; j ++ ) {
				sum += series[j];
			}
			smoothed[i] = sum / (hi - lo + 1);
		}
		int up = 0;
		int down = 0;
		for( int i = 1 ; i < keptCount ; i ++ ) {
			double step = smoothed[i] - smoothed[i - 1];
			if( step > 1.0 ) {
				up ++;
			} else if( step < -1.0 ) {
				down ++;
			}
		}
		this.movingSteps = up + down;
		this.monotony = (this.movingSteps == 0 ? 0.0 : Math.max(up, down) / (double) this.movingSteps);
	}

	private static double spread(double[] values) {
		double[] sorted = values.clone();
		Arrays.sort(sorted);
		return sorted[(int) (0.95 * (sorted.length - 1))] - sorted[(int) (0.05 * (sorted.length - 1))];
	}

	private static double thirdsDrift(double[] series) {
		int third = Math.max(1, series.length / 3);
		return median(Arrays.copyOfRange(series, series.length - third, series.length)) - median(Arrays.copyOfRange(series, 0, third));
	}

	private static double median(double[] values) {
		Arrays.sort(values);
		return values[values.length / 2];
	}

	double voicedShare(int total) {
		return total == 0 ? 0.0 : this.voiced / (double) total;
	}
}
