package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationFlow;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationPiece;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationScorer;
import app.tuxguitar.app.tools.custom.practice.calibration.EngineListeningPort;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.ProfileActivator;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * Opens the calibration of the selected input (spec 013): builds the one window with the real parts, the engine port, the tuner of the practice window, the
 * shipped score and the scorer on the recording. Nothing here decides anything; the window and the controller do.
 */
final class CalibrationLauncher {

	private CalibrationLauncher() {
	}

	static final CalibrationWindow.Texts TEXTS = (key, arguments) -> arguments.length == 0 ? TuxGuitar.getProperty(key) : TuxGuitar.getProperty(key, arguments);

	/** One line per sound of the piece, as the player reads it: "E2 (string 6, fret open)" or "chord E2 + B2 + E3". */
	static List<String> pieceLines(CalibrationPiece piece, CalibrationWindow.Texts texts) {
		List<String> lines = new ArrayList<String>();
		for (int event = 0; event < piece.eventCount(); event++) {
			List<CalibrationPiece.PieceNote> notes = piece.eventNotes(event);
			if (piece.isChord(event)) {
				StringBuilder names = new StringBuilder();
				for (CalibrationPiece.PieceNote note : notes) {
					names.append(names.length() == 0 ? "" : " + ").append(note.getName());
				}
				lines.add(texts.get("practice.profile.piece.chord", names.toString()));
			} else {
				CalibrationPiece.PieceNote note = notes.get(0);
				String fret = note.getFret() == 0 ? texts.get("practice.profile.piece.open") : Integer.toString(note.getFret());
				lines.add(texts.get("practice.profile.piece.note", note.getName(), Integer.toString(note.getString()), fret));
			}
		}
		return lines;
	}

	static void open(TGContext context, AudioEngine engine, UIFactory factory, Supplier<UIWindow> mainWindow, DeviceKey key, boolean microphone,
			Consumer<CalibrationFlow.State> onFinished) {
		final CalibrationPiece piece = CalibrationPiece.load();
		final ProfileLibrary library = ProfileLibrary.getInstance(context);
		final EngineListeningPort port = new EngineListeningPort(engine, task -> TGThreadManager.getInstance(context).start(task), () -> {
			ProfileActivator activator = ProfileActivator.of(context);
			return activator == null ? 0 : activator.getSampleRate();
		});
		final PracticeTunerOpener opener = new PracticeTunerOpener(context);
		final CalibrationController.TunerPort tuner = new CalibrationController.TunerPort() {
			public boolean available() {
				return opener.available();
			}

			public void open() {
				opener.open();
			}

			public void setClosedListener(Runnable listener) {
				opener.setClosedListener(listener);
			}
		};
		final RecognitionSettingsStore store = RecognitionSettingsStore.getInstance(context);
		List<List<TabDiagram.Mark>> sounds = new ArrayList<List<TabDiagram.Mark>>();
		for (int event = 0; event < piece.eventCount(); event++) {
			sounds.add(TabDiagram.marks(piece.eventNotes(event)));
		}
		CalibrationWindow window = new CalibrationWindow(factory, mainWindow.get(), TEXTS, microphone, pieceLines(piece, TEXTS), sounds, listener -> {
			CalibrationController controller = new CalibrationController(piece.eventCount(), key, library, store::getDefault, port, tuner,
					(segments, rate) -> CalibrationScorer.forRecording(piece.judge(context), segments, rate)::score,
					task -> TGThreadManager.getInstance(context).start(task), System::currentTimeMillis, listener);
			controller.setDiagnosticSink(new DiagnosticCopy());
			port.setDeviceLostListener(controller::deviceLost);
			return controller;
		}, task -> TGSynchronizer.getInstance(context).executeLater(task), onFinished);
		window.open();
	}

	static boolean isMicrophone(AudioInputDescriptor input) {
		return input != null && (input.getKind() == AudioInputDescriptor.Kind.BUILT_IN_MICROPHONE || input.getKind() == AudioInputDescriptor.Kind.MICROPHONE);
	}
}
