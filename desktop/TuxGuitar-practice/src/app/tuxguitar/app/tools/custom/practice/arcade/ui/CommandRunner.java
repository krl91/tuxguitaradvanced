package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/** Runs a command of the system and tells what it answered: a small seam so that what the system says can be faked in the tests (spec 009 R10). */
public interface CommandRunner {

	/** What a command answered: its exit code and what it wrote. */
	final class Result {
		private final int exitCode;
		private final String output;

		public Result(int exitCode, String output) {
			this.exitCode = exitCode;
			this.output = output;
		}

		public int exitCode() {
			return this.exitCode;
		}

		public String output() {
			return this.output;
		}
	}

	/**
	 * @param timeoutMillis the longest the command may take
	 * @throws TimeoutException when the command did not end in time (it is then stopped)
	 * @throws IOException when the command could not be run
	 */
	Result run(String[] command, long timeoutMillis) throws IOException, InterruptedException, TimeoutException;
}
