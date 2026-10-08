package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Decides, at each tick of the repaint timer, whether to ask for a new frame (spec 003 FR-055, T041). At most one frame is ever pending: while the UI
 * thread has not finished painting the last one, the ticks are counted as skipped and nothing is queued, so a slow paint can never build a backlog nor
 * make the next frame show an old instant: each frame is built from the clock at the moment it is painted. Thread-safe; it holds no graphics.
 */
public final class FrameScheduler {

	/** A request not answered after this long is taken as lost (the system may merge two repaint requests into none), so the picture cannot freeze. */
	public static final long LOST_AFTER_NANOS = 250_000_000L;

	private final Runnable requestRepaint;
	private final LongSupplier wall;
	private final AtomicLong requestedAt = new AtomicLong();
	private final AtomicLong lost = new AtomicLong();
	private final AtomicBoolean pending = new AtomicBoolean();
	private final AtomicLong requested = new AtomicLong();
	private final AtomicLong skipped = new AtomicLong();

	/** @param requestRepaint asks the UI thread to paint (it must return at once, never paint on the caller's thread) */
	public FrameScheduler(Runnable requestRepaint, LongSupplier wall) {
		this.requestRepaint = requestRepaint;
		this.wall = wall;
	}

	/** One tick of the timer (any thread). Returns true if a frame was requested. */
	public boolean tick() {
		long now = this.wall.getAsLong();
		if (this.pending.get() && now - this.requestedAt.get() > LOST_AFTER_NANOS && this.pending.compareAndSet(true, false)) {
			this.lost.incrementAndGet();
		}
		if (this.pending.compareAndSet(false, true)) {
			this.requestedAt.set(now);
			this.requested.incrementAndGet();
			this.requestRepaint.run();
			return true;
		}
		this.skipped.incrementAndGet();
		return false;
	}

	/** The UI thread has finished painting (or has abandoned the frame): the next tick may request another. */
	public void painted() {
		this.pending.set(false);
	}

	public boolean isPending() {
		return this.pending.get();
	}

	public long requested() {
		return this.requested.get();
	}

	/** The requests that were never answered. */
	public long lost() {
		return this.lost.get();
	}

	public long skipped() {
		return this.skipped.get();
	}
}
