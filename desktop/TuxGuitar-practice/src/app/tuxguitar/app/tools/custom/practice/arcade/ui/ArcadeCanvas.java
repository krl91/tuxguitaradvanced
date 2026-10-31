package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Theme;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIPaintEvent;
import app.tuxguitar.ui.event.UIPaintListener;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UICanvas;
import app.tuxguitar.ui.widget.UIContainer;

/**
 * The surface of the Arcade view (spec 003 T041): a toolkit canvas that paints the scene of the player's clock about sixty times a second. A small timer
 * thread only <em>asks</em> for a frame through the {@link FrameScheduler}; the frame is built and painted on the UI thread, at the instant of the paint,
 * from the player's clock. At most one request is pending, so a slow paint makes later ticks skip (no backlog, no old instant shown) and nothing of the
 * graphics ever runs on an audio, capture, analysis or judging thread.
 */
public final class ArcadeCanvas {

	/** Runs something on the UI thread without waiting for it. */
	public interface UiThread {
		void post(Runnable task);
	}

	/** Diagnostics of the frames, for the measurements (T042, T047, T048): nothing is computed when no observer is set. */
	public interface FrameObserver {
		/** A tick of the timer: whether a frame was requested (false: the previous one was still pending, the tick is skipped). */
		void tick(long nanos, boolean requested);

		/** A frame was painted, on the UI thread, between the two instants, allocating this many bytes on that thread (-1 when unknown). */
		void painted(long startNanos, long endNanos, long allocatedBytes);
	}

	/** The target of a frame in nanoseconds: sixty a second. */
	public static final long FRAME_NANOS = 16_666_667L;
	/**
	 * The timer ticks twice as often as a frame lasts. The system paints in step with the display: a request made just after one display cycle is painted
	 * at the next, and with a timer exactly as slow as the display the request of the following tick could always arrive before that paint, be skipped,
	 * and halve the rate. With two ticks a frame a pending frame costs a skipped tick, which is nothing, and the rate stays that of the display.
	 */
	public static final long TICK_NANOS = FRAME_NANOS / 2L;

	private final UICanvas canvas;
	private final UIColor background;
	private final ArcadeFrameSource source;
	private final PainterRenderer renderer;
	private final FrameScheduler scheduler;
	private final LongSupplier wall;
	private ScheduledExecutorService timer;
	private volatile boolean disposed;
	private long lastFrameCost;
	private volatile FrameObserver observer;

	public ArcadeCanvas(UIFactory factory, UIContainer parent, ArcadeFrameSource source, UiThread ui, LongSupplier wall, String fontName) {
		this(factory, parent, source, ui, wall, fontName, false);
	}

	/** @param forceFallback paints with the fallback path from the first frame (T046, T048); otherwise the selected path, and the fallback if it fails */
	public ArcadeCanvas(UIFactory factory, UIContainer parent, ArcadeFrameSource source, UiThread ui, LongSupplier wall, String fontName, boolean forceFallback) {
		this.source = source;
		this.wall = wall;
		this.renderer = new GuardedRenderer(new UiPainterRenderer(factory, fontName), UiPainterRenderer.fallback(factory, fontName), forceFallback);
		this.canvas = factory.createCanvas(parent, false);
		// spec 021 FR-001: the scene is mostly translucent (the neck at 10 %), so what lies under it must be the design's own dark background, the one of the
		// reference pictures, and not the window background the system gives (light grey in light appearance). It is the canvas's background, which the
		// toolkit fills before every paint, whatever painter and tier follow; not an item of the scene, whose items and calls the existing tests record (R1)
		int dark = Theme.DARK.background();
		this.background = factory.createColor((dark >> 16) & 0xFF, (dark >> 8) & 0xFF, dark & 0xFF);
		this.canvas.setBgColor(this.background);
		this.scheduler = new FrameScheduler(() -> ui.post(this::requestRedraw), wall);
		this.canvas.addPaintListener(new UIPaintListener() {
			@Override
			public void onPaint(UIPaintEvent event) {
				paint(event);
			}
		});
	}

	public UICanvas getControl() {
		return this.canvas;
	}

	/** Starts asking for frames. */
	public synchronized void start() {
		if (this.timer != null || this.disposed) {
			return;
		}
		this.timer = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "arcade-frame-timer");
			thread.setDaemon(true);
			return thread;
		});
		this.timer.scheduleAtFixedRate(() -> {
			boolean requested = this.scheduler.tick();
			FrameObserver watcher = this.observer;
			if (watcher != null) {
				watcher.tick(System.nanoTime(), requested);
			}
		}, TICK_NANOS, TICK_NANOS, TimeUnit.NANOSECONDS);
	}

	public synchronized void stop() {
		if (this.timer != null) {
			this.timer.shutdownNow();
			this.timer = null;
		}
	}

	public synchronized void dispose() {
		this.disposed = true;
		stop();
		this.renderer.dispose();
		this.background.dispose();
	}

	public void setObserver(FrameObserver observer) {
		this.observer = observer;
	}

	/** The path the frames go through now: the toolkit painter, or the fallback. */
	public String rendererName() {
		return this.renderer.name();
	}

	public FrameScheduler scheduler() {
		return this.scheduler;
	}

	/** The cost of the last frame painted, for the diagnostics. */
	public long lastFrameCostNanos() {
		return this.lastFrameCost;
	}

	private void requestRedraw() {
		if (this.disposed || this.canvas.isDisposed()) {
			this.scheduler.painted();
			return;
		}
		this.canvas.redraw();
	}

	private void paint(UIPaintEvent event) {
		FrameObserver watcher = this.observer;
		long allocatedBefore = watcher != null ? allocatedBytes() : -1L;
		long start = System.nanoTime();
		try {
			UIRectangle bounds = this.canvas.getBounds();
			if (this.disposed || bounds.getWidth() < 1f || bounds.getHeight() < 1f) {
				return;
			}
			Scene scene = this.source.frame(this.wall.getAsLong(), new Viewport(bounds.getWidth(), bounds.getHeight()));
			this.renderer.target(event.getPainter());
			this.renderer.render(scene, this.source.tier());
		} finally {
			long end = System.nanoTime();
			this.lastFrameCost = end - start;
			this.source.painted(this.lastFrameCost);
			if (watcher != null) {
				long allocatedAfter = allocatedBytes();
				watcher.painted(start, end, allocatedBefore < 0 || allocatedAfter < 0 ? -1L : allocatedAfter - allocatedBefore);
			}
			this.scheduler.painted();
		}
	}

	/** The bytes allocated so far by the calling thread, or -1 when the virtual machine cannot say. */
	private static long allocatedBytes() {
		java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
		if (bean instanceof com.sun.management.ThreadMXBean) {
			return ((com.sun.management.ThreadMXBean) bean).getThreadAllocatedBytes(Thread.currentThread().getId());
		}
		return -1L;
	}
}
