package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.ChordLeniency;
import app.tuxguitar.audio.engine.settings.ChordLeniency.Level;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * The three chord check levels on the owner's reference series (spec 024, task T030; FR-007, SC-004, data-model "Chord leniency level"), judged as
 * {@link DatasetJudge} does (the Wait session) but with each level's values: every level accepts no clearly wrong chord, <i>Lenient</i> accepts at
 * least <i>Normal</i>'s right chords and <i>Strict</i> wrongly accepts at most as many as <i>Normal</i>. It also reports, without asserting, the
 * incomplete chords of the public datasets (one note more expected than played, built as {@code DatasetBenchmarkTest} builds them) accepted by each
 * level, for the spec's edge case "Strict refuses it more often".
 *
 * <p>The owner's takes are read as {@link OwnerChordsProfileTest} reads them (skipped when absent); the datasets from {@code -Dtuxguitar.datasets}
 * (default {@code ~/tuxguitar-datasets}; their part is left out when absent). Run with {@code -Dgroups=dataset -DexcludedGroups=} and
 * {@code -Dtuxguitar.recognition.engine=java} or {@code native}. The report goes to
 * {@code target/dataset-benchmark/chord-leniency-levels-<engine>.txt}.</p>
 */
@Tag("dataset")
public class ChordLeniencyLevelsTest {

	private static final Path REFERENCE = Paths.get("../../specs/004-attack-difference-recognition/benchmark/owner-chords-3");
	private static final List<Level> LEVELS = Arrays.asList(Level.STRICT, Level.NORMAL, Level.LENIENT);
	private static final int[] OPEN_STRINGS = { 64, 59, 55, 50, 45, 40 };
	private static final int LOWEST = 40;
	private static final int HIGHEST = 88;

	private static final class Take {
		final String file;
		final int[] pitches;
		final RecordedAudio audio;

		Take(String file, int[] pitches, RecordedAudio audio) {
			this.file = file;
			this.pitches = pitches;
			this.audio = audio;
		}
	}

	/** What one level does on the series. */
	private static final class Counts {
		int right;
		int[] accepted = new int[3];
		int[] judged = new int[3];
		int incompleteAccepted;
		int incompleteJudged;
	}

	private static List<Take> takes() throws IOException {
		String property = System.getProperty("tuxguitar.owner.chords");
		Path folder = (property != null && !property.isEmpty() ? Paths.get(property.split(",")[0]) : REFERENCE);
		List<Take> takes = new ArrayList<Take>();
		Path labels = folder.resolve("labels.csv");
		if( !Files.isRegularFile(labels) ) {
			return takes;
		}
		List<String> lines = Files.readAllLines(labels, StandardCharsets.UTF_8);
		for( String line : lines.subList(1, lines.size()) ) {
			String[] cells = line.split(",", 2);
			if( cells.length < 2 || cells[1].trim().isEmpty() ) {
				continue;
			}
			int[] pitches = Arrays.stream(cells[1].trim().split(" ")).mapToInt(Integer::parseInt).toArray();
			if( DatasetJudge.strings(pitches) == null ) {
				continue;
			}
			RecordedAudio audio = RecordedAudio.load(folder.resolve(cells[0]));
			if( audio != null ) {
				takes.add(new Take(cells[0], pitches, audio));
			}
		}
		return takes;
	}

	/** As {@link DatasetJudge}'s {@code accepts}, with the given settings instead of the defaults. */
	static boolean accepts(RecordedAudio audio, int[] expected, RecognitionSettings settings) {
		int[] strings = DatasetJudge.strings(expected);
		SongFixture fixture = new SongFixture(1);
		for( int i = 0 ; i < expected.length ; i ++ ) {
			fixture.quarter(0, 0, strings[i], expected[i] - OPEN_STRINGS[strings[i] - 1]);
		}
		PracticeSequence sequence = fixture.sequence();
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeParameters parameters = PracticeParameters.of(settings);
		PracticeSession session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
				parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
		session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		float[] samples = new float[audio.samples.length + audio.sampleRate / 2];
		System.arraycopy(audio.samples, 0, samples, audio.sampleRate / 2, audio.samples.length);
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(samples, audio.sampleRate, settings, expected.length > 1, AttackCandidates.of(sequence));
		for( AnalysisFrame frame : frames ) {
			if( session.onFrame(frame).getAcceptedCount() > 0 ) {
				return true;
			}
		}
		return false;
	}

	private static boolean playable(int[] pitches) {
		for( int pitch : pitches ) {
			if( pitch < LOWEST || pitch > HIGHEST ) {
				return false;
			}
		}
		return DatasetJudge.strings(pitches) != null;
	}

	/** As {@code DatasetBenchmarkTest}: octave or harmonic relatives of the lowest note, and unison or octave of any note. */
	private static boolean relatedToPlayed(int candidate, int[] played) {
		for( int pitch : played ) {
			if( candidate == pitch || Math.abs(candidate - pitch) % 12 == 0 ) {
				return true;
			}
		}
		for( int h : new int[] { 12, 19, 24, 28, 31, 34, 36 } ) {
			if( candidate - played[0] == h ) {
				return true;
			}
		}
		return false;
	}

