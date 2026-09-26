package app.tuxguitar.audio.engine.input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.sound.sampled.AudioFormat;

import org.junit.jupiter.api.Test;

public class PcmSampleConverterTest {

	@Test
	public void convertsSigned16BitLittleEndian() {
		byte[] input = { 0x00, (byte) 0x80, 0x00, 0x00, (byte) 0xff, 0x7f };
		float[] output = new float[3];
		AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000f, 16, 1, 2, 48000f, false);

		assertEquals(3, PcmSampleConverter.convert(input, input.length, format, output));
		assertArrayEquals(new float[] { -1f, 0f, 32767f / 32768f }, output);
	}

	@Test
	public void convertsSigned24BitBigEndian() {
		byte[] input = { (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7f, (byte) 0xff, (byte) 0xff };
		float[] output = new float[3];
		AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000f, 24, 1, 3, 48000f, true);

		assertEquals(3, PcmSampleConverter.convert(input, input.length, format, output));
		assertArrayEquals(new float[] { -1f, 0f, 8388607f / 8388608f }, output);
	}

	@Test
	public void convertsUnsigned16BitAroundMidpoint() {
		byte[] input = { 0x00, 0x00, 0x00, (byte) 0x80, (byte) 0xff, (byte) 0xff };
		float[] output = new float[3];
		AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 48000f, 16, 1, 2, 48000f, false);

		PcmSampleConverter.convert(input, input.length, format, output);
		assertArrayEquals(new float[] { -1f, 0f, 32767f / 32768f }, output);
	}
}
