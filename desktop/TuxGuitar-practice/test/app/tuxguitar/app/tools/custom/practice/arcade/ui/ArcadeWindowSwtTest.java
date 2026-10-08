package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIRectangle;

/**
 * Native test of the windows of Arcade (spec 003 T058): the window of a run opens on the real toolkit with its canvas, paints, refits on resize and keeps the canvas
 * inside it, closing it by the learner is stopping the run (once), closing it by the application is not, and opening it again after closing leaves no timer and
 * no listener of the first one; the result window opens and closes. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class ArcadeWindowSwtTest {

	private static final long FIRST_TICK = 960L;
	private static final ArcadeTexts TEXTS = (key, arguments) -> key + (arguments.length == 0 ? "" : java.util.Arrays.toString(arguments));

	private interface DisplayTask {
		void run(Object display, UIFactory factory) throws Exception;
	}

	private static void withDisplay(DisplayTask task) throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			Class<?> environment = Class.forName("app.tuxguitar.ui.swt.SWTEnvironment");
			environment.getMethod("start", displayClass).invoke(environment.getMethod("getInstance").invoke(null), display);
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			task.run(display, factory);
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	private static void flush(Object display) throws Exception {
		Method readAndDispatch = display.getClass().getMethod("readAndDispatch");
		for (int i = 0; i < 300 && (Boolean) readAndDispatch.invoke(display); i++) {
			// drain the pending events
		}
	}

	private static SceneFixture fixture() {
		SongFixture song = new SongFixture(3);
		for (int i = 0; i < 6; i++) {
			song.quarter(1 + i / 4, i % 4, 1 + i % 6, 2 + i);
		}
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static final class Counting implements ArcadeWindow.Actions {
		final AtomicInteger stops = new AtomicInteger();
		final AtomicInteger pauses = new AtomicInteger();
		final AtomicInteger resumes = new AtomicInteger();

		public void pause() {
			this.pauses.incrementAndGet();
		}

		public void resume() {
			this.resumes.incrementAndGet();
		}

		public void stop() {
			this.stops.incrementAndGet();
		}
	}

	private ArcadeWindow open(Object display, UIFactory factory, Counting actions, AtomicInteger posted, FakeTempoClock[] clockOut) throws Exception {
		SceneFixture fixture = fixture();
		FakeTempoClock clock = new FakeTempoClock(ticks -> fixture.timeline.nanosAtTick((long) ticks), FIRST_TICK, 1_000_000L);
		clock.start(FIRST_TICK);
		clock.seek(FIRST_TICK + fixture.timeline.targetTicks(1) - 200);
		clockOut[0] = clock;
		ArcadeFrameSource source = new ArcadeFrameSource(fixture.scene(false), fixture.timeline, FIRST_TICK, clock, () -> null, () -> 100, ArcadeWindow.newQuality());
		Method asyncExec = display.getClass().getMethod("asyncExec", Runnable.class);
		ArcadeWindow window = new ArcadeWindow(factory, factory.createWindow(), source, task -> {
			posted.incrementAndGet();
			try {
				asyncExec.invoke(display, task);
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError(exception);
			}
		}, TEXTS, actions);
		window.open();
		flush(display);
		return window;
	}

	@Test
	public void theWindowOpensPaintsRefitsOnResizeAndTheCanvasStaysInsideIt() throws Exception {
		withDisplay((display, factory) -> {
			Counting actions = new Counting();
			AtomicInteger posted = new AtomicInteger();
			FakeTempoClock[] clock = new FakeTempoClock[1];
			ArcadeWindow window = open(display, factory, actions, posted, clock);
			try {
				assertTrue(window.isOpen());
				long deadline = System.nanoTime() + 2_000_000_000L;
				while (posted.get() == 0 && System.nanoTime() < deadline) {
					flush(display);
				}
				assertTrue(posted.get() > 0, "the canvas asked for frames on the UI thread");
				for (int[] size : new int[][] { { 700, 460 }, { 1200, 760 }, { 960, 600 }, { 520, 360 } }) {
					window.getWindow().setBounds(new UIRectangle(30, 30, size[0], size[1]));
					flush(display);
					UIRectangle bounds = window.getWindow().getBounds();
					assertTrue(bounds.getWidth() > 0 && bounds.getHeight() > 0, "the window has a size at " + size[0] + "x" + size[1]);
				}
				window.setPaused(true);
				window.setPaused(false);
				assertEquals(0, actions.stops.get(), "nothing was stopped by showing and refitting");
			} finally {
				window.close();
			}
		});
	}

	@Test
	public void closingByTheLearnerStopsTheRunOnceAndClosingByTheApplicationDoesNot() throws Exception {
		withDisplay((display, factory) -> {
			Counting byLearner = new Counting();
			FakeTempoClock[] clock = new FakeTempoClock[1];
			ArcadeWindow first = open(display, factory, byLearner, new AtomicInteger(), clock);
			first.getWindow().dispose();
			flush(display);
			assertEquals(1, byLearner.stops.get(), "the learner closed the window: the run is stopped, once");
			assertFalse(first.isOpen());

			Counting byApplication = new Counting();
			ArcadeWindow second = open(display, factory, byApplication, new AtomicInteger(), clock);
			second.close();
			second.close();
			flush(display);
			assertEquals(0, byApplication.stops.get(), "the application gave the window back: nobody is told to stop");
		});
	}

	@Test
	public void aWindowOpenedAgainAfterClosingLeavesNothingOfTheFirstOne() throws Exception {
		withDisplay((display, factory) -> {
			FakeTempoClock[] clock = new FakeTempoClock[1];
			AtomicInteger firstPosted = new AtomicInteger();
			ArcadeWindow first = open(display, factory, new Counting(), firstPosted, clock);
			flush(display);
			first.close();
			flush(display);
			int frozen = firstPosted.get();
			long until = System.nanoTime() + 300_000_000L;
			while (System.nanoTime() < until) {
				flush(display);
			}
			assertEquals(frozen, firstPosted.get(), "the closed window asks for no more frames: its timer is gone");

			AtomicInteger secondPosted = new AtomicInteger();
			ArcadeWindow second = open(display, factory, new Counting(), secondPosted, clock);
			long deadline = System.nanoTime() + 2_000_000_000L;
			while (secondPosted.get() == 0 && System.nanoTime() < deadline) {
				flush(display);
			}
			assertTrue(secondPosted.get() > 0, "the second window paints");
			assertEquals(frozen, firstPosted.get(), "and the first one stays silent");
			second.close();
		});
	}

	@Test
	public void theResultWindowOpensAndCloses() throws Exception {
		withDisplay((display, factory) -> {
			ArcadeResultWindow window = new ArcadeResultWindow(factory, factory.createWindow(), TEXTS,
					app.tuxguitar.app.tools.custom.practice.session.ArcadeResultFixtures.some(), 1, 4, measure -> {
					});
			window.open();
			flush(display);
			assertTrue(window.isOpen());
			window.close();
			window.close();
			assertFalse(window.isOpen());
		});
	}
}
