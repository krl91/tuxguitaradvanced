package app.tuxguitar.audio.engine.monitor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import app.tuxguitar.audio.engine.monitor.fx.OutputStage;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.thread.TGThreadPriority;
import app.tuxguitar.util.TGContext;

/**
 * Plays the guitar's input through the chain on an output (spec 022, contracts/monitor-engine.md, research R3).
 *
 * The sound has its own input (research R1 addendum): a thread reads each block from the duplex device, runs the chain and the output
 * stage, and writes it back to the device; the recognition's capture is not involved. The loop never allocates, locks or logs (a notice,
 * at most once per second, is the exception). Settings changes are atomic snapshots picked up at the next block. Listeners are told of
 * state changes and notices through the caller's executor.
 */
public final class MonitorEngine {

	/** The block size decided by the delay probe (research R1, task T003, with the native device). */
	public static final int BLOCK_FRAMES = 128;
	private static final double CLIPPING_LEVEL = Math.pow(10, -0.1 / 20);
	private static final long NOTICE_INTERVAL_NANOS = 1_000_000_000L;

	public interface Listener {

		void stateChanged(MonitorState state, String reason);

		default void notice(MonitorNotice notice) {
		}
	}

	private final MonitorDevice device;
	private final int blockFrames;
	private final LongSupplier clock;
	private final Consumer<Runnable> threadStarter;
	private final Executor listenerExecutor;
	private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
	private final AtomicReference<ChainSettings> chain = new AtomicReference<ChainSettings>();
	private final AtomicReference<MonitorState> state = new AtomicReference<MonitorState>(MonitorState.OFF);
	private final AtomicLong clippedInputBlocks = new AtomicLong();
	private final AtomicLong overBudgetBlocks = new AtomicLong();
	private final AtomicLong howls = new AtomicLong();
	private final AtomicLong silentBlocks = new AtomicLong();
	private volatile long droppedBlocks;
	private volatile double guitarVolume = 0.7;
	private volatile boolean stopRequested;
	private volatile long generation;
	private volatile int deviceLatencyFrames = -1;
	private volatile float sampleRate;
	private volatile ChainSettings appliedChain;
	private boolean clippingTold;
	private long lastClippingNotice;

	public MonitorEngine(MonitorDevice device, int blockFrames, LongSupplier clock, Consumer<Runnable> threadStarter, Executor listenerExecutor) {
		this.device = device;
		this.blockFrames = blockFrames;
		this.clock = clock;
		this.threadStarter = threadStarter;
		this.listenerExecutor = listenerExecutor;
	}

	/** The engine of the application: its thread runs at high priority through TuxGuitar's thread manager. */
	public static MonitorEngine create(TGContext context, MonitorDevice device, Executor listenerExecutor) {
		return new MonitorEngine(device, BLOCK_FRAMES, System::nanoTime,
				runnable -> TGThreadManager.getInstance(context).start(TGThreadPriority.HIGH, runnable), listenerExecutor);
	}

	public void addListener(Listener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		this.listeners.remove(listener);
	}

	/** Any thread; picked up at the next block. {@code null} is a dry chain. */
	public void setChain(ChainSettings value) {
		this.chain.set(value);
	}

	/** The chain the engine's thread runs now. */
	public ChainSettings appliedChain() {
		return this.appliedChain;
	}

	/** From 0 to 1. */
	public void setGuitarVolume(double value) {
		this.guitarVolume = Math.max(0, Math.min(1, value));
	}

	public double guitarVolume() {
		return this.guitarVolume;
	}

	public MonitorState state() {
		return this.state.get();
	}

	public int blockFrames() {
		return this.blockFrames;
	}

	/**
	 * Opens the input (and its channel, −1 for the first) and the output ({@code ""} for the system's default) and starts the sound with a half-second fade in. It never throws:
	 * a refused device gives {@link MonitorState#FAILED} with its reason. Ignored while the sound is starting or on.
	 */
	public synchronized void start(String inputName, int inputChannel, String outputName) {
		MonitorState current = this.state.get();
		if( current == MonitorState.STARTING || current == MonitorState.ON ) {
			return;
		}
		long worker = ++this.generation;
		this.stopRequested = false;
		changeState(MonitorState.STARTING, null);
		try {
			this.threadStarter.accept(() -> run(worker, inputName, inputChannel, outputName));
		} catch (RuntimeException exception) {
			changeState(MonitorState.FAILED, String.valueOf(exception.getMessage()));
		}
	}

	/** Fades out in 20 ms, closes the output, then {@link MonitorState#OFF}. */
	public synchronized void stop() {
		MonitorState current = this.state.get();
		if( current == MonitorState.STARTING || current == MonitorState.ON ) {
			this.stopRequested = true;
			changeState(MonitorState.STOPPING, null);
		} else if( current != MonitorState.STOPPING && current != MonitorState.OFF ) {
			// failed or muted: nothing plays any more
			this.generation++;
			changeState(MonitorState.OFF, null);
		}
	}

