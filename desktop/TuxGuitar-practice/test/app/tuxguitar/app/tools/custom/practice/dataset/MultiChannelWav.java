package app.tuxguitar.app.tools.custom.practice.dataset;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/** A PCM .wav (16 or 24 bit, any number of channels) read as one float array per channel, in [-1, 1]. Read-only: nothing is written or kept. */
final class MultiChannelWav {

	final float[][] channels;
	final int sampleRate;

	private MultiChannelWav(float[][] channels, int sampleRate) {
		this.channels = channels;
		this.sampleRate = sampleRate;
	}

	/** @return the audio, or null when it cannot be read here */
	static MultiChannelWav read(Path file) {
		try( AudioInputStream in = AudioSystem.getAudioInputStream(new File(file.toString())) ) {
			AudioFormat format = in.getFormat();
			int bytes = format.getSampleSizeInBits() / 8;
			int count = format.getChannels();
			if( bytes < 2 || bytes > 3 || format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || format.isBigEndian() ) {
				return null;
			}
			byte[] data = in.readAllBytes();
			int frames = data.length / (bytes * count);
			float[][] out = new float[count][frames];
			double scale = Math.pow(2.0, 8 * bytes - 1);
			for( int i = 0 ; i < frames ; i ++ ) {
				for( int c = 0 ; c < count ; c ++ ) {
					int at = (i * count + c) * bytes;
					int value = (data[at + bytes - 1]);
					for( int k = bytes - 2 ; k >= 0 ; k -- ) {
						value = (value << 8) | (data[at + k] & 0xff);
					}
					out[c][i] = (float) (value / scale);
				}
			}
			return new MultiChannelWav(out, (int) format.getSampleRate());
		} catch (IOException | UnsupportedAudioFileException exception) {
			return null;
		}
	}
}
