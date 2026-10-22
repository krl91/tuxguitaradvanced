package app.tuxguitar.app.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Spec 015 (T008, FR-002, decision "stored title layout"): the old default layout counts as not customised and shows the new default; any other layout is kept.
 */
public class TitleLayoutDefaultTest {

	private static final String OLD_DEFAULT = "${appname} - ${filename}";
	private static final String NEW_DEFAULT = "${appname} ${appversion} - ${filename}";

	@Test
	public void theNewDefaultAddsTheVersionAfterTheName() {
		assertEquals(NEW_DEFAULT, TitleLayoutDefault.DEFAULT);
	}

	@Test
	public void theOldDefaultStoredByTheApplicationGivesTheNewDefault() {
		assertEquals(NEW_DEFAULT, TitleLayoutDefault.resolve(OLD_DEFAULT));
	}

	@Test
	public void nothingStoredGivesTheNewDefault() {
		assertEquals(NEW_DEFAULT, TitleLayoutDefault.resolve(null));
	}

	@Test
	public void aLayoutTheUserWroteIsKept() {
		for (String custom : new String[] { "", "${appname}", "My title - ${songname}", "${appname}  - ${filename}", "${appname} ${appversion} - ${filename}", "${filename} - ${appname}" }) {
			assertEquals(custom, TitleLayoutDefault.resolve(custom));
		}
	}
}
