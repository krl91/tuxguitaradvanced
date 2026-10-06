package app.tuxguitar.app.tools.custom.practice.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.AttackCandidates;
import app.tuxguitar.app.tools.custom.practice.session.ChordMatcher;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSnapshot;
import app.tuxguitar.app.tools.custom.practice.session.TechniqueJudge;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.tg.TGSongReaderImpl;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

/**
 * The four bend scores of the owner's manual check (research R5 addendum) replayed through the whole practice chain with the audio the owner
 * rendered from them with a notation program: clean synthesised guitar, **not a played guitar** (no string noise, no human timing). Read from
 * {@code tests/2} (or {@code -Dtuxguitar.bend.dir}); skipped, with the reason, when the files are absent (they are the owner's, not redistributed).
 * What is asserted: every note of every score is accepted, no wrong note is shown on a bend, a pre-bend or a release, and with the detectors
 * unverified no technique is ever said to be heard. What the technique detectors say on this audio is reported, not asserted.
 */
public class BendRecordedReplayTest {

	private static final int LEAD_IN = 1;

	private static Path directory() {
		return Paths.get(System.getProperty("tuxguitar.bend.dir", "../../tests/2"));
	}

	private static final class Outcome {
		int accepted;
		int events;
		boolean wrongSeen;
		double firstWrong = -1.0;
		boolean heardClaimed;
		final StringBuilder techniques = new StringBuilder();
	}

	private static Outcome replay(String base, TechniqueCatalog catalog) throws IOException {
		Path score = directory().resolve(base + ".tg");
		RecordedAudio audio = RecordedAudio.load(directory().resolve(base + ".mp3"));
		assumeTrue(Files.isRegularFile(score) && audio != null, "bend check files not found or not readable here: " + directory().toAbsolutePath() + "/" + base);
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
		RecognitionSettings settings = RecognitionSettings.defaults();
		PracticeParameters parameters = PracticeParameters.of(settings);
		PracticeSession session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
				parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
		session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		session.setTechniqueJudge(new TechniqueJudge(catalog, true));
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);

		// the render starts at its first sample: half a second of silence lets the first attack be heard, as when a learner presses Start first
		float[] samples = new float[audio.sampleRate / 2 + audio.samples.length + 2 * audio.sampleRate];
		System.arraycopy(audio.samples, 0, samples, audio.sampleRate / 2, audio.samples.length);
		Outcome outcome = new Outcome();
		outcome.events = sequence.getEvents().size();
		String last = "";
		for( AnalysisFrame frame : OfflineAnalysis.analyze(samples, audio.sampleRate, settings, false, AttackCandidates.of(sequence)) ) {
			PracticeSnapshot snapshot = session.onFrame(frame);
			outcome.accepted = Math.max(outcome.accepted, snapshot.getAcceptedCount());
			double second = frame.getTimestampNanos() / 1e9 - 0.5;
			if( snapshot.getVerdict() == PracticeSession.Verdict.WRONG && !outcome.wrongSeen ) {
				outcome.wrongSeen = true;
				outcome.firstWrong = second;
			}
			StringBuilder now = new StringBuilder();
			for( TechniqueResult result : snapshot.getTechniqueResults() ) {
				now.append(result.getTechnique()).append('=').append(result.getVerdict());
				if( result.getMeasurements().containsKey("outcome") ) {
					now.append(String.format(Locale.ROOT, "(outcome %d)", Math.round(result.getMeasurements().get("outcome"))));
				}
				now.append(' ');
				outcome.heardClaimed |= (result.getVerdict() == TechniqueVerdict.DETECTED);
			}
			if( now.length() > 0 && !now.toString().equals(last) ) {
				outcome.techniques.append(String.format(Locale.ROOT, "  @%.2f s %s%n", second, now));
				last = now.toString();
			}
		}
		return outcome;
	}

	private static void report(String base, Outcome outcome) {
		System.out.println(String.format(Locale.ROOT, "%s: %d of %d notes accepted, %s%n%s", base, outcome.accepted, outcome.events,
				outcome.wrongSeen ? String.format(Locale.ROOT, "a wrong note shown first at %.2f s", outcome.firstWrong) : "no wrong note shown", outcome.techniques));
	}

	@Test
	public void theBendScoresAreFollowedOnTheRenderedAudioWithTheDetectorsUnverified() throws IOException {
		for( String base : new String[] { "1-bend-comme-ecrit", "2-bend-non-joue", "3-pre-bend", "4-ligne-de-technique" } ) {
			Outcome outcome = replay(base, TechniqueCatalog.defaults());
			report(base + " (detectors unverified)", outcome);
			assertEquals(outcome.events, outcome.accepted, base + ": notes accepted");
			assertFalse(outcome.heardClaimed, base + ": a technique was said to be heard while no detector is verified");
			if( !base.startsWith("4-") ) {
				// the fourth score also has a slide, which this render plays as a staircase: a wrong note is reported there (not asserted, see research R5 addendum)
				assertFalse(outcome.wrongSeen, base + ": a wrong note was shown on a bend");
			}
		}
	}

	@Test
	public void whatTheBendDetectorSaysOnTheRenderedAudioIsReported() throws IOException {
		TechniqueCatalog bendVerified = TechniqueCatalog.of(new BendDetector(BendDetector.Parameters.defaults()), true, "exploration on rendered audio");
		for( String base : new String[] { "1-bend-comme-ecrit", "2-bend-non-joue", "3-pre-bend" } ) {
			Outcome outcome = replay(base, bendVerified);
			report(base + " (bend detector counted as verified, for the report only)", outcome);
			assertEquals(outcome.events, outcome.accepted, base);
			assertFalse(outcome.wrongSeen, base);
		}
	}
}
