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

import org.junit.jupiter.api.Tag;
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
import app.tuxguitar.app.tools.custom.practice.session.FollowLeniency;
import app.tuxguitar.app.tools.custom.practice.session.FollowLeniencyPosition;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSnapshot;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.tg.TGSongReaderImpl;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

/**
 * The measure that fixes the values of the pitch permissiveness of Follow (spec 016 T031, T032, SC-004); a copy of the harness of
 * {@code ArcadeLeniencyReplayTest}, which stays as it is, with Follow's positions and candidate values as parameters. Needs the local reference recording
 * (tests/1) and macOS afconvert, like it.
 */
@Tag("dataset")
public class FollowLeniencyReplayTest {

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


	/** The time (seconds) at which a session with these settings, starting at {@code startEvent}, accepts it within [from, to], or NaN. */
	private static double acceptedAt(Reference reference, int startEvent, double from, double to, RecognitionSettings settings) {
		PracticeParameters parameters = PracticeParameters.of(settings);
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

	/** The three figures of SC-001 and SC-002 for one set of settings, as a line of text, and the numbers themselves. */
	private static final class Figures {
		int judged;
		int recognised;
		int falseAccepts;
		int negatives;
		int incompleteAccepted;
		int incompleteTests;
		int early;
		double medianMillis;
		double p90Millis;
		String missed;
		String falseList;

		@Override
		public String toString() {
			return String.format("judged %d, recognised %d, missed %s, false accepts %d/%d %s, chords with a note missing accepted %d/%d, accepted before the attack %d, delay median %.0f ms p90 %.0f ms",
					this.judged, this.recognised, this.missed, this.falseAccepts, this.negatives, this.falseList, this.incompleteAccepted, this.incompleteTests, this.early, this.medianMillis, this.p90Millis);
		}
	}

	private static Figures measure(Reference reference, RecognitionSettings settings) {
		List<PracticeEvent> events = reference.sequence.getEvents();
		int count = events.size();
		Figures figures = new Figures();
		List<String> missed = new ArrayList<String>();
		List<String> falseAccepts = new ArrayList<String>();
		List<Double> latencies = new ArrayList<Double>();
		for( int i = 0 ; i < count ; i ++ ) {
			double t = reference.times[i];
			double next = (i + 1 < count ? reference.times[i + 1] : t + 1.5);
			double previous = (i > 0 ? t - reference.times[i - 1] : 1.0);
			if( next - t < MIN_GAP_SECONDS || previous < MIN_GAP_SECONDS ) {
				continue;
			}
			figures.judged ++;
			double at = acceptedAt(reference, i, t - 0.15, next + 0.10, settings);
			if( Double.isNaN(at) ) {
				missed.add(i + "@" + t);
			} else {
				latencies.add(at - t);
				if( at < t ) {
					figures.early ++;
				}
			}
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
					continue;
				}
				boolean incompleteChord = (events.get(j).getKind() == PracticeEvent.Kind.EXPECTED_CHORD
						&& pitchesOf(events.get(i)).length < pitchesOf(events.get(j)).length && listOf(pitchesOf(events.get(j))).containsAll(listOf(pitchesOf(events.get(i)))));
				boolean accepted = !Double.isNaN(acceptedAt(reference, j, t - 0.05, next - 0.03, settings));
				if( incompleteChord ) {
					figures.incompleteTests ++;
					if( accepted ) {
						figures.incompleteAccepted ++;
					}
					continue;
				}
				figures.negatives ++;
				if( accepted ) {
					figures.falseAccepts ++;
					falseAccepts.add("expecting " + j + " on the audio of " + i);
				}
			}
		}
		Collections.sort(latencies);
		figures.recognised = latencies.size();
		figures.missed = missed.toString();
		figures.falseList = falseAccepts.toString();
		figures.medianMillis = latencies.isEmpty() ? 0.0 : 1000 * latencies.get(latencies.size() / 2);
		figures.p90Millis = latencies.isEmpty() ? 0.0 : 1000 * latencies.get((int) (latencies.size() * 0.9));
		return figures;
	}

	/**
	 * A candidate set of values given as {@code key=value,key=value} (the keys of {@link RecognitionParameters}, as {@code recognition.note.accept=40}), applied one
	 * way on the base as {@link FollowLeniency} does it: the more lenient of the two. Higher is more lenient for the acceptance in cents only.
	 */
	private static RecognitionSettings candidate(RecognitionSettings base, String values) {
		RecognitionSettings settings = base;
		for( String pair : values.split(",") ) {
			String[] parts = pair.trim().split("=");
			RecognitionParameter.Number parameter = (RecognitionParameter.Number) RecognitionParameters.byKey(parts[0].trim());
			double wanted = Double.parseDouble(parts[1].trim());
			double current = settings.get(parameter);
			double value = (parameter == RecognitionParameters.NOTE_ACCEPT ? Math.max(current, wanted) : Math.min(current, wanted));
			settings = settings.with(parameter, Double.valueOf(value));
		}
		return settings;
	}

	/**
	 * Spec 016 (T031): the figures at each position of Follow's permissiveness, and at each candidate set given by {@code -Dfollow.candidates=set;set} (each set as
	 * {@link #candidate}), with those of the base (the default settings, the position Strict) next to them: the right notes of the reference recording accepted,
	 * the negatives accepted (SC-004: at most 4 of the 98 at Permissive), the median delay. Gated by {@code -Dfollow.measure=true}; it prints, the limits are read by
	 * the person who fixes the values. Only the parity of Strict with the base is asserted.
	 */
	@Test
	public void theFiguresAtEachPositionOfFollowsPermissiveness() throws IOException {
		assumeTrue(Boolean.getBoolean("follow.measure"), "run with -Dfollow.measure=true");
		Reference reference = load();
		RecognitionSettings base = RecognitionSettings.defaults();
		Figures baseFigures = measure(reference, base);
		System.out.println("FOLLOW MEASURE base (the default settings): " + baseFigures);
		for( FollowLeniencyPosition position : FollowLeniencyPosition.values() ) {
			report(position.name(), measure(reference, FollowLeniency.apply(position, base)), baseFigures);
		}
		String candidates = System.getProperty("follow.candidates", "").trim();
		if( !candidates.isEmpty() ) {
			for( String values : candidates.split(";") ) {
				report("candidate " + values.trim(), measure(reference, candidate(base, values)), baseFigures);
			}
		}
		assertEquals(baseFigures.toString(), measure(reference, FollowLeniency.apply(FollowLeniencyPosition.STRICT, base)).toString());
	}

	private static void report(String name, Figures figures, Figures baseFigures) {
		System.out.println("FOLLOW MEASURE " + name + ": " + figures);
		double faster = baseFigures.medianMillis == 0 ? 0 : 100.0 * (baseFigures.medianMillis - figures.medianMillis) / baseFigures.medianMillis;
		System.out.println(String.format("FOLLOW MEASURE %s vs base: right accepted %+d, false accepts %+d, median delay %.0f%% shorter", name, figures.recognised - baseFigures.recognised,
				figures.falseAccepts - baseFigures.falseAccepts, faster));
	}
}
