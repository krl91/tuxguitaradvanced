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
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.calibration.ProfilePersistence;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Spec 013 (T054, FR-020): the check box "Keep a copy of this recording for diagnosis" of the window. It shows in the quality step only, is unticked every
 * time that step is entered, passes its state to the controller when a button is pressed, and the path (or the failure) is added to the text of the next step.
 */
public class CalibrationWindowDiagnosticTest {

	private static final int RATE = 48000;
	private static final DeviceKey KEY = DeviceKey.of("Micro MacBook Pro", null, 0);
	private static final CalibrationWindow.Texts TEXTS = (key, arguments) -> key + (arguments.length == 0 ? "" : Arrays.toString(arguments));

	private static final class Port implements CalibrationController.ListeningPort {
		float[] signal;
		boolean stop;

		public void start(HopListener... listeners) {
			this.stop = false;
			for (HopListener listener : listeners) {
				listener.configure(RATE);
			}
			float[] hop = new float[256];
			for (int offset = 0; offset + 256 <= this.signal.length && !this.stop; offset += 256) {
				System.arraycopy(this.signal, offset, hop, 0, 256);
				for (HopListener listener : listeners) {
					listener.hop(hop, 0, 256);
				}
			}
		}

		public void stop() {
			this.stop = true;
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
	private final List<float[]> written = new ArrayList<float[]>();
	private String path = "/home/x/tuxguitar-calibration-takes/calibration-1.wav";
	private CalibrationWindow window;

	private void open() {
		Port port = new Port();
		int[] onsets = { RATE, 3 * RATE, 5 * RATE, 7 * RATE };
		float[] signal = SyntheticSignals.repeatedPlucks(110.0, RATE, 10 * RATE, onsets, 7L);
		float max = 0f;
		for (float value : signal) {
			max = Math.max(max, Math.abs(value));
		}
		for (int i = 0; i < signal.length; i++) {
			signal[i] *= 0.5f / max;
		}
		port.signal = signal;
		this.window = new CalibrationWindow(this.toolkit.factory(), null, TEXTS, false, Arrays.asList("a", "b", "c", "d"), listener -> {
			CalibrationController controller = new CalibrationController(4, KEY, new ProfileLibrary(new Memory()), RecognitionSettings::defaults, port,
					new CalibrationController.TunerPort() {
						public boolean available() {
							return true;
						}

						public void open() {
						}

						public void setClosedListener(Runnable closed) {
						}
					}, (segments, rate) -> settings -> new CalibrationScore(4, 0, new RecognitionRate(4, 10, 0, 0)), Runnable::run, () -> 1L, listener);
			controller.setDiagnosticSink((samples, rate) -> {
				this.written.add(samples);
				return this.path;
			});
			return controller;
		}, Runnable::run, state -> {
		});
	}

	private void press(int button) {
		SwtContractToolkit.userSelects(this.window.button(button));
	}

	private void toQuality() {
		press(0);
		press(0);
		press(0);
		press(0);
	}

	private static String text(Object widget) {
		Object text = SwtContractToolkit.state(widget).properties.get("Text");
		return text == null ? "" : text.toString();
	}

	private static boolean visible(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Visible") != Boolean.FALSE;
	}

	private void tick(boolean on) {
		SwtContractToolkit.state(this.window.keepCopyBox()).properties.put("Selected", on);
	}

	@Test
	public void theBoxIsInTheQualityStepOnlyAndUntickedWithItsText() {
		open();
		assertFalse(visible(this.window.keepCopyBox()), "not on the offer");
		toQuality();
		assertEquals("practice.profile.quality.question", text(this.window.bodyLabel()));
		assertTrue(visible(this.window.keepCopyBox()));
		assertEquals("practice.profile.quality.keep-copy", text(this.window.keepCopyBox()));
		assertEquals(Boolean.FALSE, SwtContractToolkit.state(this.window.keepCopyBox()).properties.getOrDefault("Selected", Boolean.FALSE));
	}

	@Test
	public void aTickedBoxWritesTheCopyAndTheNextStepShowsThePath() {
		open();
		toQuality();
		tick(true);
		press(0); // yes, analyse it
		assertEquals(1, this.written.size());
		assertFalse(visible(this.window.keepCopyBox()), "gone after the quality step");
		assertTrue(text(this.window.detailLabel()).contains("practice.profile.quality.copy.written[" + this.path + "]"), text(this.window.detailLabel()));
	}

	@Test
	public void anUntickedBoxWritesNothing() {
		open();
		toQuality();
		press(0);
		assertTrue(this.written.isEmpty());
		assertFalse(text(this.window.detailLabel()).contains("copy"), text(this.window.detailLabel()));
	}

	@Test
	public void aFailedWriteIsToldAndTheCalibrationGoesOn() {
		open();
		this.path = null;
		toQuality();
		tick(true);
		press(0);
		assertEquals(1, this.written.size());
		assertTrue(text(this.window.detailLabel()).contains("practice.profile.quality.copy.failed"), text(this.window.detailLabel()));
		assertTrue(text(this.window.bodyLabel()).startsWith("practice.profile.result") || text(this.window.bodyLabel()).startsWith("practice.profile.choice"),
				text(this.window.bodyLabel()));
	}

	@Test
	public void theBoxIsUntickedAgainWhenTheQualityStepIsEnteredASecondTime() {
		open();
		toQuality();
		tick(true);
		press(1); // no: refused, with the three choices
		press(0); // record again
		press(0); // ok on the piece... the listening runs again
		assertTrue(visible(this.window.keepCopyBox()));
		assertEquals(Boolean.FALSE, SwtContractToolkit.state(this.window.keepCopyBox()).properties.getOrDefault("Selected", Boolean.FALSE));
	}
}
