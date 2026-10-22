package app.tuxguitar.app.system;

import java.util.Properties;
import java.util.regex.Pattern;

import app.tuxguitar.util.TGBranding;

/**
 * The identity of a build (spec 015): the release version (or "dev" for a local build), the commit it was built from and, for a local build, whether the
 * working copy had uncommitted changes. It is read from {@code share/build-info.properties}, written by {@code misc/stamp-build-info.sh}; a missing file or
 * an invalid value gives the development identity, never an error. It is not the version stamped in the header of {@code .tg} files ({@code TGVersion}).
 */
public final class BuildIdentity {

	public static final String DEV = "dev";
	public static final String UNKNOWN = "unknown";

	private static final Pattern VERSION = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9A-Za-z.-]+)?$");
	private static final Pattern COMMIT = Pattern.compile("^[0-9a-f]{40}$");
	private static final int SHORT_COMMIT_LENGTH = 7;

	private final String version;
	private final String commit;
	private final boolean modified;

	private BuildIdentity(String version, String commit, boolean modified) {
		this.version = version;
		this.commit = commit;
		this.modified = modified;
	}

	/** A version of a tag: {@code MAJOR.MINOR.PATCH} with an optional label of letters, digits, dots and hyphens. */
	public static boolean isValidVersion(String version) {
		return version != null && VERSION.matcher(version).matches();
	}

	/** A full commit: 40 lowercase hexadecimal characters. */
	public static boolean isValidCommit(String commit) {
		return commit != null && COMMIT.matcher(commit).matches();
	}

	/** Reads the identity from the properties of the build-info file (may be null). */
	public static BuildIdentity fromProperties(Properties properties) {
		String version = DEV;
		String commit = UNKNOWN;
		boolean modified = false;
		if (properties != null) {
			String readVersion = trim(properties.getProperty("version"));
			if (DEV.equals(readVersion) || isValidVersion(readVersion)) {
				version = readVersion;
			}
			String readCommit = trim(properties.getProperty("commit"));
			if (isValidCommit(readCommit)) {
				commit = readCommit;
			}
			modified = DEV.equals(version) && "true".equals(trim(properties.getProperty("modified")));
		}
		return new BuildIdentity(version, commit, modified);
	}

	private static String trim(String value) {
		return value == null ? null : value.trim();
	}

	public String getVersion() {
		return this.version;
	}

	/** The numeric form {@code X.Y.Z.0} for the fields of the systems that accept only numbers; the label is never in it. */
	public String getNumeric() {
		if (!this.isRelease()) {
			return "0.0.0.0";
		}
		int dash = this.version.indexOf('-');
		return (dash < 0 ? this.version : this.version.substring(0, dash)) + ".0";
	}

	/** The full commit (40 characters) or {@code unknown}. */
	public String getCommit() {
		return this.commit;
	}

	/** The first 7 characters of the commit, or {@code unknown}. */
	public String getShortCommit() {
		return this.commit.length() >= SHORT_COMMIT_LENGTH && !UNKNOWN.equals(this.commit) ? this.commit.substring(0, SHORT_COMMIT_LENGTH) : UNKNOWN;
	}

	public boolean isRelease() {
		return !DEV.equals(this.version);
	}

	/** True only for a development build whose working copy had uncommitted changes. */
	public boolean isModified() {
		return this.modified;
	}

	/** The name and the version, as shown in the title of About. */
	public String title() {
		return TGBranding.NAME + " " + this.version;
	}

	/** The line copied by the About button: name, version, full commit, and ", modified" when it applies. */
	public String copyLine() {
		return this.title() + " (" + this.commit + (this.modified ? ", modified" : "") + ")";
	}

	/** The text printed by {@code --version}: name, version, short commit. */
	public String commandLine() {
		return this.title() + " (" + this.getShortCommit() + ")";
	}

	public String toString() {
		return this.copyLine();
	}
}
