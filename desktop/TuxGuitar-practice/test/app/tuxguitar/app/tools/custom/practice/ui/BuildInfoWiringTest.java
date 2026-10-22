package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.system.BuildIdentity;
import app.tuxguitar.app.system.TitleLayoutDefault;
import app.tuxguitar.app.system.variables.TGVarAppName;
import app.tuxguitar.app.system.variables.TGVarAppVersion;
import app.tuxguitar.util.TGExpressionResolver;

/**
 * Spec 015 (T016, FR-002, FR-012, FR-014): the application version variable gives the build identity's version, the default title layout resolves to
 * "name version - file", a layout the user wrote is kept, and the sources that print the version use the build identity.
 */
public class BuildInfoWiringTest {

	private static final String COMMIT = "fc50a2f43aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	private static BuildIdentity release() {
		Properties properties = new Properties();
		properties.setProperty("version", "0.6.0-beta.1");
		properties.setProperty("commit", COMMIT);
		return BuildIdentity.fromProperties(properties);
	}

	private static String title(String storedLayout, BuildIdentity identity) {
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put(TGVarAppName.NAME, new TGVarAppName());
		variables.put(TGVarAppVersion.NAME, new TGVarAppVersion(identity));
		variables.put("filename", "song.tg");
		TGExpressionResolver resolver = new TGExpressionResolver();
		resolver.addResolver(new TGExpressionResolver.MapPropertyResolver(variables));
		return resolver.resolve(TitleLayoutDefault.resolve(storedLayout));
	}

	@Test
	public void theVersionVariableGivesTheBuildVersionNotTheUpstreamNumber() {
		assertEquals("0.6.0-beta.1", new TGVarAppVersion(release()).toString());
		assertEquals("dev", new TGVarAppVersion(BuildIdentity.fromProperties(null)).toString());
	}

	@Test
	public void theDefaultTitleShowsTheNameTheVersionAndTheFile() {
		assertEquals("TuxGuitarAdvanced 0.6.0-beta.1 - song.tg", title(TitleLayoutDefault.DEFAULT, release()));
	}

	@Test
	public void theOldStoredDefaultShowsTheNewDefaultAndAUserLayoutIsKept() {
		assertEquals("TuxGuitarAdvanced 0.6.0-beta.1 - song.tg", title("${appname} - ${filename}", release()));
		assertEquals("song.tg | TuxGuitarAdvanced", title("${filename} | ${appname}", release()));
	}

	@Test
	public void aDevelopmentBuildShowsDevInTheTitle() {
		assertEquals("TuxGuitarAdvanced dev - song.tg", title(null, BuildIdentity.fromProperties(null)));
	}

	private static String source(String relative) throws IOException {
		Path path = Paths.get("..", "TuxGuitar", "src", relative);
		if (!Files.exists(path)) {
			path = Paths.get("desktop", "TuxGuitar", "src", relative);
		}
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	@Test
	public void theCommandLineVersionAndTheWindowTitleUseTheBuildIdentity() throws IOException {
		String parser = source("app/tuxguitar/app/util/ArgumentParser.java");
		assertTrue(parser.contains("commandLine()"), "--version prints the build identity's command line text");
		assertTrue(!parser.contains("TGVersion.CURRENT.getVersion()"), "--version no longer prints the upstream number");
		String window = source("app/tuxguitar/app/view/main/TGWindow.java");
		assertTrue(window.contains("TitleLayoutDefault.resolve("), "the stored layout goes through TitleLayoutDefault");
		String defaults = source("app/tuxguitar/app/system/config/TGConfigDefaults.java");
		assertTrue(defaults.contains("TitleLayoutDefault.DEFAULT"), "the default layout is TitleLayoutDefault.DEFAULT");
		String option = source("app/tuxguitar/app/view/dialog/settings/items/MainOption.java");
		assertTrue(option.contains("TitleLayoutDefault.resolve("), "the settings window shows the layout that is really used");
	}
}
