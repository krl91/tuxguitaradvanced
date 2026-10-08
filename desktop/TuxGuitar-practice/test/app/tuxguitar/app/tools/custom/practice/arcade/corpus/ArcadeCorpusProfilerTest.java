package app.tuxguitar.app.tools.custom.practice.arcade.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.corpus.CorpusPipeline.Case;
import app.tuxguitar.app.tools.custom.practice.arcade.corpus.CorpusPipeline.FileRecord;
import app.tuxguitar.app.tools.custom.practice.arcade.corpus.CorpusPipeline.Status;
import app.tuxguitar.app.tools.custom.practice.arcade.corpus.CorpusScenes.Moment;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneImageWriter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;

/**
 * The validation of Arcade on real scores (spec 003 FR-064, SC-021, SC-022, T060 to T065): optional, driven by the environment variable
 * {@code TG_ARCADE_GP_CORPUS} naming a folder of Guitar Pro 3 and 4 files; without it the test is skipped and says so. It profiles a stable-hash sample of
 * the files, chooses the cases, builds the scenes of every case at representative instants and checks them, writes the images and a contact sheet for the
 * owner, counts the files that cannot be used, and does it all a second time from nothing to prove that the selection is the same. The exact paths, the
 * scores and the images stay in the ignored folder {@code tests/}; only the aggregate, anonymous report goes to the specification folder.
 */
@Tag("benchmark")
public class ArcadeCorpusProfilerTest {

	private static final Path LOCAL = Paths.get("../../tests");
	private static final Path REPORT = Paths.get("../../specs/003-arcade-mode/benchmark/arcade-corpus-aggregate-2026-10-08.md");

	/** What one run of the profile and the selection gives. */
	private static final class Run {
		final List<FileRecord> records = new ArrayList<FileRecord>();
		final List<Case> cases = new ArrayList<Case>();
		int sampled;
		int read;
		long millis;
		int fromCache;
		final Map<Status, Integer> statuses = new LinkedHashMap<Status, Integer>();
		final Map<String, Integer> formats = new LinkedHashMap<String, Integer>();
		int extensionMismatch;
		Map<String, Integer> available;
	}

	private static Run run(Path root, List<String> all, Path cache) throws IOException {
		long start = System.currentTimeMillis();
		Run run = new Run();
		List<String> sample = CorpusPipeline.sample(all, CorpusPipeline.SAMPLE_PER_EXTENSION);
		run.sampled = sample.size();
		Map<String, FileRecord> cached = CorpusPipeline.loadCache(cache);
		CorpusReader reader = new CorpusReader();
		for (String relative : sample) {
			FileRecord record = cached.get(relative);
			if (record == null) {
				record = CorpusPipeline.profile(reader, root, relative);
				CorpusPipeline.appendCache(cache, record);
			} else {
				run.fromCache++;
			}
			run.records.add(record);
			run.statuses.merge(record.status, 1, Integer::sum);
			if (record.status != Status.UNREADABLE) {
				run.read++;
				run.formats.merge(record.format, 1, Integer::sum);
				if (!record.format.equals(record.extension)) {
					run.extensionMismatch++;
				}
			}
		}
		run.cases.addAll(CorpusPipeline.select(run.records));
		run.available = CorpusPipeline.available(run.records);
		run.millis = System.currentTimeMillis() - start;
		return run;
	}

	private static double percentile(List<Double> sorted, double p) {
		return sorted.isEmpty() ? 0.0 : sorted.get(Math.max(0, Math.min(sorted.size() - 1, (int) Math.ceil(p * sorted.size()) - 1)));
	}

	private static String distribution(String name, List<Double> values) {
		List<Double> sorted = new ArrayList<Double>(values);
		java.util.Collections.sort(sorted);
		return String.format(Locale.ROOT, "| %s | %.1f | %.1f | %.1f | %.1f | %.1f |\n", name, percentile(sorted, 0.0), percentile(sorted, 0.5), percentile(sorted, 0.9),
				percentile(sorted, 0.99), percentile(sorted, 1.0));
	}

