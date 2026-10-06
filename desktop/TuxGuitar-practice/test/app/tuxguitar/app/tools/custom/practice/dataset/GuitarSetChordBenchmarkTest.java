package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Complete versus incomplete chords on independent real recordings (spec 004 T007, FR-003): GuitarSet's
 * hexaphonic recordings of strummed chords (one channel per string).
 *
 * <p>For every strummed chord whose annotation can be trusted (see {@link GuitarSetChords}), Practice expects the chord and hears:
 * the sum of the six string channels (the complete chord, which must be accepted); the same sum with one string's channel
 * removed, one string at a time (a chord with a note missing, which must be refused when that note can be told from the others by
 * audio); and the recording of the microphone (the complete chord again, through a real microphone). A missing note that is an
 * octave or a twelfth of a lower note of the chord cannot be told apart by audio (spec FR-004): it is reported on its own line,
 * never counted as heard nor as missing.</p>
 *
 * <p>Data: {@code -Dtuxguitar.datasets=<dir>} (default {@code ~/tuxguitar-datasets}), folder {@code guitarset/GuitarSet} (the
 * annotation, audio_hex-pickup_debleeded and audio_mic folders of the official release, Zenodo 10.5281/zenodo.1422265, licence CC BY-NC 4.0).
 * The data are never committed or copied; only the counts are written, to {@code target/dataset-benchmark}.
 * {@code -Dtuxguitar.datasets.sample=N} sets the number of recordings (default 24, spread over the whole list; 0 = all 180).
 * Not part of the default suite: run with {@code -Dgroups=dataset -DexcludedGroups=}.</p>
 */
@Tag("dataset")
public class GuitarSetChordBenchmarkTest {

	enum Kind {
		COMPLETE_HEXAPHONIC("complete chord, six string channels summed"),
		COMPLETE_MICROPHONE("complete chord, microphone"),
		MISSING_VERIFIABLE("chord with one separately verifiable note missing"),
		MISSING_NOT_VERIFIABLE("chord with one note missing that audio cannot tell apart (octave or harmonic of a lower note)");

		final String text;

		Kind(String text) {
			this.text = text;
		}
	}

	static final class Row {
		final String recording;
		final String chord;
		final String family;
		final int size;
		final Kind kind;
		/** bass, inner or top for a missing note. */
		final String position;
		/** true when the verdict is the right one (accepted for a complete chord, refused for an incomplete one). */
		final boolean right;
		final double delaySeconds;
		final String note;
		/** Share of the chord's pitches that were still ringing from an earlier strike when it was played (0 to 1). */
		double ringingShare;

		Row(String recording, String chord, String family, int size, Kind kind, String position, boolean right, double delaySeconds, String note) {
			this.recording = recording;
			this.chord = chord;
			this.family = family;
			this.size = size;
			this.kind = kind;
			this.position = position;
			this.right = right;
			this.delaySeconds = delaySeconds;
			this.note = note;
		}
	}

	private static Path root() {
		return Paths.get(System.getProperty("tuxguitar.datasets", System.getProperty("user.home") + "/tuxguitar-datasets"), "guitarset", "GuitarSet");
	}

