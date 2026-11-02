package app.tuxguitar.app.tools.custom.practice.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * What the amp sound of a score is remembered by (spec 022 FR-019, data-model "ScoreKey"): its title and artist, lower-cased, trimmed and
 * joined by " — ", else its file name, hashed so that no title is written in the settings.
 */
public final class ScoreKey {

	private final String value;

	private ScoreKey(String value) {
		this.value = value;
	}

	/** The key of a score, empty when it has no title, no artist and no file name (a new unsaved score). */
	public static Optional<ScoreKey> of(String title, String artist, String fileName) {
		String t = clean(title);
		String a = clean(artist);
		String text = (t.isEmpty() && a.isEmpty() ? clean(fileName) : t + " — " + a);
		return (text.isEmpty() ? Optional.<ScoreKey>empty() : Optional.of(new ScoreKey(sha256Prefix(text))));
	}

	/** Sixteen hex characters. */
	public String value() {
		return this.value;
	}

	static String sha256Prefix(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8; i++) {
				hex.append(String.format("%02x", digest[i] & 0xff));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException exception) {
			// every Java runtime has SHA-256
			throw new IllegalStateException(exception);
		}
	}

	private static String clean(String text) {
		return (text == null ? "" : text.trim().toLowerCase());
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof ScoreKey && ((ScoreKey) other).value.equals(this.value));
	}

	@Override
	public int hashCode() {
		return this.value.hashCode();
	}

	@Override
	public String toString() {
		return this.value;
	}
}