	@Test
	public void profileChooseCheckAndShowTheRealScores() throws Exception {
		String folder = System.getenv("TG_ARCADE_GP_CORPUS");
		assumeTrue(folder != null && !folder.isEmpty(), "TG_ARCADE_GP_CORPUS is not set: no real scores to validate Arcade on");
		Path root = Paths.get(folder);
		assumeTrue(Files.isDirectory(root), "TG_ARCADE_GP_CORPUS is not a directory");
		long begin = System.currentTimeMillis();
		List<String> all = CorpusPipeline.enumerate(root);
		int gp3 = 0;
		int gp4 = 0;
		for (String name : all) {
			if (name.toLowerCase(Locale.ROOT).endsWith(".gp3")) {
				gp3++;
			} else {
				gp4++;
			}
		}

		// T060, T061: the first run, with the local cache (it may be resumed after an interruption)
		Path cache = LOCAL.resolve("arcade-corpus-cache/profile-" + CorpusPipeline.VERSION + ".tsv");
		Run first = run(root, all, cache);
		// T065: the second run, from nothing: another cache, so that every file is read and measured again
		Path coldCache = LOCAL.resolve("arcade-corpus-cache/profile-second-" + CorpusPipeline.VERSION + ".tsv");
		Files.deleteIfExists(coldCache);
		Run second = run(root, all, coldCache);
		assertEquals(CorpusPipeline.anonymousTable(first.cases), CorpusPipeline.anonymousTable(second.cases), "the selection is not reproducible");

		// the exact paths, only here
		Files.createDirectories(LOCAL);
		StringBuilder local = new StringBuilder();
		for (Case c : first.cases) {
			local.append(c.id).append('\t').append(c.file.relative).append('\t').append(c.metrics.trackNumber).append('\t').append(c.category).append('\t').append(c.file.format)
					.append('\n');
		}
		Files.write(LOCAL.resolve("arcade-corpus-selection.tsv"), local.toString().getBytes(StandardCharsets.UTF_8));

		// T064: the files that cannot be used, counted apart; the known defective score stays a negative case
		StringBuilder negative = new StringBuilder();
		CorpusReader reader = new CorpusReader();
		for (String relative : all) {
			String lower = relative.toLowerCase(Locale.ROOT);
			if (lower.endsWith("metallica - to live is to die (6).gp4") || lower.endsWith("metallica - to live is to die (4).gp4")) {
				FileRecord record = CorpusPipeline.profile(reader, root, relative);
				negative.append("- ").append(lower.endsWith("(6).gp4") ? "the known defective file (6)" : "the file (4)").append(": ").append(record.status)
						.append(record.tracks.isEmpty() ? "" : ", " + record.tracks.size() + " playable tracks").append('\n');
				if (lower.endsWith("(6).gp4")) {
					assertTrue(record.status != Status.OK, "the defective file must never count as a success: " + record.status);
				}
			}
		}

		// T062: every selected track, every representative scene
		long checkStart = System.currentTimeMillis();
		List<String> failures = new ArrayList<String>();
		int[] scenes = { 0 };
		int tracksChecked = 0;
		List<CorpusScenes.Prepared> prepared = new ArrayList<CorpusScenes.Prepared>();
		for (Case c : first.cases) {
			CorpusScenes.Prepared p = CorpusScenes.prepare(reader, root, c);
			prepared.add(p);
			failures.addAll(CorpusScenes.checkAll(p, scenes));
			tracksChecked++;
		}
		long checkMillis = System.currentTimeMillis() - checkStart;

		// T063: the images and the contact sheet, local only
		Path images = LOCAL.resolve("arcade-corpus-images");
		Files.createDirectories(images);
		for (File old : images.toFile().listFiles()) {
			old.delete();
		}
		StringBuilder html = new StringBuilder("<!doctype html><meta charset=\"utf-8\"><title>Arcade real scores, contact sheet</title>"
				+ "<style>body{font-family:sans-serif;background:#111;color:#eee;margin:16px}.case{margin:24px 0;border-top:1px solid #444;padding-top:8px}"
				+ "img{width:48%;min-width:420px;margin:2px}code{color:#9cf}</style><h1>Arcade on real scores</h1><p>Local file, never committed. Each case: the active target "
				+ "(filled opaque disks) on the neck, in FULL and in PERFORMANCE, at the armed moment of the busiest second or of the longest run of chords.</p>");
		Map<String, Integer> perCategory = new LinkedHashMap<String, Integer>();
		int imagesWritten = 0;
		for (int i = 0; i < first.cases.size(); i++) {
			Case c = first.cases.get(i);
			if (perCategory.merge(c.category, 1, Integer::sum) > 2) {
				continue;
			}
			CorpusScenes.Prepared p = prepared.get(i);
			int anchor = c.category.startsWith("chord") ? p.anchorChordRun : p.anchorBusiest;
			long now = CorpusScenes.nowOf(p, anchor, Moment.ARMED);
			html.append("<div class=\"case\"><b>").append(c.id).append("</b> <code>").append(c.category).append("</code> ").append(c.file.format).append(" - ")
					.append(c.metrics).append("<br>");
			for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.PERFORMANCE }) {
				Scene scene = p.scene(false).build(now, CorpusScenes.SCREEN, CorpusScenes.runOf(p, anchor, Moment.ARMED), 100, tier);
				assertTrue(!scene.withRole(Role.TARGET_DISK).isEmpty(), c.id + ": the capture must hold an active target");
				String name = c.id + "-" + tier.name().toLowerCase(Locale.ROOT) + ".png";
				SceneImageWriter.write(scene, images.resolve(name).toFile());
				html.append("<img src=\"").append(name).append("\" alt=\"").append(name).append("\">");
				imagesWritten++;
			}
			html.append("</div>");
		}
		Files.write(images.resolve("index.html"), html.toString().getBytes(StandardCharsets.UTF_8));

		// the aggregate, anonymous report
		StringBuilder report = new StringBuilder();
		report.append("# Arcade on real scores: aggregate report (spec 003 T060 to T065)\n\n");
		report.append("Produced by `ArcadeCorpusProfilerTest` from a folder of Guitar Pro 3 and 4 files named by `TG_ARCADE_GP_CORPUS`. No score, title, path or image is in this file: only counts, distributions and anonymous case identifiers.\n\n");
		report.append("## Files\n\n");
		report.append(String.format(Locale.ROOT, "- found recursively: %d (%d by extension .gp3, %d by extension .gp4)\n", all.size(), gp3, gp4));
		report.append(String.format(Locale.ROOT, "- sampled by stable hash of the relative path (SHA-256): %d (up to %d of each extension)\n", first.sampled, CorpusPipeline.SAMPLE_PER_EXTENSION));
		report.append(String.format(Locale.ROOT, "- read (format detected from the content by the application's detectors): %d; format shown by the content: %s; extension and content disagree: %d\n", first.read, first.formats, first.extensionMismatch));
		report.append("- outcome of each sampled file: ").append(first.statuses).append("\n");
		report.append("  - OK: read cleanly with at least one playable track; DEGRADED: read with measure errors (never used as a case); UNREADABLE: could not be read;\n    NO_ELIGIBLE_TRACK: no pitched guitar or bass track with events; IMPOSSIBLE_TUNING: strings without a pitch; PATHOLOGICAL: tempo or duration out of range, or events out of order\n");
		report.append("- negative cases:\n").append(negative.length() == 0 ? "  - not present in the folder\n" : negative.toString().replaceAll("(?m)^", "  "));
		report.append(String.format(Locale.ROOT, "- duration: profile of the sample %.1f s (from the cache of an earlier pass: %d of %d files), second pass from nothing %.1f s, structural check of the cases %.1f s, in all %.1f s\n\n",
				first.millis / 1000.0, first.fromCache, first.sampled, second.millis / 1000.0, checkMillis / 1000.0, (System.currentTimeMillis() - begin) / 1000.0));
		List<Double> events = new ArrayList<Double>();
		List<Double> ratio = new ArrayList<Double>();
		List<Double> runLength = new ArrayList<Double>();
		List<Double> peak = new ArrayList<Double>();
		List<Double> named = new ArrayList<Double>();
		List<Double> unnamed = new ArrayList<Double>();
		List<Double> size = new ArrayList<Double>();
		List<Double> jump = new ArrayList<Double>();
		List<Double> strings = new ArrayList<Double>();
		List<Double> tracks = new ArrayList<Double>();
		List<Double> repeats = new ArrayList<Double>();
		List<Double> tempo = new ArrayList<Double>();
		int playable = 0;
		for (FileRecord record : first.records) {
			if (record.status != Status.OK) {
				continue;
			}
			for (TrackMetrics m : record.tracks) {
				playable++;
				events.add((double) m.events);
				ratio.add(m.chordRatio());
				runLength.add((double) m.longestChordRun);
				peak.add(m.peakPerSecond);
				named.add((double) m.namedChords);
				unnamed.add((double) m.unnamedChords);
				size.add((double) m.maxChordSize);
				jump.add((double) m.maxJump);
				strings.add((double) m.strings);
				tracks.add((double) m.tracks);
				repeats.add((double) m.repeats);
				tempo.add((double) m.tempoChanges);
			}
		}
		report.append("## Playable tracks of the files read cleanly: ").append(playable).append("\n\n| metric | min | median | p90 | p99 | max |\n|---|---|---|---|---|---|\n");
		report.append(distribution("events", events)).append(distribution("chords / (notes + chords)", ratio)).append(distribution("longest run of chords", runLength))
				.append(distribution("peak events in one second", peak)).append(distribution("named chords", named)).append(distribution("unnamed chords", unnamed))
				.append(distribution("largest chord (notes)", size)).append(distribution("largest jump of the fret centre", jump)).append(distribution("strings", strings))
				.append(distribution("tracks in the score", tracks)).append(distribution("measures with a repeat sign", repeats)).append(distribution("tempo changes", tempo));
		report.append("\n## Cases\n\n| category | tracks available | cases chosen | GP3 | GP4 |\n|---|---|---|---|---|\n");
		Map<String, int[]> chosen = new LinkedHashMap<String, int[]>();
		for (String name : CorpusPipeline.CATEGORY_NAMES) {
			chosen.put(name, new int[3]);
		}
		for (Case c : first.cases) {
			int[] counts = chosen.get(c.category);
			counts[0]++;
			counts[c.file.format.equals("gp3") ? 1 : 2]++;
		}
		boolean enough = first.cases.size() >= 60;
		for (Map.Entry<String, int[]> entry : chosen.entrySet()) {
			report.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d |\n", entry.getKey(), first.available.get(entry.getKey()), entry.getValue()[0], entry.getValue()[1], entry.getValue()[2]));
			enough &= entry.getValue()[0] >= CorpusPipeline.MIN_PER_CATEGORY;
		}
		report.append(String.format(Locale.ROOT, "\nTotal %d cases (at least 60 and five in each category: %s); no folder or artist gives more than %d; one arrangement per title.\n", first.cases.size(),
				enough ? "met" : "NOT MET", CorpusPipeline.MAX_PER_FOLDER));
		report.append("\n## Structural check (T062)\n\n");
		report.append(String.format(Locale.ROOT, "- %d tracks, %d scenes (two anchors, five moments, FULL and PERFORMANCE, colour and monochrome), failures: %d\n", tracksChecked, scenes[0], failures.size()));
		for (String failure : failures.subList(0, Math.min(failures.size(), 20))) {
			report.append("  - ").append(failure.replaceAll("[^ -~]", "?")).append('\n');
		}
		report.append(String.format(Locale.ROOT, "\n## Images (T063)\n\n- %d images written locally (FULL and PERFORMANCE of %d cases), each with an active target; the contact sheet is a local file for the owner's review (T044), never committed.\n", imagesWritten, imagesWritten / 2));
		report.append("\n## Reproducibility (T065)\n\n- the selection was made twice, the second time from nothing (every file read and measured again): the anonymous identifiers, categories and metrics of the ")
				.append(first.cases.size()).append(" cases are identical.\n");
		report.append("\n## Selected cases (anonymous)\n\n| case | category | format | metrics |\n|---|---|---|---|\n");
		for (Case c : first.cases) {
			report.append("| ").append(c.id).append(" | ").append(c.category).append(" | ").append(c.file.format).append(" | ").append(c.metrics).append(" |\n");
		}
		Files.createDirectories(REPORT.getParent());
		Files.write(REPORT, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report.substring(0, Math.min(report.length(), 6000)));
		assertTrue(failures.isEmpty(), failures.size() + " structural failures, the first: " + failures.subList(0, Math.min(3, failures.size())));
		assertTrue(enough, "fewer than 60 cases or fewer than five in a category");
	}
}