	private static boolean contains(int[] values, int value) {
		for( int v : values ) {
			if( v == value ) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void completeAndIncompleteChordsOnTheHexaphonicRecordingsOfGuitarSet() throws IOException {
		Path root = root();
		assumeTrue(Files.isDirectory(root.resolve("annotation")) && Files.isDirectory(root.resolve("audio").resolve("audio_hex-pickup_debleeded")),
				"GuitarSet (annotation and hexaphonic audio) not found under " + root.toAbsolutePath() + " (see the class comment)");
		int sample = Integer.getInteger("tuxguitar.datasets.sample", 24);
		List<String> ids = new ArrayList<String>();
		try( Stream<Path> stream = Files.list(root.resolve("annotation")) ) {
			ids.addAll(stream.map(p -> p.getFileName().toString()).filter(n -> n.endsWith("_comp.jams")).map(n -> n.substring(0, n.length() - ".jams".length()))
					.sorted().collect(Collectors.toList()));
		}
		assumeTrue(!ids.isEmpty(), "no comping recording under " + root.toAbsolutePath());
		if( sample > 0 && ids.size() > sample ) {
			List<String> spread = new ArrayList<String>();
			for( int i = 0 ; i < sample ; i ++ ) {
				spread.add(ids.get((int) ((long) i * ids.size() / sample)));
			}
			ids = spread;
		}
		ConcurrentLinkedQueue<Row> rows = new ConcurrentLinkedQueue<Row>();
		ConcurrentLinkedQueue<String> excluded = new ConcurrentLinkedQueue<String>();
		ConcurrentLinkedQueue<String> unreadable = new ConcurrentLinkedQueue<String>();
		ids.parallelStream().forEach(id -> {
			try {
				runRecording(root, id, rows, excluded, unreadable, PolyphonicAcceptance.Parameters.defaults());
			} catch (IOException | RuntimeException exception) {
				unreadable.add(id + ": " + exception);
			}
		});
		List<Row> all = new ArrayList<Row>(rows);
		String report = report(ids, all, excluded, unreadable);
		System.out.println(report);
		Path target = Paths.get("target/dataset-benchmark");
		Files.createDirectories(target);
		Files.write(target.resolve("guitarset-chords-report.txt"), report.getBytes(StandardCharsets.UTF_8));
		List<String> csv = new ArrayList<String>();
		csv.add("recording,chord,family,notes,test,missing_position,right,delay_s,note");
		for( Row r : all ) {
			csv.add(String.join(",", r.recording, r.chord, r.family, Integer.toString(r.size), "\"" + r.kind.text + "\"", r.position, Boolean.toString(r.right),
					Double.isNaN(r.delaySeconds) ? "" : String.format(java.util.Locale.ROOT, "%.3f", r.delaySeconds), "\"" + r.note + "\""));
		}
		Files.write(target.resolve("guitarset-chords-outcomes.csv"), csv, StandardCharsets.UTF_8);
		assertTrue(all.stream().anyMatch(r -> r.kind == Kind.COMPLETE_HEXAPHONIC), report);
	}

	static void runRecording(Path root, String id, ConcurrentLinkedQueue<Row> rows, ConcurrentLinkedQueue<String> excluded, ConcurrentLinkedQueue<String> unreadable, PolyphonicAcceptance.Parameters acceptance) throws IOException {
		GuitarSetChords.Extraction extraction = GuitarSetChords.extract(root.resolve("annotation").resolve(id + ".jams"), id);
		for( Map.Entry<String, Integer> e : extraction.exclusions.entrySet() ) {
			for( int i = 0 ; i < e.getValue() ; i ++ ) {
				excluded.add(e.getKey());
			}
		}
		if( extraction.chords.isEmpty() ) {
			return;
		}
		MultiChannelWav hex = MultiChannelWav.read(root.resolve("audio").resolve("audio_hex-pickup_debleeded").resolve(id + "_hex_cln.wav"));
		MultiChannelWav mic = MultiChannelWav.read(root.resolve("audio").resolve("audio_mic").resolve(id + "_mic.wav"));
		if( hex == null || hex.channels.length != 6 ) {
			unreadable.add(id + ": hexaphonic audio unreadable or not six channels");
			return;
		}
		int length = hex.channels[0].length;
		double peak = 0.0;
		for( int i = 0 ; i < length ; i ++ ) {
			double sum = 0.0;
			for( int c = 0 ; c < 6 ; c ++ ) {
				sum += hex.channels[c][i];
			}
			peak = Math.max(peak, Math.abs(sum));
		}
		if( peak <= 0.0 ) {
			unreadable.add(id + ": silent hexaphonic audio");
			return;
		}
		float gain = (float) (0.7 / peak);
		float micGain = 1.0f;
		if( mic != null ) {
			double micPeak = 0.0;
			for( float v : mic.channels[0] ) {
				micPeak = Math.max(micPeak, Math.abs(v));
			}
			micGain = (micPeak > 0.0 ? (float) (0.7 / micPeak) : 1.0f);
		}
		int rate = hex.sampleRate;
		for( GuitarSetChords.Chord chord : extraction.chords ) {
			if( !stringChannelsSound(hex, chord, peak) ) {
				excluded.add("a string channel is silent at its annotated note (annotation and audio disagree)");
				continue;
			}
			int from = Math.max(0, (int) Math.round((chord.onset - GuitarSetChords.BEFORE_SECONDS) * rate));
			int to = Math.min(length, (int) Math.round((chord.onset + GuitarSetChords.AFTER_SECONDS) * rate));
			double start = (double) from / rate;
			int[] pitches = chord.pitches;
			int[] strings = new int[pitches.length];
			for( int i = 0 ; i < pitches.length ; i ++ ) {
				for( GuitarSetChords.Note n : chord.notes ) {
					if( n.midi == pitches[i] ) {
						strings[i] = 6 - n.string;
					}
				}
			}
			String family = chord.family;
			// the complete chord, from the six channels
			float[] full = mix(hex, from, to, -1, gain);
			rows.add(ringing(chord, complete(chord, family, Kind.COMPLETE_HEXAPHONIC, start, DatasetJudge.acceptanceSeconds(full, rate, pitches, strings, acceptance))));
			// the complete chord, from the microphone
			if( mic != null && mic.sampleRate == rate && mic.channels[0].length >= to ) {
				float[] micMix = new float[to - from];
				for( int i = 0 ; i < micMix.length ; i ++ ) {
					micMix[i] = mic.channels[0][from + i] * micGain;
				}
				rows.add(ringing(chord, complete(chord, family, Kind.COMPLETE_MICROPHONE, start, DatasetJudge.acceptanceSeconds(micMix, rate, pitches, strings, acceptance))));
			}
			// one string removed at a time
			int[] separable = PolyphonicAcceptance.separable(pitches);
			for( GuitarSetChords.Note missing : chord.notes ) {
				float[] without = mix(hex, from, to, missing.string, gain);
				double at = DatasetJudge.acceptanceSeconds(without, rate, pitches, strings, acceptance);
				boolean verifiable = contains(separable, missing.midi);
				String position = (missing.midi == pitches[0] ? "bass" : (missing.midi == pitches[pitches.length - 1] ? "top" : "inner"));
				boolean refused = Double.isNaN(at);
				rows.add(new Row(chord.recording, chord.id(), family, pitches.length, verifiable ? Kind.MISSING_VERIFIABLE : Kind.MISSING_NOT_VERIFIABLE, position, refused, Double.NaN,
						"without " + GuitarSetChords.STRING_NAMES[missing.string] + " (midi " + missing.midi + ")" + (refused ? "" : ", accepted at " + String.format("%.2f", start + at - chord.onset) + " s from the strum")));
			}
		}
	}

	private static Row ringing(GuitarSetChords.Chord chord, Row row) {
		row.ringingShare = chord.ringingShare;
		return row;
	}

	private static Row complete(GuitarSetChords.Chord chord, String family, Kind kind, double start, double accepted) {
		if( Double.isNaN(accepted) ) {
			return new Row(chord.recording, chord.id(), family, chord.pitches.length, kind, "", false, Double.NaN, "not recognised");
		}
		double delay = start + accepted - chord.onset;
		if( delay < -0.05 ) {
			return new Row(chord.recording, chord.id(), family, chord.pitches.length, kind, "", false, delay, "accepted before the strum");
		}
		return new Row(chord.recording, chord.id(), family, chord.pitches.length, kind, "", true, delay, "");
	}

	/** Every string channel must carry sound at the note annotated for it: a cross-check of the annotation against the audio. */
	private static boolean stringChannelsSound(MultiChannelWav hex, GuitarSetChords.Chord chord, double peak) {
		for( GuitarSetChords.Note note : chord.notes ) {
			int from = (int) Math.round(note.time * hex.sampleRate);
			int to = Math.min(hex.channels[note.string].length, from + (int) (0.2 * hex.sampleRate));
			if( to - from < 100 ) {
				return false;
			}
			double sum = 0.0;
			for( int i = from ; i < to ; i ++ ) {
				sum += hex.channels[note.string][i] * (double) hex.channels[note.string][i];
			}
			if( Math.sqrt(sum / (to - from)) < 0.005 * peak ) {
				return false;
			}
		}
		return true;
	}

	/** The sum of the string channels over the excerpt, without one string when {@code without} is 0 to 5, times the recording's gain. */
	private static float[] mix(MultiChannelWav hex, int from, int to, int without, float gain) {
		float[] out = new float[to - from];
		for( int c = 0 ; c < 6 ; c ++ ) {
			if( c == without ) {
				continue;
			}
			float[] channel = hex.channels[c];
			for( int i = 0 ; i < out.length ; i ++ ) {
				out[i] += channel[from + i] * gain;
			}
		}
		return out;
	}

	private static String percent(int part, int total) {
		return total == 0 ? "-" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * part / total);
	}

	private static String delays(List<Row> rows, Kind kind) {
		List<Double> values = new ArrayList<Double>();
		for( Row r : rows ) {
			if( r.kind == kind && r.right && !Double.isNaN(r.delaySeconds) ) {
				values.add(r.delaySeconds);
			}
		}
		Collections.sort(values);
		if( values.isEmpty() ) {
			return "no delay";
		}
		return String.format(java.util.Locale.ROOT, "delay median %.0f ms, p90 %.0f ms, max %.0f ms (%d accepted chords)", 1000 * values.get(values.size() / 2),
				1000 * values.get((int) (values.size() * 0.9)), 1000 * values.get(values.size() - 1), values.size());
	}

	private static String revision() {
		try {
			Process process = new ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start();
			String head = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			Process status = new ProcessBuilder("git", "status", "--porcelain", "--untracked-files=no").redirectErrorStream(true).start();
			boolean dirty = !new String(status.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim().isEmpty();
			return head + (dirty ? " (+ uncommitted changes)" : "");
		} catch (IOException exception) {
			return "unknown";
		}
	}

	private static String report(List<String> ids, List<Row> all, ConcurrentLinkedQueue<String> excluded, ConcurrentLinkedQueue<String> unreadable) {
		StringBuilder text = new StringBuilder();
		text.append("GUITARSET CHORD BENCHMARK (spec 004 T007): complete versus incomplete chords\n");
		text.append("Data: GuitarSet 1.0.1 (Xi, Bittner, Pauwels, Ye, Bello, ISMIR 2018), Zenodo 10.5281/zenodo.1422265, licence CC BY-NC 4.0; kept outside the repository.\n");
		text.append("Code revision: ").append(revision()).append("\n");
		text.append("Recordings: ").append(ids.size()).append(" comping recordings: ").append(ids.size() <= 40 ? ids.toString() : ids.subList(0, 3) + " ... " + ids.subList(ids.size() - 2, ids.size())).append("\n");
		text.append("Chords evaluated: ").append(all.stream().filter(r -> r.kind == Kind.COMPLETE_HEXAPHONIC).count()).append("\n");
		text.append("Acceptance parameters: minShare 0.30, minBalance 0.10, minPeakRatio 0.7, hops 6, rearm 220 ms (PolyphonicAcceptance.Parameters.defaults()); recognition settings (RecognitionSettings.defaults()): ")
				.append(new TreeMap<String, String>(RecognitionSettings.defaults().toStrings())).append("\n");
		text.append("Excerpt of a chord: from ").append(GuitarSetChords.BEFORE_SECONDS).append(" s before its onset (the previous chord still rings) to ").append(GuitarSetChords.AFTER_SECONDS)
				.append(" s after it; the recording's gain is set so that the sum of the six channels peaks at 0.7, the same for all its chords. Accepted = within the excerpt, not before 50 ms ahead of the strum.\n\n");

		text.append("Complete chords (must be accepted):\n");
		for( Kind kind : new Kind[] { Kind.COMPLETE_HEXAPHONIC, Kind.COMPLETE_MICROPHONE } ) {
			List<Row> rows = all.stream().filter(r -> r.kind == kind).collect(Collectors.toList());
			int ok = (int) rows.stream().filter(r -> r.right).count();
			text.append(String.format("  %-52s accepted %d / %d (%s); %s%n", kind.text, ok, rows.size(), percent(ok, rows.size()), delays(rows, kind)));
			long early = rows.stream().filter(r -> r.note.startsWith("accepted before")).count();
			if( early > 0 ) {
				text.append("    accepted before the strum: ").append(early).append("\n");
			}
		}
		text.append("  complete chords by how many of their notes were still ringing from an earlier strike when they were played (the string is struck again while it vibrates):\n");
		for( Kind kind : new Kind[] { Kind.COMPLETE_HEXAPHONIC, Kind.COMPLETE_MICROPHONE } ) {
			for( Map.Entry<String, int[]> e : tally(all.stream().filter(r -> r.kind == kind).collect(Collectors.toList()),
					r -> r.ringingShare == 0.0 ? "none ringing" : (r.ringingShare >= 0.999 ? "all ringing" : "some ringing")).entrySet() ) {
				text.append(String.format("    %-14s %-52s accepted %3d / %3d (%s)%n", e.getKey(), kind.text, e.getValue()[1] - e.getValue()[0], e.getValue()[1], percent(e.getValue()[1] - e.getValue()[0], e.getValue()[1])));
			}
		}
		List<Row> verifiable = all.stream().filter(r -> r.kind == Kind.MISSING_VERIFIABLE).collect(Collectors.toList());
		int refused = (int) verifiable.stream().filter(r -> r.right).count();
		text.append("\nIncomplete chords, one separately verifiable note removed (must be refused):\n");
		text.append(String.format("  refused %d / %d (%s); FALSE ACCEPTANCES %d%n", refused, verifiable.size(), percent(refused, verifiable.size()), verifiable.size() - refused));
		text.append("  false acceptances by chord family (instructed label quality):\n");
		for( Map.Entry<String, int[]> e : tally(verifiable, r -> r.family).entrySet() ) {
			text.append(String.format("    %-8s %3d / %3d accepted wrongly (%s)%n", e.getKey(), e.getValue()[0], e.getValue()[1], percent(e.getValue()[0], e.getValue()[1])));
		}
		text.append("  false acceptances by position of the missing note:\n");
		for( Map.Entry<String, int[]> e : tally(verifiable, r -> r.position).entrySet() ) {
			text.append(String.format("    %-8s %3d / %3d accepted wrongly (%s)%n", e.getKey(), e.getValue()[0], e.getValue()[1], percent(e.getValue()[0], e.getValue()[1])));
		}
		text.append("  false acceptances by number of notes of the chord:\n");
		for( Map.Entry<String, int[]> e : tally(verifiable, r -> r.size + " notes").entrySet() ) {
			text.append(String.format("    %-8s %3d / %3d accepted wrongly (%s)%n", e.getKey(), e.getValue()[0], e.getValue()[1], percent(e.getValue()[0], e.getValue()[1])));
		}
		text.append("  family x position, false acceptances / tests:\n");
		for( Map.Entry<String, int[]> e : tally(verifiable, r -> r.family + " / " + r.position).entrySet() ) {
			if( e.getValue()[0] > 0 ) {
				text.append(String.format("    %-16s %d / %d%n", e.getKey(), e.getValue()[0], e.getValue()[1]));
			}
		}
		List<Row> notVerifiable = all.stream().filter(r -> r.kind == Kind.MISSING_NOT_VERIFIABLE).collect(Collectors.toList());
		int notVerifiableRefused = (int) notVerifiable.stream().filter(r -> r.right).count();
		text.append("\nNot separately verifiable (the removed note is an octave or a harmonic of a lower note of the chord: audio cannot tell it apart, spec FR-004).\n");
		text.append(String.format("  reported on their own, never counted as heard or missing: %d cases (%d refused, %d accepted: information only)%n", notVerifiable.size(), notVerifiableRefused, notVerifiable.size() - notVerifiableRefused));

		text.append("\nStrikes not used, with the reason:\n");
		Map<String, Integer> reasons = new TreeMap<String, Integer>();
		for( String reason : excluded ) {
			reasons.merge(reason, 1, Integer::sum);
		}
		for( Map.Entry<String, Integer> e : reasons.entrySet() ) {
			text.append(String.format("  %5d  %s%n", e.getValue(), e.getKey()));
		}
		if( !unreadable.isEmpty() ) {
			text.append("\nRecordings that could not be used: ").append(new ArrayList<String>(unreadable)).append("\n");
		}
		return text.toString();
	}

	private static Map<String, int[]> tally(List<Row> rows, java.util.function.Function<Row, String> key) {
		Map<String, int[]> result = new TreeMap<String, int[]>();
		for( Row r : rows ) {
			int[] counts = result.computeIfAbsent(key.apply(r), k -> new int[2]);
			counts[1] ++;
			if( !r.right ) {
				counts[0] ++;
			}
		}
		return result;
	}
}
