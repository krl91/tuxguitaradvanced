package app.tuxguitar.app.tools.custom.practice.dataset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The strummed chords of a GuitarSet recording (JAMS annotation), found from the per-string note annotations and
 * cross-checked against the instructed chord label. A chord that cannot be trusted is not silently dropped: it is
 * excluded with a documented reason, counted in {@link Extraction#exclusions}.
 *
 * <p>GuitarSet: Xi, Bittner, Pauwels, Ye, Bello, ISMIR 2018, Zenodo 10.5281/zenodo.1422265, licence CC BY-NC 4.0.</p>
 */
final class GuitarSetChords {

	/** Notes whose onsets lie within this span of the first one belong to the same strum. */
	static final double STRUM_SPAN_SECONDS = 0.12;
	/** The excerpt of a chord runs from this long before its onset to {@link #AFTER_SECONDS} after it. */
	static final double BEFORE_SECONDS = 0.35;
	static final double AFTER_SECONDS = 0.40;
	static final double MIN_NOTE_SECONDS = 0.20;
	static final int MIN_NOTES = 3;
	private static final double MIN_PREVIOUS_GAP_SECONDS = 0.15;

	static final String[] STRING_NAMES = { "E2", "A2", "D3", "G3", "B3", "E4" };

	/** One note of one string. */
	static final class Note {
		final int string;
		final double time;
		final double duration;
		final int midi;

		Note(int string, double time, double duration, int midi) {
			this.string = string;
			this.time = time;
			this.duration = duration;
			this.midi = midi;
		}
	}

	/** One strummed chord, with what it is made of string by string. */
	static final class Chord {
		final String recording;
		final double onset;
		/** The notes, by string (0 = low E). */
		final List<Note> notes;
		/** The distinct sounding pitches, ascending. */
		final int[] pitches;
		final String label;
		final String family;
		/** Share (0 to 1) of the chord's pitches that were already sounding, by the annotation, when it was struck (the string is struck again while it still rings). */
		final double ringingShare;

		Chord(String recording, double onset, List<Note> notes, String label, String family, double ringingShare) {
			this.ringingShare = ringingShare;
			this.recording = recording;
			this.onset = onset;
			this.notes = notes;
			this.label = label;
			this.family = family;
			int[] sorted = new int[notes.size()];
			for( int i = 0 ; i < sorted.length ; i ++ ) {
				sorted[i] = notes.get(i).midi;
			}
			Arrays.sort(sorted);
			this.pitches = sorted;
		}

		String id() {
			return this.recording + "@" + String.format("%.2f", this.onset);
		}
	}

	static final class Extraction {
		final List<Chord> chords = new ArrayList<Chord>();
		/** Why strums were not used, with how many. */
		final Map<String, Integer> exclusions = new TreeMap<String, Integer>();

		void exclude(String reason) {
			this.exclusions.merge(reason, 1, Integer::sum);
		}
	}

	private static final Map<String, int[]> QUALITIES = new HashMap<String, int[]>();
	private static final Map<String, Integer> ROOTS = new HashMap<String, Integer>();

	static {
		QUALITIES.put("maj", new int[] { 0, 4, 7 });
		QUALITIES.put("min", new int[] { 0, 3, 7 });
		QUALITIES.put("7", new int[] { 0, 4, 7, 10 });
		QUALITIES.put("maj7", new int[] { 0, 4, 7, 11 });
		QUALITIES.put("min7", new int[] { 0, 3, 7, 10 });
		QUALITIES.put("hdim7", new int[] { 0, 3, 6, 10 });
		QUALITIES.put("dim", new int[] { 0, 3, 6 });
		QUALITIES.put("sus4", new int[] { 0, 5, 7 });
		QUALITIES.put("sus2", new int[] { 0, 2, 7 });
		String[] names = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
		for( int i = 0 ; i < names.length ; i ++ ) {
			ROOTS.put(names[i], i);
		}
		ROOTS.put("Db", 1);
		ROOTS.put("Eb", 3);
		ROOTS.put("Gb", 6);
		ROOTS.put("Ab", 8);
		ROOTS.put("Bb", 10);
		ROOTS.put("Cb", 11);
		ROOTS.put("Fb", 4);
		ROOTS.put("E#", 5);
		ROOTS.put("B#", 0);
	}

	private GuitarSetChords() {
	}

	/** The pitch classes of a chord label such as {@code D#:maj7}, or null when the label is not one this table knows. */
	static int[] tonesOf(String label) {
		int colon = label.indexOf(':');
		if( colon < 0 ) {
			return null;
		}
		Integer root = ROOTS.get(label.substring(0, colon));
		String quality = label.substring(colon + 1);
		int slash = quality.indexOf('/');
		if( slash >= 0 || quality.indexOf('(') >= 0 ) {
			return null;
		}
		int[] intervals = QUALITIES.get(quality);
		if( root == null || intervals == null ) {
			return null;
		}
		int[] tones = new int[intervals.length];
		for( int i = 0 ; i < tones.length ; i ++ ) {
			tones[i] = (root + intervals[i]) % 12;
		}
		return tones;
	}

	static String qualityOf(String label) {
		int colon = label.indexOf(':');
		return colon < 0 ? "other" : label.substring(colon + 1);
	}

	/** Reads the notes of the six strings and the instructed chord labels of a JAMS file. */
	static Extraction extract(Path jams, String recording) throws IOException {
		Map<String, Object> root = MiniJson.object(MiniJson.parse(new String(Files.readAllBytes(jams), StandardCharsets.UTF_8)));
		List<Note> notes = new ArrayList<Note>();
		List<double[]> labelSpans = new ArrayList<double[]>();
		List<String> labels = new ArrayList<String>();
		boolean labelsRead = false;
		for( Object annotation : MiniJson.array(root.get("annotations")) ) {
			Map<String, Object> map = MiniJson.object(annotation);
			String namespace = (String) map.get("namespace");
			if( "note_midi".equals(namespace) ) {
				int string = stringOf(map);
				for( Object item : MiniJson.array(map.get("data")) ) {
					Map<String, Object> note = MiniJson.object(item);
					notes.add(new Note(string, (Double) note.get("time"), (Double) note.get("duration"), (int) Math.round((Double) note.get("value"))));
				}
			} else if( "chord".equals(namespace) && !labelsRead ) {
				// the first chord annotation is the instructed (sheet) label, the second a transcription of what was played
				labelsRead = true;
				for( Object item : MiniJson.array(map.get("data")) ) {
					Map<String, Object> label = MiniJson.object(item);
					labelSpans.add(new double[] { (Double) label.get("time"), (Double) label.get("time") + (Double) label.get("duration") });
					labels.add((String) label.get("value"));
				}
			}
		}
		return strums(recording, notes, labelSpans, labels);
	}

	/** The annotation of a string is numbered 0 (low E) to 5 (high E) in its data source. */
	private static int stringOf(Map<String, Object> annotation) {
		Object metadata = annotation.get("annotation_metadata");
		Object source = MiniJson.object(metadata).get("data_source");
		return Integer.parseInt(String.valueOf(source).trim());
	}

	/** Groups the notes into strums and applies the exclusion rules. Separate from the file reading so that it can be tested. */
	static Extraction strums(String recording, List<Note> notes, List<double[]> labelSpans, List<String> labels) {
		Extraction result = new Extraction();
		List<Note> sorted = new ArrayList<Note>(notes);
		Collections.sort(sorted, new Comparator<Note>() {
			@Override
			public int compare(Note a, Note b) {
				return Double.compare(a.time, b.time);
			}
		});
		List<List<Note>> clusters = new ArrayList<List<Note>>();
		List<Note> current = null;
		boolean[] used = new boolean[6];
		for( Note note : sorted ) {
			if( current == null || note.time - current.get(0).time > STRUM_SPAN_SECONDS || used[note.string] ) {
				current = new ArrayList<Note>();
				clusters.add(current);
				Arrays.fill(used, false);
			}
			current.add(note);
			used[note.string] = true;
		}
		for( int i = 0 ; i < clusters.size() ; i ++ ) {
			List<Note> cluster = clusters.get(i);
			if( cluster.size() < MIN_NOTES ) {
				continue;
			}
			double onset = medianTime(cluster);
			if( i + 1 < clusters.size() && clusters.get(i + 1).get(0).time - onset < AFTER_SECONDS ) {
				result.exclude("next strike within " + AFTER_SECONDS + " s");
				continue;
			}
			if( i > 0 && onset - clusters.get(i - 1).get(0).time < MIN_PREVIOUS_GAP_SECONDS ) {
				result.exclude("previous strike within " + MIN_PREVIOUS_GAP_SECONDS + " s");
				continue;
			}
			if( hasUnison(cluster) ) {
				result.exclude("two strings sound the same pitch");
				continue;
			}
			double shortest = Double.MAX_VALUE;
			for( Note note : cluster ) {
				shortest = Math.min(shortest, note.duration);
			}
			if( shortest < MIN_NOTE_SECONDS ) {
				result.exclude("a note is shorter than " + MIN_NOTE_SECONDS + " s");
				continue;
			}
			String label = labelAt(labelSpans, labels, onset);
			int[] tones = (label == null ? null : tonesOf(label));
			if( tones == null ) {
				result.exclude("instructed label missing or not in the table of plain chords");
				continue;
			}
			if( !pitchClassesWithin(cluster, tones) ) {
				result.exclude("a played note is not in the instructed chord (annotation disagreement)");
				continue;
			}
			result.chords.add(new Chord(recording, onset, cluster, label, qualityOf(label), ringingShare(cluster, sorted, onset)));
		}
		return result;
	}

	/** The share of the cluster's pitches that another note of the recording, struck earlier, still holds at the onset. */
	private static double ringingShare(List<Note> cluster, List<Note> all, double onset) {
		int ringing = 0;
		for( Note note : cluster ) {
			for( Note other : all ) {
				if( !cluster.contains(other) && other.midi == note.midi && other.time < onset - 0.02 && other.time + other.duration > onset ) {
					ringing ++;
					break;
				}
			}
		}
		return (double) ringing / cluster.size();
	}

	private static double medianTime(List<Note> cluster) {
		double[] times = new double[cluster.size()];
		for( int i = 0 ; i < times.length ; i ++ ) {
			times[i] = cluster.get(i).time;
		}
		Arrays.sort(times);
		return times[times.length / 2];
	}

	private static boolean hasUnison(List<Note> cluster) {
		for( int i = 0 ; i < cluster.size() ; i ++ ) {
			for( int j = i + 1 ; j < cluster.size() ; j ++ ) {
				if( cluster.get(i).midi == cluster.get(j).midi ) {
					return true;
				}
			}
		}
		return false;
	}

	private static String labelAt(List<double[]> spans, List<String> labels, double time) {
		for( int i = 0 ; i < spans.size() ; i ++ ) {
			if( time >= spans.get(i)[0] - STRUM_SPAN_SECONDS && time < spans.get(i)[1] ) {
				return labels.get(i);
			}
		}
		return null;
	}

	private static boolean pitchClassesWithin(List<Note> cluster, int[] tones) {
		for( Note note : cluster ) {
			boolean found = false;
			for( int tone : tones ) {
				found |= (note.midi % 12 == tone);
			}
			if( !found ) {
				return false;
			}
		}
		return true;
	}
}
