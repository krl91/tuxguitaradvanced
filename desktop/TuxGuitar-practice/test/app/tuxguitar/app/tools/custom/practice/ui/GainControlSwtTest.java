package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.frontend.GainScale;
import app.tuxguitar.audio.engine.frontend.GainSuggestion;
import app.tuxguitar.audio.engine.frontend.InputGain;
import app.tuxguitar.audio.engine.frontend.InputWarning;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the input gain control (spec 002, tasks T139): the real widgets, the learner's
 * moves and clicks sent as native events, against a stub backend (no audio engine, no hardware).
 * Runs only with the profile {@code swt-gui-tests} (see {@link RecognitionSettingsSwtTest}).
 */
@Tag("swt-gui")
public class GainControlSwtTest {

	private static final int SWT_SELECTION = 13;

	private static final class StubBackend implements GainBackend {
		double gain = 1.0;
		boolean suggestions = true;
		GainSuggestion suggestion;
		InputWarning warning = InputWarning.NONE;
		Runnable settingsListener;

		public double getGain() {
			return this.gain;
		}

		public double setGain(double multiplier) {
			this.gain = Math.max(InputGain.MIN_MULTIPLIER, Math.min(InputGain.MAX_MULTIPLIER, multiplier));
			return this.gain;
		}

		public void resetGain() {
			this.gain = 1.0;
		}

		public Optional<GainSuggestion> suggest() {
			return Optional.ofNullable(this.suggestion);
		}

		public InputWarning warning(AnalysisFrame latest) {
			return this.warning;
		}

		public boolean suggestionsEnabled() {
			return this.suggestions;
		}

		public Runnable onSettingsChanged(Runnable listener) {
			this.settingsListener = listener;
			return () -> this.settingsListener = null;
		}
	}

	@Test
	public void theSliderTheResetAndTheSuggestionWorkAsTheLearnerUsesThem() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			UIWindow window = factory.createWindow();
			window.setLayout(new UITableLayout());
			UIPanel root = factory.createPanel(window, false);
			root.setLayout(new UITableLayout());
			((UITableLayout) window.getLayout()).set(root, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
			StubBackend backend = new StubBackend();
			GainControl control = new GainControl(new app.tuxguitar.util.TGContext(), backend, factory, root);
			((UITableLayout) root.getLayout()).set(control.getControl(), 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
			window.pack();
			window.open();
			flush(display);

			Object slider = nativeOf(find(control, "slider"));
			Object valueLabel = nativeOf(find(control, "valueLabel"));
			assertEquals(24, nativeInt(slider, "getMaximum"));
			assertEquals(GainScale.NEUTRAL_POSITION, nativeInt(slider, "getSelection"));
			assertEquals("0 dB", nativeString(valueLabel, "getText"));

			// the learner drags to +6 dB
			userMoves(display, slider, GainScale.NEUTRAL_POSITION + 6);
			assertEquals(InputGain.fromDb(6.0), backend.gain, 1e-9);
			assertEquals("+6 dB", nativeString(valueLabel, "getText"));

			// reset
			click(display, nativeOf(find(control, "resetButton")));
			assertEquals(1.0, backend.gain, 0.0);
			assertEquals(GainScale.NEUTRAL_POSITION, nativeInt(slider, "getSelection"));
			assertEquals("0 dB", nativeString(valueLabel, "getText"));

			// suggestion: asked, shown with its text, applied only on Apply
			backend.suggestion = new GainSuggestion(InputGain.fromDb(12.0), GainSuggestion.Reason.TOO_LOW);
			Object suggestButton = nativeOf(find(control, "suggestButton"));
			Object applyButton = nativeOf(find(control, "applyButton"));
			assertTrue(nativeBoolean(suggestButton, "isVisible"));
			assertFalse(nativeBoolean(applyButton, "isEnabled"));
			click(display, suggestButton);
			assertEquals(1.0, backend.gain, 0.0, "a suggestion never changes the gain by itself");
			assertTrue(nativeString(nativeOf(find(control, "suggestionLabel")), "getText").contains("practice.gain.suggestion.TOO_LOW"));
			assertTrue(nativeBoolean(applyButton, "isEnabled"));
			click(display, applyButton);
			assertEquals(InputGain.fromDb(12.0), backend.gain, 1e-9);
			assertEquals("+12 dB", nativeString(valueLabel, "getText"));

			// a suggestion that says "nothing to change" cannot be applied
			backend.suggestion = new GainSuggestion(backend.gain, GainSuggestion.Reason.OK);
			click(display, suggestButton);
			assertFalse(nativeBoolean(applyButton, "isEnabled"));

			// switching the suggestion off hides its controls
			backend.suggestions = false;
			control.refresh();
			flush(display);
			assertFalse(nativeBoolean(suggestButton, "isVisible"));
			assertFalse(nativeBoolean(applyButton, "isVisible"));

			// warning
			backend.warning = InputWarning.BOOST_AMPLIFIES_NOISE;
			control.showWarning(null);
			assertTrue(nativeString(nativeOf(find(control, "warningLabel")), "getText").contains("practice.gain.warning.BOOST_AMPLIFIES_NOISE"));
			backend.warning = InputWarning.NONE;
			control.showWarning(null);
			assertEquals("", nativeString(nativeOf(find(control, "warningLabel")), "getText"));

			// an input change resets the gain in the engine: refresh shows it
			backend.gain = 1.0;
			control.refresh();
			assertEquals(GainScale.NEUTRAL_POSITION, nativeInt(slider, "getSelection"));
			window.dispose();
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	private static Object find(GainControl control, String field) throws Exception {
		java.lang.reflect.Field f = GainControl.class.getDeclaredField(field);
		f.setAccessible(true);
		return f.get(control);
	}

	private static Object nativeOf(Object uiControl) throws Exception {
		return uiControl.getClass().getMethod("getControl").invoke(uiControl);
	}

	private static void userMoves(Object display, Object nativeScale, int position) throws Exception {
		nativeScale.getClass().getMethod("setSelection", int.class).invoke(nativeScale, position);
		notifySelection(display, nativeScale);
	}

	private static void click(Object display, Object nativeButton) throws Exception {
		notifySelection(display, nativeButton);
	}

	private static void notifySelection(Object display, Object nativeWidget) throws Exception {
		Class<?> eventClass = Class.forName("org.eclipse.swt.widgets.Event");
		Method notify = Class.forName("org.eclipse.swt.widgets.Widget").getMethod("notifyListeners", int.class, eventClass);
		notify.invoke(nativeWidget, SWT_SELECTION, eventClass.getConstructor().newInstance());
		flush(display);
	}

	private static int nativeInt(Object nativeControl, String method) throws Exception {
		return (Integer) nativeControl.getClass().getMethod(method).invoke(nativeControl);
	}

	private static boolean nativeBoolean(Object nativeControl, String method) throws Exception {
		return (Boolean) Class.forName("org.eclipse.swt.widgets.Control").getMethod(method).invoke(nativeControl);
	}

	private static String nativeString(Object nativeControl, String method) throws Exception {
		return (String) nativeControl.getClass().getMethod(method).invoke(nativeControl);
	}

	private static void flush(Object display) throws Exception {
		Method readAndDispatch = display.getClass().getMethod("readAndDispatch");
		for( int i = 0 ; i < 50 && (Boolean) readAndDispatch.invoke(display) ; i ++ ) {
			// drain the pending events
		}
	}
}
