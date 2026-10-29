package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.State;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * Spec 016 (T010): the end hook of a Follow run, where the record of the run reads the session. Told once whatever the number of stops, on the
 * session's thread, before the session stops (so it still sees how far the run went); what it throws changes nothing of the end.
 */
public class FollowRunEndHookTest {

	private static final long WAIT_SECONDS = 20;

	private TGContext context;
	private FakePlayerRun fake;
	private final BlockingQueue<FollowSession.Snapshot> snapshots = new LinkedBlockingQueue<FollowSession.Snapshot>();
	private final List<State> seen = new CopyOnWriteArrayList<State>();
	private final List<String> threads = new CopyOnWriteArrayList<String>();

	@BeforeEach
	public void setUp() {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		this.fake = new FakePlayerRun(fixture, 100);
	}

	@AfterEach
	public void tearDown() {
		TGThreadManager.getInstance(this.context).dispose();
	}

	private FollowRun newRun() {
		return new FollowRun(this.context, this.fake.session, this.fake.player, FakePlayerRun.TRACK, 100, FakePlayerRun.BASE_TICK, this.snapshots::add,
				Runnable::run, reason -> {}, null, null);
	}

	private FollowSession.Snapshot await(Predicate<FollowSession.Snapshot> condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
		while (System.nanoTime() < deadline) {
			FollowSession.Snapshot snapshot = this.snapshots.poll(250, TimeUnit.MILLISECONDS);
			if (snapshot != null && condition.test(snapshot)) {
				return snapshot;
			}
		}
		throw new AssertionError("the session did not reach the expected state");
	}

	@Test
	public void theHookIsToldOnceBeforeTheSessionStops() throws InterruptedException {
		FollowRun run = newRun();
		run.setEndHook(session -> {
			this.seen.add(session.snapshot().state());
			this.threads.add(Thread.currentThread().getName());
		});
		String starter = Thread.currentThread().getName();
		assertEquals(FollowPlayback.Result.STARTED, run.start());
		await(snapshot -> snapshot.state() == State.COUNT_IN);
		run.stop();
		run.stop();
		await(snapshot -> snapshot.state() == State.STOPPED);
		assertEquals(1, this.seen.size(), "told once");
		assertEquals(State.COUNT_IN, this.seen.get(0), "the session is not stopped yet: the record knows the count-in did not end");
		assertTrue(!this.threads.get(0).equals(starter), "on the session's thread");
	}

	@Test
	public void aHookThatThrowsChangesNothingOfTheEnd() throws InterruptedException {
		FollowRun run = newRun();
		run.setEndHook(session -> {
			throw new IllegalStateException("disk full");
		});
		assertEquals(FollowPlayback.Result.STARTED, run.start());
		await(snapshot -> snapshot.state() == State.COUNT_IN);
		run.stop();
		await(snapshot -> snapshot.state() == State.STOPPED);
		assertTrue(run.isFinished());
	}
}
