package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.app.tools.custom.practice.session.AttackCandidates;
import app.tuxguitar.app.tools.custom.practice.session.ChordMatcher;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * The chord lines of the datasets with the chord evidence the native engine carries live (spec 023, US3, T077 to T080; see
 * {@code benchmark/chord-experiments.md}): the judge is {@link DatasetJudge#accepts}'s, except that the offline analysis is given the expected chord, so
 * the chord check reads the native engine's evidence for it, as live after {@code setExpectedChord}, instead of evaluating the spectrum in Java. With the
 * Java engine the figures are those of the committed benchmarks. Lines: the owner's chords (accepted, and the neighbour of the next take wrongly accepted),
 * the chords of IDMT-SMT-Guitar and of IDMT-SMT-Audio-Effects poly (recognised, incomplete refused, a whole tone higher refused), and the complete chords of
 * GuitarSet (six string channels summed, and the microphone). It only reports, to
 * {@code target/dataset-benchmark/native-chord-evidence.txt}. Excluded from the default run: {@code -Dtest=NativeChordEvidenceBenchmarkTest}.
 */
@Tag("dataset")
public class NativeChordEvidenceBenchmarkTest {

	private static final int[] OPEN_STRINGS = { 64, 59, 55, 50, 45, 40 };
	private static final Path OWNER = Paths.get("../../specs/004-attack-difference-recognition/benchmark");

	/** {@link DatasetJudge#accepts}, with the expected chord given to the offline analysis. */
	static boolean accepts(RecordedAudio audio, int[] expected) {
		float[] samples = new float[audio.samples.length + audio.sampleRate / 2];
		System.arraycopy(audio.samples, 0, samples, audio.sampleRate / 2, audio.samples.length);
		return !Double.isNaN(acceptanceSeconds(samples, audio.sampleRate, expected, DatasetJudge.strings(expected)));
	}

	/** {@link DatasetJudge#acceptanceSeconds}, with the expected chord given to the offline analysis. */
	static double acceptanceSeconds(float[] samples, int sampleRate, int[] expected, int[] strings) {
		SongFixture fixture = new SongFixture(1);
		for( int i = 0 ; i < expected.length ; i ++ ) {
			fixture.quarter(0, 0, strings[i], expected[i] - OPEN_STRINGS[strings[i] - 1]);
		}
		PracticeSequence sequence = fixture.sequence();
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeParameters parameters = PracticeParameters.of(RecognitionSettings.defaults());
		PracticeSession session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
				parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
		session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		boolean chord = expected.length > 1;
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(samples, sampleRate, RecognitionSettings.defaults(), chord, AttackCandidates.of(sequence),
				chord ? expected : null, null);
		for( AnalysisFrame frame : frames ) {
			if( session.onFrame(frame).getAcceptedCount() > 0 ) {
				return frame.getTimestampNanos() / 1e9;
			}
		}
		return Double.NaN;
	}

	@Test
	public void theChordLinesWithTheNativeChordEvidence() throws Exception {
		StringBuilder report = new StringBuilder("Chord lines with the expected chord given to the offline analysis (engine "
				+ System.getProperty("tuxguitar.recognition.engine", "") + ")\n\n");
		boolean any = owner(report);
		Path root = Paths.get(System.getProperty("tuxguitar.datasets", System.getProperty("user.home") + "/tuxguitar-datasets"));
		if( Files.isDirectory(root) ) {
			int sample = Integer.getInteger("tuxguitar.datasets.sample", 8);
			any |= idmt(report, "IDMT-SMT-Guitar", DatasetLoaders.idmtGuitar(root, sample));
			any |= idmt(report, "IDMT-SMT-Audio-Effects poly", DatasetLoaders.effects(root, true, sample));
			any |= guitarSet(report, root.resolve("guitarset").resolve("GuitarSet"), Integer.getInteger("tuxguitar.datasets.guitarset.sample", 24));
		}
		assumeTrue(any, "no dataset here");
		Path out = Paths.get("target", "dataset-benchmark", "native-chord-evidence.txt");
		Files.createDirectories(out.getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report);
	}

	private static boolean owner(StringBuilder report) throws IOException {
		List<int[]> pitches = new ArrayList<int[]>();
		List<RecordedAudio> audios = new ArrayList<RecordedAudio>();
		for( String name : new String[] { "owner-chords", "owner-chords-2" } ) {
			Path folder = OWNER.resolve(name);
			Path labels = folder.resolve("labels.csv");
			if( !Files.isRegularFile(labels) ) {
				continue;
			}
			List<String> lines = Files.readAllLines(labels, StandardCharsets.UTF_8);
			for( String line : lines.subList(1, lines.size()) ) {
				String[] cells = line.split(",", 2);
				if( cells.length < 2 || cells[1].trim().isEmpty() ) {
					continue;
				}
				int[] chord = Arrays.stream(cells[1].trim().split(" ")).mapToInt(Integer::parseInt).toArray();
				if( DatasetJudge.strings(chord) == null ) {
					continue;
				}
				RecordedAudio audio = RecordedAudio.load(folder.resolve(cells[0]));
				if( audio != null ) {
					pitches.add(chord);
					audios.add(audio);
				}
			}
		}
		if( audios.isEmpty() ) {
			return false;
		}
		AtomicInteger accepted = new AtomicInteger();
		AtomicInteger wrong = new AtomicInteger();
		AtomicInteger controls = new AtomicInteger();
		java.util.stream.IntStream.range(0, audios.size()).parallel().forEach(i -> {
			if( accepts(audios.get(i), pitches.get(i)) ) {
				accepted.incrementAndGet();
			}
			for( int k = 1 ; k < audios.size() ; k ++ ) {
				int[] other = pitches.get((i + k) % audios.size());
				if( !Arrays.equals(other, pitches.get(i)) ) {
					controls.incrementAndGet();
					if( accepts(audios.get(i), other) ) {
						wrong.incrementAndGet();
					}
					break;
				}
			}
		});
		report.append(String.format(Locale.ROOT, "Owner's chords: %d of %d accepted; %d of %d neighbouring chords wrongly accepted%n", accepted.get(), audios.size(),
				wrong.get(), controls.get()));
		return true;
	}

	/** The chord cases: recognised, one more note refused, a whole tone higher refused (as {@code DatasetBenchmarkTest}). */
	private static boolean idmt(StringBuilder report, String name, List<DatasetCase> all) {
		List<DatasetCase> chords = new ArrayList<DatasetCase>();
		for( DatasetCase c : all ) {
			if( c.kind != DatasetCase.Kind.SINGLE_NOTE && playable(c.played) ) {
				chords.add(c);
			}
		}
		if( chords.isEmpty() ) {
			return false;
		}
		int[][] counts = new int[3][2];
		chords.parallelStream().forEach(c -> {
			RecordedAudio audio = RecordedAudio.load(c.audio);
			if( audio == null ) {
				return;
			}
			add(counts[0], accepts(audio, c.played));
			int extra = -1;
			for( int offset : new int[] { 2, 3, 4, 5, 6, 1 } ) {
				int candidate = c.played[c.played.length - 1] + offset;
				if( candidate <= 88 && !relatedToPlayed(candidate, c.played) ) {
					extra = candidate;
					break;
				}
			}
			if( extra >= 0 ) {
				int[] bigger = Arrays.copyOf(c.played, c.played.length + 1);
				bigger[bigger.length - 1] = extra;
				Arrays.sort(bigger);
				if( playable(bigger) ) {
					add(counts[1], !accepts(audio, bigger));
				}
			}
			int[] shifted = new int[c.played.length];
			for( int i = 0 ; i < shifted.length ; i ++ ) {
				shifted[i] = c.played[i] + 2;
			}
			if( playable(shifted) ) {
				add(counts[2], !accepts(audio, shifted));
			}
		});
		report.append(String.format(Locale.ROOT, "%s: recognised when played %s; incomplete chord refused %s; chord a whole tone higher refused %s%n", name,
				share(counts[0]), share(counts[1]), share(counts[2])));
		return true;
	}

	/**
	 * The complete chords of GuitarSet, as {@code GuitarSetChordBenchmarkTest} judges them (six string channels summed, and the microphone): accepted within the
	 * excerpt and not more than 50 ms ahead of the strum.
	 */
	private static boolean guitarSet(StringBuilder report, Path root, int sample) throws IOException {
		if( !Files.isDirectory(root.resolve("annotation")) || !Files.isDirectory(root.resolve("audio").resolve("audio_hex-pickup_debleeded")) ) {
			return false;
		}
		List<String> ids = new ArrayList<String>();
		try( java.util.stream.Stream<Path> stream = Files.list(root.resolve("annotation")) ) {
			stream.map(p -> p.getFileName().toString()).filter(n -> n.endsWith("_comp.jams")).map(n -> n.substring(0, n.length() - ".jams".length())).sorted()
					.forEach(ids::add);
		}
		if( sample > 0 && ids.size() > sample ) {
			List<String> spread = new ArrayList<String>();
			for( int i = 0 ; i < sample ; i ++ ) {
				spread.add(ids.get((int) ((long) i * ids.size() / sample)));
			}
			ids = spread;
		}
		int[][] counts = new int[2][2];
		ids.parallelStream().forEach(id -> {
			try {
				guitarSetRecording(root, id, counts);
			} catch (IOException | RuntimeException exception) {
				// unreadable recording: left out, as in GuitarSetChordBenchmarkTest
			}
		});
		report.append(String.format(Locale.ROOT, "GuitarSet complete chords: six string channels summed %s; microphone %s%n", share(counts[0]), share(counts[1])));
		return true;
	}

	private static void guitarSetRecording(Path root, String id, int[][] counts) throws IOException {
		GuitarSetChords.Extraction extraction = GuitarSetChords.extract(root.resolve("annotation").resolve(id + ".jams"), id);
		if( extraction.chords.isEmpty() ) {
			return;
		}
		MultiChannelWav hex = MultiChannelWav.read(root.resolve("audio").resolve("audio_hex-pickup_debleeded").resolve(id + "_hex_cln.wav"));
		MultiChannelWav mic = MultiChannelWav.read(root.resolve("audio").resolve("audio_mic").resolve(id + "_mic.wav"));
		if( hex == null || hex.channels.length != 6 ) {
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
			float[] full = new float[to - from];
			for( int c = 0 ; c < 6 ; c ++ ) {
				for( int i = 0 ; i < full.length ; i ++ ) {
					full[i] += hex.channels[c][from + i] * gain;
				}
			}
			add(counts[0], onTime(start, chord.onset, acceptanceSeconds(full, rate, pitches, strings)));
			if( mic != null && mic.sampleRate == rate && mic.channels[0].length >= to ) {
				float[] micMix = new float[to - from];
				for( int i = 0 ; i < micMix.length ; i ++ ) {
					micMix[i] = mic.channels[0][from + i] * micGain;
				}
				add(counts[1], onTime(start, chord.onset, acceptanceSeconds(micMix, rate, pitches, strings)));
			}
		}
	}

	private static boolean onTime(double start, double onset, double accepted) {
		return !Double.isNaN(accepted) && start + accepted - onset >= -0.05;
	}

	/** As {@code GuitarSetChordBenchmarkTest}: every string channel carries sound at the note annotated for it. */
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

	private static void add(int[] count, boolean ok) {
		synchronized( count ) {
			count[0] += ok ? 1 : 0;
			count[1]++;
		}
	}

	private static String share(int[] count) {
		return count[1] == 0 ? "-" : String.format(Locale.ROOT, "%d of %d (%.1f %%)", count[0], count[1], 100.0 * count[0] / count[1]);
	}

	private static boolean playable(int[] pitches) {
		for( int pitch : pitches ) {
			if( pitch < 40 || pitch > 88 ) {
				return false;
			}
		}
		return DatasetJudge.strings(pitches) != null;
	}

	private static boolean relatedToPlayed(int candidate, int[] played) {
		int lowest = played[0];
		int[] harmonics = { 12, 19, 24, 28, 31, 34, 36 };
		for( int pitch : played ) {
			if( candidate == pitch || Math.abs(candidate - pitch) % 12 == 0 ) {
				return true;
			}
		}
		for( int h : harmonics ) {
			if( candidate - lowest == h ) {
				return true;
			}
		}
		return false;
	}
}
