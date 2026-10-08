package app.tuxguitar.app.tools.custom.practice.arcade.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import app.tuxguitar.app.tools.custom.practice.model.ArcadeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidate;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;

/**
 * The profile of a folder of real Guitar Pro scores and the choice of the cases of the Arcade validation (spec 003 FR-064, T060, T061, T064): the files are
 * listed recursively, a stable share of them is chosen by the hash of their relative path, each is read by content, each playable track gets its metrics from
 * the events the application builds, and the cases are chosen by category so that no artist, folder or arrangement dominates. Nothing of a score is copied:
 * the cache keeps numbers only, and the exact paths are written only to a local, ignored file.
 */
public final class CorpusPipeline {

	/** The version of the metrics: a change of it invalidates the cache. */
	static final String VERSION = "v3";
	/** The files taken from each extension, by stable hash of the relative path. */
	public static final int SAMPLE_PER_EXTENSION = 1100;
	public static final int PER_CATEGORY = 8;
	public static final int MIN_PER_CATEGORY = 5;
	public static final int MAX_PER_FOLDER = 3;

	/** What became of a file. */
	public enum Status {
		OK, UNREADABLE, DEGRADED, NO_ELIGIBLE_TRACK, IMPOSSIBLE_TUNING, PATHOLOGICAL
	}

	public static final class FileRecord {
		public final String relative;
		public final String extension;
		public final String id;
		public Status status;
		public String format = "";
		public String reason = "";
		public final List<TrackMetrics> tracks = new ArrayList<TrackMetrics>();

		FileRecord(String relative, String extension) {
			this.relative = relative;
			this.extension = extension;
			this.id = "C" + sha(relative).substring(0, 8);
		}

		/** The key of the folder, the artist and the arrangement: used to keep one source from dominating. */
		String folder() {
			int slash = this.relative.lastIndexOf('/');
			return slash < 0 ? "" : this.relative.substring(0, slash);
		}

		String name() {
			int slash = this.relative.lastIndexOf('/');
			String name = slash < 0 ? this.relative : this.relative.substring(slash + 1);
			int dot = name.lastIndexOf('.');
			return dot < 0 ? name : name.substring(0, dot);
		}

		String artist() {
			String name = name().toLowerCase(Locale.ROOT);
			int dash = name.indexOf(" - ");
			return dash < 0 ? name : name.substring(0, dash).trim();
		}

		String title() {
			return name().toLowerCase(Locale.ROOT).replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
		}
	}

	/** One chosen case: a track of a file, its category and its anonymous identifier. */
	public static final class Case {
		public final FileRecord file;
		public final TrackMetrics metrics;
		public final String category;
		public final String id;

		Case(FileRecord file, TrackMetrics metrics, String category) {
			this.file = file;
			this.metrics = metrics;
			this.category = category;
			this.id = file.id + "-T" + metrics.trackNumber;
		}
	}

