package app.tuxguitar.app.tools.custom.practice.arcade.stress;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeCanvas;

/**
 * What a stress run measures about the frames (spec 003 SC-014): the cost of each paint, the interval between paints, the deadlines (a deadline is one tick
 * of the sixty-a-second timer: it is met when the frame asked for at that tick is painted before the next tick; a tick skipped because the previous frame
 * was still pending is missed), the longest run of missed deadlines, the allocations of the painting thread. Written by the timer thread and the UI thread.
 */
public final class StressMetrics implements ArcadeCanvas.FrameObserver {

	private final List<Long> paintEnds = new ArrayList<Long>();
	private long runStart;
	private final List<String> longPaints = new ArrayList<String>();
	private final List<String> gaps = new ArrayList<String>();

	private final List<Double> costsMs = new ArrayList<Double>();
	private final List<Double> intervalsMs = new ArrayList<Double>();
	private final List<Long> allocations = new ArrayList<Long>();
	private long lastPaintEnd;
	private long lastRequestedTick = -1;
	private long ticks;
	private long skippedTicks;
	private long lateFrames;
	private long missed;
	private long consecutive;
	private long worstRun;
	private long maxQueued;
	private long pendingRequests;
	private long slots;
	private long worstRunAt;
	/** The tasks posted to the UI thread and not yet run: the real queue of the event loop. */
	private final java.util.concurrent.atomic.AtomicInteger uiQueued = new java.util.concurrent.atomic.AtomicInteger();
	private final java.util.concurrent.atomic.AtomicInteger uiQueuedMax = new java.util.concurrent.atomic.AtomicInteger();
	private volatile long lostRequests;
	private volatile long skippedByScheduler;
	/** What the UI thread was doing, sampled every few milliseconds: (wall nanos, the top frames of its stack). */
	private final java.util.concurrent.ConcurrentLinkedQueue<Object[]> samples = new java.util.concurrent.ConcurrentLinkedQueue<Object[]>();

	/** A task was posted to the UI thread. */
	public void postedToUi() {
		this.uiQueuedMax.accumulateAndGet(this.uiQueued.incrementAndGet(), Math::max);
	}

	/** A posted task ran on the UI thread. */
	public void ranOnUi() {
		this.uiQueued.decrementAndGet();
	}

	public int maxUiQueue() {
		return this.uiQueuedMax.get();
	}

	public void schedulerFigures(long lost, long skipped) {
		this.lostRequests = lost;
		this.skippedByScheduler = skipped;
	}

	public void sampleUiThread(long nanos, String where) {
		this.samples.add(new Object[] { nanos, where });
	}

	/** What the UI thread was doing during each gap between two paints of more than this many milliseconds: the three most frequent places of its stack. */
	public synchronized String explainGaps(long thresholdMillis) {
		StringBuilder text = new StringBuilder();
		for (int i = 1; i < this.paintEnds.size() && text.length() < 3000; i++) {
			long from = this.paintEnds.get(i - 1);
			long to = this.paintEnds.get(i);
			if (to - from <= thresholdMillis * 1_000_000L) {
				continue;
			}
			java.util.Map<String, Integer> where = new java.util.TreeMap<String, Integer>();
			int count = 0;
			for (Object[] sample : this.samples) {
				long at = (Long) sample[0];
				if (at > from && at < to) {
					where.merge((String) sample[1], 1, Integer::sum);
					count++;
				}
			}
			java.util.List<java.util.Map.Entry<String, Integer>> sorted = new java.util.ArrayList<java.util.Map.Entry<String, Integer>>(where.entrySet());
			sorted.sort((a, b) -> b.getValue() - a.getValue());
			text.append(String.format(Locale.ROOT, "%n    gap %.0f ms at %.1f s, %d samples of the UI thread:", (to - from) / 1e6, (to - this.runStart) / 1e9, count));
			for (int k = 0; k < Math.min(3, sorted.size()); k++) {
				text.append(" [").append(sorted.get(k).getValue()).append("x ").append(sorted.get(k).getKey()).append(']');
			}
		}
		return text.length() == 0 ? "no gap longer than " + thresholdMillis + " ms" : text.toString();
	}

	/** The run begins now: the frames painted so far (the window opening and warming up) are not part of it. */
	public synchronized void beginRun(long nanos) {
		this.runStart = nanos;
		this.costsMs.clear();
		this.intervalsMs.clear();
		this.allocations.clear();
		this.paintEnds.clear();
		this.longPaints.clear();
		this.gaps.clear();
		this.lastPaintEnd = 0;
		this.ticks = 0;
		this.skippedTicks = 0;
		this.lateFrames = 0;
		this.missed = 0;
		this.consecutive = 0;
		this.worstRun = 0;
		this.maxQueued = 0;
		this.pendingRequests = 0;
		this.lastRequestedTick = -1;
		this.samples.clear();
		this.uiQueued.set(0);
		this.uiQueuedMax.set(0);
	}

	@Override
	public synchronized void tick(long nanos, boolean requested) {
		this.ticks++;
		if (requested) {
			this.lastRequestedTick = nanos;
			this.pendingRequests++;
			this.maxQueued = Math.max(this.maxQueued, this.pendingRequests);
		} else {
			this.skippedTicks++;
		}
	}

