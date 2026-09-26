package app.tuxguitar.audio.engine.input;

import javax.sound.sampled.AudioFormat;

/** Converts interleaved 16/24-bit PCM bytes to normalized floats without allocation. */
final class PcmSampleConverter {

	private PcmSampleConverter() {
	}

	static int convert(byte[] input, int byteCount, AudioFormat format, float[] output) {
		int bytesPerSample = format.getSampleSizeInBits() / 8;
		if ((bytesPerSample != 2 && bytesPerSample != 3) || byteCount < 0 || byteCount > input.length) {
			throw new IllegalArgumentException("only valid 16/24-bit PCM input is supported");
		}
		int sampleCount = byteCount / bytesPerSample;
		if (sampleCount > output.length) {
			throw new IllegalArgumentException("output buffer is too small");
		}
		boolean signed = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding());
		if (!signed && !AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding())) {
			throw new IllegalArgumentException("PCM signed or unsigned encoding required");
		}
		int midpoint = 1 << (format.getSampleSizeInBits() - 1);
		for (int sample = 0; sample < sampleCount; sample++) {
			int start = sample * bytesPerSample;
			int value = decode(input, start, bytesPerSample, format.isBigEndian());
			if (signed && (value & midpoint) != 0) {
				value -= midpoint << 1;
			} else if (!signed) {
				value -= midpoint;
			}
			output[sample] = value / (float) midpoint;
		}
		return sampleCount;
	}

	private static int decode(byte[] input, int offset, int bytesPerSample, boolean bigEndian) {
		int value = 0;
		for (int index = 0; index < bytesPerSample; index++) {
			int sourceIndex = bigEndian ? offset + index : offset + bytesPerSample - 1 - index;
			value = (value << 8) | (input[sourceIndex] & 0xff);
		}
		return value;
	}
}
