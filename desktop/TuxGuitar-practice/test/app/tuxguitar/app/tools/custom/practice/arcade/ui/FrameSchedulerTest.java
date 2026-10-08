package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** The frame pacing (spec 003 T041, FR-055): at most one frame pending, a slow paint never builds a backlog, a lost request cannot freeze the picture. */
public class FrameSchedulerTest {

	private final AtomicLong wall = new AtomicLong();
	private final AtomicInteger asked = new AtomicInteger();
	private final FrameScheduler scheduler = new FrameScheduler(asked::incrementAndGet, wall::get);

	@Test
	public void aStalledPaintMakesTheFollowingTicksSkipInsteadOfQueueing() {
		assertTrue(scheduler.tick());
		for (int i = 0; i < 100; i++) {
			wall.addAndGet(ArcadeCanvas.FRAME_NANOS / 8);
			assertFalse(scheduler.tick(), "the paint is still going on");
		}
		assertEquals(1, asked.get(), "one frame asked, never a queue");
		assertEquals(100, scheduler.skipped());
		assertTrue(scheduler.isPending());

		scheduler.painted();
		assertFalse(scheduler.isPending());
		assertTrue(scheduler.tick(), "the next tick after the paint asks again");
		assertEquals(2, asked.get(), "and a single frame is asked, not the hundred that were skipped");
	}

	@Test
	public void everyTickAsksWhenThePaintsKeepUp() {
		for (int i = 0; i < 50; i++) {
			assertTrue(scheduler.tick());
			scheduler.painted();
			wall.addAndGet(ArcadeCanvas.FRAME_NANOS);
		}
		assertEquals(50, asked.get());
		assertEquals(0, scheduler.skipped());
		assertEquals(0, scheduler.lost());
	}

	@Test
	public void aRequestThatWasNeverAnsweredIsTakenAsLostSoThePictureDoesNotFreeze() {
		assertTrue(scheduler.tick());
		wall.addAndGet(FrameScheduler.LOST_AFTER_NANOS);
		assertFalse(scheduler.tick(), "not yet lost at exactly the limit");
		wall.addAndGet(1);
		assertTrue(scheduler.tick(), "lost: a new request goes out");
		assertEquals(1, scheduler.lost());
		assertEquals(2, asked.get());
	}

	@Test
	public void manyThreadsTickingAtOnceNeverAskForMoreThanOneFramePendingAtATime() throws Exception {
		final AtomicInteger inFlight = new AtomicInteger();
		final AtomicInteger worst = new AtomicInteger();
		final FrameScheduler[] holder = new FrameScheduler[1];
		holder[0] = new FrameScheduler(() -> worst.accumulateAndGet(inFlight.incrementAndGet(), Math::max), wall::get);
		Thread[] threads = new Thread[4];
		for (int t = 0; t < threads.length; t++) {
			threads[t] = new Thread(() -> {
				for (int i = 0; i < 2000; i++) {
					holder[0].tick();
				}
			});
			threads[t].start();
		}
		for (Thread thread : threads) {
			thread.join();
		}
		assertEquals(1, worst.get(), "one pending frame, whatever the number of tickers (none was painted)");
		assertEquals(1, holder[0].requested());
		assertEquals(8000 - 1, holder[0].skipped());
	}
}
