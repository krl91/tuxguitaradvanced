package app.tuxguitar.app.system;

/**
 * The default layout of the title of the main window (spec 015, FR-002): the name, the version, then the file. A stored layout equal to the previous default
 * counts as not customised and gives the new default; a layout the user wrote is kept as written.
 */
public final class TitleLayoutDefault {

	public static final String PREVIOUS_DEFAULT = "${appname} - ${filename}";
	public static final String DEFAULT = "${appname} ${appversion} - ${filename}";

	private TitleLayoutDefault() {
	}

	public static String resolve(String storedLayout) {
		if (storedLayout == null || PREVIOUS_DEFAULT.equals(storedLayout)) {
			return DEFAULT;
		}
		return storedLayout;
	}
}
