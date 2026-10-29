package app.tuxguitar.app.tools.custom.practice.ui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * The record of a Follow run on disk (spec 016 FR-003, contracts/run-record.md): the only code of the run that writes a file, and only when the learner
 * ticked the box. In the folder {@code tuxguitar-follow-takes} of the user's home, the CSV record and, when asked, a 16-bit mono WAV of the sound,
 * sharing a stem made of the date and the time. Written in the manner of {@link DiagnosticCopy}; nothing leaves the computer.
 */
final class FollowTakeCopy {

	static final String FOLDER = "tuxguitar-follow-takes";
	private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

	private final File home;
	private final Supplier<LocalDateTime> now;

	FollowTakeCopy() {
		this(new File(System.getProperty("user.home")), LocalDateTime::now);
	}

	FollowTakeCopy(File home, Supplier<LocalDateTime> now) {
		this.home = home;
		this.now = now;
	}

	File folder() {
		return new File(this.home, FOLDER);
	}

	/** The stem of the files of a run ended now: {@code follow-<yyyy-MM-dd-HHmmss>}. */
	String stem() {
		return "follow-" + NAME.format(this.now.get());
	}

	/**
	 * {@link #write} on a daemon thread of its own, never the analysis's nor the window's (constitution VI): a long sound takes time to write.
	 *
	 * @param failed told what went wrong when the write failed, on that thread
	 */
	void writeLater(String stem, String record, float[] samples, int sampleRate, Consumer<String> failed) {
		Thread writer = new Thread(() -> {
			String error = write(stem, record, samples, sampleRate);
			if (error != null) {
				failed.accept(error);
			}
		}, "follow-take-writer");
		writer.setDaemon(true);
		writer.start();
	}

	/**
	 * @param samples the sound, or null for the record alone
	 * @return null when written, otherwise what went wrong (nothing is thrown)
	 */
	String write(String stem, String record, float[] samples, int sampleRate) {
		try {
			File folder = folder();
			if (!folder.isDirectory() && !folder.mkdirs()) {
				return folder.getAbsolutePath();
			}
			Files.write(new File(folder, stem + ".csv").toPath(), record.getBytes(StandardCharsets.UTF_8));
			if (samples != null) {
				byte[] bytes = new byte[samples.length * 2];
				for (int i = 0; i < samples.length; i++) {
					int value = Math.round(Math.max(-1f, Math.min(1f, samples[i])) * 32767f);
					bytes[2 * i] = (byte) (value & 0xff);
					bytes[2 * i + 1] = (byte) ((value >> 8) & 0xff);
				}
				AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
				try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes), format, samples.length)) {
					AudioSystem.write(stream, AudioFileFormat.Type.WAVE, new File(folder, stem + ".wav"));
				}
			}
			return null;
		} catch (Exception exception) {
			String message = exception.getMessage();
			return message != null ? message : exception.getClass().getSimpleName();
		}
	}
}
