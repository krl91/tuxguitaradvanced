package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIPaintEvent;
import app.tuxguitar.ui.event.UIPaintListener;
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

	/** The target of a frame in nanoseconds: sixty a second. */
	public static final long FRAME_NANOS = 16_666_667L;

	private final UICanvas canvas;
	private final ArcadeFrameSource source;
	private final UiPainterRenderer renderer;
	private final FrameScheduler scheduler;
	private final LongSupplier wall;
	private ScheduledExecutorService timer;
	private volatile boolean disposed;
	private long lastFrameCost;

	public ArcadeCanvas(UIFactory factory, UIContainer parent, ArcadeFrameSource source, UiThread ui, LongSupplier wall, String fontName) {
		this.source = source;
		this.wall = wall;
		this.renderer = new UiPainterRenderer(factory, fontName);
		this.canvas = factory.createCanvas(parent, false);
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
		this.timer.scheduleWithFixedDelay(this.scheduler::tick, FRAME_NANOS, FRAME_NANOS, TimeUnit.NANOSECONDS);
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
			this.lastFrameCost = System.nanoTime() - start;
			this.source.painted(this.lastFrameCost);
			this.scheduler.painted();
		}
	}
}
