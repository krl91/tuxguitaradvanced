package app.tuxguitar.audio.engine.backend;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.nativeengine.FrameRecords;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;

/**
 * A stand-in for the native library's live entries (spec 023, T042): the test pushes the records and hop blocks the native thread would write, and
 * reads back what the backend asked for. The waits behave as the crate's: 0 on timeout, −1 once the stream failed and everything was read, −2 after close.
 */
public final class FakeRecognitionApi implements NativeRecognitionLibrary.Api {

	public static final int HOP_BLOCK = 1 + 256;

	public final AtomicInteger opens = new AtomicInteger();
	public final AtomicInteger closes = new AtomicInteger();
	public final AtomicInteger strumResets = new AtomicInteger();
	public volatile NativeRecognitionException openFailure;
	public volatile float rate = 48000f;
	public volatile String openedName;
	public volatile int openedChannel = Integer.MIN_VALUE;
	public volatile float[] openedParameters;
	public volatile float[] configured;
	public volatile boolean spectrum;
	public volatile int[] expected;
	public volatile int[] attack;
	public volatile int[] chordRequired;
	public volatile int[] chordTolerated;
	public volatile boolean hopsEnabled;
	public volatile long dropped;
	public volatile float[] strum = { 0f, 0f, Float.NaN };

	private final BlockingQueue<float[]> records = new LinkedBlockingQueue<float[]>();
	private final BlockingQueue<float[]> hops = new LinkedBlockingQueue<float[]>();
	private volatile long handle;
	private volatile boolean closed = true;
	private volatile boolean failed;

	@Override
	public long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters) throws NativeRecognitionException {
		this.opens.incrementAndGet();
		NativeRecognitionException failure = this.openFailure;
		if( failure != null ) {
			throw failure;
		}
		this.openedName = inputName;
		this.openedChannel = inputChannel;
		this.openedParameters = parameters.clone();
		this.records.clear();
		this.hops.clear();
		this.hopsEnabled = false;
		this.failed = false;
		this.closed = false;
		this.handle = 1000 + this.opens.get();
		return this.handle;
	}

	@Override
	public float sampleRate(long handle) {
		return this.rate;
	}

	@Override
	public void configure(long handle, float[] parameters) {
		this.configured = parameters.clone();
	}

	@Override
	public void setSpectrumEnabled(long handle, boolean on) {
		this.spectrum = on;
	}

	@Override
	public void setExpectedPitches(long handle, int[] midi) {
		this.expected = (midi == null ? null : midi.clone());
	}

	@Override
	public void setExpectedChord(long handle, int[] required, int[] tolerated) {
		this.chordRequired = (required == null ? null : required.clone());
		this.chordTolerated = (tolerated == null ? null : tolerated.clone());
	}

	@Override
	public void setAttackCandidates(long handle, int[] pitches) {
		this.attack = (pitches == null ? null : pitches.clone());
	}

	@Override
	public int nextFrames(long handle, float[] out, int timeoutMillis) {
		return next(handle, this.records, out, timeoutMillis);
	}

	@Override
	public int nextHops(long handle, float[] out, int timeoutMillis) {
		return next(handle, this.hops, out, timeoutMillis);
	}

	private int next(long handle, BlockingQueue<float[]> queue, float[] out, int timeoutMillis) {
		long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
		while( true ) {
			if( this.closed || handle != this.handle ) {
				return -2;
			}
			float[] first;
			try {
				first = queue.poll(2, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return 0;
			}
			if( first != null ) {
				int count = 1;
				int offset = first.length;
				System.arraycopy(first, 0, out, 0, first.length);
				float[] more;
				while( (more = queue.peek()) != null && offset + more.length <= out.length ) {
					queue.poll();
					System.arraycopy(more, 0, out, offset, more.length);
					offset += more.length;
					count++;
				}
				return count;
			}
			if( this.failed ) {
				return -1;
			}
			if( System.nanoTime() >= deadline ) {
				return 0;
			}
		}
	}

	@Override
	public void setHopsEnabled(long handle, boolean on) {
		if( on && !this.hopsEnabled ) {
			float[] marker = new float[HOP_BLOCK];
			marker[0] = this.rate;
			this.hops.offer(marker);
		}
		this.hopsEnabled = on;
	}

	@Override
	public long droppedBlocks(long handle) {
		return this.dropped;
	}

	@Override
	public void close(long handle) {
		if( handle == this.handle && !this.closed ) {
			this.closes.incrementAndGet();
			this.closed = true;
		}
	}

	@Override
	public float[] strumClip(long handle) {
		return this.strum.clone();
	}

	@Override
	public void resetStrumClip(long handle) {
		this.strumResets.incrementAndGet();
	}

	@Override
	public float[] stats(long handle) {
		return new float[] { this.dropped, 0f, 0f };
	}

	/** A record the native thread wrote for this frame. */
	public void push(AnalysisFrame frame, long arrivalNanos) {
		float[] record = new float[FrameRecords.CAPACITY];
		int length = FrameRecords.write(frame, arrivalNanos, record, 0);
		this.records.offer(Arrays.copyOf(record, length));
	}

	/** A hop block, written only while the hops are enabled (as the crate does). */
	public void pushHop(float[] samples) {
		if( this.hopsEnabled ) {
			float[] block = new float[HOP_BLOCK];
			System.arraycopy(samples, 0, block, 1, 256);
			this.hops.offer(block);
		}
	}

	/** The stream stopped (device lost): the waits return −1 once everything was read. */
	public void fail() {
		this.failed = true;
	}

	public boolean isOpen() {
		return !this.closed;
	}

	/** A plain frame at this time and capture sample end. */
	public static AnalysisFrame frame(long timestampNanos, long captureSampleEnd) {
		return new AnalysisFrame(timestampNanos, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, 220.0, 0.9, false, null, null, null, null, captureSampleEnd);
	}
}
