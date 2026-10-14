package app.tuxguitar.app.tools.custom.practice.arcade.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.ui.UIFactory;

/**
 * The stress run of Arcade (spec 003 T047): the whole assembly of the product, with the real MIDI player and the real recognition chain on a seeded
 * recording, without the renderer and then with the real canvas on the real SWT painter, compared event by event. The length is
 * {@code TUXGUITAR_ARCADE_STRESS_SECONDS} (or the property {@code tuxguitar.arcade.stress.seconds}): the acceptance run is 600 seconds (and takes about twenty-two minutes for the two runs); with less it is a
 * check of the harness, and its report goes to {@code target/}. Runs only with the profile {@code swt-gui-tests}, which gives the toolkit and the main thread.
 */
@Tag("swt-gui")
public class ArcadeStressTest {

	/** A setting of the run: a system property, or the environment variable of the same name in capitals with underscores (the profile fixes the argument line of the forked machine). */
	static String setting(String name, String fallback) {
		String value = System.getProperty(name);
		if (value == null || value.isEmpty()) {
			value = System.getenv(name.toUpperCase(Locale.ROOT).replace('.', '_'));
		}
		return value == null || value.isEmpty() ? fallback : value;
	}

	static void withDisplay(DisplayTask task) throws Exception {
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

	interface DisplayTask {
		void run(Object display, UIFactory factory) throws Exception;
	}

	static String describe(String name, StressRun.Result r) {
		StringBuilder text = new StringBuilder();
		text.append("### ").append(name).append('\n');
		text.append("- run ended: ").append(r.ended).append("; events ").append(r.events).append(", ").append(r.summary).append('\n');
		text.append("- judgement: ").append(r.offsets).append('\n');
		text.append(String.format(Locale.ROOT, "- audio: capture overruns (samples refused by the ring buffer) %d; analysis frames %d, longest gap between two frames %d ms; the feeder was at most %d ms late for a block%n",
				r.captureDropped, r.frames, r.maxFrameGapMs, r.maxFeederLatenessMs));
		text.append(String.format(Locale.ROOT, "- MIDI: backing notes delivered to the silent channels %d of %d expected; longest time the player's position did not move while it ran: %d ms%n",
				r.backingNotesSent, r.backingNotesExpected, r.maxTickStallMs));
		text.append(String.format(Locale.ROOT, "- JVM: %d collections, %d ms in total%n", r.gcCount, r.gcMillis));
		if (r.metrics.frames() > 0) {
			text.append("- frames: ").append(r.metrics.describe()).append('\n');
			text.append("- what the UI thread was doing in each gap of more than 40 ms between two paints:").append(r.metrics.explainGaps(40)).append('\n');
			text.append("- renderer path: ").append(r.renderer).append('\n');
			text.append("- quality tier at the end: ").append(r.tier).append(", ").append(r.tierChanges).append(" changes during the run\n");
		}
		return text.toString();
	}

	@Test
	public void theWholeAssemblyRunsWithAndWithoutTheRendererAndGivesTheSameJudgements() throws Exception {
		final int seconds = Integer.parseInt(setting("tuxguitar.arcade.stress.seconds", "30"));
		final String[] plan = setting("tuxguitar.arcade.stress.runs", "plain,render").split(",");
		final StressRun.Result[] results = new StressRun.Result[plan.length];
		withDisplay((display, factory) -> {
			for (int r = 0; r < plan.length; r++) {
				StressRun.Config config = new StressRun.Config();
				config.seconds = seconds;
				config.noteRate = Double.parseDouble(setting("tuxguitar.arcade.stress.noteRate", "3"));
				config.render = plan[r].startsWith("render");
				config.fallback = plan[r].equals("render-fallback");
				config.updateAtOnce = plan[r].equals("render-update");
				config.flames = plan[r].equals("render-flames") ? app.tuxguitar.app.tools.custom.practice.session.FlameSetting.FULL : app.tuxguitar.app.tools.custom.practice.session.FlameSetting.OFF;
				results[r] = new StressRun(config, display, factory).run();
			}
		});
		StressRun.Result plain = results[0];
		StressRun.Result rendered = results[results.length - 1];
		int differing = 0;
		StringBuilder differences = new StringBuilder();
		StringBuilder pairs = new StringBuilder();
		for (int r = 1; r < results.length; r++) {
			for (int q = 0; q < r; q++) {
				String[] a = results[q].digest.split("\n");
				String[] b = results[r].digest.split("\n");
				int count = 0;
				for (int i = 0; i < Math.max(a.length, b.length); i++) {
					String x = i < a.length ? a[i] : "(none)";
					String y = i < b.length ? b[i] : "(none)";
					if (!x.equals(y)) {
						count++;
						if (q == 0 && differences.length() < 3000) {
							differences.append("  run 1 ").append(x).append("   <>   run ").append(r + 1).append(' ').append(y).append('\n');
						}
					}
				}
				pairs.append("- run ").append(q + 1).append(" (").append(plan[q]).append(") against run ").append(r + 1).append(" (").append(plan[r]).append("): ").append(count)
						.append(" differing lines of ").append(Math.max(a.length, b.length)).append('\n');
				if (q == 0 && r == results.length - 1) {
					differing = count;
				}
			}
		}
		StringBuilder report = new StringBuilder();
		report.append("# Arcade stress run, ").append(seconds).append(" s per run (spec 003 T047)\n\nplan: ").append(String.join(",", plan)).append("; notes/s ").append(setting("tuxguitar.arcade.stress.noteRate", "3")).append(" (chords on every other beat)").append("\n\n");
		report.append("Machine: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch")).append(", Java ").append(System.getProperty("java.version"))
				.append(", ").append(Runtime.getRuntime().availableProcessors()).append(" processors. Window 1280x720, SWT painter, quality controller on.\n\n");
		for (int r = 0; r < results.length; r++) {
			report.append(describe("run " + (r + 1) + ": " + (plan[r].startsWith("render") ? "with the renderer" : "without the renderer"), results[r])).append('\n');
		}
		report.append("### digest of the judgements\n").append(pairs);
		if (differing > 0) {
			report.append(differences);
		}
		if (seconds < 100) {
			report.append("\n### digest of run 1\n").append(results[0].digest);
		}
		Path out = seconds >= 600 ? Paths.get("../../specs/003-arcade-mode/benchmark/stress-10min-2026-10-08.md") : Paths.get("target/arcade-stress-" + seconds + "s.md");
		Files.createDirectories(out.toAbsolutePath().getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report);

		assertTrue(plain.judged > 0 && rendered.judged > 0, "events were judged");
		assertTrue(plain.backingNotesSent > 0 && rendered.backingNotesSent > 0, "the real player played the backing");
		assertEquals(0L, plain.captureDropped + rendered.captureDropped, "no capture overrun");
		// only the acceptance run (ten minutes) asserts the identity of the digests: the real-time chain is not bit-reproducible over a long run even without the renderer,
		// and a short check must not fail on that; the figures of every pair are in the report
		if (seconds >= 600) {
			assertEquals(0, differing, "the digest of the judgements differs between the run without and the run with the renderer");
		}
	}

