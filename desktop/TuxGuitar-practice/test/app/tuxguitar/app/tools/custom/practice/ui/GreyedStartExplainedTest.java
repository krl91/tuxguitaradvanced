package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.input.InputAssessment;

/**
 * Bug (owner, 2026-10-29, test sheet F01 step 2): in the preparation window the start button stayed greyed with no word of why. The input had heard
 * nothing yet (level not checked), and only the one-click start said "play a few notes". Owner's decision: the button stays greyed, but explained.
 */
public class GreyedStartExplainedTest {

	private static final String[] FILES = { "messages.properties", "messages_fr.properties", "messages_es.properties" };

	private static Properties load(String file) throws IOException {
		Path path = Paths.get("share", "lang", file);
		if (!Files.exists(path)) {
			path = Paths.get("desktop", "TuxGuitar-practice", "share", "lang", file);
		}
		Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	private static String source() throws IOException {
		for (String root : new String[] { "src", "TuxGuitar-practice/src", "desktop/TuxGuitar-practice/src" }) {
			Path path = Paths.get(root, "app/tuxguitar/app/tools/custom/practice/ui/PracticeWindow.java");
			if (Files.exists(path)) {
				return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			}
		}
		throw new IOException("PracticeWindow.java not found");
	}

	@Test
	public void everyGreyedStartWithATrackHasAReasonInEveryLanguage() throws IOException {
		Properties[] languages = new Properties[FILES.length];
		for (int i = 0; i < FILES.length; i++) {
			languages[i] = load(FILES[i]);
		}
		for (InputAssessment.Status status : InputAssessment.Status.values()) {
			for (boolean waiting : new boolean[] { false, true }) {
				String key = QuickStart.greyedReason(true, status, false, waiting);
				if (QuickStart.canStart(true, status, false)) {
					assertNull(key, status + ": a start that can be pressed needs no reason");
					continue;
				}
				assertNotNull(key, status + ": a greyed start must say why");
				for (int i = 0; i < FILES.length; i++) {
					String text = languages[i].getProperty(key);
					assertNotNull(text, key + " in " + FILES[i]);
					assertFalse(text.trim().isEmpty(), key + " is empty in " + FILES[i]);
				}
			}
		}
	}

	@Test
	public void aLevelNotCheckedYetAsksForSoundAndTheOneClickStartKeepsItsOwnText() {
		assertEquals("practice.start.greyed.UNCHECKED", QuickStart.greyedReason(true, InputAssessment.Status.UNCHECKED, false, false));
		assertEquals("practice.start.greyed.UNUSABLE", QuickStart.greyedReason(true, InputAssessment.Status.UNUSABLE, false, false));
		// the one-click start already said "waiting for your guitar: play a few notes and practice starts by itself"
		assertEquals("practice.quickstart.waiting", QuickStart.greyedReason(true, InputAssessment.Status.UNCHECKED, false, true));
		// no track: the track tab says it, the start line has nothing to add
		assertNull(QuickStart.greyedReason(false, InputAssessment.Status.UNCHECKED, false, false));
	}

	@Test
	public void theWindowShowsTheReasonUnderTheStartButton() throws IOException {
		String text = source();
		int at = text.indexOf("private void updateStartButton()");
		assertTrue(at >= 0, "updateStartButton exists");
		String method = text.substring(at, text.indexOf("\n\t}\n", at));
		assertTrue(method.contains("QuickStart.greyedReason("), "the start line asks the tested rule for the reason of a greyed button");
		assertTrue(method.contains("this.startLabel.setText("), "the reason is written under the start position");
	}
}
