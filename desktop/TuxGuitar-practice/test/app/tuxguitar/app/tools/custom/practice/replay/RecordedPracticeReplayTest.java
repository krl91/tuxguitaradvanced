package app.tuxguitar.app.tools.custom.practice.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.TestSequences;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.AttackCandidates;
import app.tuxguitar.app.tools.custom.practice.session.ChordMatcher;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSnapshot;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.tg.TGSongReaderImpl;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

/**
 * Replays a real recording (acoustic fingerstyle guitar, a bass ringing under the melody) through
 * the whole recognition chain: analysis stages, attack evidence, Practice session (spec 004).
 * Each event is judged with a session that expects it, fed the audio around it; neighbouring
 * events are judged on the same audio and must not be recognised. The audio and its score are not
 * redistributed: they are read from {@code tests/1} (or {@code -Dtuxguitar.recording.dir}) and the
 * test is skipped, with the reason, when they are absent. Event times come from the committed
 * fixture {@code test-data/real-recording-1/events.csv} (see its README), not from the recogniser.
 */
public class RecordedPracticeReplayTest {

	private static final double MIN_GAP_SECONDS = 0.15;

	private static final class Reference {
		final PracticeTrackSelection selection;
		final PracticeSequence sequence;
		final double[] times;
		final List<AnalysisFrame> frames;
		final int[] candidates;

		Reference(PracticeTrackSelection selection, PracticeSequence sequence, double[] times, List<AnalysisFrame> frames, int[] candidates) {
			this.selection = selection;
			this.sequence = sequence;
			this.times = times;
			this.frames = frames;
			this.candidates = candidates;
		}
	}

	private static Path directory() {
		return Paths.get(System.getProperty("tuxguitar.recording.dir", "../../tests/1"));
	}

