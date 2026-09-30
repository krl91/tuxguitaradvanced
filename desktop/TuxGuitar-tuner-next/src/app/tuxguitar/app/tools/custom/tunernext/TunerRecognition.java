package app.tuxguitar.app.tools.custom.tunernext;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;

/**
 * What the tuner shows for one analysis frame, with the saved audio-recognition settings (T102):
 * minimum certainty, in-tune range and "keep the last note". Used by {@link TunerWindow} on the
 * UI thread; the interpreter is rebuilt only when the settings change.
 */
public final class TunerRecognition {

	private final RecognitionSettingsStore store;
	private final TunerHold hold = new TunerHold();
	private RecognitionSettings interpreterSettings;
	private NoteInterpreter interpreter;

	public TunerRecognition(RecognitionSettingsStore store) {
		this.store = store;
	}

	/**
	 * @param targets the guided mode's target pitches, or null in chromatic mode
	 * @param selectedString the chosen target string, 0 for automatic
	 */
	public TunerHold.Shown show(AnalysisFrame frame, InputAssessment assessment, int[] targets, int selectedString) {
		RecognitionSettings settings = this.store.get();
		PitchReading reading = interpreter(settings).interpret(frame.getF0Hz(), frame.getConfidence(), assessment);
		return this.hold.update(TunerDisplay.of(frame, reading, targets, selectedString, settings.get(RecognitionParameters.TUNER_IN_TUNE)),
				settings.get(RecognitionParameters.TUNER_HOLD));
	}

	/** Forgets the held reading (mode, target or input changed). */
	public void clearHold() {
		this.hold.clear();
	}

	/** The interpreter in use (its certainty threshold is the saved setting). */
	public NoteInterpreter interpreter() {
		return interpreter(this.store.get());
	}

	private NoteInterpreter interpreter(RecognitionSettings settings) {
		if( settings != this.interpreterSettings ) {
			this.interpreter = new NoteInterpreter(settings.get(RecognitionParameters.TUNER_CONFIDENCE));
			this.interpreterSettings = settings;
		}
		return this.interpreter;
	}
}
