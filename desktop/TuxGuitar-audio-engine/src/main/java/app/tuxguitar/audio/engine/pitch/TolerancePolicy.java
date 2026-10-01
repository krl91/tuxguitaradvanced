package app.tuxguitar.audio.engine.pitch;

/** Shared, parameterized note-tolerance policy for tuner and practice. */
public final class TolerancePolicy {

	public enum TunerState {
		FLAT,
		IN_TUNE,
		SHARP,
		NO_READING
	}

	private final double tunerInTuneCents;
	private final double practiceAcceptCents;
	private final double fretSharpCentsPerFret;

	/** The sharp side never reaches the neighbouring note: the nearest-note reading would flip. */
	private static final double MAX_SHARP_CENTS = 49.0;

	public TolerancePolicy(double tunerInTuneCents, double practiceAcceptCents) {
		this(tunerInTuneCents, practiceAcceptCents, 0.0);
	}

	/**
	 * @param fretSharpCentsPerFret extra cents accepted on the sharp side per fret of the written
	 *        note: pressing a string on a fret stretches it, more so higher up the neck (measured
	 *        +10 to +25 cents on real fretted notes); 0 disables it. The flat side is unchanged.
	 */
	public TolerancePolicy(double tunerInTuneCents, double practiceAcceptCents, double fretSharpCentsPerFret) {
		if (!Double.isFinite(tunerInTuneCents) || !Double.isFinite(practiceAcceptCents)
				|| tunerInTuneCents < 0.0
				|| practiceAcceptCents < tunerInTuneCents
				|| practiceAcceptCents >= 50.0) {
			throw new IllegalArgumentException("require 0 <= tunerInTuneCents <= practiceAcceptCents < 50");
		}
		if (!Double.isFinite(fretSharpCentsPerFret) || fretSharpCentsPerFret < 0.0) {
			throw new IllegalArgumentException("fretSharpCentsPerFret must be >= 0: " + fretSharpCentsPerFret);
		}
		this.tunerInTuneCents = tunerInTuneCents;
		this.practiceAcceptCents = practiceAcceptCents;
		this.fretSharpCentsPerFret = fretSharpCentsPerFret;
	}

	public TunerState tunerState(PitchReading reading) {
		if (reading == null || !reading.hasPitch() || !reading.isReliable()) {
			return TunerState.NO_READING;
		}
		if (Math.abs(reading.getCents()) <= this.tunerInTuneCents) {
			return TunerState.IN_TUNE;
		}
		return reading.getCents() < 0.0 ? TunerState.FLAT : TunerState.SHARP;
	}

	public boolean matches(PitchReading reading, int expectedMidi) {
		return matches(reading, expectedMidi, 0);
	}

	/** Like {@link #matches(PitchReading, int)} with the fret of the written note: sharp readings get the per-fret allowance. */
	public boolean matches(PitchReading reading, int expectedMidi, int fret) {
		return reading != null
				&& reading.hasPitch()
				&& reading.isReliable()
				&& reading.getMidi() == expectedMidi
				&& reading.getCents() >= -this.practiceAcceptCents
				&& reading.getCents() <= sharpLimitCents(fret);
	}

	/** The largest sharp deviation accepted for a note written on the given fret. */
	public double sharpLimitCents(int fret) {
		double limit = this.practiceAcceptCents + this.fretSharpCentsPerFret * Math.max(0, fret);
		return Math.max(this.practiceAcceptCents, Math.min(MAX_SHARP_CENTS, limit));
	}

	public double getTunerInTuneCents() {
		return this.tunerInTuneCents;
	}

	public double getPracticeAcceptCents() {
		return this.practiceAcceptCents;
	}
}
