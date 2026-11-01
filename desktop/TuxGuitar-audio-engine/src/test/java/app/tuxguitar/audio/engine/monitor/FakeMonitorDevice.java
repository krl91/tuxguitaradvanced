package app.tuxguitar.audio.engine.monitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A stand-in for the duplex device (spec 022, contracts/monitor-engine.md, research R1 addendum): a test feeds its input block by block
 * (or gives a whole recording), it records what is written, and it can refuse to open, be lost after some writes, or hold each write
 * until the test grants it (so a test decides when the engine's thread may go on). Like the native rings, at most two input blocks wait:
 * feeding more drops the oldest and counts it.
 */
final class FakeMonitorDevice implements MonitorDevice {

	volatile boolean refuse;
	volatile int loseAfterWrites = -1;
	volatile long writeNanos;
	volatile int latencyFrames = 256;
	volatile float rate = 8000f;
	private static final int WAITING_BLOCKS = 2;
	private final ArrayDeque<float[]> input = new ArrayDeque<float[]>();
	private float[] recording;
	private int recordingOffset;
	private long dropped;
	private final Semaphore permits;
	private final List<float[]> left = new ArrayList<float[]>();
	private final List<float[]> right = new ArrayList<float[]>();
	private int opens;
	private int writes;
	private String lastInput;
	private String lastOutput;

	/** A device that writes at once (or after {@link #writeNanos}). */
	FakeMonitorDevice() {
		this.permits = null;
	}

	/** A device whose every write waits for a permit given by {@link #grant(int)}. */
	static FakeMonitorDevice gated() {
		return new FakeMonitorDevice(new Semaphore(0));
	}

	private FakeMonitorDevice(Semaphore permits) {
		this.permits = permits;
	}

	void grant(int count) {
		this.permits.release(count);
	}

	/** One input block, as the input callback would hand it. */
	synchronized void feed(float[] block) {
		if( this.input.size() >= WAITING_BLOCKS ) {
			this.input.removeFirst();
			this.dropped++;
		}
		this.input.addLast(block.clone());
		notifyAll();
	}

	/** A whole recording, read block by block as fast as the engine asks; then silence. */
	synchronized void play(float[] samples) {
		this.recording = samples;
		this.recordingOffset = 0;
	}

	synchronized boolean recordingDone() {
		return this.recording == null || this.recordingOffset >= this.recording.length;
	}

	private synchronized int read(float[] block, int n) throws InterruptedException {
		if( this.recording != null ) {
			int frames = Math.min(n, this.recording.length - this.recordingOffset);
			if( frames > 0 ) {
				System.arraycopy(this.recording, this.recordingOffset, block, 0, frames);
				this.recordingOffset += frames;
				return frames;
			}
		}
		long deadline = System.currentTimeMillis() + 20;
		while( this.input.isEmpty() ) {
			long left = deadline - System.currentTimeMillis();
			if( left <= 0 ) {
				return 0;
			}
			wait(left);
		}
		float[] next = this.input.removeFirst();
		int frames = Math.min(n, next.length);
		System.arraycopy(next, 0, block, 0, frames);
		return frames;
	}

	@Override
	public Opened open(String inputName, int inputChannel, String outputName, int blockFrames) throws MonitorDeviceException {
		synchronized (this) {
			this.opens++;
			this.lastInput = inputName;
			this.lastOutput = outputName;
		}
		if( this.refuse ) {
			throw new MonitorDeviceException("format refused by the fake device");
		}
		return new Opened() {
			@Override
			public float sampleRate() {
				return FakeMonitorDevice.this.rate;
			}

			@Override
			public int read(float[] block, int n) {
				try {
					return FakeMonitorDevice.this.read(block, n);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					return 0;
				}
			}

			@Override
			public long droppedBlocks() {
				synchronized (FakeMonitorDevice.this) {
					return FakeMonitorDevice.this.dropped;
				}
			}

			@Override
			public void write(float[] l, float[] r, int n) throws MonitorDeviceException {
				synchronized (FakeMonitorDevice.this) {
					if( FakeMonitorDevice.this.loseAfterWrites >= 0 && FakeMonitorDevice.this.writes >= FakeMonitorDevice.this.loseAfterWrites ) {
						throw new MonitorDeviceException("the fake device was unplugged");
					}
					FakeMonitorDevice.this.writes++;
					float[] a = new float[n];
					float[] b = new float[n];
					System.arraycopy(l, 0, a, 0, n);
					System.arraycopy(r, 0, b, 0, n);
					FakeMonitorDevice.this.left.add(a);
					FakeMonitorDevice.this.right.add(b);
					FakeMonitorDevice.this.notifyAll();
				}
				if( FakeMonitorDevice.this.permits != null ) {
					try {
						while( !FakeMonitorDevice.this.permits.tryAcquire(50, TimeUnit.MILLISECONDS) ) {
							if( Thread.currentThread().isInterrupted() ) {
								return;
							}
						}
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
					}
				} else if( FakeMonitorDevice.this.writeNanos > 0 ) {
					long until = System.nanoTime() + FakeMonitorDevice.this.writeNanos;
					while( System.nanoTime() < until ) {
						Thread.onSpinWait();
					}
				}
			}

			@Override
			public int estimatedLatencyFrames() {
				return FakeMonitorDevice.this.latencyFrames;
			}

			@Override
			public void close() {
			}
		};
	}

	synchronized int writes() {
		return this.left.size();
	}

	synchronized int opens() {
		return this.opens;
	}

	synchronized String lastOutput() {
		return this.lastOutput;
	}

	synchronized String lastInput() {
		return this.lastInput;
	}

	/** Waits until at least {@code count} blocks were written. */
	synchronized boolean awaitWrites(int count, long timeoutMillis) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while( this.left.size() < count ) {
			long left = deadline - System.currentTimeMillis();
			if( left <= 0 ) {
				return false;
			}
			wait(left);
		}
		return true;
	}

	synchronized float[] left(int index) {
		return this.left.get(index);
	}

	synchronized float[] right(int index) {
		return this.right.get(index);
	}

	/** Everything written on the left channel, in order. */
	synchronized float[] allLeft() {
		int total = 0;
		for( float[] block : this.left ) {
			total += block.length;
		}
		float[] all = new float[total];
		int offset = 0;
		for( float[] block : this.left ) {
			System.arraycopy(block, 0, all, offset, block.length);
			offset += block.length;
		}
		return all;
	}
}
