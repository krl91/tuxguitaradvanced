package app.tuxguitar.app.tools.custom.practice.calibration;

import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
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
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

/**
 * Says whether one recorded sound makes a Wait-mode practice session accept ONE event of the piece (spec 013, contract scoring-search). It is the
 * procedure of {@code DatasetJudge} of the test module, copied here (that class is not touched), with two differences: the settings of the candidate
 * are used for BOTH the offline analysis and the practice parameters, and the event comes from the real piece (its strings and frets), alone in the
 * session. Half a second of silence precedes the sound, as when a learner presses Start and then plays.
 */
public final class EventJudge {

	private final TGContext context;
	private final TGSong song;
	private final TGTrack track;
	private final PracticeSequence piece;

	public EventJudge(TGContext context, TGSong song, TGTrack track, PracticeSequence piece) {
		this.context = context;
		this.song = song;
		this.track = track;
		this.piece = piece;
	}

	public int eventCount() {
		return this.piece.getEvents().size();
	}

	/** True when the event is a chord (several notes together): it is judged with the polyphonic spectrum. */
	public boolean isChord(int eventIndex) {
		return this.piece.getEvents().get(eventIndex).getKind() != PracticeEvent.Kind.EXPECTED_NOTE;
	}

	/**
	 * What the analysis sees of the sound for a session expecting {@code eventIndex}: the frames of the offline analysis made with {@code settings}. They
	 * depend on the settings that reach the analysis (the level and attack values, the pitch engine) and on the event (its attack candidates, the spectrum
	 * of a chord), not on the others, so the caller may keep them for several candidates.
	 */
	public List<AnalysisFrame> analyse(float[] segment, int sampleRate, int eventIndex, RecognitionSettings settings) {
		PracticeSequence only = this.piece.onlyEvent(eventIndex);
		float[] samples = new float[segment.length + sampleRate / 2];
		System.arraycopy(segment, 0, samples, sampleRate / 2, segment.length);
		return OfflineAnalysis.analyze(samples, sampleRate, settings, isChord(eventIndex), AttackCandidates.of(only));
	}

	/** True when a session expecting {@code eventIndex} accepts the frames, with the practice parameters of {@code settings}. */
	public boolean accepts(List<AnalysisFrame> frames, int eventIndex, RecognitionSettings settings) {
		PracticeSequence only = this.piece.onlyEvent(eventIndex);
		PracticeTrackSelection selection = new PracticeTrackSelection(this.song, PracticeTrackCandidates.evaluate(this.song, this.track).getPreselected(),
				PracticeTunerContext.getInstance(this.context));
		selection.accept();
		PracticeParameters parameters = PracticeParameters.of(settings);
		PracticeSession session = new PracticeSession(selection, only, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
				parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
		session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		for (AnalysisFrame frame : frames) {
			if (session.onFrame(frame).getAcceptedCount() > 0) {
				return true;
			}
		}
		return false;
	}

	public boolean accepts(float[] segment, int sampleRate, int eventIndex, RecognitionSettings settings) {
		return accepts(analyse(segment, sampleRate, eventIndex, settings), eventIndex, settings);
	}
}
