package app.tuxguitar.app.tools.custom.practice.session;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * The practice session thread: the only writer of session state. Analysis frames and learner
 * commands are queued without waiting and handled one at a time, in arrival order, by the
 * {@link Handler}. Snapshots for the UI are coalesced: at most one delivery per
 * {@link #MIN_UI_INTERVAL_NANOS} (about 30 per second), always ending with the latest one.
 */
public final class SessionLoop<S> {

	public interface Handler<S> {
		/** @return the new snapshot, or null when nothing visible changed */
		S onFrame(AnalysisFrame frame);

		/** @return the new snapshot, or null when nothing visible changed */
		S onCommand(PracticeCommand command);
	}

	public static final long MIN_UI_INTERVAL_NANOS = 33_000_000L;

	private static final Object STOP = new Object();

	private final TGContext context;
	private final Handler<S> handler;
	private final Consumer<S> uiSink;
	private final Executor uiExecutor;
	private final BlockingQueue<Object> queue;
	private final AtomicReference<S> latestSnapshot;
	private volatile boolean running;
	private long lastDeliveryNanos;

	/** Delivers snapshots on the UI thread through {@code TGSynchronizer.executeLater}. */
	public SessionLoop(TGContext context, Handler<S> handler, Consumer<S> uiSink) {
		this(context, handler, uiSink, runnable -> TGSynchronizer.getInstance(context).executeLater(runnable));
	}

	public SessionLoop(TGContext context, Handler<S> handler, Consumer<S> uiSink, Executor uiExecutor) {
		this.context = context;
		this.handler = handler;
		this.uiSink = uiSink;
		this.uiExecutor = uiExecutor;
		this.queue = new LinkedBlockingQueue<Object>();
		this.latestSnapshot = new AtomicReference<S>();
		this.lastDeliveryNanos = System.nanoTime() - MIN_UI_INTERVAL_NANOS;
	}

	public synchronized void start() {
		if( !this.running ) {
			this.running = true;
			TGThreadManager.getInstance(this.context).start(this::run);
		}
	}

	/** Stops after the events already queued; later posts are ignored. */
	public synchronized void stop() {
		if( this.running ) {
			this.running = false;
			this.queue.offer(STOP);
		}
	}

	/** Called on the analysis thread; never waits. */
	public void postFrame(AnalysisFrame frame) {
		if( this.running ) {
			this.queue.offer(frame);
		}
	}

	/** Called by the practice actions; never waits. */
	public void postCommand(PracticeCommand command) {
		if( this.running ) {
			this.queue.offer(command);
		}
	}

	private void run() {
		while( true ) {
			Object event;
			try {
				event = (this.latestSnapshot.get() != null ? this.queue.poll(remainingIntervalNanos(), TimeUnit.NANOSECONDS) : this.queue.take());
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
			if( event == STOP ) {
				deliverPending();
				return;
			}
			S snapshot = null;
			if( event instanceof AnalysisFrame ) {
				snapshot = this.handler.onFrame((AnalysisFrame) event);
			} else if( event instanceof PracticeCommand ) {
				snapshot = this.handler.onCommand((PracticeCommand) event);
			}
			if( snapshot != null ) {
				this.latestSnapshot.set(snapshot);
			}
			if( remainingIntervalNanos() <= 0 ) {
				deliverPending();
			}
		}
	}

	private long remainingIntervalNanos() {
		return MIN_UI_INTERVAL_NANOS - (System.nanoTime() - this.lastDeliveryNanos);
	}

	private void deliverPending() {
		S snapshot = this.latestSnapshot.getAndSet(null);
		if( snapshot != null ) {
			this.lastDeliveryNanos = System.nanoTime();
			this.uiExecutor.execute(() -> this.uiSink.accept(snapshot));
		}
	}
}
