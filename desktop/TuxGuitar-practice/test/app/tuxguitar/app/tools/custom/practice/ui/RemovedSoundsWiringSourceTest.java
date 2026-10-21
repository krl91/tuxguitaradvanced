package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Spec 014 (T024, FR-015): the sounds a level removed are heard in the backing for the length of the Arcade run, and only then; read from the source of the
 * practice window (the window is built by no test). The sequence of the removed sounds is made once per fresh start with the level; the player is made and started
 * where the run is started, stopped where the Arcade windows are torn down (the end of a run, a restart, a replay); the environment that cannot play them gives null.
 */
public class RemovedSoundsWiringSourceTest {

	private static String read(String name) throws IOException {
		for (String root : new String[] { "src", "TuxGuitar-practice/src", "desktop/TuxGuitar-practice/src" }) {
			Path path = Paths.get(root, "app/tuxguitar/app/tools/custom/practice/ui/" + name);
			if (Files.exists(path)) {
				return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			}
		}
		throw new IOException(name + " not found");
	}

	private static String body(String text, String declaration) {
		int at = text.indexOf(declaration);
		assertTrue(at >= 0, declaration + " exists");
		int open = text.indexOf('{', at);
		int depth = 0;
		for (int i = open; i < text.length(); i++) {
			if (text.charAt(i) == '{') {
				depth++;
			} else if (text.charAt(i) == '}' && --depth == 0) {
				return text.substring(open, i + 1);
			}
		}
		throw new AssertionError("unbalanced " + declaration);
	}

	@Test
	public void theRemovedSequenceIsMadeOnTheFreshStartAndThePlayerIsStartedWithTheRunAndStoppedWithTheArcadeWindows() throws IOException {
		String window = read("PracticeWindow.java");
		String fresh = body(window, "private void startSessionNow(");
		assertTrue(fresh.contains("this.arcadeRemovedSequence =") && fresh.contains(".removedSequence()"), "made with the level, on the whole piece");
		String start = body(window, "private void startArcade(");
		assertTrue(start.contains("RemovedSoundsPlayer.forRun(") && start.contains("this.removedSounds.start()"), "made and started with the run");
		assertTrue(start.contains("this.followAdapter.removedSoundsOutput()") && start.contains("this.followAdapter.player().clock()"), "on the output and the clock of the run");
		String teardown = body(window, "private void teardownArcade()");
		assertTrue(teardown.contains("this.removedSounds.stop()"), "stopped where the Arcade windows are torn down");
		for (String method : new String[] { "private void replayArcade(", "private void restartArcade()", "private void restartArcadeFromStart()" }) {
			assertFalse(body(window, method).contains("removedSequence"), method + " does not remake the sequence: the level is kept");
		}
	}

	@Test
	public void theEnvironmentThatCannotPlayThemGivesNullAndTheApplicationOneSendsThroughThePlayersOutput() throws IOException {
		app.tuxguitar.app.tools.custom.practice.ui.PracticeHost.RunEnvironment cannot = new app.tuxguitar.app.tools.custom.practice.ui.PracticeHost.RunEnvironment() {
			public app.tuxguitar.app.tools.custom.practice.session.FollowPlayer player() {
				return null;
			}

			public long wallNanosOf(app.tuxguitar.audio.engine.analysis.AnalysisFrame frame) {
				return 0;
			}

			public boolean captureReady() {
				return false;
			}

			public double lagSpreadMillis() {
				return 0;
			}

			public app.tuxguitar.app.tools.custom.practice.session.AudioRoute route() {
				return null;
			}

			public app.tuxguitar.app.tools.custom.practice.session.RouteCalibrations calibrations() {
				return null;
			}

			public app.tuxguitar.audio.engine.input.InputAssessment assessment() {
				return null;
			}

			public app.tuxguitar.app.tools.custom.practice.session.CalibrationRun newCalibration() {
				return null;
			}

			public boolean playerBusy() {
				return false;
			}

			public void begin(app.tuxguitar.app.tools.custom.practice.session.CalibrationRun run, Runnable changed) {
			}

			public void end(app.tuxguitar.app.tools.custom.practice.session.CalibrationRun run) {
			}
		};
		assertNull(cannot.removedSoundsOutput());
		String adapter = read("EngineFollowAdapter.java");
		assertTrue(adapter.contains("sendNoteOn(channel, key, velocity, -1, false)") && adapter.contains("sendNoteOff(channel, key, 0, -1, false)"), "straight to the output transmitter of the player");
	}
}