	private static Reference load() throws IOException {
		Path dir = directory();
		Path score = dir.resolve("_TEST_verifie.tg");
		assumeTrue(Files.isRegularFile(score), "reference score not found: " + score.toAbsolutePath());
		RecordedAudio audio = RecordedAudio.load(dir.resolve("audio_15ps.m4a"));
		assumeTrue(audio != null, "reference audio not readable here (needs tests/1/audio_15ps.m4a and macOS afconvert): " + dir.toAbsolutePath());
		TGSong song;
		try( InputStream in = new BufferedInputStream(Files.newInputStream(score)) ) {
			TGSongReaderHandle handle = new TGSongReaderHandle();
			handle.setFactory(new TGFactory());
			handle.setInputStream(in);
			new TGSongReaderImpl().read(handle);
			song = handle.getSong();
		}
		TGTrack track = song.getTrack(0);
		PracticeTrackSelection selection = new PracticeTrackSelection(song, PracticeTrackCandidates.evaluate(song, track).getPreselected(),
				PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeSequence sequence = PracticeSequenceBuilder.build(song, track, TrackTuning.of(track, null));
		double[] times = readTimes(sequence.getEvents().size());
		int[] candidates = AttackCandidates.of(sequence);
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(audio.samples, audio.sampleRate, RecognitionSettings.defaults(), false, candidates);
		return new Reference(selection, sequence, times, frames, candidates);
	}

	private static double[] readTimes(int events) throws IOException {
		List<String> lines = Files.readAllLines(Paths.get("test-data/real-recording-1/events.csv"));
		assertEquals(events + 1, lines.size(), "fixture rows");
		double[] times = new double[events];
		for( int i = 0 ; i < events ; i ++ ) {
			times[i] = Double.parseDouble(lines.get(i + 1).split(",")[1]);
		}
		return times;
	}

	private static int[] pitchesOf(PracticeEvent event) {
		if( event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD ) {
			int[] pitches = event.getRequiredPitches().clone();
			Arrays.sort(pitches);
			return pitches;
		}
		return new int[] { event.getExpectedMidi() };
	}

	private static List<Integer> listOf(int[] values) {
		List<Integer> list = new ArrayList<Integer>();
		for( int value : values ) {
			list.add(value);
		}
		return list;
	}

	/**
	 * Audio cannot tell {@code expected} from what was {@code played} (spec FR-004): the expected notes
	 * are all among the played ones (extra strings sounding are tolerated, as the chord setting
	 * "foreign strings: ignore" does), or one is an octave of a played note (the octave is a partial of
	 * the lower note). Expecting MORE than was played (a note missing from a chord) is not related:
	 * it must never be recognised (FR-003).
	 */
	private static boolean related(int[] expected, int[] played) {
		List<Integer> listPlayed = new ArrayList<Integer>();
		for( int x : played ) {
			listPlayed.add(x);
		}
		boolean subset = true;
		for( int x : expected ) {
			subset &= listPlayed.contains(x);
		}
		if( subset ) {
			return true;
		}
		for( int x : expected ) {
			for( int y : played ) {
				if( x != y && Math.abs(x - y) % 12 == 0 ) {
					return true;
				}
			}
		}
		return false;
	}

	/** The time (seconds) at which a session starting at {@code startEvent} accepts it within [from, to], or NaN. */
	private static double acceptedAt(Reference reference, int startEvent, double from, double to) {
		PracticeParameters parameters = PracticeParameters.of(RecognitionSettings.defaults());
		PracticeSession session = new PracticeSession(reference.selection, TestSequences.startingAt(reference.sequence, startEvent), parameters.noteInterpreter(),
				parameters.tolerance(), parameters.stabilityNanos(), parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
		session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		for( AnalysisFrame frame : reference.frames ) {
			double t = frame.getTimestampNanos() / 1e9;
			if( t < from ) {
				continue;
			}
			if( t > to ) {
				break;
			}
			PracticeSnapshot snapshot = session.onFrame(frame);
			if( snapshot.getAcceptedCount() > 0 ) {
				return t;
			}
		}
		return Double.NaN;
	}

	@Test
	public void everyEventIsRecognisedAndNoNeighbouringEventIsTakenForIt() throws IOException {
		Reference reference = load();
		List<PracticeEvent> events = reference.sequence.getEvents();
		int count = events.size();
		List<String> missed = new ArrayList<String>();
		List<String> falseAccepts = new ArrayList<String>();
		List<String> incompleteChordAccepts = new ArrayList<String>();
		int incompleteChordTests = 0;
		List<String> early = new ArrayList<String>();
		List<Double> latencies = new ArrayList<Double>();
		int judged = 0;
		int fast = 0;
		int related = 0;
		int negatives = 0;
		for( int i = 0 ; i < count ; i ++ ) {
			double t = reference.times[i];
			double next = (i + 1 < count ? reference.times[i + 1] : t + 1.5);
			double previous = (i > 0 ? t - reference.times[i - 1] : 1.0);
			if( next - t < MIN_GAP_SECONDS || previous < MIN_GAP_SECONDS ) {
				// notes this close overlap in time: a speed limit, reported, not judged (spec edge cases)
				fast ++;
				continue;
			}
			judged ++;
			double at = acceptedAt(reference, i, t - 0.15, next + 0.10);
			if( Double.isNaN(at) ) {
				missed.add(i + "@" + t);
			} else {
				latencies.add(at - t);
				if( at < t ) {
					early.add(i + "@" + at);
				}
			}
			// the neighbouring events, and every chord of which this event is only a part (a chord with a note missing, FR-003)
			TreeSet<Integer> others = new TreeSet<Integer>(Arrays.asList(i - 2, i - 1, i + 1, i + 2));
			for( int c = 0 ; c < count ; c ++ ) {
				if( events.get(c).getKind() == PracticeEvent.Kind.EXPECTED_CHORD && Math.abs(c - i) <= 8 && pitchesOf(events.get(i)).length < pitchesOf(events.get(c)).length
						&& listOf(pitchesOf(events.get(c))).containsAll(listOf(pitchesOf(events.get(i)))) ) {
					others.add(c);
				}
			}
			for( int j : others ) {
				if( j < 0 || j >= count || Arrays.equals(pitchesOf(events.get(j)), pitchesOf(events.get(i))) ) {
					continue;
				}
				if( related(pitchesOf(events.get(j)), pitchesOf(events.get(i))) ) {
					related ++;
					continue;
				}
				boolean incompleteChord = (events.get(j).getKind() == PracticeEvent.Kind.EXPECTED_CHORD
						&& pitchesOf(events.get(i)).length < pitchesOf(events.get(j)).length && listOf(pitchesOf(events.get(j))).containsAll(listOf(pitchesOf(events.get(i)))));
				boolean accepted = !Double.isNaN(acceptedAt(reference, j, t - 0.05, next - 0.03));
				if( incompleteChord ) {
					incompleteChordTests ++;
					if( accepted ) {
						incompleteChordAccepts.add("expecting " + j + " on the audio of " + i);
					}
					continue;
				}
				negatives ++;
				if( accepted ) {
					falseAccepts.add("expecting " + j + " on the audio of " + i);
				}
			}
		}
		Collections.sort(latencies);
		String report = String.format("reference recording: %d events, %d judged (%d within %.2f s of a neighbour are a speed limit), recognised %d, missed %s, "
				+ "false accepts %d/%d %s, KNOWN LIMIT chords with a note missing recognised %d/%d %s, related pairs not counted %d, accepted before the attack %s, delay median %.0f ms p90 %.0f ms",
				count, judged, fast, MIN_GAP_SECONDS, latencies.size(), missed, falseAccepts.size(), negatives, falseAccepts, incompleteChordAccepts.size(), incompleteChordTests, incompleteChordAccepts, related, early,
				latencies.isEmpty() ? 0.0 : 1000 * latencies.get(latencies.size() / 2), latencies.isEmpty() ? 0.0 : 1000 * latencies.get((int) (latencies.size() * 0.9)));
		System.out.println(report);
		assertTrue(missed.isEmpty(), report);
		assertTrue(falseAccepts.isEmpty(), report);
		// FR-003 is not met yet: the count of chords wrongly recognised with a note missing must not grow beyond what is measured and documented (research R8)
		assertTrue(incompleteChordAccepts.size() <= 4, report);
		assertTrue(early.isEmpty(), report);
		assertTrue(latencies.get(latencies.size() / 2) <= 0.200, report);
		assertTrue(latencies.get((int) (latencies.size() * 0.9)) <= 0.400, report);
	}
}
