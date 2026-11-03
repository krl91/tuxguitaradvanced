package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException.Reason;

/**
 * The texts of the engine choice and of its notice (spec 023, T063, contract §5): the new keys exist and are not empty in English, French and Spanish,
 * one notice per reason that falls back, and the old keys are still there.
 */
public class RecognitionEngineMessagesTest {

	private static final String[] FILES = { "messages.properties", "messages_fr.properties", "messages_es.properties" };
	private static final String[] NEW_KEYS = { "recognition.engine.pitch.option.native", "recognition.engine.pitch.option.fastyin", "recognition.engine.pitch.tip.v2",
			"recognition.engine.notice.library-missing", "recognition.engine.notice.device-not-found", "recognition.engine.notice.format-unsupported",
			"recognition.engine.notice.stream-failed" };
	private static final String[] OLD_KEYS = { "recognition.engine.pitch.name", "recognition.engine.pitch.tip", "recognition.engine.pitch.option.automatic" };

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

	@Test
	public void theNewKeysExistInEveryLanguage() throws IOException {
		for (String file : FILES) {
			Properties properties = load(file);
			for (String key : NEW_KEYS) {
				String text = properties.getProperty(key);
				assertNotNull(text, file + ": " + key);
				assertFalse(text.trim().isEmpty(), file + ": " + key + " is empty");
			}
		}
	}

	@Test
	public void theOldKeysAreKept() throws IOException {
		for (String file : FILES) {
			Properties properties = load(file);
			for (String key : OLD_KEYS) {
				assertNotNull(properties.getProperty(key), file + ": " + key);
			}
		}
	}

	@Test
	public void everyReasonThatFallsBackHasItsNotice() throws IOException {
		Properties english = load("messages.properties");
		for (Reason reason : Reason.values()) {
			String key = RecognitionEngineNotices.key(reason);
			if (reason == Reason.PERMISSION_DENIED) {
				assertEquals(null, key, "no fallback, no notice: today's permission message");
			} else {
				assertNotNull(english.getProperty(key), reason + ": " + key);
			}
		}
	}
}
