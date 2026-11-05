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
import app.tuxguitar.audio.engine.input.InputAssessment.Reason;
import app.tuxguitar.audio.engine.input.InputAssessment.Status;

/**
 * The line under a greyed start button names the cause and what to do (spec 025, FR-002 to FR-004, data-model "Start blocker"), with Check again
 * for the causes of the input level.
 */
public class QuickStartBlockerTest {

	private static final String[] FILES = { "messages.properties", "messages_fr.properties", "messages_es.properties" };

	private static QuickStart.Blocker blocker(InputAssessment assessment) {
		return QuickStart.blocker(true, assessment, false, false, false);
	}

	private static void assertBlocker(String key, boolean checkAgain, QuickStart.Blocker blocker) {
		assertNotNull(blocker, key);
		assertEquals(key, blocker.key());
		assertEquals(checkAgain, blocker.checkAgain(), key + ": Check again");
	}

	@Test
	public void eachCauseOfTheLevelHasItsLineAndCheckAgain() {
		assertBlocker("practice.start.blocker.not-checked", true, blocker(InputAssessment.UNCHECKED));
		assertBlocker("practice.start.blocker.no-signal", true, blocker(new InputAssessment(Status.UNUSABLE, Reason.NO_SIGNAL)));
		assertBlocker("practice.start.blocker.saturating", true, blocker(new InputAssessment(Status.UNUSABLE, Reason.CLIPPING)));
		assertBlocker("practice.start.blocker.input-lost", true, blocker(new InputAssessment(Status.UNUSABLE, Reason.DEVICE_LOST)));
		assertBlocker("practice.start.blocker.access-refused", true, blocker(new InputAssessment(Status.UNUSABLE, Reason.ACCESS_DENIED_SUSPECTED)));
	}

	@Test
	public void aRunningCheckSaysItListensWithoutCheckAgain() {
		assertBlocker("practice.start.blocker.checking", false, QuickStart.blocker(true, new InputAssessment(Status.UNUSABLE, Reason.NO_SIGNAL), false, false, true));
	}

	@Test
	public void noTrackAndTheOneClickWaitHaveTheirOwnLine() {
		assertBlocker("practice.start.blocker.no-track", false, QuickStart.blocker(false, InputAssessment.UNCHECKED, false, false, false));
		assertBlocker("practice.quickstart.waiting", false, QuickStart.blocker(true, InputAssessment.UNCHECKED, false, true, false));
	}

	@Test
	public void noLineWhenTheStartCanBePressed() {
		assertNull(blocker(new InputAssessment(Status.SUITABLE, Reason.NONE)));
		assertNull(blocker(new InputAssessment(Status.SUBOPTIMAL, Reason.TOO_LOW)));
		assertNull(QuickStart.blocker(true, InputAssessment.UNCHECKED, true, false, false), "a start at once does not wait for the check");
	}

	@Test
	public void everyLineAndCheckAgainExistInTheThreeLanguages() throws IOException {
		String[] keys = { "practice.start.blocker.not-checked", "practice.start.blocker.no-signal", "practice.start.blocker.saturating",
				"practice.start.blocker.input-lost", "practice.start.blocker.access-refused", "practice.start.blocker.checking",
				"practice.start.blocker.no-track", "practice.start.check-again" };
		for (String file : FILES) {
			Properties properties = load(file);
			for (String key : keys) {
				String text = properties.getProperty(key);
				assertNotNull(text, key + " in " + file);
				assertFalse(text.trim().isEmpty(), key + " is empty in " + file);
			}
		}
		assertTrue(load("messages.properties").getProperty("practice.start.blocker.no-signal").toLowerCase().contains("play"), "the line says what to do");
	}

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
}
