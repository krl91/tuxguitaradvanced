package app.tuxguitar.audio.engine.input;

/**
 * Preallocated single-producer/single-consumer buffer for mono audio samples.
 *
 * <p>Exactly one thread may call {@code write}; exactly one other thread may
 * call {@code read}. The producer owns {@code writeSequence} and publishes
 * completed writes with its volatile update. The consumer owns
 * {@code readSequence} and publishes freed space in the same way. This gives
 * each side visibility of array writes without locks.</p>
 *
 * <p>Writes never wait. If a write is larger than the currently free space,
 * its prefix is accepted and the remaining new samples are dropped. Reads
 * from an empty buffer return zero immediately. The cumulative dropped-sample
 * count lets downstream analysis detect a discontinuity.</p>
 */
public final class SampleRingBuffer {

	private final float[] samples;
	private final int capacity;

	private volatile long readSequence;
	private volatile long writeSequence;
	private volatile long droppedSampleCount;

	public SampleRingBuffer(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		this.samples = new float[capacity];
		this.capacity = capacity;
	}

	public int capacity() {
		return this.capacity;
	}

	/**
	 * Writes one sample without waiting.
	 *
	 * @return {@code true} when accepted; {@code false} when the full buffer
	 *         dropped the sample
	 */
	public boolean write(float sample) {
		long currentWrite = this.writeSequence;
		if (currentWrite - this.readSequence >= this.capacity) {
			this.droppedSampleCount++;
			return false;
		}

		this.samples[index(currentWrite)] = sample;
		this.writeSequence = currentWrite + 1;
		return true;
	}

	/**
	 * Writes as much of the requested block as current capacity permits.
	 * Accepted samples retain FIFO order; any unaccepted suffix is counted as
	 * dropped. This method performs no allocation on its valid-input path.
	 *
	 * @return number of accepted samples
	 */
	public int write(float[] source, int offset, int length) {
		checkRange(source.length, offset, length);

		long currentWrite = this.writeSequence;
		int free = this.capacity - (int) (currentWrite - this.readSequence);
		int accepted = Math.min(length, free);
		copyIntoBuffer(source, offset, accepted, currentWrite);

		if (accepted > 0) {
			this.writeSequence = currentWrite + accepted;
		}
		this.droppedSampleCount += length - accepted;
		return accepted;
	}

	/**
	 * Reads up to {@code length} samples without waiting. This method performs
	 * no allocation on its valid-input path.
	 *
	 * @return number of samples read, or zero when the buffer is empty
	 */
	public int read(float[] destination, int offset, int length) {
		checkRange(destination.length, offset, length);

		long currentRead = this.readSequence;
		int available = (int) (this.writeSequence - currentRead);
		int count = Math.min(length, available);
		copyFromBuffer(destination, offset, count, currentRead);

		if (count > 0) {
			this.readSequence = currentRead + count;
		}
		return count;
	}

	public int availableToRead() {
		return (int) (this.writeSequence - this.readSequence);
	}

	public int remainingCapacity() {
		return this.capacity - availableToRead();
	}

	/** Returns the cumulative number of new samples rejected by full writes. */
	public long getDroppedSampleCount() {
		return this.droppedSampleCount;
	}

	/**
	 * Returns the cumulative number of accepted samples. Any thread may read it;
	 * the producer can use it to mark where its next write will start.
	 */
	public long getWrittenSampleCount() {
		return this.writeSequence;
	}

	/**
	 * Returns the cumulative number of samples read (consumer side): with the written count, the index of the stream the consumer is at,
	 * which the capture clock turns into a wall clock time.
	 */
	public long getReadSampleCount() {
		return this.readSequence;
	}

	/**
	 * Consumer only: discards unread samples written before the given written-sample
	 * count, without waiting. Samples written after that mark stay readable.
	 *
	 * @return number of samples discarded
	 */
	public int skipTo(long writtenSampleCount) {
		long currentRead = this.readSequence;
		long target = Math.min(writtenSampleCount, this.writeSequence);
		if (target <= currentRead) {
			return 0;
		}
		this.readSequence = target;
		return (int) (target - currentRead);
	}

	private int index(long sequence) {
		return (int) (sequence % this.capacity);
	}

	private void copyIntoBuffer(float[] source, int offset, int length, long sequence) {
		int target = index(sequence);
		int firstLength = Math.min(length, this.capacity - target);
		System.arraycopy(source, offset, this.samples, target, firstLength);
		System.arraycopy(source, offset + firstLength, this.samples, 0, length - firstLength);
	}

	private void copyFromBuffer(float[] destination, int offset, int length, long sequence) {
		int source = index(sequence);
		int firstLength = Math.min(length, this.capacity - source);
		System.arraycopy(this.samples, source, destination, offset, firstLength);
		System.arraycopy(this.samples, 0, destination, offset + firstLength, length - firstLength);
	}

	private static void checkRange(int arrayLength, int offset, int length) {
		if (offset < 0 || length < 0 || offset > arrayLength - length) {
			throw new IndexOutOfBoundsException("offset=" + offset + ", length=" + length + ", arrayLength=" + arrayLength);
		}
	}
}
