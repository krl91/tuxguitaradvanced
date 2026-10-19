package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationScore;
import app.tuxguitar.app.tools.custom.practice.ui.TabDiagram.Mark;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.calibration.ProfilePersistence;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Spec 013 (T059, FR-021): the tablature picture in the window. With the sounds it is shown at the piece step (the first sound) and while listening (the
 * sound after the ones heard), changes at each sound heard and is gone at the next step; the written list of the piece step gives way to a count. Without
 * the sounds (the constructor the existing tests use) there is no picture and nothing changes.
 */
public class CalibrationWindowTabTest {

	private static final int RATE = 48000;
	private static final DeviceKey KEY = DeviceKey.of("Micro MacBook Pro", null, 0);
	private static final CalibrationWindow.Texts TEXTS = (key, arguments) -> key + (arguments.length == 0 ? "" : Arrays.toString(arguments));
	private static final List<List<Mark>> SOUNDS = Arrays.asList(Arrays.asList(new Mark(6, 0)), Arrays.asList(new Mark(5, 2)), Arrays.asList(new Mark(4, 0)),
			Arrays.asList(new Mark(6, 0), new Mark(5, 2), new Mark(4, 2)));

	/** delivers the signal one second at a time when told, so that the heard count can be watched */
	private static final class Port implements CalibrationController.ListeningPort {
		float[] signal;
		HopListener[] listeners;
		int offset;

		public void start(HopListener... listeners) {
			this.listeners = listeners;
			for (HopListener listener : listeners) {
				listener.configure(RATE);
			}
		}

		/** plays on until the sample position given */
		void playTo(int end) {
			float[] hop = new float[256];
			for (; this.offset + 256 <= end && this.offset + 256 <= this.signal.length; this.offset += 256) {
				System.arraycopy(this.signal, this.offset, hop, 0, 256);
				for (HopListener listener : this.listeners) {
					listener.hop(hop, 0, 256);
				}
			}
		}

		public void stop() {
		}

		public FrontEndState frontEnd() {
			return new FrontEndState(1.0, false);
		}

		public int sampleRate() {
			return RATE;
		}
	}

	private static final class Memory implements ProfilePersistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private final SwtContractToolkit toolkit = new SwtContractToolkit();
	private final Port port = new Port();
	private CalibrationWindow window;

	private void open(List<List<Mark>> sounds) {
		int[] onsets = { RATE, 3 * RATE, 5 * RATE, 7 * RATE };
		float[] signal = SyntheticSignals.repeatedPlucks(110.0, RATE, 10 * RATE, onsets, 7L);
		float max = 0f;
		for (float value : signal) {
			max = Math.max(max, Math.abs(value));
		}
		for (int i = 0; i < signal.length; i++) {
			signal[i] *= 0.5f / max;
		}
		this.port.signal = signal;
		CalibrationWindow.ControllerFactory factory = listener -> new CalibrationController(4, KEY, new ProfileLibrary(new Memory()), RecognitionSettings::defaults, this.port,
				new CalibrationController.TunerPort() {
					public boolean available() {
						return true;
					}

					public void open() {
					}

					public void setClosedListener(Runnable closed) {
					}
				}, (segments, rate) -> settings -> new CalibrationScore(4, 0, new RecognitionRate(4, 10, 0, 0)), Runnable::run, () -> 1L, listener);
		List<String> lines = Arrays.asList("a line", "b line", "c line", "d line");
		this.window = sounds == null ? new CalibrationWindow(this.toolkit.factory(), null, TEXTS, false, lines, factory, Runnable::run, state -> {
		}) : new CalibrationWindow(this.toolkit.factory(), null, TEXTS, false, lines, sounds, factory, Runnable::run, state -> {
		});
	}

	private void press(int button) {
		SwtContractToolkit.userSelects(this.window.button(button));
	}

	private static String text(Object widget) {
		Object text = SwtContractToolkit.state(widget).properties.get("Text");
		return text == null ? "" : text.toString();
	}

	private boolean pictureVisible() {
		return SwtContractToolkit.state(this.window.tabCanvas()).properties.get("Visible") == Boolean.TRUE;
	}

	@Test
	public void theFirstSoundIsShownAtThePieceStepWithTheCountInPlaceOfTheList() {
		open(SOUNDS);
		assertFalse(pictureVisible(), "not on the offer");
		press(0);
		press(0);
		press(0);
		assertEquals("practice.profile.piece.intro", text(this.window.bodyLabel()));
		assertTrue(pictureVisible());
		assertEquals(SOUNDS.get(0), this.window.shownSound());
		assertEquals("practice.profile.piece.count[4]", text(this.window.detailLabel()));
	}

	@Test
	public void whileListeningItShowsTheSoundAfterTheOnesHeardAndChangesAtEachSound() {
		open(SOUNDS);
		press(0);
		press(0);
		press(0);
		press(0); // the listening starts
		assertTrue(pictureVisible());
		assertEquals(SOUNDS.get(0), this.window.shownSound());
		this.port.playTo(3 * RATE - RATE / 2); // the first sound has been played
		assertEquals(1, this.window.controllerHeard());
		assertEquals(SOUNDS.get(1), this.window.shownSound());
		this.port.playTo(5 * RATE - RATE / 2);
		assertEquals(SOUNDS.get(2), this.window.shownSound());
		this.port.playTo(7 * RATE - RATE / 2);
		assertEquals(SOUNDS.get(3), this.window.shownSound(), "the chord of the end");
		this.port.playTo(10 * RATE);
		assertFalse(pictureVisible(), "gone at the quality question");
	}

	@Test
	public void withoutTheSoundsThereIsNoPictureAndTheListStaysAsBefore() {
		open(null);
		press(0);
		press(0);
		press(0);
		assertFalse(pictureVisible());
		assertTrue(text(this.window.detailLabel()).contains("1. a line"), text(this.window.detailLabel()));
	}
}
