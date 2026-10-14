package app.tuxguitar.app.tools.custom.practice.arcade.ui;

/**
 * Whether the system asks applications to reduce motion (spec 009 FR-013, research R10). Asked once when a run starts, never stored. It is read on macOS
 * only, with the command {@code defaults}, because the toolkit and the SWT of the application have no way to read it and a native module is not worth a
 * decoration; on every other platform nothing is read and the answer is "no preference", which is not a claim of support. Anything that goes wrong is also
 * "no preference".
 */
public final class ReducedMotion {

	/** The longest a command may take, so that starting a run is never held up by the system. */
	static final long LIMIT_MILLIS = 500L;

	private static final String[] FIRST = { "defaults", "read", "com.apple.Accessibility", "ReduceMotionEnabled" };
	private static final String[] SECOND = { "defaults", "read", "com.apple.universalaccess", "reduceMotion" };

	private ReducedMotion() {
	}

	/** True when the system, as asked now, wants motion reduced. */
	public static boolean requested() {
		return requested(new ProcessCommandRunner(), System.getProperty("os.name"));
	}

	public static boolean requested(CommandRunner runner, String osName) {
		if (osName == null || !osName.toLowerCase().contains("mac")) {
			return false;
		}
		try {
			CommandRunner.Result answer = runner.run(FIRST, LIMIT_MILLIS);
			if (answer.exitCode() != 0) {
				// the key is absent when it was never set: the older one is the second try
				answer = runner.run(SECOND, LIMIT_MILLIS);
				return answer.exitCode() == 0 && says(answer);
			}
			return says(answer);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception exception) {
			return false;
		}
	}

	private static boolean says(CommandRunner.Result answer) {
		return answer.output() != null && answer.output().trim().equals("1");
	}
}
