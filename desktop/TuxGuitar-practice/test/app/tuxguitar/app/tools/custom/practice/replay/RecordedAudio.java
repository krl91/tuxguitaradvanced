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
public final class RecordedAudio {

	public final float[] samples;
	public final int sampleRate;

	private RecordedAudio(float[] samples, int sampleRate) {
		this.samples = samples;
		this.sampleRate = sampleRate;
	}

	/** @return the audio, or null when it cannot be read here (missing file, no converter) */
	public static RecordedAudio load(Path file) {
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
			try( AudioInputStream raw = AudioSystem.getAudioInputStream(new File(wav.toString())) ) {
				// any PCM depth (16 or 24 bit) is read as 16 bit little-endian
				javax.sound.sampled.AudioFormat source = raw.getFormat();
				javax.sound.sampled.AudioFormat target = new javax.sound.sampled.AudioFormat(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
						source.getSampleRate(), 16, source.getChannels(), 2 * source.getChannels(), source.getSampleRate(), false);
				try( AudioInputStream in = (source.matches(target) ? raw : AudioSystem.getAudioInputStream(target, raw)) ) {
					int channels = target.getChannels();
					int rate = (int) target.getSampleRate();
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
				}
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
