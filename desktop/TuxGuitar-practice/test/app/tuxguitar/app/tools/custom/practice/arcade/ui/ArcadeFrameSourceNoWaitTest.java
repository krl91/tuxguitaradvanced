package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.audio.engine.backend.FakeRecognitionApi;
import app.tuxguitar.audio.engine.backend.NativeBackend;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * No frame waits on the recognition (spec 023, T068, FR-015, SC-006, research R7): while the native recognition thread is blocked in {@code nextFrames} (a
 * fake library that does not answer), Arcade's frame source builds each frame within 1 ms, and each frame shows the verdicts last published by the judging
 * side, read without a lock. The stage flames are off: their drawing costs a few milliseconds whatever the recognition does, and the cost of the picture
 * is measured by T070 and by spec 003's benchmarks.
 */
public class ArcadeFrameSourceNoWaitTest {

	private static final long FIRST_TICK = 960L;
	private static final long MS = 1_000_000L;
	private static final Viewport SCREEN = new Viewport(1280, 720);
	private static final int WARM_UP = 200;
	private static final int FRAMES = 400;

	@Test
	public void eachFrameIsBuiltWithin1MsWhileTheRecognitionThreadIsBlocked() throws Exception {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		BlockingApi api = new BlockingApi(new FakeRecognitionApi());
		NativeBackend backend = new NativeBackend(context, CurrentInputSelection.getInstance(context), api, () -> NativeParameters.of(RecognitionSettings.defaults()));
		ExecutorService judging = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "test-judging");
			thread.setDaemon(true);
			return thread;
		});
		try {
			backend.acquire("arcade");
			assertTrue(api.entered.await(2, TimeUnit.SECONDS), "the recognition thread is in nextFrames");

			SongFixture song = new SongFixture(2);
			for (int i = 0; i < 8; i++) {
				song.quarter(i / 4, i % 4, 1 + i % 6, i * 2 + 1);
			}
			SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
			FakeTempoClock clock = new FakeTempoClock(ticks -> fixture.timeline.nanosAtTick((long) ticks), FIRST_TICK, 100_000L);
			clock.start(FIRST_TICK);
			AtomicReference<ArcadeRun.View> published = new AtomicReference<ArcadeRun.View>();
			AtomicReference<ArcadeRun.View> read = new AtomicReference<ArcadeRun.View>();
			ArcadeFrameSource source = new ArcadeFrameSource(fixture.scene(false), fixture.timeline, FIRST_TICK, clock, () -> {
				ArcadeRun.View view = published.get();
				read.set(view);
				return view;
			}, () -> 100,
					new QualityController(ArcadeCanvas.FRAME_NANOS), 0L, 0L, FlameSetting.OFF, true, ArcadeScoring.standard());

			long slowest = 0L;
			for (int frame = 0; frame < WARM_UP + FRAMES; frame++) {
				final int rights = frame / 50;
				// the judging side publishes its verdicts on its own thread, as the session's sink does
				judging.submit(() -> published.set(view(rights))).get(1, TimeUnit.SECONDS);
				long start = System.nanoTime();
				source.frame(clock.nowNanos(), SCREEN);
				long cost = System.nanoTime() - start;
				if (frame >= WARM_UP) {
					slowest = Math.max(slowest, cost);
				}
				assertTrue(read.get() == published.get(), "the latest verdicts at frame " + frame);
				assertEquals(rights, read.get().states().size());
				clock.advance(8 * MS);
			}
			assertTrue(api.inside.get(), "the recognition thread was blocked all along");
			assertEquals(1, api.calls.get(), "it never came back from nextFrames");
			assertTrue(slowest < MS, "the slowest frame took " + slowest / 1000 + " µs");
		} finally {
			api.release.countDown();
			judging.shutdownNow();
			backend.close();
			TGThreadManager.getInstance(context).dispose();
		}
	}

	private static ArcadeRun.View view(int rights) {
		List<ArcadeRun.EventState> states = new ArrayList<ArcadeRun.EventState>();
		for (int i = 0; i < rights; i++) {
			states.add(new ArcadeRun.EventState(i, ArcadeVerdict.ON_TIME, 0L, 100, i + 1, Collections.emptyList(), new int[0]));
		}
		return new ArcadeRun.View(states, 100 * rights, rights, rights, 0, rights);
	}

	/** The fake library, except that {@code nextFrames} does not come back until the test ends. */
	private static final class BlockingApi implements NativeRecognitionLibrary.Api {
		final FakeRecognitionApi api;
		final CountDownLatch entered = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final AtomicBoolean inside = new AtomicBoolean();
		final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

		BlockingApi(FakeRecognitionApi api) {
			this.api = api;
		}

		@Override
		public int nextFrames(long handle, float[] records, int timeoutMillis) {
			this.calls.incrementAndGet();
			this.inside.set(true);
			this.entered.countDown();
			try {
				this.release.await();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			this.inside.set(false);
			return -2;
		}

		@Override
		public long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters) throws NativeRecognitionException {
			return this.api.open(inputName, inputChannel, blockFrames, javaNanosAtOpen, parameters);
		}

		@Override
		public float sampleRate(long handle) {
			return this.api.sampleRate(handle);
		}

		@Override
		public void configure(long handle, float[] parameters) {
			this.api.configure(handle, parameters);
		}

		@Override
		public void setSpectrumEnabled(long handle, boolean on) {
			this.api.setSpectrumEnabled(handle, on);
		}

		@Override
		public void setExpectedPitches(long handle, int[] midi) {
			this.api.setExpectedPitches(handle, midi);
		}

		@Override
		public void setExpectedChord(long handle, int[] required, int[] tolerated) {
			this.api.setExpectedChord(handle, required, tolerated);
		}

		@Override
		public void setAttackCandidates(long handle, int[] pitches) {
			this.api.setAttackCandidates(handle, pitches);
		}

		@Override
		public int nextHops(long handle, float[] hops, int timeoutMillis) {
			return this.api.nextHops(handle, hops, timeoutMillis);
		}

		@Override
		public void setHopsEnabled(long handle, boolean on) {
			this.api.setHopsEnabled(handle, on);
		}

		@Override
		public long droppedBlocks(long handle) {
			return this.api.droppedBlocks(handle);
		}

		@Override
		public void close(long handle) {
			this.api.close(handle);
		}

		@Override
		public float[] strumClip(long handle) {
			return this.api.strumClip(handle);
		}

		@Override
		public void resetStrumClip(long handle) {
			this.api.resetStrumClip(handle);
		}

		@Override
		public float[] stats(long handle) {
			return this.api.stats(handle);
		}
	}
}
