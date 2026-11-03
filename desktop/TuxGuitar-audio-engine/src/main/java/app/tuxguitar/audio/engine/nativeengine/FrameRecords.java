package app.tuxguitar.audio.engine.nativeengine;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.analysis.ChordEvidence;
import app.tuxguitar.audio.engine.analysis.PitchCandidate;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.recognition.TargetEvidence;

/**
 * The frame record of the native engine (spec 023, data-model "Frame record"): one {@link AnalysisFrame} packed into a {@code float[]}, with the
 * arrival of its last block for the capture clock. The offsets are those of the crate's {@code src/analysis/frame_layout.txt}. A long or a double takes
 * two slots (the high then the low 32 bits, as raw float bits); an int or a count is a float value. A record that does not fit is refused, never cut.
 */
public final class FrameRecords {

	public static final int LENGTH = 0;
	public static final int TIMESTAMP_NANOS = 1;
	public static final int CAPTURE_SAMPLE_END = 3;
	public static final int ARRIVAL_NANOS = 5;
	public static final int RMS_DBFS = 7;
	public static final int PEAK_DBFS = 9;
	public static final int CLIPPED_RATIO = 11;
	public static final int LEVEL_STATE = 13;
	public static final int F0_HZ = 14;
	public static final int CONFIDENCE = 16;
	public static final int ONSET = 18;
	public static final int PARTS = 19;
	public static final int HEADER = 20;

	public static final int PART_SPECTRUM = 1;
	public static final int PART_TARGET = 2;
	public static final int PART_ATTACK = 4;
	public static final int PART_CHORD = 8;

	/** The largest record, in floats. */
	public static final int CAPACITY = 4096;

	private static final AnalysisFrame.LevelState[] STATES = AnalysisFrame.LevelState.values();

	private FrameRecords() {
	}

	/** The number of floats of the record at {@code offset}. */
	public static int length(float[] records, int offset) {
		if (offset < 0 || offset >= records.length) {
			throw new IllegalArgumentException("no record at " + offset);
		}
		return (int) records[offset + LENGTH];
	}

	/** The arrival, in {@code System.nanoTime}'s base, of the last block of the record at {@code offset}. */
	public static long arrivalNanos(float[] records, int offset) {
		return getLong(records, offset + ARRIVAL_NANOS);
	}

	/**
	 * Writes {@code frame} at {@code offset}; its technique features are not carried (the native engine has none).
	 *
	 * @return the record's length
	 * @throws IllegalArgumentException if the record does not fit between {@code offset} and the end of {@code out}
	 */
	public static int write(AnalysisFrame frame, long arrivalNanos, float[] out, int offset) {
		Writer w = new Writer(out, offset);
		w.at(HEADER);
		w.setLong(TIMESTAMP_NANOS, frame.getTimestampNanos());
		w.setLong(CAPTURE_SAMPLE_END, frame.getCaptureSampleEnd());
		w.setLong(ARRIVAL_NANOS, arrivalNanos);
		w.setDouble(RMS_DBFS, frame.getRmsDbfs());
		w.setDouble(PEAK_DBFS, frame.getPeakDbfs());
		w.setDouble(CLIPPED_RATIO, frame.getClippedRatio());
		w.setInt(LEVEL_STATE, frame.getLevelState().ordinal());
		w.setDouble(F0_HZ, frame.getF0Hz());
		w.setDouble(CONFIDENCE, frame.getConfidence());
		w.setInt(ONSET, frame.isOnset() ? 1 : 0);
		int parts = 0;
		SpectrumFrame spectrum = frame.getSpectrum();
		if (spectrum != null) {
			parts |= PART_SPECTRUM;
			w.putDouble(spectrum.getBinHz());
			w.putInt(spectrum.getMinBandMidi());
			w.putInt(spectrum.getBandCount());
			for (int i = 0; i < spectrum.getBandCount(); i++) {
				w.putFloat((float) spectrum.getBandDb(spectrum.getMinBandMidi() + i));
			}
			w.putInt(spectrum.getMinSalienceMidi());
			w.putInt(spectrum.getSalienceCount());
			for (int i = 0; i < spectrum.getSalienceCount(); i++) {
				w.putFloat((float) spectrum.getSalience(spectrum.getMinSalienceMidi() + i));
			}
			putCandidates(w, spectrum.getCandidates());
			w.putInt(spectrum.getPeakLength());
			for (int k = 0; k < spectrum.getPeakLength(); k++) {
				w.putFloat(spectrum.getPeak(k));
			}
		}
		TargetEvidence target = frame.getTargetEvidence();
		if (target != null) {
			parts |= PART_TARGET;
			w.putDouble(target.getWindowMillis());
			w.putInt(target.getHypotheses().size());
			for (TargetEvidence.Hypothesis hypothesis : target.getHypotheses()) {
				w.putInt(hypothesis.getMidi());
				w.putDouble(hypothesis.getScore());
			}
		}
		AttackEvidence attack = frame.getAttackEvidence();
		if (attack != null) {
			parts |= PART_ATTACK;
			int[] pitches = attack.getPitches();
			w.putInt(pitches.length);
			for (int pitch : pitches) {
				w.putInt(pitch);
			}
			for (int i = 0; i < pitches.length; i++) {
				w.putDouble(attack.getAmplitude(i));
			}
			w.putDouble(attack.getAttackStrength());
			w.putDouble(attack.getBinHz());
			double[] growth = attack.getGrowth();
			if (growth == null) {
				w.putInt(-1);
			} else {
				w.putInt(growth.length);
				for (double value : growth) {
					w.putDouble(value);
				}
			}
		}
		ChordEvidence chord = frame.getChordEvidence();
		if (chord != null) {
			parts |= PART_CHORD;
			w.putInt(chord.getPitches().size());
			for (ChordEvidence.Pitch pitch : chord.getPitches()) {
				w.putInt(pitch.getMidi());
				w.putDouble(pitch.getSalience());
				w.putDouble(pitch.getIndependentSalience());
				w.putInt(pitch.isHarmonicOfLower() ? 1 : 0);
				w.putDouble(pitch.getMeanAmplitude());
				w.putDouble(pitch.getUniqueAmplitude());
				w.putInt(pitch.getUniquePartials());
				w.putDouble(pitch.getUniquePresentFraction());
				w.putInt(pitch.getUniqueClearPartials());
			}
			putCandidates(w, chord.getResidual());
		}
		w.setInt(PARTS, parts);
		int length = w.position;
		w.setInt(LENGTH, length);
		return length;
	}

