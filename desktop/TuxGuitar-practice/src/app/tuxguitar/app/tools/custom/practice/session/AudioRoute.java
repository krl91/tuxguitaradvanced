package app.tuxguitar.app.tools.custom.practice.session;

import java.util.Objects;

/**
 * The route a calibration belongs to (spec 002 FR-058): an input, an output and the audio format or profile in use. Two routes are the same only when
 * all four parts are, so a calibration is never reused after one of them changed. The parts are opaque texts and numbers given by the application: a
 * route is never judged by the name or the transport of a device (FR-057), Bluetooth included.
 * <p>
 * Java Sound does not tell the Bluetooth profile; what tells one profile from another is the format that was negotiated (the sample rate and the
 * number of channels), which is therefore the {@code profile} of the route.
 */
public final class AudioRoute {

	private final String input;
	private final String output;
	private final int sampleRate;
	private final String profile;

	public AudioRoute(String input, String output, int sampleRate, String profile) {
		this.input = Objects.requireNonNull(input);
		this.output = Objects.requireNonNull(output);
		this.sampleRate = sampleRate;
		this.profile = Objects.requireNonNull(profile);
	}

	public String input() {
		return this.input;
	}

	public String output() {
		return this.output;
	}

	public int sampleRate() {
		return this.sampleRate;
	}

	public String profile() {
		return this.profile;
	}

	/** A single text that identifies the route, reversible by {@link #parse(String)}. */
	public String key() {
		return escape(this.input) + "|" + escape(this.output) + "|" + this.sampleRate + "|" + escape(this.profile);
	}

	public static AudioRoute parse(String key) {
		String[] parts = key.split("\\|", -1);
		if (parts.length != 4) {
			throw new IllegalArgumentException("not a route: " + key);
		}
		return new AudioRoute(unescape(parts[0]), unescape(parts[1]), Integer.parseInt(parts[2]), unescape(parts[3]));
	}

	private static String escape(String text) {
		return text.replace("%", "%25").replace("|", "%7C");
	}

	private static String unescape(String text) {
		return text.replace("%7C", "|").replace("%25", "%");
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof AudioRoute)) {
			return false;
		}
		AudioRoute route = (AudioRoute) other;
		return this.sampleRate == route.sampleRate && this.input.equals(route.input) && this.output.equals(route.output) && this.profile.equals(route.profile);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.input, this.output, this.sampleRate, this.profile);
	}

	@Override
	public String toString() {
		return key();
	}
}
