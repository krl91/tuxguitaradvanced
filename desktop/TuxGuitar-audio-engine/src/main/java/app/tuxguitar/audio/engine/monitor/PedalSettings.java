package app.tuxguitar.audio.engine.monitor;

import java.util.Arrays;

/** One pedal of the chain: its kind, its switch and its knob values (spec 022 data-model "PedalSettings"). Immutable. */
public final class PedalSettings {

	private final EffectKind kind;
	private final boolean enabled;
	private final double[] values;

	private PedalSettings(EffectKind kind, boolean enabled, double[] values) {
		this.kind = kind;
		this.enabled = enabled;
		this.values = values;
	}

	/** The pedal switched on with every knob at its default. */
	public static PedalSettings defaults(EffectKind kind) {
		double[] values = new double[kind.knobs().size()];
		for( int i = 0 ; i < values.length ; i++ ) {
			values[i] = kind.knobs().get(i).defaultValue();
		}
		return new PedalSettings(kind, true, values);
	}

	/** The values in the order of {@link EffectKind#knobs()}, each brought back into its range. */
	public static PedalSettings of(EffectKind kind, boolean enabled, double... values) {
		if( values.length != kind.knobs().size() ) {
			throw new IllegalArgumentException(kind + " has " + kind.knobs().size() + " knobs, not " + values.length);
		}
		double[] clamped = new double[values.length];
		for( int i = 0 ; i < values.length ; i++ ) {
			clamped[i] = kind.knobs().get(i).clamp(values[i]);
		}
		return new PedalSettings(kind, enabled, clamped);
	}

	public EffectKind kind() {
		return this.kind;
	}

	public boolean enabled() {
		return this.enabled;
	}

	public double value(String key) {
		int index = this.kind.knobIndex(key);
		if( index < 0 ) {
			throw new IllegalArgumentException(this.kind + " has no knob " + key);
		}
		return this.values[index];
	}

	/** The value of the knob at that position of {@link EffectKind#knobs()}. */
	public double value(int index) {
		return this.values[index];
	}

	/** A copy with that knob changed, brought back into its range. */
	public PedalSettings with(String key, double value) {
		int index = this.kind.knobIndex(key);
		if( index < 0 ) {
			throw new IllegalArgumentException(this.kind + " has no knob " + key);
		}
		double[] copy = this.values.clone();
		copy[index] = this.kind.knobs().get(index).clamp(value);
		return new PedalSettings(this.kind, this.enabled, copy);
	}

	public PedalSettings withEnabled(boolean enabled) {
		return new PedalSettings(this.kind, enabled, this.values);
	}

	@Override
	public boolean equals(Object other) {
		if( !(other instanceof PedalSettings) ) {
			return false;
		}
		PedalSettings pedal = (PedalSettings) other;
		return this.kind == pedal.kind && this.enabled == pedal.enabled && Arrays.equals(this.values, pedal.values);
	}

	@Override
	public int hashCode() {
		return (this.kind.hashCode() * 31 + (this.enabled ? 1 : 0)) * 31 + Arrays.hashCode(this.values);
	}

	@Override
	public String toString() {
		return this.kind.textName() + (this.enabled ? ":on" : ":off") + Arrays.toString(this.values);
	}
}