	/**
	 * The frame of the record at {@code offset}.
	 *
	 * @throws IllegalArgumentException if the record is longer than what is left of {@code records}, or malformed
	 */
	public static AnalysisFrame read(float[] records, int offset) {
		int length = length(records, offset);
		if (length < HEADER || length > CAPACITY || offset + length > records.length) {
			throw new IllegalArgumentException("a record of " + length + " floats at " + offset + " does not fit in " + records.length);
		}
		Reader r = new Reader(records, offset, length);
		int stateOrdinal = (int) records[offset + LEVEL_STATE];
		if (stateOrdinal < 0 || stateOrdinal >= STATES.length) {
			throw new IllegalArgumentException("unknown level state " + stateOrdinal);
		}
		int parts = (int) records[offset + PARTS];
		r.at(HEADER);
		SpectrumFrame spectrum = null;
		if ((parts & PART_SPECTRUM) != 0) {
			double binHz = r.nextDouble();
			int minBandMidi = r.nextInt();
			float[] bandDb = r.nextFloats(r.nextCount());
			int minSalienceMidi = r.nextInt();
			float[] salience = r.nextFloats(r.nextCount());
			List<PitchCandidate> candidates = nextCandidates(r);
			float[] peaks = r.nextFloats(r.nextCount());
			spectrum = SpectrumFrame.of(binHz, minBandMidi, bandDb, minSalienceMidi, salience, candidates, peaks);
		}
		TargetEvidence target = null;
		if ((parts & PART_TARGET) != 0) {
			double windowMillis = r.nextDouble();
			int count = r.nextCount();
			List<TargetEvidence.Hypothesis> hypotheses = new ArrayList<TargetEvidence.Hypothesis>(count);
			for (int i = 0; i < count; i++) {
				int midi = r.nextInt();
				hypotheses.add(new TargetEvidence.Hypothesis(midi, r.nextDouble()));
			}
			target = new TargetEvidence(hypotheses, windowMillis);
		}
		AttackEvidence attack = null;
		if ((parts & PART_ATTACK) != 0) {
			int count = r.nextCount();
			int[] pitches = new int[count];
			for (int i = 0; i < count; i++) {
				pitches[i] = r.nextInt();
			}
			double[] amplitudes = new double[count];
			for (int i = 0; i < count; i++) {
				amplitudes[i] = r.nextDouble();
			}
			double attackStrength = r.nextDouble();
			double binHz = r.nextDouble();
			int growthCount = r.nextInt();
			double[] growth = null;
			if (growthCount >= 0) {
				growth = new double[r.checkCount(growthCount)];
				for (int i = 0; i < growthCount; i++) {
					growth[i] = r.nextDouble();
				}
			}
			attack = new AttackEvidence(pitches, amplitudes, attackStrength, growth, binHz);
		}
		ChordEvidence chord = null;
		if ((parts & PART_CHORD) != 0) {
			int count = r.nextCount();
			List<ChordEvidence.Pitch> pitches = new ArrayList<ChordEvidence.Pitch>(count);
			for (int i = 0; i < count; i++) {
				int midi = r.nextInt();
				double salience = r.nextDouble();
				double independentSalience = r.nextDouble();
				boolean harmonicOfLower = r.nextInt() != 0;
				double meanAmplitude = r.nextDouble();
				double uniqueAmplitude = r.nextDouble();
				int uniquePartials = r.nextInt();
				double uniquePresentFraction = r.nextDouble();
				int uniqueClearPartials = r.nextInt();
				pitches.add(ChordEvidence.pitch(midi, salience, independentSalience, harmonicOfLower, meanAmplitude, uniqueAmplitude, uniquePartials,
						uniquePresentFraction, uniqueClearPartials));
			}
			chord = ChordEvidence.of(pitches, nextCandidates(r));
		}
		if (r.position != length) {
			throw new IllegalArgumentException("a record of " + length + " floats ends at " + r.position);
		}
		return new AnalysisFrame(getLong(records, offset + TIMESTAMP_NANOS), getDouble(records, offset + RMS_DBFS), getDouble(records, offset + PEAK_DBFS),
				getDouble(records, offset + CLIPPED_RATIO), STATES[stateOrdinal], getDouble(records, offset + F0_HZ), getDouble(records, offset + CONFIDENCE),
				records[offset + ONSET] != 0f, spectrum, target, null, attack, getLong(records, offset + CAPTURE_SAMPLE_END), chord);
	}

