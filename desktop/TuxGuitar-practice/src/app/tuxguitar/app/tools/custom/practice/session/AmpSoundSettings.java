package app.tuxguitar.app.tools.custom.practice.session;

import java.util.Arrays;

/**
 * The settings of the amp window that are not a sound (spec 022, data-model "AmpSoundSettings", FR-009): the switch, the output, the
 * guitar's volume and the window's bounds. Immutable; the score's volume is TuxGuitar's own and is not kept here.
 */
public final class AmpSoundSettings {

	public static final double DEFAULT_GUITAR_VOLUME = 0.7;

	private final boolean enabled;
	private final String output;
	private final double guitarVolume;
	private final int[] windowBounds;

	public AmpSoundSettings(boolean enabled, String output, double guitarVolume, int[] windowBounds) {
		this.enabled = enabled;
		this.output = (output != null ? output : "");
		this.guitarVolume = Double.isNaN(guitarVolume) ? DEFAULT_GUITAR_VOLUME : Math.max(0, Math.min(1, guitarVolume));
		this.windowBounds = (windowBounds != null && windowBounds.length == 4 ? windowBounds.clone() : null);
	}

	/** Off, the system's default output, the default guitar volume, no bounds yet. */
	public static AmpSoundSettings defaults() {
		return new AmpSoundSettings(false, "", DEFAULT_GUITAR_VOLUME, null);
	}

	public boolean enabled() {
		return this.enabled;
	}

	/** The output's name, {@code ""} for the system's default. */
	public String output() {
		return this.output;
	}

	/** From 0 to 1. */
	public double guitarVolume() {
		return this.guitarVolume;
	}

	/** {x, y, width, height}, or null when the window was never closed. */
	public int[] windowBounds() {
		return (this.windowBounds != null ? this.windowBounds.clone() : null);
	}

	public AmpSoundSettings withEnabled(boolean value) {
		return new AmpSoundSettings(value, this.output, this.guitarVolume, this.windowBounds);
	}

	public AmpSoundSettings withOutput(String value) {
		return new AmpSoundSettings(this.enabled, value, this.guitarVolume, this.windowBounds);
	}

	public AmpSoundSettings withGuitarVolume(double value) {
		return new AmpSoundSettings(this.enabled, this.output, value, this.windowBounds);
	}

	public AmpSoundSettings withWindowBounds(int[] value) {
		return new AmpSoundSettings(this.enabled, this.output, this.guitarVolume, value);
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof AmpSoundSettings)) {
			return false;
		}
		AmpSoundSettings settings = (AmpSoundSettings) other;
		return this.enabled == settings.enabled && this.output.equals(settings.output) && this.guitarVolume == settings.guitarVolume
				&& Arrays.equals(this.windowBounds, settings.windowBounds);
	}

	@Override
	public int hashCode() {
		return ((Boolean.hashCode(this.enabled) * 31 + this.output.hashCode()) * 31 + Double.hashCode(this.guitarVolume)) * 31 + Arrays.hashCode(this.windowBounds);
	}
}