	/**
	 * The cost of the stage flames (spec 009 T027, T029, SC-006): the same run with the renderer, once without flames and once with them at full, compared
	 * on the judgements and on the frames lost. The acceptance run is 600 seconds ({@code TUXGUITAR_ARCADE_STRESS_SECONDS=600}); a shorter one only checks
	 * the harness and writes to {@code target/}.
	 */
	@Test
	public void theFlamesAtFullChangeNoJudgementAndLoseAtMostOnePercentMoreFrames() throws Exception {
		final int seconds = Integer.parseInt(setting("tuxguitar.arcade.stress.seconds", "30"));
		final String[] plan = { "render", "render-flames" };
		final StressRun.Result[] results = new StressRun.Result[2];
		withDisplay((display, factory) -> {
			for (int r = 0; r < 2; r++) {
				StressRun.Config config = new StressRun.Config();
				config.seconds = seconds;
				config.noteRate = Double.parseDouble(setting("tuxguitar.arcade.stress.noteRate", "3"));
				config.render = true;
				config.flames = r == 0 ? app.tuxguitar.app.tools.custom.practice.session.FlameSetting.OFF : app.tuxguitar.app.tools.custom.practice.session.FlameSetting.FULL;
				results[r] = new StressRun(config, display, factory).run();
			}
		});
		String[] a = results[0].digest.split("\n");
		String[] b = results[1].digest.split("\n");
		int differing = 0;
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			if (!(i < a.length ? a[i] : "(none)").equals(i < b.length ? b[i] : "(none)")) {
				differing++;
			}
		}
		StressMetrics off = results[0].metrics;
		StressMetrics full = results[1].metrics;
		double lostOff = 100.0 * off.missedDeadlines() / Math.max(1L, off.slots());
		double lostFull = 100.0 * full.missedDeadlines() / Math.max(1L, full.slots());
		StringBuilder report = new StringBuilder();
		report.append("# Cost of the stage flames, ").append(seconds).append(" s per run (spec 009 T029, SC-006)\n\n");
		report.append("Machine: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch")).append(", Java ").append(System.getProperty("java.version"))
				.append(", ").append(Runtime.getRuntime().availableProcessors()).append(" processors. Window 1280x720, SWT painter, quality controller on, notes/s ")
				.append(setting("tuxguitar.arcade.stress.noteRate", "3")).append(".\n\n");
		report.append(describe("run 1: flames off", results[0])).append('\n').append(describe("run 2: flames at full", results[1])).append('\n');
		report.append(String.format(Locale.ROOT, "### frames lost\n- flames off: %d missed deadline slots of %d (%.3f %%)\n- flames full: %d missed deadline slots of %d (%.3f %%)\n- difference: %.3f points (limit 1 point)\n",
				off.missedDeadlines(), off.slots(), lostOff, full.missedDeadlines(), full.slots(), lostFull, lostFull - lostOff));
		report.append("### digest of the judgements\n- run 1 (off) against run 2 (full): ").append(differing).append(" differing lines of ").append(Math.max(a.length, b.length)).append('\n');
		Path out = seconds >= 600 ? Paths.get("../../specs/009-arcade-flames/benchmark/cost-2026-10-14.txt") : Paths.get("target/arcade-flames-cost-" + seconds + "s.txt");
		Files.createDirectories(out.toAbsolutePath().getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report);

		assertTrue(results[0].judged > 0 && results[1].judged > 0, "events were judged");
		assertTrue(results[1].metrics.frames() > 0, "the flames run painted");
		if (seconds >= 600) {
			assertEquals(0, differing, "the digest of the judgements differs between the run without flames and the run with them");
			assertTrue(lostFull - lostOff <= 1.0, "the flames lose more than 1 point more frames: " + lostOff + " % against " + lostFull + " %");
		}
	}
}
