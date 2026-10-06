package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.editor.TGEditorManager;
import app.tuxguitar.editor.util.TGSyncProcessLocked;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGLock;
import app.tuxguitar.util.TGSynchronizer;

/**
 * A tool bar update asked while another thread loads or replaces the song (the situation behind a
 * ConcurrentModificationException seen when a file is opened at start-up) must not read the song
 * until that thread releases the editor lock. The update goes through {@code TGSyncProcessLocked},
 * the process the tool bar model uses ({@code TGToolBarModel.updateItemsLocked}).
 */
public class ToolbarUpdateDuringSongLoadTest {

	@Test
	public void theUpdateWaitsForTheThreadThatLoadsTheSong() throws Exception {
		TGContext context = new TGContext();
		// as in the application: without a handler every thread has the same identity (null)
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		TGLock lock = new TGLock(context);
		TGEditorManager.getInstance(context).setLockControl(lock);
		final Deque<Runnable> uiQueue = new ArrayDeque<Runnable>();
		TGSynchronizer.getInstance(context).setController(uiQueue::addLast);

		final SongFixture fixture = new SongFixture(4);
		final AtomicInteger evaluations = new AtomicInteger();
		final AtomicReference<Boolean> seen = new AtomicReference<Boolean>();
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		TGSyncProcessLocked update = new TGSyncProcessLocked(context, new Runnable() {
			public void run() {
				try {
					seen.set(PracticeTrackCandidates.evaluate(fixture.song, null).getCandidates().isEmpty() ? Boolean.FALSE : Boolean.TRUE);
					evaluations.incrementAndGet();
				} catch (Throwable t) {
					failure.set(t);
				}
			}
		});

		final CountDownLatch loading = new CountDownLatch(1);
		final CountDownLatch finishLoading = new CountDownLatch(1);
		Thread loader = new Thread(new Runnable() {
			public void run() {
				lock.lock();
				try {
					fixture.quarter(0, 0, 1, 0);
					loading.countDown();
					finishLoading.await(10, TimeUnit.SECONDS);
					fixture.quarter(1, 0, 1, 0);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					lock.unlock();
				}
			}
		});
		loader.start();
		assertTrue(loading.await(10, TimeUnit.SECONDS));

		update.process();
		for( int i = 0 ; i < 50 ; i ++ ) {
			Runnable next = uiQueue.pollFirst();
			if( next != null ) {
				next.run();
			}
		}
		assertEquals(0, evaluations.get(), "the song is read while another thread holds the lock");
		assertNull(seen.get());
		assertEquals(1, uiQueue.size(), "the update stays queued");

		finishLoading.countDown();
		loader.join(10000);
		for( int i = 0 ; i < 5 && evaluations.get() == 0 ; i ++ ) {
			Runnable next = uiQueue.pollFirst();
			if( next != null ) {
				next.run();
			}
		}
		assertEquals(null, failure.get());
		assertEquals(1, evaluations.get());
		assertEquals(Boolean.TRUE, seen.get());
		assertTrue(uiQueue.isEmpty());
	}
}
