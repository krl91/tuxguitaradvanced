package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

/**
 * The system preference "reduce motion" (spec 009 FR-013, research R10): asked once per call through a command, with a limit of 500 ms and "no preference" as
 * the answer to anything that goes wrong. The command runner is a fake that gives the answers a macOS would.
 */
public class ReducedMotionTest {

	private static final String MAC = "Mac OS X";
	private static final String[] FIRST = { "defaults", "read", "com.apple.Accessibility", "ReduceMotionEnabled" };
	private static final String[] SECOND = { "defaults", "read", "com.apple.universalaccess", "reduceMotion" };

	/** A fake system: an answer for each command, or a failure. */
	private static final class Fake implements CommandRunner {
		final List<String> asked = new ArrayList<String>();
		final List<Long> limits = new ArrayList<Long>();
		CommandRunner.Result first = new CommandRunner.Result(0, "0\n");
		CommandRunner.Result second = new CommandRunner.Result(1, "");
		Exception failure;

		@Override
		public CommandRunner.Result run(String[] command, long timeoutMillis) throws IOException, InterruptedException, TimeoutException {
			this.asked.add(String.join(" ", command));
			this.limits.add(timeoutMillis);
			if (this.failure != null) {
				if (this.failure instanceof IOException) {
					throw (IOException) this.failure;
				}
				if (this.failure instanceof TimeoutException) {
					throw (TimeoutException) this.failure;
				}
				throw (InterruptedException) this.failure;
			}
			return String.join(" ", command).equals(String.join(" ", FIRST)) ? this.first : this.second;
		}
	}

	@Test
	public void theAnswerOneMeansTheSystemAsksToReduceMotionAndZeroMeansItDoesNot() {
		Fake fake = new Fake();
		fake.first = new CommandRunner.Result(0, "1\n");
		assertTrue(ReducedMotion.requested(fake, MAC));
		assertEquals(List.of(String.join(" ", FIRST)), fake.asked, "one question is enough when the key answers");
		fake = new Fake();
		fake.first = new CommandRunner.Result(0, "0\n");
		assertFalse(ReducedMotion.requested(fake, MAC));
		assertEquals(1, fake.asked.size());
	}

	@Test
	public void anAbsentKeyTriesTheSecondOneAndTheCommandHasAHalfSecondLimit() {
		Fake fake = new Fake();
		fake.first = new CommandRunner.Result(1, "");
		fake.second = new CommandRunner.Result(0, "1\n");
		assertTrue(ReducedMotion.requested(fake, MAC), "the second key says reduce");
		assertEquals(List.of(String.join(" ", FIRST), String.join(" ", SECOND)), fake.asked);
		assertEquals(List.of(500L, 500L), fake.limits, "every command has the limit of 500 ms");

		fake = new Fake();
		fake.first = new CommandRunner.Result(1, "");
		fake.second = new CommandRunner.Result(1, "");
		assertFalse(ReducedMotion.requested(fake, MAC), "neither key exists: no preference");
	}

	@Test
	public void anythingThatGoesWrongIsNoPreference() {
		for (Exception failure : new Exception[] { new IOException("no such command"), new TimeoutException("too long"), new InterruptedException() }) {
			Fake fake = new Fake();
			fake.failure = failure;
			assertFalse(ReducedMotion.requested(fake, MAC), failure.getClass().getSimpleName());
			assertEquals(1, fake.asked.size(), "and no second try after a failure");
		}
		Fake odd = new Fake();
		odd.first = new CommandRunner.Result(0, "banana");
		assertFalse(ReducedMotion.requested(odd, MAC), "an answer that is not 1");
		Fake nonZero = new Fake();
		nonZero.first = new CommandRunner.Result(2, "1\n");
		nonZero.second = new CommandRunner.Result(2, "1\n");
		assertFalse(ReducedMotion.requested(nonZero, MAC), "a non-zero exit is not an answer");
		CommandRunner broken = (command, limit) -> {
			throw new IllegalStateException("bug in the runner");
		};
		assertFalse(ReducedMotion.requested(broken, MAC), "not even a bug of the runner reaches the run");
	}

	@Test
	public void aPlatformThatIsNotMacosIsNotReadAndGivesNoPreference() {
		for (String os : new String[] { "Windows 11", "Linux", "FreeBSD", "", null }) {
			Fake fake = new Fake();
			fake.first = new CommandRunner.Result(0, "1\n");
			assertFalse(ReducedMotion.requested(fake, os), String.valueOf(os));
			assertTrue(fake.asked.isEmpty(), "nothing is run there: " + os);
		}
	}

	@Test
	public void theAnswerIsAskedAgainEveryTimeAndNeverStored() {
		Fake fake = new Fake();
		fake.first = new CommandRunner.Result(0, "0\n");
		assertFalse(ReducedMotion.requested(fake, MAC));
		fake.first = new CommandRunner.Result(0, "1\n");
		assertTrue(ReducedMotion.requested(fake, MAC), "the learner turned it on between two runs");
		assertEquals(2, fake.asked.size());
	}

	@Test
	public void theRealRunnerGivesUpAfterTheLimitAndReadsTheOutputOfACommandThatEnds() throws Exception {
		if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
			return;
		}
		CommandRunner real = new ProcessCommandRunner();
		CommandRunner.Result ok = real.run(new String[] { "echo", "1" }, 500);
		assertEquals(0, ok.exitCode());
		assertEquals("1", ok.output().trim());
		long start = System.nanoTime();
		try {
			real.run(new String[] { "sleep", "5" }, 200);
			throw new AssertionError("a command that runs longer than the limit must be given up");
		} catch (TimeoutException expected) {
			assertTrue((System.nanoTime() - start) < 2_000_000_000L, "and given up in time");
		}
		assertEquals(1, real.run(new String[] { "false" }, 500).exitCode(), "the exit code is given, not hidden");
	}
}