	private static void putCandidates(Writer w, List<PitchCandidate> candidates) {
		w.putInt(candidates.size());
		for (PitchCandidate candidate : candidates) {
			w.putInt(candidate.getMidi());
			w.putDouble(candidate.getSalience());
			w.putDouble(candidate.getRelativeSalience());
			w.putDouble(candidate.getLevelDb());
		}
	}

	private static List<PitchCandidate> nextCandidates(Reader r) {
		int count = r.nextCount();
		List<PitchCandidate> candidates = new ArrayList<PitchCandidate>(count);
		for (int i = 0; i < count; i++) {
			int midi = r.nextInt();
			double salience = r.nextDouble();
			double relativeSalience = r.nextDouble();
			candidates.add(new PitchCandidate(midi, salience, relativeSalience, r.nextDouble()));
		}
		return candidates;
	}

	static long getLong(float[] records, int slot) {
		long high = Float.floatToRawIntBits(records[slot]) & 0xFFFFFFFFL;
		long low = Float.floatToRawIntBits(records[slot + 1]) & 0xFFFFFFFFL;
		return (high << 32) | low;
	}

	static double getDouble(float[] records, int slot) {
		return Double.longBitsToDouble(getLong(records, slot));
	}

	private static final class Writer {
		final float[] out;
		final int offset;
		final int limit;
		int position;

		Writer(float[] out, int offset) {
			if (offset < 0 || offset > out.length) {
				throw new IllegalArgumentException("no room at " + offset);
			}
			this.out = out;
			this.offset = offset;
			this.limit = Math.min(CAPACITY, out.length - offset);
		}

		void at(int position) {
			check(position);
			this.position = position;
		}

		void check(int end) {
			if (end > this.limit) {
				throw new IllegalArgumentException("the record does not fit: " + end + " floats, " + this.limit + " available");
			}
		}

		void setInt(int slot, int value) {
			this.out[this.offset + slot] = value;
		}

		void setLong(int slot, long value) {
			this.out[this.offset + slot] = Float.intBitsToFloat((int) (value >>> 32));
			this.out[this.offset + slot + 1] = Float.intBitsToFloat((int) value);
		}

		void setDouble(int slot, double value) {
			setLong(slot, Double.doubleToRawLongBits(value));
		}

		void putInt(int value) {
			check(this.position + 1);
			setInt(this.position++, value);
		}

		void putFloat(float value) {
			check(this.position + 1);
			this.out[this.offset + this.position++] = value;
		}

		void putDouble(double value) {
			check(this.position + 2);
			setDouble(this.position, value);
			this.position += 2;
		}
	}

	private static final class Reader {
		final float[] records;
		final int offset;
		final int length;
		int position;

		Reader(float[] records, int offset, int length) {
			this.records = records;
			this.offset = offset;
			this.length = length;
		}

		void at(int position) {
			this.position = position;
		}

		void need(int slots) {
			if (this.position + slots > this.length) {
				throw new IllegalArgumentException("the record of " + this.length + " floats is cut at " + this.position);
			}
		}

		int checkCount(int count) {
			if (count < 0 || count > this.length) {
				throw new IllegalArgumentException("bad count " + count);
			}
			return count;
		}

		int nextInt() {
			need(1);
			return (int) this.records[this.offset + this.position++];
		}

		int nextCount() {
			return checkCount(nextInt());
		}

		double nextDouble() {
			need(2);
			double value = getDouble(this.records, this.offset + this.position);
			this.position += 2;
			return value;
		}

		float[] nextFloats(int count) {
			need(count);
			float[] values = new float[count];
			System.arraycopy(this.records, this.offset + this.position, values, 0, count);
			this.position += count;
			return values;
		}
	}
}