	/** Counts a block read at or above −0.1 dBFS, and tells it at most once per second. */
	private void checkClipping(float[] block, int frames) {
		double peak = 0;
		for( int i = 0 ; i < frames ; i++ ) {
			double magnitude = Math.abs(block[i]);
			if( magnitude > peak ) {
				peak = magnitude;
			}
		}
		if( peak >= CLIPPING_LEVEL ) {
			this.clippedInputBlocks.incrementAndGet();
			long now = this.clock.getAsLong();
			if( !this.clippingTold || now - this.lastClippingNotice >= NOTICE_INTERVAL_NANOS ) {
				this.clippingTold = true;
				this.lastClippingNotice = now;
				tell(MonitorNotice.INPUT_CLIPPING);
			}
		}
	}

	public MonitorStats stats() {
		int latency = this.deviceLatencyFrames;
		float rate = this.sampleRate;
		double delay = latency < 0 || rate <= 0 ? Double.NaN : (this.blockFrames + latency) * 1000.0 / rate;
		return new MonitorStats(this.droppedBlocks, this.silentBlocks.get(), this.overBudgetBlocks.get(), this.howls.get(),
				this.clippedInputBlocks.get(), delay);
	}

	private boolean current(long worker) {
		return this.generation == worker;
	}

	private void run(long worker, String inputName, int inputChannel, String outputName) {
		MonitorDevice.Opened opened = null;
		boolean failed = false;
		try {
			opened = this.device.open(inputName, inputChannel, outputName, this.blockFrames);
			float rate = opened.sampleRate();
			this.sampleRate = rate;
			this.deviceLatencyFrames = opened.estimatedLatencyFrames();
			float[] block = new float[this.blockFrames];
			float[] right = new float[this.blockFrames];
			Chain elements = new Chain();
			elements.prepare(rate, this.blockFrames);
			OutputStage stage = new OutputStage(rate, this.blockFrames);
			stage.setVolume(this.guitarVolume);
			stage.start();
			long blockNanos = Math.round(this.blockFrames * 1_000_000_000.0 / rate);
			boolean stopping = false;
			boolean inputStarted = false;
			while( current(worker) ) {
				if( this.stopRequested && !stopping ) {
					stopping = true;
					stage.stop();
				}
				if( stopping && stage.isSilent() ) {
					break;
				}
				int frames = opened.read(block, this.blockFrames);
				this.droppedBlocks = opened.droppedBlocks();
				if( frames <= 0 ) {
					this.silentBlocks.incrementAndGet();
					frames = 0;
					if( inputStarted && !stopping ) {
						// the input is late: its frames come with the next reads, and a block of silence written now would push the sound back by a block
						continue;
					}
				} else {
					inputStarted = true;
					checkClipping(block, frames);
				}
				if( frames < this.blockFrames ) {
					java.util.Arrays.fill(block, frames, this.blockFrames, 0f);
				}
				ChainSettings wanted = this.chain.get();
				if( wanted != elements.settings() || this.appliedChain != wanted ) {
					elements.apply(wanted);
					this.appliedChain = wanted;
				}
				long began = this.clock.getAsLong();
				elements.process(block, this.blockFrames);
				stage.setVolume(this.guitarVolume);
				stage.process(block, this.blockFrames);
				if( this.clock.getAsLong() - began > blockNanos / 2 ) {
					this.overBudgetBlocks.incrementAndGet();
				}
				System.arraycopy(block, 0, right, 0, this.blockFrames);
				opened.write(block, right, this.blockFrames);
				if( stage.isFadedIn() && current(worker) && this.state.get() == MonitorState.STARTING ) {
					changeStateIf(worker, MonitorState.STARTING, MonitorState.ON);
				}
			}
		} catch (MonitorDeviceException | RuntimeException exception) {
			failed = true;
			if( current(worker) ) {
				fail(worker, exception.getMessage() == null ? exception.toString() : exception.getMessage());
			}
		} finally {
			if( opened != null ) {
				try {
					opened.close();
				} catch (RuntimeException exception) {
					// a device that disappeared may already be closed
				}
			}
			this.deviceLatencyFrames = -1;
			if( !failed ) {
				synchronized (this) {
					if( current(worker) && this.state.get() == MonitorState.STOPPING ) {
						changeState(MonitorState.OFF, null);
					}
				}
			}
		}
	}

	private synchronized void fail(long worker, String reason) {
		if( current(worker) ) {
			changeState(MonitorState.FAILED, reason);
		}
	}

	private synchronized void changeStateIf(long worker, MonitorState expected, MonitorState next) {
		if( current(worker) && this.state.get() == expected ) {
			changeState(next, null);
		}
	}

	private void changeState(MonitorState next, String reason) {
		this.state.set(next);
		for( Listener listener : this.listeners ) {
			this.listenerExecutor.execute(() -> listener.stateChanged(next, reason));
		}
	}

	private void tell(MonitorNotice notice) {
		for( Listener listener : this.listeners ) {
			this.listenerExecutor.execute(() -> listener.notice(notice));
		}
	}
}
