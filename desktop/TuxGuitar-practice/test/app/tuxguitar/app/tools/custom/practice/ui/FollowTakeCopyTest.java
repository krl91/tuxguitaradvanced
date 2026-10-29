package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Test;

/**
 * Spec 016 (T010, FR-003, contracts/run-record.md): the only code that writes the record of a Follow run. In the folder {@code tuxguitar-follow-takes}
 * of the home, created when needed; the record and its sound share a stem made of the date and the time; a write that fails gives an error and throws
 * nothing.
 */
public class FollowTakeCopyTest {

	private static final LocalDateTime WHEN = LocalDateTime.of(2026, 10, 29, 18, 4, 7);

	@Test
	public void theRecordAndItsSoundShareAStemInAFolderCreatedWhenNeeded() throws Exception {
		Path home = Files.createTempDirectory("follow-home");
		FollowTakeCopy copy = new FollowTakeCopy(home.toFile(), () -> WHEN);
		String stem = copy.stem();
		assertEquals("follow-2026-10-29-180407", stem);
		assertNull(copy.write(stem, "# follow-run-record 1\n", new float[4800], 48000), "no error");
		File folder = new File(home.toFile(), "tuxguitar-follow-takes");
		File record = new File(folder, "follow-2026-10-29-180407.csv");
		File sound = new File(folder, "follow-2026-10-29-180407.wav");
		assertTrue(record.isFile());
		assertEquals("# follow-run-record 1\n", new String(Files.readAllBytes(record.toPath()), StandardCharsets.UTF_8));
		assertTrue(sound.isFile());
		AudioFileFormat format = AudioSystem.getAudioFileFormat(sound);
		assertEquals(48000f, format.getFormat().getSampleRate());
		assertEquals(16, format.getFormat().getSampleSizeInBits());
		assertEquals(1, format.getFormat().getChannels());
		assertEquals(4800, format.getFrameLength());
	}

	@Test
	public void withoutTheSoundOnlyTheRecordIsWritten() throws Exception {
		Path home = Files.createTempDirectory("follow-home");
		FollowTakeCopy copy = new FollowTakeCopy(home.toFile(), () -> WHEN);
		assertNull(copy.write(copy.stem(), "x\n", null, 0));
		File folder = new File(home.toFile(), "tuxguitar-follow-takes");
		assertTrue(new File(folder, "follow-2026-10-29-180407.csv").isFile());
		assertFalse(new File(folder, "follow-2026-10-29-180407.wav").exists());
	}

	@Test
	public void aFolderThatCannotBeWrittenGivesAnErrorAndThrowsNothing() throws Exception {
		Path home = Files.createTempFile("follow-not-a-folder", ".tmp");
		String error = new FollowTakeCopy(home.toFile(), () -> WHEN).write("follow-2026-10-29-180407", "x\n", new float[100], 48000);
		assertNotNull(error);
		assertTrue(Files.isRegularFile(home), "the file that is in the way is untouched");
	}

	@Test
	public void aLaterWriteTellsItsErrorOnItsOwnThread() throws Exception {
		Path home = Files.createTempFile("follow-not-a-folder", ".tmp");
		java.util.concurrent.BlockingQueue<String> errors = new java.util.concurrent.LinkedBlockingQueue<String>();
		java.util.concurrent.BlockingQueue<String> threads = new java.util.concurrent.LinkedBlockingQueue<String>();
		new FollowTakeCopy(home.toFile(), () -> WHEN).writeLater("follow-x", "x\n", null, 0, error -> {
			threads.add(Thread.currentThread().getName());
			errors.add(error);
		});
		assertNotNull(errors.poll(20, java.util.concurrent.TimeUnit.SECONDS), "told");
		assertEquals("follow-take-writer", threads.poll());
	}

	@Test
	public void theDefaultFolderIsInTheHomeOfTheUser() {
		assertEquals(new File(System.getProperty("user.home"), "tuxguitar-follow-takes"), new FollowTakeCopy().folder());
	}
}
