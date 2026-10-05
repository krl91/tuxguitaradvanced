package app.tuxguitar.app.tools.custom.practice.replay;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Recorded audio for replay tests, read as mono samples in [-1, 1]. A .wav is read as is; any other
 * file (the reference recording is an .m4a) is converted to a temporary .wav with the macOS
 * {@code afconvert} tool when it exists. Nothing is written next to the audio and nothing is kept.
 */
final class RecordedAudio {

	final float[] samples;
	final int sampleRate;

	private RecordedAudio(float[] samples, int sampleRate) {
		this.samples = samples;
		this.sampleRate = sampleRate;
	}

	/** @return the audio, or null when it cannot be read here (missing file, no converter) */
	static RecordedAudio load(Path file) {
		try {
			if( !Files.isRegularFile(file) ) {
				return null;
			}
			Path wav = file;
			Path temporary = null;
			if( !file.getFileName().toString().toLowerCase().endsWith(".wav") ) {
				if( !Files.isExecutable(Paths.get("/usr/bin/afconvert")) ) {
					return null;
				}
				temporary = Files.createTempFile("practice-replay", ".wav");
				Process process = new ProcessBuilder("/usr/bin/afconvert", "-f", "WAVE", "-d", "LEI16", "-c", "1", file.toString(), temporary.toString())
						.redirectErrorStream(true).start();
				process.getInputStream().readAllBytes();
				if( process.waitFor() != 0 ) {
					Files.deleteIfExists(temporary);
					return null;
				}
				wav = temporary;
			}
			try( AudioInputStream in = AudioSystem.getAudioInputStream(new File(wav.toString())) ) {
				int channels = in.getFormat().getChannels();
				int rate = (int) in.getFormat().getSampleRate();
				byte[] bytes = in.readAllBytes();
				int frames = bytes.length / (2 * channels);
				float[] mono = new float[frames];
				for( int i = 0 ; i < frames ; i ++ ) {
					double sum = 0.0;
					for( int c = 0 ; c < channels ; c ++ ) {
						int at = 2 * (i * channels + c);
						sum += ((short) ((bytes[at + 1] << 8) | (bytes[at] & 0xff))) / 32768.0;
					}
					mono[i] = (float) (sum / channels);
				}
				return new RecordedAudio(mono, rate);
			} finally {
				if( temporary != null ) {
					Files.deleteIfExists(temporary);
				}
			}
		} catch (IOException | InterruptedException | javax.sound.sampled.UnsupportedAudioFileException exception) {
			return null;
		}
	}
}
