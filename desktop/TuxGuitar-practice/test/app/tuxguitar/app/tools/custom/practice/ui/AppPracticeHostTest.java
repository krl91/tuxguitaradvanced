package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * The default {@code PracticeHost} (spec 011 T006): the parts of it that can be proved without the application, the time and the plugin calls. {@code later} is the
 * daemon thread that sleeps and then posts to the synchronizer, exactly the code that was in {@code PracticeWindow} (the quick-start patience and the Arcade restart
 * retry); {@code nanoTime} is the system clock; {@code windowStateChanged} goes through the synchronizer and is not run here (it reaches the main toolbar). The access
 * to the application (the factory, the main window, the song, the caret) is checked one for one by {@code PracticeHostSourceTest}, and by the owner on the built application.
 */
public class AppPracticeHostTest {

	private final TGContext context = new TGContext();
	private final CopyOnWriteArrayList<Runnable> queued = new CopyOnWriteArrayList<Runnable>();
	private final AtomicReference<Thread> poster = new AtomicReference<Thread>();
	private final CountDownLatch posted = new CountDownLatch(1);

	private AppPracticeHost host() {
		TGSynchronizer.getInstance(this.context).setController(task -> {
			this.poster.compareAndSet(null, Thread.currentThread());
			this.queued.add(task);
			this.posted.countDown();
		});
		return new AppPracticeHost(this.context, new PracticePlugin());
	}

	@Test
	public void laterRunsTheTaskThroughTheSynchronizerAfterTheDelayFromANamedDaemonThread() throws InterruptedException {
		AppPracticeHost host = host();
		long before = System.nanoTime();
		AtomicReference<String> ran = new AtomicReference<String>();
		host.later("practice-test-thread", 80L, () -> ran.set("ran"));
		assertEquals(0, this.queued.size(), "nothing is posted at once: the thread sleeps first");
		assertTrue(this.posted.await(5, TimeUnit.SECONDS), "the task is posted through the synchronizer");
		assertTrue(System.nanoTime() - before >= 70_000_000L, "after the delay (80 ms)");
		assertEquals(1, this.queued.size());
		assertEquals(null, ran.get(), "the synchronizer decides when it runs: it was only posted");
		this.queued.get(0).run();
		assertEquals("ran", ran.get());
		Thread thread = this.poster.get();
		assertEquals("practice-test-thread", thread.getName(), "the thread is named by the caller");
		assertTrue(thread.isDaemon(), "a daemon: it never keeps the application alive");
	}

	@Test
	public void nanoTimeIsTheSystemClock() {
		AppPracticeHost host = host();
		long a = System.nanoTime();
		long h = host.nanoTime();
		long b = System.nanoTime();
		assertTrue(a <= h && h <= b);
	}

	@Test
	public void windowStateChangedIsPostedToTheSynchronizerAndNotRunHere() {
		AppPracticeHost host = host();
		host.windowStateChanged();
		assertEquals(1, this.queued.size(), "the plugin posts the update of the main toolbar (it is not run in a test)");
		assertFalse(this.queued.isEmpty());
	}
}
