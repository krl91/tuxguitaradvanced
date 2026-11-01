package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

/**
 * Spec 019 (T041, FR-013, SC-008, research R7): Reduce motion read on macOS, Linux and Windows for both fires. macOS is asked as {@link ReducedMotion}
 * asks it; Linux asks GNOME and KDE and reduces if any says so; Windows asks the system value behind "Show animations in Windows". On Linux and Windows the
 * answer is fetched in the background and the latest one is used, so a run never waits. The commands are answered by a fake system.
 */
public class SystemReducedMotionTest {

	private static final String GSETTINGS = "gsettings get org.gnome.desktop.interface enable-animations";
	private static final String KDE6 = "kreadconfig6 --file kdeglobals --group KDE --key AnimationDurationFactor";
	private static final String KDE5 = "kreadconfig5 --file kdeglobals --group KDE --key AnimationDurationFactor";
	private static final String WINDOWS = "powershell -NoProfile -NonInteractive -Command Add-Type -AssemblyName PresentationFramework; "
			+ "[System.Windows.SystemParameters]::ClientAreaAnimation";
	private static final String MAC_FIRST = "defaults read com.apple.Accessibility ReduceMotionEnabled";

	/** A fake system: an answer per command, a failure for any other; every question and its limit noted. */
	private static final class Fake implements CommandRunner {
		final Map<String, CommandRunner.Result> answers = new HashMap<String, CommandRunner.Result>();
		final List<String> asked = new ArrayList<String>();
		final List<Long> limits = new ArrayList<Long>();
		long delayMillis;
		RuntimeException thrown;

		Fake answer(String command, int exitCode, String output) {
			this.answers.put(command, new CommandRunner.Result(exitCode, output));
			return this;
		}

		@Override
		public synchronized CommandRunner.Result run(String[] command, long timeoutMillis) throws IOException, InterruptedException, TimeoutException {
			String line = String.join(" ", command);
			this.asked.add(line);
			this.limits.add(timeoutMillis);
			if (this.thrown != null) {
				throw this.thrown;
			}
			if (this.delayMillis > 0) {
				Thread.sleep(this.delayMillis);
			}
			CommandRunner.Result answer = this.answers.get(line);
			if (answer == null) {
				throw new IOException("no such command: " + line);
			}
			return answer;
		}
	}

	/** The background of the tests: each job runs when the test says so. */
	private static final class Later implements java.util.concurrent.Executor {
		final List<Runnable> jobs = new ArrayList<Runnable>();

		public void execute(Runnable job) {
			this.jobs.add(job);
		}

		void runAll() {
			List<Runnable> now = new ArrayList<Runnable>(this.jobs);
			this.jobs.clear();
			for (Runnable job : now) {
				job.run();
			}
		}
	}

	private static boolean linux(Fake fake) {
		Later later = new Later();
		SystemReducedMotion motion = new SystemReducedMotion(fake, "Linux", later);
		motion.refresh();
		later.runAll();
		return motion.requested();
	}

	private static boolean windows(Fake fake) {
		Later later = new Later();
		SystemReducedMotion motion = new SystemReducedMotion(fake, "Windows 11", later);
		motion.refresh();
		later.runAll();
		return motion.requested();
	}

	@Test
	public void macOsIsAskedAsBefore() {
		Fake fake = new Fake().answer(MAC_FIRST, 0, "1\n");
		Later later = new Later();
		SystemReducedMotion motion = new SystemReducedMotion(fake, "Mac OS X", later);
		assertTrue(motion.requested());
		assertEquals(List.of(MAC_FIRST), fake.asked, "the same question as ReducedMotion");
		motion.refresh();
		assertTrue(later.jobs.isEmpty(), "nothing fetched in the background on macOS");
		Fake no = new Fake().answer(MAC_FIRST, 0, "0\n");
		assertFalse(new SystemReducedMotion(no, "Mac OS X", later).requested());
		assertEquals(ReducedMotion.requested(new Fake().answer(MAC_FIRST, 0, "1\n"), "Mac OS X"),
				new SystemReducedMotion(new Fake().answer(MAC_FIRST, 0, "1\n"), "Mac OS X", later).requested());
	}

	@Test
	public void linuxReducesIfGnomeOrKdeSaysSo() {
		assertTrue(linux(new Fake().answer(GSETTINGS, 0, "false\n").answer(KDE6, 0, "1\n")), "GNOME without animations");
		assertTrue(linux(new Fake().answer(GSETTINGS, 0, "true\n").answer(KDE6, 0, "0\n")), "KDE with gsettings installed and saying true");
		assertTrue(linux(new Fake().answer(GSETTINGS, 0, "true\n").answer(KDE6, 1, "").answer(KDE5, 0, "0\n")), "KDE 5");
		assertTrue(linux(new Fake().answer(KDE5, 0, "0\n")), "no gsettings, no kreadconfig6");
		assertFalse(linux(new Fake().answer(GSETTINGS, 0, "true\n").answer(KDE6, 0, "1\n")));
		assertFalse(linux(new Fake().answer(GSETTINGS, 0, "true\n").answer(KDE6, 0, "")), "a KDE key never set");
		assertFalse(linux(new Fake()), "nothing answers");
	}

