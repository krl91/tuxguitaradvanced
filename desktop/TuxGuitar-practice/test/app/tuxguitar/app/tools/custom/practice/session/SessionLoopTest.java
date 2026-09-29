package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

public class SessionLoopTest {

	private TGContext context;

	@BeforeEach
	public void setUp() {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
	}

	@AfterEach
	public void tearDown() {
		TGThreadManager.getInstance(this.context).dispose();
	}

	@Test
	public void handlesEventsInArrivalOrderOnOneThread() throws InterruptedException {
		List<String> handled = new CopyOnWriteArrayList<String>();
		Set<Thread> threads = ConcurrentHashMap.newKeySet();
		CountDownLatch done = new CountDownLatch(4);
		SessionLoop.Handler<String> handler = new SessionLoop.Handler<String>() {
			public String onFrame(AnalysisFrame frame) {
				return record("frame@" + frame.getTimestampNanos());
			}

			public String onCommand(PracticeCommand command) {
				return record(command.name());
			}

			private String record(String event) {
				threads.add(Thread.currentThread());
				handled.add(event);
				done.countDown();
				return null;
			}
		};
		SessionLoop<String> loop = new SessionLoop<String>(this.context, handler, snapshot -> { }, Runnable::run);
		loop.start();
		loop.postCommand(PracticeCommand.START);
		loop.postFrame(frame(1));
		loop.postCommand(PracticeCommand.SKIP);
		loop.postFrame(frame(2));

		assertTrue(done.await(2, TimeUnit.SECONDS));
		loop.stop();
		assertEquals(List.of("START", "frame@1", "SKIP", "frame@2"), handled);
		assertEquals(1, threads.size());
	}

	@Test
	public void coalescesSnapshotsAndAlwaysDeliversTheLatest() throws InterruptedException {
		int events = 500;
		List<Integer> delivered = new CopyOnWriteArrayList<Integer>();
		CountDownLatch last = new CountDownLatch(1);
		int[] counter = new int[1];
		SessionLoop.Handler<Integer> handler = new SessionLoop.Handler<Integer>() {
			public Integer onFrame(AnalysisFrame frame) {
				return ++counter[0];
			}

			public Integer onCommand(PracticeCommand command) {
				return null;
			}
		};
		SessionLoop<Integer> loop = new SessionLoop<Integer>(this.context, handler, snapshot -> {
			delivered.add(snapshot);
			if( snapshot == events ) {
				last.countDown();
			}
		}, Runnable::run);

		long start = System.nanoTime();
		loop.start();
		for( int i = 0 ; i < events ; i ++ ) {
			loop.postFrame(frame(i));
		}
		assertTrue(last.await(2, TimeUnit.SECONDS));
		long elapsedNanos = System.nanoTime() - start;
		loop.stop();

		assertEquals(events, (int) delivered.get(delivered.size() - 1));
		long allowed = elapsedNanos / SessionLoop.MIN_UI_INTERVAL_NANOS + 2;
		assertTrue(delivered.size() <= allowed, "deliveries " + delivered.size() + " > " + allowed);
		List<Integer> sorted = new ArrayList<Integer>(delivered);
		sorted.sort(null);
		assertEquals(sorted, delivered);
	}

	@Test
	public void ignoresPostsAfterStop() throws InterruptedException {
		List<PracticeCommand> handled = new CopyOnWriteArrayList<PracticeCommand>();
		CountDownLatch first = new CountDownLatch(1);
		SessionLoop.Handler<String> handler = new SessionLoop.Handler<String>() {
			public String onFrame(AnalysisFrame frame) {
				return null;
			}

			public String onCommand(PracticeCommand command) {
				handled.add(command);
				first.countDown();
				return null;
			}
		};
		SessionLoop<String> loop = new SessionLoop<String>(this.context, handler, snapshot -> { }, Runnable::run);
		loop.start();
		loop.postCommand(PracticeCommand.START);
		assertTrue(first.await(2, TimeUnit.SECONDS));
		loop.stop();
		loop.postCommand(PracticeCommand.STOP);
		Thread.sleep(50L);

		assertEquals(List.of(PracticeCommand.START), handled);
	}

	private static AnalysisFrame frame(long timestampNanos) {
		return new AnalysisFrame(timestampNanos, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, 440.0, 0.95, false);
	}
}