	@Override
	public synchronized void painted(long startNanos, long endNanos, long allocatedBytes) {
		this.costsMs.add((endNanos - startNanos) / 1e6);
		this.paintEnds.add(endNanos);
		if ((endNanos - startNanos) > 2 * ArcadeCanvas.FRAME_NANOS && this.longPaints.size() < 8) {
			this.longPaints.add(String.format(Locale.ROOT, "%.1f ms at %.1f s into the run", (endNanos - startNanos) / 1e6, (endNanos - this.runStart) / 1e9));
		}
		if (this.lastPaintEnd != 0) {
			this.intervalsMs.add((endNanos - this.lastPaintEnd) / 1e6);
			if (endNanos - this.lastPaintEnd > 50_000_000L && this.gaps.size() < 12) {
				this.gaps.add(String.format(Locale.ROOT, "%.0f ms at %.1f s", (endNanos - this.lastPaintEnd) / 1e6, (endNanos - this.runStart) / 1e9));
			}
		}
		this.lastPaintEnd = endNanos;
		if (allocatedBytes >= 0) {
			this.allocations.add(allocatedBytes);
		}
		this.pendingRequests = Math.max(0, this.pendingRequests - 1);
	}

	/**
	 * The deadlines are the slots of one frame (16.7 ms) counted from the first paint of the run: a slot is met when a paint ends in it and missed when no
	 * paint ends in it. The longest run of missed slots, and when it began, are the figures of the consecutive misses.
	 */
	private void computeSlots() {
		this.missed = 0;
		this.worstRun = 0;
		this.worstRunAt = 0;
		if (this.paintEnds.size() < 2) {
			return;
		}
		long first = this.paintEnds.get(0);
		long last = this.paintEnds.get(this.paintEnds.size() - 1);
		int slots = (int) ((last - first) / ArcadeCanvas.FRAME_NANOS) + 1;
		boolean[] met = new boolean[slots];
		for (long end : this.paintEnds) {
			met[(int) ((end - first) / ArcadeCanvas.FRAME_NANOS)] = true;
		}
		long run = 0;
		this.slots = slots;
		for (int k = 0; k < slots; k++) {
			if (!met[k]) {
				this.missed++;
				run++;
				if (run > this.worstRun) {
					this.worstRun = run;
					this.worstRunAt = first + (long) (k - run + 1) * ArcadeCanvas.FRAME_NANOS - this.runStart;
				}
			} else {
				run = 0;
			}
		}
	}

	private static double percentile(List<Double> values, double p) {
		if (values.isEmpty()) {
			return Double.NaN;
		}
		double[] sorted = new double[values.size()];
		for (int i = 0; i < sorted.length; i++) {
			sorted[i] = values.get(i);
		}
		Arrays.sort(sorted);
		return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1)];
	}

	public synchronized double costP(double p) {
		return percentile(this.costsMs, p);
	}

	public synchronized double costMax() {
		return percentile(this.costsMs, 1.0);
	}

	public synchronized double intervalP(double p) {
		return percentile(this.intervalsMs, p);
	}

	public synchronized long frames() {
		return this.costsMs.size();
	}

	public synchronized long ticks() {
		return this.ticks;
	}

	public synchronized long skippedTicks() {
		return this.skippedTicks;
	}

	public synchronized long lateFrames() {
		return this.lateFrames;
	}

	public synchronized long missedDeadlines() {
		computeSlots();
		return this.missed;
	}

	public synchronized long worstConsecutiveMisses() {
		computeSlots();
		return this.worstRun;
	}

	/** The most frames asked for and not yet painted at any moment: more than one would be a queue. */
	public synchronized long maxQueuedFrames() {
		return this.uiQueuedMax.get();
	}

	public synchronized long allocationP(double p) {
		if (this.allocations.isEmpty()) {
			return -1L;
		}
		long[] sorted = new long[this.allocations.size()];
		for (int i = 0; i < sorted.length; i++) {
			sorted[i] = this.allocations.get(i);
		}
		Arrays.sort(sorted);
		return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1)];
	}

	public synchronized String describe() {
		computeSlots();
		return String.format(Locale.ROOT,
				"%d frames painted in %d deadline slots of 16.7 ms (timer ticks skipped because a frame was pending: %d, which is not a miss); paint cost p50 %.2f p95 %.2f p99 %.2f max %.2f ms;"
						+ " interval between paints p50 %.2f p95 %.2f p99 %.2f ms; missed deadline slots %d, longest run of consecutive misses %d (began %.1f s into the run);"
						+ " most tasks waiting in the UI thread's queue at once %d; requests the scheduler took as lost %d, ticks it skipped %d; allocation per paint p50 %d p99 %d KB; paints longer than two frames: %s; gaps of more than 50 ms between two paints: %s",
				frames(), this.slots, this.skippedTicks, costP(0.50), costP(0.95), costP(0.99), costMax(), intervalP(0.50), intervalP(0.95), intervalP(0.99), this.missed,
				this.worstRun, this.worstRunAt / 1e9, this.uiQueuedMax.get(), this.lostRequests, this.skippedByScheduler, allocationP(0.50) / 1024, allocationP(0.99) / 1024, this.longPaints.isEmpty() ? "none" : this.longPaints, this.gaps.isEmpty() ? "none" : this.gaps);
	}
}
