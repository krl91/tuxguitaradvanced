package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationPiece;

/**
 * Spec 013 (T042): the dot and the calibration buttons are opt-in. The panel is built in two places and only the Input tab asks for them; the four-argument
 * constructor, used by the Configuration page, delegates with no main window and so builds none of them. Source checks (the panel needs the real input
 * catalog), and the lines of the piece as the player reads them.
 */
public class InputSelectorPanelOptInTest {

	private static final Path UI = Paths.get("src/app/tuxguitar/app/tools/custom/practice/ui");

	private static String source(String name) throws IOException {
		return new String(Files.readAllBytes(UI.resolve(name)), StandardCharsets.UTF_8);
	}

	@Test
	public void theFourArgumentConstructorDelegatesWithNoWindowAndTheDotIsBuiltOnlyWithOne() throws IOException {
		String panel = source("InputSelectorPanel.java");
		assertTrue(panel.contains("this(context, engine, uiFactory, parent, null);"));
		assertTrue(panel.contains("if( mainWindow != null ) {\n\t\t\tcreateProfileDot("));
		assertEquals(1, panel.split("createProfileDot\\(uiFactory", -1).length - 1, "built from one place only");
	}

	@Test
	public void onlyTheInputTabAsksForTheDot() throws IOException {
		assertTrue(source("PracticeWindow.java").contains("new InputSelectorPanel(this.context, this.engine, uiFactory, inputPanel, () -> this.host.mainWindow());"));
		String option = source("AudioRecognitionOption.java");
		int at = option.indexOf("new InputSelectorPanel(");
		String call = option.substring(at, option.indexOf(';', at));
		assertEquals(3, call.split(",").length - 1 + 0, "four arguments: " + call);
	}

	@Test
	public void thePieceIsShownOneLinePerSoundWithTheNotesOfTheChords() {
		CalibrationPiece piece = CalibrationPiece.load();
		List<String> lines = CalibrationLauncher.pieceLines(piece, (key, arguments) -> key + java.util.Arrays.toString(arguments));
		assertEquals(piece.eventCount(), lines.size());
		int chords = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (piece.isChord(i)) {
				chords++;
				assertTrue(lines.get(i).startsWith("practice.profile.piece.chord["), lines.get(i));
				assertTrue(lines.get(i).contains(" + "), "the notes of the chord: " + lines.get(i));
			} else {
				assertTrue(lines.get(i).startsWith("practice.profile.piece.note["), lines.get(i));
			}
		}
		assertEquals(3, chords);
	}
}
