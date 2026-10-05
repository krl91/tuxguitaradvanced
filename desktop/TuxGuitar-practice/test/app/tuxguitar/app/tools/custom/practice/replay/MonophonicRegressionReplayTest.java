package app.tuxguitar.app.tools.custom.practice.replay;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.ChordMatcher;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * The attack-difference rule must keep what the earlier rules did on the real single-pluck
 * built-in-microphone clips (spec 004 SC-004): every clip recognised before is still recognised, and
 * no wrong note (a semitone or a whole tone away) is accepted that was refused before.
 */
public class MonophonicRegressionReplayTest {

	private static final int[] OPEN_STRINGS = { 64, 59, 55, 50, 45, 40 };

	private static Path clips() {
		return Paths.get("../TuxGuitar-audio-engine/src/test/resources/benchmark");
	}

	/** Builds the one-note song whose note has the given pitch, on the highest string that can play it. */
	private static SongFixture songWithPitch(int midi) {
		SongFixture fixture = new SongFixture(1);
		int string = 1;
		for( int s = 1 ; s <= 6 ; s ++ ) {
			if( OPEN_STRINGS[s - 1] <= midi ) {
				string = s;
				break;
			}
		}
		fixture.quarter(0, 0, string, midi - OPEN_STRINGS[string - 1]);
		return fixture;
	}

	private static boolean accepts(List<AnalysisFrame> frames, int midi, boolean polyphonic) {
		SongFixture fixture = songWithPitch(midi);
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeParameters parameters = PracticeParameters.of(RecognitionSettings.defaults());
		PracticeSession session = new PracticeSession(selection, fixture.sequence(), parameters.noteInterpreter(), parameters.tolerance(),
				parameters.stabilityNanos(), parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
		if( polyphonic ) {
			session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		}
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		for( AnalysisFrame frame : frames ) {
			if( session.onFrame(frame).getAcceptedCount() > 0 ) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void singlePluckClipsKeepTheirRecognitionAndRefuseTheSameWrongNotes() throws IOException {
		Path manifest = clips().resolve("manifest.csv");
		assumeTrue(Files.isRegularFile(manifest), "benchmark clips not found: " + manifest.toAbsolutePath());
		List<String> lost = new ArrayList<String>();
		List<String> gained = new ArrayList<String>();
		List<String> newFalse = new ArrayList<String>();
		int clipsCount = 0;
		int before = 0;
		int after = 0;
		int falseBefore = 0;
		int falseAfter = 0;
		for( String line : Files.readAllLines(manifest) ) {
			String[] cells = line.split(",");
			// the first columns (file ... fret) never contain commas; the role is one of the last ones
			if( cells.length < 8 || !line.contains(",reference,") || cells[5].isEmpty() ) {
				continue;
			}
			int midi = Integer.parseInt(cells[5]);
			RecordedAudio audio = RecordedAudio.load(clips().resolve(cells[0]));
			assumeTrue(audio != null, "clip not readable: " + cells[0]);
			int[] candidates = { midi - 12, midi - 2, midi - 1, midi, midi + 1, midi + 2, midi + 12 };
			List<AnalysisFrame> frames = OfflineAnalysis.analyze(audio.samples, audio.sampleRate, RecognitionSettings.defaults(), false, candidates);
			clipsCount ++;
			boolean oldRight = accepts(frames, midi, false);
			boolean newRight = accepts(frames, midi, true);
			before += (oldRight ? 1 : 0);
			after += (newRight ? 1 : 0);
			if( oldRight && !newRight ) {
				lost.add(cells[0]);
			}
			if( !oldRight && newRight ) {
				gained.add(cells[0]);
			}
			for( int wrong : new int[] { midi - 2, midi - 1, midi + 1, midi + 2 } ) {
				boolean oldWrong = accepts(frames, wrong, false);
				boolean newWrong = accepts(frames, wrong, true);
				falseBefore += (oldWrong ? 1 : 0);
				falseAfter += (newWrong ? 1 : 0);
				if( newWrong && !oldWrong ) {
					newFalse.add(cells[0] + " expecting " + wrong);
				}
			}
		}
		String report = String.format("single-pluck clips: %d; recognised before %d, after %d (lost %s, gained %s); wrong notes accepted before %d, after %d (new %s)",
				clipsCount, before, after, lost, gained, falseBefore, falseAfter, newFalse);
		System.out.println(report);
		assertTrue(clipsCount > 0, report);
		assertTrue(lost.isEmpty(), report);
		assertTrue(newFalse.isEmpty(), report);
	}
}
