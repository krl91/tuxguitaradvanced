package app.tuxguitar.app.tools.custom.practice.functional;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
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
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.util.TGContext;

/**
 * Runs a score built with {@link SongFixture} through the whole practice chain over a signal (the offline analysis, the same stages as the live
 * engine: see the engine's LiveEngineEquivalenceTest), with the technique judge, and says what was accepted and what the technique line said.
 */
final class FunctionalRun {

	static final int RATE = 48000;

	final int accepted;
	/** Seconds from the start of the signal at which the n-th note was accepted. */
	final List<Double> acceptedAt = new ArrayList<Double>();
	final List<TechniqueResult> techniqueResults = new ArrayList<TechniqueResult>();
	/** Events Practice judges (notes and chords); the others (bend, harmonic...) it passes automatically. */
	final int eventCount;
	final int autoPassed;

	private FunctionalRun(int accepted, int eventCount, int autoPassed) {
		this.accepted = accepted;
		this.eventCount = eventCount;
		this.autoPassed = autoPassed;
	}

	/** Half a second of silence, the signal, and two seconds of silence (for the technique verdict to be given). */
	static FunctionalRun of(SongFixture fixture, float[] signal) {
		PracticeSequence sequence = fixture.sequence();
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song, PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(),
				PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		RecognitionSettings settings = RecognitionSettings.defaults();
		PracticeParameters parameters = PracticeParameters.of(settings);
		PracticeSession session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
				parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
		session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		session.setTechniqueJudge(new TechniqueJudge(TechniqueCatalog.defaults(), true));
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		float[] samples = new float[RATE / 2 + signal.length + 2 * RATE];
		System.arraycopy(signal, 0, samples, RATE / 2, signal.length);
		FunctionalRun run = null;
		List<Double> times = new ArrayList<Double>();
		List<TechniqueResult> results = new ArrayList<TechniqueResult>();
		int accepted = 0;
		for( AnalysisFrame frame : OfflineAnalysis.analyze(samples, RATE, settings, false, AttackCandidates.of(sequence)) ) {
			PracticeSnapshot snapshot = session.onFrame(frame);
			while( accepted < snapshot.getAcceptedCount() ) {
				accepted ++;
				times.add(frame.getTimestampNanos() / 1e9 - 0.5);
			}
			if( !snapshot.getTechniqueResults().isEmpty() ) {
				results.clear();
				results.addAll(snapshot.getTechniqueResults());
			}
		}
		int auto = 0;
		for( app.tuxguitar.app.tools.custom.practice.model.PracticeEvent event : sequence.getEvents() ) {
			auto += (event.getKind() == app.tuxguitar.app.tools.custom.practice.model.PracticeEvent.Kind.AUTO_PASS ? 1 : 0);
		}
		run = new FunctionalRun(accepted, sequence.getEvents().size() - auto, auto);
		run.acceptedAt.addAll(times);
		run.techniqueResults.addAll(results);
		return run;
	}
}
