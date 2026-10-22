package app.tuxguitar.app.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * Spec 015 (T006, FR-002, FR-007, FR-011, FR-013, FR-014): the build identity read from build-info.properties and the texts it gives.
 */
public class BuildIdentityTest {

	private static final String COMMIT = "fc50a2f43aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	private static Properties props(String... keyValues) {
		Properties properties = new Properties();
		for (int i = 0; i + 1 < keyValues.length; i += 2) {
			properties.setProperty(keyValues[i], keyValues[i + 1]);
		}
		return properties;
	}

	@Test
	public void aReleaseIsReadWithAllItsKeys() {
		BuildIdentity identity = BuildIdentity.fromProperties(props("version", "0.6.0-beta.1", "numeric", "0.6.0.0", "commit", COMMIT, "modified", "false"));
		assertEquals("0.6.0-beta.1", identity.getVersion());
		assertEquals("0.6.0.0", identity.getNumeric());
		assertEquals(COMMIT, identity.getCommit());
		assertEquals("fc50a2f", identity.getShortCommit());
		assertTrue(identity.isRelease());
		assertFalse(identity.isModified());
	}

	@Test
	public void theNumericVersionNeverContainsTheLabel() {
		assertEquals("0.6.0.0", BuildIdentity.fromProperties(props("version", "0.6.0", "commit", COMMIT)).getNumeric());
		assertEquals("0.6.0.0", BuildIdentity.fromProperties(props("version", "0.6.0-test", "commit", COMMIT)).getNumeric());
		assertEquals("12.3.45.0", BuildIdentity.fromProperties(props("version", "12.3.45-rc.2", "commit", COMMIT)).getNumeric());
	}

	@Test
	public void aMissingFileOrMissingKeysGiveTheDevelopmentIdentity() {
		for (Properties properties : new Properties[] { null, new Properties() }) {
			BuildIdentity identity = BuildIdentity.fromProperties(properties);
			assertEquals("dev", identity.getVersion());
			assertEquals("unknown", identity.getCommit());
			assertEquals("unknown", identity.getShortCommit());
			assertFalse(identity.isRelease());
			assertFalse(identity.isModified());
		}
	}

	@Test
	public void unknownKeysAreIgnoredAndAnInvalidValueFallsBackToDevOrUnknown() {
		BuildIdentity identity = BuildIdentity.fromProperties(props("version", "0.6.0", "commit", COMMIT, "colour", "blue"));
		assertEquals("0.6.0", identity.getVersion());
		assertEquals("dev", BuildIdentity.fromProperties(props("version", "0.6", "commit", COMMIT)).getVersion());
		assertEquals("unknown", BuildIdentity.fromProperties(props("version", "0.6.0", "commit", "not-a-sha")).getCommit());
		assertEquals("unknown", BuildIdentity.fromProperties(props("version", "0.6.0", "commit", COMMIT.substring(0, 39))).getCommit());
	}

	@Test
	public void onlyAdevelopmentBuildCanBeModified() {
		BuildIdentity dev = BuildIdentity.fromProperties(props("version", "dev", "commit", COMMIT, "modified", "true"));
		assertTrue(dev.isModified());
		BuildIdentity release = BuildIdentity.fromProperties(props("version", "0.6.0", "commit", COMMIT, "modified", "true"));
		assertFalse(release.isModified());
	}

	@Test
	public void theTextsOfARelease() {
		BuildIdentity identity = BuildIdentity.fromProperties(props("version", "0.6.0-beta.1", "commit", COMMIT));
		assertEquals("TuxGuitarAdvanced 0.6.0-beta.1", identity.title());
		assertEquals("TuxGuitarAdvanced 0.6.0-beta.1 (" + COMMIT + ")", identity.copyLine());
		assertEquals("TuxGuitarAdvanced 0.6.0-beta.1 (fc50a2f)", identity.commandLine());
	}

	@Test
	public void theTextsOfDevelopmentBuilds() {
		BuildIdentity clean = BuildIdentity.fromProperties(props("version", "dev", "commit", COMMIT, "modified", "false"));
		assertEquals("TuxGuitarAdvanced dev", clean.title());
		assertEquals("TuxGuitarAdvanced dev (" + COMMIT + ")", clean.copyLine());
		assertEquals("TuxGuitarAdvanced dev (fc50a2f)", clean.commandLine());
		BuildIdentity modified = BuildIdentity.fromProperties(props("version", "dev", "commit", COMMIT, "modified", "true"));
		assertEquals("TuxGuitarAdvanced dev (" + COMMIT + ", modified)", modified.copyLine());
		BuildIdentity noGit = BuildIdentity.fromProperties(null);
		assertEquals("TuxGuitarAdvanced dev", noGit.title());
		assertEquals("TuxGuitarAdvanced dev (unknown)", noGit.copyLine());
		assertEquals("TuxGuitarAdvanced dev (unknown)", noGit.commandLine());
	}

	@Test
	public void theVersionOfATagIsValidatedWithAnyLabel() {
		for (String valid : new String[] { "0.6.0", "0.6.0-beta.1", "0.6.0-test", "1.0.0-rc.2", "10.20.30-a-b.c" }) {
			assertTrue(BuildIdentity.isValidVersion(valid), valid);
		}
		for (String invalid : new String[] { "0.6", "0.6.0-", "0.6.0-be ta", "", null, "v0.6.0", "0.6.0.1", "0.6.0-é", "dev" }) {
			assertFalse(BuildIdentity.isValidVersion(invalid), String.valueOf(invalid));
		}
	}

	@Test
	public void theCommitIsFortyHexadecimalCharacters() {
		assertTrue(BuildIdentity.isValidCommit(COMMIT));
		assertTrue(BuildIdentity.isValidCommit("0123456789abcdef0123456789abcdef01234567"));
		assertFalse(BuildIdentity.isValidCommit(COMMIT.toUpperCase().substring(0, 39)));
		assertFalse(BuildIdentity.isValidCommit("g" + COMMIT.substring(1)));
		assertFalse(BuildIdentity.isValidCommit(null));
		assertFalse(BuildIdentity.isValidCommit("unknown"));
	}
}