	static String sha(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : digest) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/** The relative paths of every .gp3 and .gp4 under the folder, with '/' as separator, sorted. */
	public static List<String> enumerate(Path root) throws IOException {
		try (Stream<Path> stream = Files.walk(root)) {
			return stream.filter(Files::isRegularFile).map(p -> root.relativize(p).toString().replace('\\', '/'))
					.filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".gp3") || n.toLowerCase(Locale.ROOT).endsWith(".gp4")).sorted().collect(Collectors.toList());
		}
	}

	/** The files chosen by stable hash: the first {@code count} of each extension in the order of the hash of the relative path. */
	public static List<String> sample(List<String> all, int count) {
		List<String> chosen = new ArrayList<String>();
		for (String extension : new String[] { ".gp3", ".gp4" }) {
			List<String> ofExtension = new ArrayList<String>();
			for (String name : all) {
				if (name.toLowerCase(Locale.ROOT).endsWith(extension)) {
					ofExtension.add(name);
				}
			}
			ofExtension.sort(Comparator.comparing(CorpusPipeline::sha));
			chosen.addAll(ofExtension.subList(0, Math.min(count, ofExtension.size())));
		}
		Collections.sort(chosen);
		return chosen;
	}

	/** Profiles one file: reads it by content and measures its playable tracks. */
	public static FileRecord profile(CorpusReader reader, Path root, String relative) {
		String lower = relative.toLowerCase(Locale.ROOT);
		FileRecord record = new FileRecord(relative, lower.substring(lower.lastIndexOf('.') + 1));
		CorpusReader.Result read = reader.read(root.resolve(relative));
		if (read.song == null) {
			record.status = Status.UNREADABLE;
			record.reason = read.failure == null ? "unknown" : read.failure.replaceAll("[\\t\\r\\n]+", " ");
			return record;
		}
		record.format = read.format == null ? "?" : read.format;
		TGSong song = read.song;
		try {
			new TGSongManager().updatePreciseStart(song);
			ArcadeTrackCandidates candidates = ArcadeTrackCandidates.evaluate(song, null);
			if (candidates.getCandidates().isEmpty()) {
				boolean tuning = candidates.getExclusions().containsValue(ArcadeTrackCandidates.Exclusion.UNDEFINED_STRING_PITCH);
				record.status = tuning ? Status.IMPOSSIBLE_TUNING : Status.NO_ELIGIBLE_TRACK;
				record.reason = candidates.getExclusions().values().toString();
				return record;
			}
			boolean pathological = false;
			for (PracticeTrackCandidate candidate : candidates.getCandidates()) {
				TGTrack track = candidate.getTrack();
				PracticeSequence sequence = PracticeSequenceBuilder.build(song, track, TrackTuning.ofAnyStrings(track));
				if (sequence.isEmpty()) {
					continue;
				}
				FollowTimeline timeline = FollowTimeline.build(song, sequence, 100);
				TrackMetrics metrics = TrackMetrics.of(song, track, sequence, timeline);
				if (metrics.durationSeconds < 1.0 || metrics.durationSeconds > 3 * 3600.0 || !metrics.sequenceMonotonic || badTempo(song)) {
					pathological = true;
					record.reason = String.format(Locale.ROOT, "duration %.0f s, monotonic %s", metrics.durationSeconds, metrics.sequenceMonotonic);
				}
				record.tracks.add(metrics);
			}
			if (record.tracks.isEmpty()) {
				record.status = Status.NO_ELIGIBLE_TRACK;
				record.reason = "no event";
			} else if (pathological) {
				record.status = Status.PATHOLOGICAL;
			} else if (!new TGSongManager().getMeasureErrors(song).isEmpty()) {
				record.status = Status.DEGRADED;
				record.reason = new TGSongManager().getMeasureErrors(song).size() + " measure errors";
			} else {
				record.status = Status.OK;
			}
		} catch (Throwable throwable) {
			record.status = Status.UNREADABLE;
			record.reason = "profile failed: " + throwable.getClass().getSimpleName();
			record.tracks.clear();
		}
		return record;
	}

	private static boolean badTempo(TGSong song) {
		for (java.util.Iterator<TGMeasureHeader> headers = song.getMeasureHeaders(); headers.hasNext();) {
			int tempo = headers.next().getTempo().getQuarterValue();
			if (tempo < 10 || tempo > 1000) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------------------------ the cache

	/** A disposable local cache of the profile of each file, so that an interrupted scan can be taken up again. */
	public static Map<String, FileRecord> loadCache(Path cache) throws IOException {
		Map<String, FileRecord> records = new LinkedHashMap<String, FileRecord>();
		if (!Files.isRegularFile(cache)) {
			return records;
		}
		FileRecord current = null;
		for (String line : Files.readAllLines(cache, StandardCharsets.UTF_8)) {
			String[] f = line.split("\t", -1);
			if (f[0].equals("F") && f[1].equals(VERSION)) {
				current = new FileRecord(f[2], f[3]);
				current.status = Status.valueOf(f[4]);
				current.format = f[5];
				current.reason = f[6];
				records.put(f[2], current);
			} else if (f[0].equals("T") && current != null) {
				current.tracks.add(TrackMetrics.fromTsv(line.substring(2)));
			}
		}
		return records;
	}

	public static void appendCache(Path cache, FileRecord record) throws IOException {
		StringBuilder text = new StringBuilder();
		text.append("F\t").append(VERSION).append('\t').append(record.relative).append('\t').append(record.extension).append('\t').append(record.status).append('\t')
				.append(record.format).append('\t').append(record.reason).append('\n');
		for (TrackMetrics metrics : record.tracks) {
			text.append("T\t").append(metrics.toTsv()).append('\n');
		}
		Files.createDirectories(cache.toAbsolutePath().getParent());
		Files.write(cache, text.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	// ------------------------------------------------------------------------------------------------------------------ the selection

	/** A category of cases: its name and what puts a track in it. The order is that of the choice, the scarcest first. */
	static final class Category {
		final String name;
		final java.util.function.Predicate<TrackMetrics> member;

		Category(String name, java.util.function.Predicate<TrackMetrics> member) {
			this.name = name;
			this.member = member;
		}
	}

	public static final List<String> CATEGORY_NAMES = new ArrayList<String>();
	static final List<Category> CATEGORIES = new ArrayList<Category>();

	static {
		CATEGORIES.add(new Category("unusual-strings", m -> m.strings != 6 && m.events >= 30));
		CATEGORIES.add(new Category("large-jumps", m -> m.maxJump >= 10 && m.events >= 40 && m.strings == 6));
		CATEGORIES.add(new Category("chord-runs", m -> m.longestChordRun >= 12));
		CATEGORIES.add(new Category("fast-notes", m -> m.peakPerSecond >= 9 && m.chordRatio() <= 0.3 && m.events >= 40));
		CATEGORIES.add(new Category("multi-track-repeats-tempo", m -> m.tracks >= 4 && (m.repeats > 0 || m.tempoChanges > 0) && m.events >= 40));
		CATEGORIES.add(new Category("chord-heavy", m -> m.events >= 40 && m.chordRatio() >= 0.6));
		CATEGORIES.add(new Category("mixed", m -> m.events >= 40 && m.chordRatio() >= 0.25 && m.chordRatio() <= 0.75));
		CATEGORIES.add(new Category("sparse-notes", m -> m.events >= 40 && m.chordRatio() <= 0.05));
		for (Category category : CATEGORIES) {
			CATEGORY_NAMES.add(category.name);
		}
	}

	/** How many tracks of the profile fall in each category, whether chosen or not. */
	public static Map<String, Integer> available(List<FileRecord> records) {
		Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
		for (Category category : CATEGORIES) {
			int n = 0;
			for (FileRecord record : records) {
				if (record.status == Status.OK) {
					for (TrackMetrics metrics : record.tracks) {
						n += category.member.test(metrics) ? 1 : 0;
					}
				}
			}
			counts.put(category.name, n);
		}
		return counts;
	}

	/**
	 * The cases, chosen without looking at a title: for each category in turn the tracks of the files that were read cleanly, in the order of the hash of their
	 * identifier, taking the format with fewer cases so far first, and refusing a fourth case from one folder or artist and a second from one title.
	 */
	public static List<Case> select(List<FileRecord> records) {
		List<Case> cases = new ArrayList<Case>();
		Map<String, Integer> byFolder = new HashMap<String, Integer>();
		Map<String, Integer> byArtist = new HashMap<String, Integer>();
		Set<String> titles = new HashSet<String>();
		Set<String> chosen = new HashSet<String>();
		for (Category category : CATEGORIES) {
			List<Case> candidates = new ArrayList<Case>();
			for (FileRecord record : records) {
				if (record.status != Status.OK) {
					continue;
				}
				for (TrackMetrics metrics : record.tracks) {
					if (category.member.test(metrics)) {
						candidates.add(new Case(record, metrics, category.name));
					}
				}
			}
			candidates.sort(Comparator.comparing(c -> sha(c.id)));
			int taken = 0;
			Map<String, Integer> formats = new HashMap<String, Integer>();
			List<Case> remaining = new ArrayList<Case>(candidates);
			while (taken < PER_CATEGORY && !remaining.isEmpty()) {
				// the format with fewer cases in this category goes first
				String wanted = formats.getOrDefault("gp3", 0) <= formats.getOrDefault("gp4", 0) ? "gp3" : "gp4";
				Case pick = null;
				for (Case candidate : remaining) {
					if (ok(candidate, chosen, byFolder, byArtist, titles) && candidate.file.format.equals(wanted)) {
						pick = candidate;
						break;
					}
				}
				if (pick == null) {
					for (Case candidate : remaining) {
						if (ok(candidate, chosen, byFolder, byArtist, titles)) {
							pick = candidate;
							break;
						}
					}
				}
				if (pick == null) {
					break;
				}
				remaining.remove(pick);
				cases.add(pick);
				taken++;
				chosen.add(pick.id);
				formats.merge(pick.file.format, 1, Integer::sum);
				byFolder.merge(pick.file.folder(), 1, Integer::sum);
				byArtist.merge(pick.file.artist(), 1, Integer::sum);
				titles.add(pick.file.title());
			}
		}
		return cases;
	}

	private static boolean ok(Case candidate, Set<String> chosen, Map<String, Integer> byFolder, Map<String, Integer> byArtist, Set<String> titles) {
		return !chosen.contains(candidate.id) && byFolder.getOrDefault(candidate.file.folder(), 0) < MAX_PER_FOLDER
				&& byArtist.getOrDefault(candidate.file.artist(), 0) < MAX_PER_FOLDER && !titles.contains(candidate.file.title());
	}

	/** The cases as text, without any path: identifier, category, format, metrics. The same selection gives the same text. */
	public static String anonymousTable(List<Case> cases) {
		StringBuilder text = new StringBuilder();
		for (Case c : cases) {
			text.append(c.id).append('\t').append(c.category).append('\t').append(c.file.format).append('\t').append(c.metrics.toTsv()).append('\n');
		}
		return text.toString();
	}
}
