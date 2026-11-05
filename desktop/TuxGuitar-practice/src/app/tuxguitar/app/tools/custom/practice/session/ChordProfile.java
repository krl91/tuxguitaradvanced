package app.tuxguitar.app.tools.custom.practice.session;

import java.util.Arrays;

import app.tuxguitar.audio.engine.analysis.SpectrumFrame;

/**
 * The pitch-class profile of a strum (spec 024, data-model "Chord profile", contracts/chord-profile-rule.md): the linear magnitude of each
 * semitone band, {@code 10^(dB/20)}, summed per note name over the frames of the strum, from a semitone under the lowest required pitch up to
 * {@code top}. A name that is only tolerated (a note still sounding from an earlier beat) is not summed. The match is the cosine between the summed
 * names and the required names; a required name is weak when its sum is under a share of the strongest required name's.
 *
 * <p>Made once and reset for each strum: nothing is allocated when a frame is added or the profile is read (the feedback of {@link #weakest} excepted,
 * which is asked once, on a refusal).</p>
 */
public final class ChordProfile {

	private final double[] sums = new double[12];
	private final boolean[] required = new boolean[12];
	private final boolean[] ignored = new boolean[12];
	private int[] requiredPitches = new int[0];
	private int names;
	private int low;
	private int top;
	private long startNanos;

	/** Starts a new strum expecting {@code required} (pitches), with {@code tolerated} pitches that may still sound, summed up to {@code top}. */
	public void reset(int[] required, int[] tolerated, int top, long startNanos) {
		Arrays.fill(this.sums, 0.0);
		Arrays.fill(this.required, false);
		Arrays.fill(this.ignored, false);
		this.requiredPitches = required;
		this.low = Integer.MAX_VALUE;
		for( int pitch : required ) {
			this.required[pitch % 12] = true;
			this.low = Math.min(this.low, pitch - 1);
		}
		this.names = 0;
		for( boolean name : this.required ) {
			this.names += name ? 1 : 0;
		}
		for( int pitch : tolerated ) {
			this.ignored[pitch % 12] = !this.required[pitch % 12];
		}
		this.top = top;
		this.startNanos = startNanos;
	}

	/** Adds the bands of one frame. */
	public void add(SpectrumFrame frame) {
		int minBand = frame.getMinBandMidi();
		int first = Math.max(minBand, this.low);
		int last = Math.min(minBand + frame.getBandCount() - 1, this.top);
		for( int midi = first ; midi <= last ; midi ++ ) {
			double db = frame.getBandDb(midi);
			int name = midi % 12;
			if( db == Double.NEGATIVE_INFINITY || Double.isNaN(db) || this.ignored[name] ) {
				continue;
			}
			this.sums[name] += Math.pow(10.0, db / 20.0);
		}
	}

	/** The cosine between the summed names and the required names, 0 for an empty profile. */
	public double match() {
		double dot = 0.0;
		double norm = 0.0;
		for( int k = 0 ; k < 12 ; k ++ ) {
			norm += this.sums[k] * this.sums[k];
			if( this.required[k] ) {
				dot += this.sums[k];
			}
		}
		return (norm > 0.0 && this.names > 0 ? dot / (Math.sqrt(norm) * Math.sqrt(this.names)) : 0.0);
	}

	/** How many required names are summed under {@code share} of the strongest required name (all of them for an empty profile). */
	public int weakNames(double share) {
		double strongest = strongestRequired();
		int weak = 0;
		for( int k = 0 ; k < 12 ; k ++ ) {
			if( this.required[k] && !(strongest > 0.0 && this.sums[k] / strongest >= share) ) {
				weak ++;
			}
		}
		return weak;
	}

	/** The required pitches, sorted, of the {@code n} weakest required names (for the feedback of a refusal). */
	public int[] weakest(int n) {
		boolean[] chosen = new boolean[12];
		for( int i = 0 ; i < n ; i ++ ) {
			int weakest = -1;
			for( int k = 0 ; k < 12 ; k ++ ) {
				if( this.required[k] && !chosen[k] && (weakest < 0 || this.sums[k] < this.sums[weakest]) ) {
					weakest = k;
				}
			}
			if( weakest < 0 ) {
				break;
			}
			chosen[weakest] = true;
		}
		return Arrays.stream(this.requiredPitches).filter(pitch -> chosen[pitch % 12]).distinct().sorted().toArray();
	}

	/** The required pitches, distinct and sorted. */
	int[] requiredPitches() {
		return Arrays.stream(this.requiredPitches).distinct().sorted().toArray();
	}

	/** The time summed since the strum started. */
	public long summedNanos(long nowNanos) {
		return nowNanos - this.startNanos;
	}

	/** True when nothing was summed. */
	public boolean isEmpty() {
		for( double sum : this.sums ) {
			if( sum > 0.0 ) {
				return false;
			}
		}
		return true;
	}

	private double strongestRequired() {
		double strongest = 0.0;
		for( int k = 0 ; k < 12 ; k ++ ) {
			if( this.required[k] ) {
				strongest = Math.max(strongest, this.sums[k]);
			}
		}
		return strongest;
	}
}
