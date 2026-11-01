package app.tuxguitar.audio.engine.monitor;

/** One knob of a pedal: its key, range, unit and default (spec 022 data-model "EffectKind"). Immutable. */
public final class Knob {

	private final String key;
	private final double min;
	private final double max;
	private final String unit;
	private final double defaultValue;

	Knob(String key, double min, double max, String unit, double defaultValue) {
		this.key = key;
		this.min = min;
		this.max = max;
		this.unit = unit;
		this.defaultValue = defaultValue;
	}

	public String key() {
		return this.key;
	}

	public double min() {
		return this.min;
	}

	public double max() {
		return this.max;
	}

	/** "dB", "ms", "Hz", "%" or "" for a plain 0 to 10 scale. */
	public String unit() {
		return this.unit;
	}

	public double defaultValue() {
		return this.defaultValue;
	}

	/** The value brought back into the range; a value that is not a number becomes the minimum. */
	public double clamp(double value) {
		if( Double.isNaN(value) ) {
			return this.min;
		}
		return Math.max(this.min, Math.min(this.max, value));
	}
}