	@Test
	public void linuxAsksEveryQuestion() {
		Fake fake = new Fake().answer(GSETTINGS, 0, "false\n").answer(KDE6, 0, "1\n");
		assertTrue(linux(fake));
		assertEquals(List.of(GSETTINGS, KDE6), fake.asked, "a first answer does not hide the other desktop's");
		Fake old = new Fake().answer(GSETTINGS, 0, "true\n").answer(KDE5, 0, "1\n");
		linux(old);
		assertEquals(List.of(GSETTINGS, KDE6, KDE5), old.asked, "kreadconfig5 when kreadconfig6 gives no answer");
	}

	@Test
	public void windowsReadsShowAnimations() {
		assertTrue(windows(new Fake().answer(WINDOWS, 0, "False\r\n")));
		assertFalse(windows(new Fake().answer(WINDOWS, 0, "True\r\n")));
		assertFalse(windows(new Fake().answer(WINDOWS, 1, "")), "an error");
		assertFalse(windows(new Fake().answer(WINDOWS, 0, "something else")));
		assertFalse(windows(new Fake()), "no PowerShell");
	}

	@Test
	public void onLinuxAndWindowsTheLatestAnswerIsUsedAndNoneBeforeTheFirst() {
		for (String os : new String[] { "Linux", "Windows 10" }) {
			Fake fake = new Fake().answer(GSETTINGS, 0, "false\n").answer(WINDOWS, 0, "False\n");
			Later later = new Later();
			SystemReducedMotion motion = new SystemReducedMotion(fake, os, later);
			assertFalse(motion.requested(), os + ": no answer yet, no reduction");
			assertTrue(fake.asked.isEmpty(), os + ": requested() asks nothing itself");
			motion.refresh();
			assertFalse(motion.requested(), os + ": still fetching");
			later.runAll();
			assertTrue(motion.requested(), os + ": the answer arrived");
			fake.answer(GSETTINGS, 0, "true\n").answer(WINDOWS, 0, "True\n");
			motion.refresh();
			later.runAll();
			assertFalse(motion.requested(), os + ": the latest answer");
		}
	}

	@Test
	public void aSlowSystemNeverHoldsTheCaller() throws Exception {
		Fake fake = new Fake().answer(GSETTINGS, 0, "false\n").answer(WINDOWS, 0, "False\n");
		fake.delayMillis = 300;
		ExecutorService background = Executors.newSingleThreadExecutor();
		try {
			for (String os : new String[] { "Linux", "Windows 10" }) {
				SystemReducedMotion motion = new SystemReducedMotion(fake, os, background);
				long start = System.nanoTime();
				motion.refresh();
				boolean now = motion.requested();
				long spent = System.nanoTime() - start;
				assertTrue(spent < 5_000_000L, os + ": refresh and requested took " + spent / 1_000_000.0 + " ms");
				assertFalse(now);
				CountDownLatch done = new CountDownLatch(1);
				background.execute(done::countDown);
				assertTrue(done.await(10, TimeUnit.SECONDS));
				assertTrue(motion.requested(), os + ": the slow answer is used once it came");
			}
		} finally {
			background.shutdownNow();
		}
	}

	@Test
	public void otherSystemsAskNothingAndEveryCommandHasALimit() {
		Fake other = new Fake().answer(GSETTINGS, 0, "false\n");
		Later later = new Later();
		SystemReducedMotion motion = new SystemReducedMotion(other, "SunOS", later);
		motion.refresh();
		later.runAll();
		assertFalse(motion.requested());
		assertTrue(other.asked.isEmpty());

		Fake fake = new Fake().answer(GSETTINGS, 0, "true\n").answer(KDE6, 1, "").answer(KDE5, 0, "1\n").answer(WINDOWS, 0, "True\n");
		linux(fake);
		windows(fake);
		assertEquals(4, fake.limits.size());
		for (long limit : fake.limits) {
			assertTrue(limit > 0 && limit <= 5000, "a limit: " + limit);
		}

		Fake throwing = new Fake();
		throwing.thrown = new IllegalStateException("broken");
		assertFalse(linux(throwing), "a runner that throws: no reduction");
		assertFalse(windows(throwing));
		assertFalse(new SystemReducedMotion(throwing, "Mac OS X", later).requested());
	}
}
