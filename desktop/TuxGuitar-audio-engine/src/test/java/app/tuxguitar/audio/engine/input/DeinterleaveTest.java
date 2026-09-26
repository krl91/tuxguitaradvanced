package app.tuxguitar.audio.engine.input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

public class DeinterleaveTest {

	@Test
	public void extractsOnlySelectedChannelWithoutMixing() {
		float[] left = new float[] { 1f, 2f, 3f };
		float[] right = new float[] { 10f, 20f, 30f };
		float[] interleaved = SyntheticSignals.interleave(left, right);
		float[] output = new float[3];

		ChannelDeinterleaver.deinterleave(interleaved, 3, 2, 1, output);
		assertArrayEquals(right, output);
	}

	@Test
	public void silentSelectedChannelStaysSilent() {
		float[] signal = new float[] { 0.3f, 0.4f, 0.5f };
		float[] silence = new float[3];
		float[] output = new float[3];

		ChannelDeinterleaver.deinterleave(SyntheticSignals.interleave(signal, silence), 3, 2, 1, output);
		assertArrayEquals(silence, output);
	}

	@Test
	public void validatesChannelAndDimensions() {
		assertThrows(IllegalArgumentException.class,
				() -> ChannelDeinterleaver.deinterleave(new float[4], 2, 2, 2, new float[2]));
		assertThrows(IllegalArgumentException.class,
				() -> ChannelDeinterleaver.deinterleave(new float[4], 3, 2, 0, new float[3]));
	}
}
