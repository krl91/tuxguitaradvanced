package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.function.Function;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.backend.RecognitionEngineNotice;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException.Reason;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the notice of the practice window (spec 023, T062, FR-006, data-model "Recognition engine notice"): when the native engine could not
 * start, a line of the window says why and that the practice goes on with Java; each reason is shown once per session, a refused permission shows
 * nothing (today's message comes instead), and nothing opens over the window or waits for the learner. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class RecognitionEngineNoticeSwtTest {

	@Test
	public void theNoticeIsALineOfTheWindowShownOncePerReasonPerSession() throws Exception {
		Properties french = load();
		Function<String, String> texts = key -> {
			String value = french.getProperty(key);
			if( value == null ) {
				throw new AssertionError("missing text: " + key);
			}
			return value;
		};
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			RecognitionEngineNoticeView.Session session = new RecognitionEngineNoticeView.Session();

			UIWindow window = factory.createWindow();
			RecognitionEngineNoticeView view = open(factory, window, texts, session);
			flush(display);
			assertEquals("", view.getText(), "nothing before a fallback");
			assertTrue(!view.isShowing());

			long started = System.nanoTime();
			view.show(new RecognitionEngineNotice(Reason.DEVICE_NOT_FOUND, "the input \"X\" is not connected"));
			long micros = (System.nanoTime() - started) / 1000;
			flush(display);
			String shown = view.getText();
			assertEquals(french.getProperty("recognition.engine.notice.device-not-found"), shown);
			assertTrue(shown.contains("Java"), "the practice goes on with Java: " + shown);
			assertTrue(view.isShowing());
			assertTrue(micros < 100_000, "the notice waits for nobody: " + micros + " µs");
			assertEquals(1, ((Object[]) displayClass.getMethod("getShells").invoke(display)).length, "nothing opens over the window");

			// the same reason again in the session: not shown again
			view.clear();
			view.show(new RecognitionEngineNotice(Reason.DEVICE_NOT_FOUND, "again"));
			flush(display);
			assertEquals("", view.getText());

			// a refused permission is not a fallback: nothing here
			view.show(new RecognitionEngineNotice(Reason.PERMISSION_DENIED, "the user said no"));
			flush(display);
			assertEquals("", view.getText());
			window.dispose();

			// the window opened again in the same session: an old reason stays quiet, a new one is shown
			UIWindow again = factory.createWindow();
			RecognitionEngineNoticeView second = open(factory, again, texts, session);
			second.show(new RecognitionEngineNotice(Reason.DEVICE_NOT_FOUND, "still gone"));
			flush(display);
			assertEquals("", second.getText());
			second.show(new RecognitionEngineNotice(Reason.STREAM_FAILED, "the input stopped"));
			flush(display);
			assertEquals(french.getProperty("recognition.engine.notice.stream-failed"), second.getText());
			again.dispose();
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	private static RecognitionEngineNoticeView open(UIFactory factory, UIWindow window, Function<String, String> texts, RecognitionEngineNoticeView.Session session) {
		UITableLayout layout = new UITableLayout();
		window.setLayout(layout);
		UIPanel root = factory.createPanel(window, false);
		root.setLayout(new UITableLayout());
		layout.set(root, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
		RecognitionEngineNoticeView view = new RecognitionEngineNoticeView(factory, root, (UITableLayout) root.getLayout(), 1, texts, session, window::pack);
		window.pack();
		window.open();
		return view;
	}

	private static void flush(Object display) throws Exception {
		Method read = display.getClass().getMethod("readAndDispatch");
		while( (Boolean) read.invoke(display) ) {
			// process pending events
		}
	}

	private static Properties load() throws Exception {
		Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(Paths.get("share/lang/messages_fr.properties")), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}
}
