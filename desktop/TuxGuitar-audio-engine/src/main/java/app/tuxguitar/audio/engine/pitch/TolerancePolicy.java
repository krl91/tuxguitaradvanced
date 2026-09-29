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

	public TolerancePolicy(double tunerInTuneCents, double practiceAcceptCents) {
		if (!Double.isFinite(tunerInTuneCents) || !Double.isFinite(practiceAcceptCents)
				|| tunerInTuneCents < 0.0
				|| practiceAcceptCents < tunerInTuneCents
				|| practiceAcceptCents >= 50.0) {
			throw new IllegalArgumentException("require 0 <= tunerInTuneCents <= practiceAcceptCents < 50");
		}
		this.tunerInTuneCents = tunerInTuneCents;
		this.practiceAcceptCents = practiceAcceptCents;
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
		return reading != null
				&& reading.hasPitch()
				&& reading.isReliable()
				&& reading.getMidi() == expectedMidi
				&& Math.abs(reading.getCents()) <= this.practiceAcceptCents;
	}

	public double getTunerInTuneCents() {
		return this.tunerInTuneCents;
	}

	public double getPracticeAcceptCents() {
		return this.practiceAcceptCents;
	}
}
