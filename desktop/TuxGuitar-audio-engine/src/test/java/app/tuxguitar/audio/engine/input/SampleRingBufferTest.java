package app.tuxguitar.audio.engine.input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SampleRingBufferTest {

	@Test
	public void preservesFifoOrderAcrossSingleSampleOperations() {
		SampleRingBuffer buffer = new SampleRingBuffer(3);
		assertTrue(buffer.write(1f));
		assertTrue(buffer.write(2f));
		assertTrue(buffer.write(3f));

		float[] actual = new float[3];
		assertEquals(3, buffer.read(actual, 0, actual.length));
		assertArrayEquals(new float[] { 1f, 2f, 3f }, actual);
	}

	@Test
	public void emptyReadReturnsImmediatelyWithoutChangingDestination() {
		SampleRingBuffer buffer = new SampleRingBuffer(4);
		float[] destination = new float[] { 7f, 8f };

		assertEquals(0, buffer.read(destination, 0, destination.length));
		assertArrayEquals(new float[] { 7f, 8f }, destination);
		assertEquals(0, buffer.availableToRead());
	}

	@Test
	public void bulkOperationsRespectOffsetsAndRequestedLengths() {
		SampleRingBuffer buffer = new SampleRingBuffer(5);
		float[] source = new float[] { 90f, 1f, 2f, 3f, 91f };
		float[] destination = new float[] { 80f, 80f, 80f, 80f, 80f };

		assertEquals(3, buffer.write(source, 1, 3));
		assertEquals(2, buffer.read(destination, 2, 2));
		assertArrayEquals(new float[] { 80f, 80f, 1f, 2f, 80f }, destination);
		assertEquals(1, buffer.availableToRead());
		assertEquals(4, buffer.remainingCapacity());
	}

	@Test
	public void wrapsAroundWithoutChangingOrder() {
		SampleRingBuffer buffer = new SampleRingBuffer(4);
		float[] discarded = new float[3];
		assertEquals(4, buffer.write(new float[] { 1f, 2f, 3f, 4f }, 0, 4));
		assertEquals(3, buffer.read(discarded, 0, 3));
		assertEquals(3, buffer.write(new float[] { 5f, 6f, 7f }, 0, 3));

		float[] actual = new float[4];
		assertEquals(4, buffer.read(actual, 0, actual.length));
		assertArrayEquals(new float[] { 4f, 5f, 6f, 7f }, actual);
	}

	@Test
	public void exactCapacityIsAcceptedAndNextSampleIsDropped() {
		SampleRingBuffer buffer = new SampleRingBuffer(2);

		assertEquals(2, buffer.write(new float[] { 10f, 11f }, 0, 2));
		assertEquals(0, buffer.remainingCapacity());
		assertFalse(buffer.write(12f));
		assertEquals(1, buffer.getDroppedSampleCount());
	}

	@Test
	public void overflowKeepsExistingSamplesAndDropsNewSuffix() {
		SampleRingBuffer buffer = new SampleRingBuffer(4);
		assertTrue(buffer.write(1f));

		assertEquals(3, buffer.write(new float[] { 2f, 3f, 4f, 5f, 6f }, 0, 5));
		assertEquals(2, buffer.getDroppedSampleCount());

		float[] actual = new float[4];
		assertEquals(4, buffer.read(actual, 0, actual.length));
		assertArrayEquals(new float[] { 1f, 2f, 3f, 4f }, actual);
	}

	@Test
	public void recoversAfterFullConditionAndKeepsCumulativeDropCount() {
		SampleRingBuffer buffer = new SampleRingBuffer(2);
		assertEquals(2, buffer.write(new float[] { 1f, 2f, 3f }, 0, 3));

		float[] first = new float[1];
		assertEquals(1, buffer.read(first, 0, 1));
		assertTrue(buffer.write(4f));

		float[] remainder = new float[2];
		assertEquals(2, buffer.read(remainder, 0, 2));
		assertArrayEquals(new float[] { 2f, 4f }, remainder);
		assertEquals(1, buffer.getDroppedSampleCount());
	}

	@Test
	public void canBeReusedAfterCompleteDrain() {
		SampleRingBuffer buffer = new SampleRingBuffer(3);
		float[] drain = new float[3];
		assertEquals(3, buffer.write(new float[] { 1f, 2f, 3f }, 0, 3));
		assertEquals(3, buffer.read(drain, 0, drain.length));

		assertEquals(2, buffer.write(new float[] { 8f, 9f }, 0, 2));
		float[] actual = new float[2];
		assertEquals(2, buffer.read(actual, 0, actual.length));
		assertArrayEquals(new float[] { 8f, 9f }, actual);
	}

	@Test
	public void zeroLengthOperationsAreNoOps() {
		SampleRingBuffer buffer = new SampleRingBuffer(2);
		float[] values = new float[] { 1f };

		assertEquals(0, buffer.write(values, 1, 0));
		assertEquals(0, buffer.read(values, 1, 0));
		assertEquals(0, buffer.getDroppedSampleCount());
	}

	@Test
	public void skipToDiscardsOnlySamplesWrittenBeforeTheMark() {
		SampleRingBuffer buffer = new SampleRingBuffer(4);
		buffer.write(new float[] { 1f, 2f }, 0, 2);
		long mark = buffer.getWrittenSampleCount();
		buffer.write(new float[] { 3f, 4f }, 0, 2);

		assertEquals(2, mark);
		assertEquals(2, buffer.skipTo(mark));
		float[] actual = new float[2];
		assertEquals(2, buffer.read(actual, 0, actual.length));
		assertArrayEquals(new float[] { 3f, 4f }, actual);
	}

	@Test
	public void skipToNeverPassesUnwrittenOrAlreadyReadSamples() {
		SampleRingBuffer buffer = new SampleRingBuffer(4);
		buffer.write(new float[] { 1f, 2f }, 0, 2);

		assertEquals(2, buffer.skipTo(10L));
		assertEquals(0, buffer.availableToRead());
		assertEquals(0, buffer.skipTo(1L));
		assertTrue(buffer.write(5f));
		float[] actual = new float[1];
		assertEquals(1, buffer.read(actual, 0, 1));
		assertArrayEquals(new float[] { 5f }, actual);
		assertEquals(3, buffer.getWrittenSampleCount());
	}

	@Test
	public void validatesCapacityAndArrayRanges() {
		assertThrows(IllegalArgumentException.class, () -> new SampleRingBuffer(0));
		SampleRingBuffer buffer = new SampleRingBuffer(2);
		float[] values = new float[2];
		assertThrows(IndexOutOfBoundsException.class, () -> buffer.write(values, -1, 1));
		assertThrows(IndexOutOfBoundsException.class, () -> buffer.write(values, 1, 2));
		assertThrows(IndexOutOfBoundsException.class, () -> buffer.read(values, 0, 3));
	}
}
