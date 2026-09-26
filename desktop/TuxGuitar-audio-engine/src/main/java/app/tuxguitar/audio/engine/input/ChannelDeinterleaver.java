package app.tuxguitar.audio.engine.input;

/** Allocation-free selected-channel extraction for interleaved PCM samples. */
public final class ChannelDeinterleaver {

	private ChannelDeinterleaver() {
	}

	public static void deinterleave(float[] interleaved, int frameCount, int channelCount, int selectedChannel, float[] output) {
		if (interleaved == null || output == null || frameCount < 0 || channelCount <= 0
				|| selectedChannel < 0 || selectedChannel >= channelCount
				|| frameCount > output.length || (long) frameCount * channelCount > interleaved.length) {
			throw new IllegalArgumentException("invalid channel or buffer dimensions");
		}
		for (int frame = 0; frame < frameCount; frame++) {
			output[frame] = interleaved[frame * channelCount + selectedChannel];
		}
	}
}
