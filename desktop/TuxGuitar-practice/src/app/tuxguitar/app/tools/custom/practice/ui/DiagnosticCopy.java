package app.tuxguitar.app.tools.custom.practice.ui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController;

/**
 * The diagnostic copy of a calibration recording (spec 013, FR-020): the one place where the calibration writes sound, and only when the player ticked the
 * box. A 16-bit mono WAV at the capture rate in the folder {@code tuxguitar-calibration-takes} of the user's home, named after the date and the time.
 * It lives here and not in the {@code calibration} package, which uses no file API at all (FR-017).
 */
final class DiagnosticCopy implements CalibrationController.DiagnosticSink {

	static final String FOLDER = "tuxguitar-calibration-takes";
	private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

	private final File home;
	private final Supplier<LocalDateTime> now;

	DiagnosticCopy() {
		this(new File(System.getProperty("user.home")), LocalDateTime::now);
	}

	DiagnosticCopy(File home, Supplier<LocalDateTime> now) {
		this.home = home;
		this.now = now;
	}

	File folder() {
		return new File(this.home, FOLDER);
	}

	/** @return the path of the file written, or null when it could not be written (nothing is thrown) */
	@Override
	public String write(float[] samples, int sampleRate) {
		try {
			File folder = folder();
			if (!folder.isDirectory() && !folder.mkdirs()) {
				return null;
			}
			byte[] bytes = new byte[samples.length * 2];
			for (int i = 0; i < samples.length; i++) {
				int value = Math.round(Math.max(-1f, Math.min(1f, samples[i])) * 32767f);
				bytes[2 * i] = (byte) (value & 0xff);
				bytes[2 * i + 1] = (byte) ((value >> 8) & 0xff);
			}
			File file = new File(folder, "calibration-" + NAME.format(this.now.get()) + ".wav");
			AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
			try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes), format, samples.length)) {
				AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file);
			}
			return file.getAbsolutePath();
		} catch (Exception exception) {
			return null;
		}
	}
}
