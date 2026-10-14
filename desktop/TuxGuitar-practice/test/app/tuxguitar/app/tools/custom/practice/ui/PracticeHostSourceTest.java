package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * The seam of spec 011 (T007), checked on the source. <strong>Its limit, stated:</strong> it proves that the calls of the practice window to the application moved to
 * the default host one for one and that none is left in the window; it does <em>not</em> prove that the behaviour is equal. That is proved by the existing suites, by the
 * harness once it exists, and by the owner on the built application (task T010).
 */
public class PracticeHostSourceTest {

	private static String read(String name) throws IOException {
		for (String root : new String[] { "src", "TuxGuitar-practice/src", "desktop/TuxGuitar-practice/src" }) {
			Path path = Paths.get(root, "app/tuxguitar/app/tools/custom/practice/ui/" + name);
			if (Files.exists(path)) {
				return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			}
		}
		throw new IOException(name + " not found");
	}

	private static int count(String text, String word) {
		int count = 0;
		for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + word.length())) {
			count++;
		}
		return count;
	}

	@Test
	public void thePracticeWindowNoLongerReachesTheApplicationNorSleepsNorReadsTheClockItself() throws IOException {
		String window = read("PracticeWindow.java");
		for (String forbidden : new String[] { "TGApplication.getInstance(", "TGWindow.getInstance(", "TuxGuitar.getInstance(", "TGDialogUtil.openDialog(", "AudioEngine.getInstance(",
				"new EngineFollowAdapter(", "new Thread(", "Thread.sleep(", "plugin.windowStateChanged(", "plugin.setCommandSink(", "System.nanoTime(" }) {
			assertFalse(window.contains(forbidden), "PracticeWindow must reach the application through the host, not by " + forbidden);
		}
		assertTrue(window.contains("PracticeWindow(TGContext context, PracticePlugin plugin)"), "the public constructor the plugin calls is unchanged");
	}

	@Test
	public void theDefaultHostMakesEachCallOnceAndTheSleepingThreadOnceAsItWasInTheWindow() throws IOException {
		String host = read("AppPracticeHost.java");
		for (String call : new String[] { "TGApplication.getInstance(", "TGWindow.getInstance(", "TuxGuitar.getInstance().getIconManager()", "TuxGuitar.getInstance().getDocumentManager()",
				"TuxGuitar.getInstance().getTablatureEditor()", "TGDialogUtil.openDialog(", "AudioEngine.getInstance(", "new EngineFollowAdapter(", "new Thread(", "Thread.sleep(",
				"plugin.windowStateChanged(", "plugin.setCommandSink(", "System.nanoTime(" }) {
			assertEquals(1, count(host, call), "the default host contains " + call + " exactly once");
		}
		assertTrue(host.contains("setDaemon(true)") && host.contains("TGSynchronizer.getInstance("), "the delayed task is a daemon thread that posts to the synchronizer, as before");
	}

	@Test
	public void theStartGateTakesTheTimeFromTheHostNotFromTheSystem() throws IOException {
		assertFalse(read("PreRunStartGate.java").contains("System::nanoTime"), "the ten seconds of the start window are driven by the host's clock");
	}
}