	/** The chord one note bigger than played, as {@code DatasetBenchmarkTest} builds it, or null when there is none. */
	private static int[] incomplete(int[] played) {
		for( int offset : new int[] { 2, 3, 4, 5, 6, 1 } ) {
			int candidate = played[played.length - 1] + offset;
			if( candidate <= HIGHEST && !relatedToPlayed(candidate, played) ) {
				int[] bigger = Arrays.copyOf(played, played.length + 1);
				bigger[bigger.length - 1] = candidate;
				Arrays.sort(bigger);
				return playable(bigger) ? bigger : null;
			}
		}
		return null;
	}

	private static List<DatasetCase> chordCases() throws IOException {
		Path root = Paths.get(System.getProperty("tuxguitar.datasets", System.getProperty("user.home") + "/tuxguitar-datasets"));
		List<DatasetCase> cases = new ArrayList<DatasetCase>();
		if( !Files.isDirectory(root) ) {
			return cases;
		}
		int sample = Integer.getInteger("tuxguitar.datasets.sample", 8);
		List<DatasetCase> all = new ArrayList<DatasetCase>();
		all.addAll(DatasetLoaders.idmtGuitar(root, sample));
		all.addAll(DatasetLoaders.effects(root, false, sample));
		all.addAll(DatasetLoaders.effects(root, true, sample));
		for( DatasetCase c : all ) {
			if( c.kind != DatasetCase.Kind.SINGLE_NOTE && playable(c.played) ) {
				cases.add(c);
			}
		}
		return cases;
	}

	@Test
	public void theLevelsAreOrderedAndNoneAcceptsAClearlyWrongChord() throws Exception {
		List<Take> takes = takes();
		assumeTrue(!takes.isEmpty(), "the owner's reference series is not here (see the class comment)");
		String engine = PitchEngineCatalog.resolveKind(RecognitionSettings.defaults()).name().toLowerCase();
		List<DatasetCase> cases = chordCases();
		StringBuilder report = new StringBuilder("Chord check levels on the owner's reference series (spec 024, T030), engine " + engine + "\n\n");
		Map<Level, Counts> counts = new EnumMap<Level, Counts>(Level.class);
		List<String> clearlyWrong = new ArrayList<String>();
		for( Level level : LEVELS ) {
			RecognitionSettings settings = ChordLeniency.apply(level, RecognitionSettings.defaults());
			Counts count = new Counts();
			counts.put(level, count);
			report.append(level).append(' ').append(ChordLeniency.values(level)).append('\n');
			for( Take take : takes ) {
				boolean own = accepts(take.audio, take.pitches, settings);
				count.right += own ? 1 : 0;
				if( !own ) {
					report.append("    refused, own chord: ").append(take.file).append(' ').append(Arrays.toString(take.pitches)).append('\n');
				}
				for( Take other : takes ) {
					if( other == take || Arrays.equals(other.pitches, take.pitches) ) {
						continue;
					}
					int kind = OwnerChordsStrumHoldProbeTest.distance(take.pitches, other.pitches);
					boolean wrong = accepts(take.audio, other.pitches, settings);
					count.judged[kind]++;
					count.accepted[kind] += wrong ? 1 : 0;
					if( wrong && kind == 2 ) {
						String line = level + ": " + take.file + " accepted for " + Arrays.toString(other.pitches);
						clearlyWrong.add(line);
						report.append("    CLEARLY WRONG ").append(line).append('\n');
					}
				}
			}
			for( DatasetCase c : cases ) {
				int[] bigger = incomplete(c.played);
				RecordedAudio audio = bigger == null ? null : RecordedAudio.load(c.audio);
				if( audio != null ) {
					count.incompleteJudged++;
					count.incompleteAccepted += accepts(audio, bigger, settings) ? 1 : 0;
				}
			}
			report.append(String.format("    %d of %d right accepted; voicings %d of %d, one name off %d of %d, clearly wrong %d of %d accepted;"
					+ " incomplete chords of the datasets %d of %d accepted (reported only)%n%n", count.right, takes.size(), count.accepted[0], count.judged[0],
					count.accepted[1], count.judged[1], count.accepted[2], count.judged[2], count.incompleteAccepted, count.incompleteJudged));
		}
		Counts strict = counts.get(Level.STRICT);
		Counts normal = counts.get(Level.NORMAL);
		Counts lenient = counts.get(Level.LENIENT);
		// a voicing of the expected chord is reported apart, not counted wrong (spec Clarifications, option a)
		int strictWrong = strict.accepted[1] + strict.accepted[2];
		int normalWrong = normal.accepted[1] + normal.accepted[2];
		report.append(String.format("Strict refuses more incomplete chords than Normal: %s (%d against %d accepted)%n",
				strict.incompleteAccepted < normal.incompleteAccepted ? "yes" : "NO", strict.incompleteAccepted, normal.incompleteAccepted));
		Path out = Paths.get("target", "dataset-benchmark", "chord-leniency-levels-" + engine + ".txt");
		Files.createDirectories(out.getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report);
		assertEquals(new ArrayList<String>(), clearlyWrong, "no level accepts a clearly wrong chord");
		assertTrue(lenient.right >= normal.right, "Lenient accepts at least Normal's right chords: " + lenient.right + " < " + normal.right);
		assertTrue(strictWrong <= normalWrong, "Strict wrongly accepts at most as many as Normal: " + strictWrong + " > " + normalWrong);
	}
}
