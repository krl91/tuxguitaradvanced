package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Test;

/**
 * Spec 013 (T053, FR-020): the only code that writes a file of the calibration. A 16-bit mono WAV at the rate given, that reads back to the samples, in a
 * folder created when needed, named after the date and the time; a folder that cannot be written gives null and throws nothing.
 */
public class DiagnosticCopyTest {

	@Test
	public void theFileIsASixteenBitMonoWavThatReadsBackToTheSamplesInAFolderCreatedWhenNeeded() throws Exception {
		Path home = Files.createTempDirectory("diag-home");
		float[] samples = new float[4800];
		for (int i = 0; i < samples.length; i++) {
			samples[i] = (float) (0.5 * Math.sin(2 * Math.PI * 110.0 * i / 48000.0));
		}
		String path = new DiagnosticCopy(home.toFile(), () -> LocalDateTime.of(2026, 10, 19, 14, 5, 9)).write(samples, 48000);
		assertNotNull(path);
		File file = new File(path);
		assertEquals(new File(home.toFile(), "tuxguitar-calibration-takes"), file.getParentFile());
		assertEquals("calibration-2026-10-19-140509.wav", file.getName());
		AudioFileFormat format = AudioSystem.getAudioFileFormat(file);
		assertEquals(48000f, format.getFormat().getSampleRate());
		assertEquals(16, format.getFormat().getSampleSizeInBits());
		assertEquals(1, format.getFormat().getChannels());
		assertEquals(samples.length, format.getFrameLength());
		try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
			byte[] bytes = in.readAllBytes();
			int middle = 1200;
			short value = (short) ((bytes[2 * middle] & 0xff) | (bytes[2 * middle + 1] << 8));
			assertEquals(samples[middle] * 32767.0, value, 2.0);
		}
	}

	@Test
	public void aFolderThatCannotBeWrittenGivesNullAndThrowsNothing() throws Exception {
		Path home = Files.createTempFile("diag-not-a-folder", ".tmp");
		assertNull(new DiagnosticCopy(home.toFile(), () -> LocalDateTime.of(2026, 10, 19, 14, 5, 9)).write(new float[100], 48000));
		assertTrue(Files.isRegularFile(home), "the file that is in the way is untouched");
	}

	@Test
	public void theDefaultFolderIsInTheHomeOfTheUser() {
		assertEquals(new File(System.getProperty("user.home"), "tuxguitar-calibration-takes"), new DiagnosticCopy().folder());
	}
}
