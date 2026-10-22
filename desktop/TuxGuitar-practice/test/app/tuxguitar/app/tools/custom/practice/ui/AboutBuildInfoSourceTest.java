package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Spec 015 (T018, FR-002, FR-013): structure of the About window's source. The window cannot be built in a test (it needs the whole application context), so
 * what it shows is seen by hand (T021); this test keeps the wiring from being lost.
 */
public class AboutBuildInfoSourceTest {

	private static String source() throws IOException {
		String relative = "app/tuxguitar/app/view/dialog/about/TGAboutDialog.java";
		Path path = Paths.get("..", "TuxGuitar", "src", relative);
		if (!Files.exists(path)) {
			path = Paths.get("desktop", "TuxGuitar", "src", relative);
		}
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	@Test
	public void theTitleComesFromTheBuildIdentityNotFromTheUpstreamVersion() throws IOException {
		String source = source();
		assertFalse(source.contains("TGVersion"), "About no longer shows the upstream number");
		assertTrue(source.contains("TGBuildInfo.current()"), "About reads the build identity");
		assertTrue(source.contains(".title()"), "the header is the identity's title");
	}

	@Test
	public void theShortAndTheFullCommitAreShown() throws IOException {
		String source = source();
		assertTrue(source.contains("getShortCommit()"));
		assertTrue(source.contains("getCommit()"));
		assertTrue(source.contains("help.about.commit.full"));
		assertTrue(source.contains("help.about.commit.modified"));
	}

	@Test
	public void theCopyButtonCopiesTheCopyLine() throws IOException {
		String source = source();
		assertTrue(source.contains("help.about.copy-version"));
		assertTrue(source.contains("copyLine()"));
		assertTrue(source.contains("UITextClipboard"));
		assertTrue(source.contains("help.about.copy-version.error"), "a failed or unavailable copy says so");
	}
}
