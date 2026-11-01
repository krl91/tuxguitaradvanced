package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Whether the system asks applications to reduce motion, on macOS, Linux and Windows (spec 019 FR-013, SC-008, research R7), for Arcade's fire, the score
 * fire and their previews. Never stored; anything that goes wrong, and every other system, is "no preference".
 * <ul>
 * <li>macOS: asked when called, as {@link ReducedMotion} asks it (spec 009), unchanged.</li>
 * <li>Linux: GNOME ({@code gsettings}) and KDE ({@code kreadconfig6}, or {@code kreadconfig5} when it gives no answer) are both asked, and motion is
 * reduced if any says so: {@code gsettings} is often installed on KDE too, where it gives GNOME's default. Other desktops are not read.</li>
 * <li>Windows: the system value behind "Show animations in Windows" ({@code SPI_GETCLIENTAREAANIMATION}), through PowerShell.</li>
 * </ul>
 * On Linux and Windows the commands can take longer than a run may wait, so the answer is fetched in the background by {@link #refresh()}, and
 * {@link #requested()} gives the latest one without waiting: no reduction until one arrives.
 */
public final class SystemReducedMotion {

	/** The longest a Linux command may take. */
	static final long LINUX_LIMIT_MILLIS = 500L;

	/** The longest PowerShell may take: its start alone often passes half a second. */
	static final long WINDOWS_LIMIT_MILLIS = 5000L;

	private static final String[] GSETTINGS = { "gsettings", "get", "org.gnome.desktop.interface", "enable-animations" };
	private static final String[] KDE6 = { "kreadconfig6", "--file", "kdeglobals", "--group", "KDE", "--key", "AnimationDurationFactor" };
	private static final String[] KDE5 = { "kreadconfig5", "--file", "kdeglobals", "--group", "KDE", "--key", "AnimationDurationFactor" };
	private static final String[] WINDOWS = { "powershell", "-NoProfile", "-NonInteractive", "-Command",
			"Add-Type -AssemblyName PresentationFramework; [System.Windows.SystemParameters]::ClientAreaAnimation" };

	private enum Family {
		MAC, LINUX, WINDOWS, OTHER
	}

	private final CommandRunner runner;
	private final String osName;
	private final Family family;
	private final Executor background;
	private volatile boolean latest;

	/**
	 * @param background where the answers of Linux and Windows are fetched
	 */
	public SystemReducedMotion(CommandRunner runner, String osName, Executor background) {
		this.runner = runner;
		this.osName = osName;
		this.family = family(osName);
		this.background = background;
	}

	/** The system of this computer, with its own background thread. */
	public static SystemReducedMotion ofThisSystem() {
		ExecutorService background = Executors.newSingleThreadExecutor(job -> {
			Thread thread = new Thread(job, "practice-reduced-motion");
			thread.setDaemon(true);
			return thread;
		});
		return new SystemReducedMotion(new ProcessCommandRunner(), System.getProperty("os.name"), background);
	}

	/** True when the system wants motion reduced: asked now on macOS, the latest answer on Linux and Windows. Never waits on Linux and Windows. */
	public boolean requested() {
		if (this.family == Family.MAC) {
			return ReducedMotion.requested(this.runner, this.osName);
		}
		return (this.family == Family.LINUX || this.family == Family.WINDOWS) && this.latest;
	}

	/** Asks the system again in the background, on Linux and Windows (when the practice window opens and when a run starts). */
	public void refresh() {
		if (this.family == Family.LINUX) {
			this.background.execute(() -> this.latest = linux(this.runner));
		} else if (this.family == Family.WINDOWS) {
			this.background.execute(() -> this.latest = windows(this.runner));
		}
	}

	/** GNOME without animations, or KDE with animations at speed 0. */
	static boolean linux(CommandRunner runner) {
		String gnome = answer(runner, GSETTINGS, LINUX_LIMIT_MILLIS);
		String kde = answer(runner, KDE6, LINUX_LIMIT_MILLIS);
		if (kde == null) {
			kde = answer(runner, KDE5, LINUX_LIMIT_MILLIS);
		}
		return "false".equals(gnome) || isZero(kde);
	}

	/** "Show animations in Windows" turned off. */
	static boolean windows(CommandRunner runner) {
		String answer = answer(runner, WINDOWS, WINDOWS_LIMIT_MILLIS);
		return answer != null && answer.equalsIgnoreCase("false");
	}

	/** What a command wrote, trimmed; null when it failed, wrote nothing or could not be run. */
	private static String answer(CommandRunner runner, String[] command, long limitMillis) {
		try {
			CommandRunner.Result result = runner.run(command, limitMillis);
			if (result.exitCode() != 0 || result.output() == null || result.output().trim().isEmpty()) {
				return null;
			}
			return result.output().trim();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception exception) {
			return null;
		}
	}

	private static boolean isZero(String text) {
		if (text == null) {
			return false;
		}
		try {
			return Double.parseDouble(text) == 0.0;
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	private static Family family(String osName) {
		String name = (osName != null ? osName.toLowerCase(Locale.ROOT) : "");
		if (name.contains("mac")) {
			return Family.MAC;
		}
		if (name.contains("linux")) {
			return Family.LINUX;
		}
		if (name.contains("windows")) {
			return Family.WINDOWS;
		}
		return Family.OTHER;
	}
}
