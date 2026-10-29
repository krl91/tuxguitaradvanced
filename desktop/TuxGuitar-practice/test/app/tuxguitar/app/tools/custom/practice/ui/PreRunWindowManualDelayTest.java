package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.CalibrationRun;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.session.LatencyCompensation;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;

/**
 * Spec 016 (T045, FR-004, gate G2): when the calibration of the start window fails because the notes are too scattered, its sentence says where the
 * delay can be set by hand (the Mode tab), which the start window has no field for; the Mode tab, which has the field, keeps its own sentence.
 */
public class PreRunWindowManualDelayTest {

	private static final class Source implements PreRunSource {
		StrumVerdict verdict = StrumVerdict.NO_STRUM;

		public StrumClipMeter.Reading reading() {
			return new StrumClipMeter.Reading(this.verdict, 0.0, -20.0);
		}

		public double levelDbfs() {
			return -20.0;
		}

		public boolean boostActive() {
			return false;
		}

		public void reset() {
			this.verdict = StrumVerdict.NO_STRUM;
		}

		public void close() {
		}
	}

	private static final class Tuner implements PreRunWindow.TunerOpener {
		public boolean available() {
			return true;
		}

		public void open() {
		}

		public void toFront() {
		}

		public void setClosedListener(Runnable listener) {
		}
	}

	private final SwtContractToolkit toolkit = new SwtContractToolkit();
	private final FollowPanelTest.FakeBackend backend = new FollowPanelTest.FakeBackend();
	private final DeclinedRoutes declined = new DeclinedRoutes();
	private final AtomicInteger finished = new AtomicInteger();
	private final Source source = new Source();
	private PreRunWindow window;

	private PreRunWindow open(FollowSettings.Mode mode, boolean withOffer) {
		PreRunCheck check = new PreRunCheck(mode);
		RhythmOffer offer = new RhythmOffer(mode, this.backend::route, this.backend.calibrations, this.declined);
		this.window = new PreRunWindow(this.toolkit.factory(), null, check, this.source, (key, arguments) -> key, () -> 0L, new Tuner(), this.finished::incrementAndGet,
				null, withOffer ? offer : null, withOffer ? this.backend : null);
		this.window.refresh();
		check.answerTuned(true);
		this.source.verdict = StrumVerdict.GOOD;
		this.window.refresh();
		return this.window;
	}

	private static boolean visible(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Visible") == Boolean.TRUE;
	}

	private static String text(Object widget) {
		Object text = SwtContractToolkit.state(widget).properties.get("Text");
		return text == null ? "" : text.toString();
	}

	private static final long MS = 1_000_000L;
	private static final long HOP = 5 * MS;
	private static final long LATENCY = 65 * MS;

	/** Eight notes on the clicks, every other one 250 ms later: too scattered to keep a delay. */
	private void playScattered(FollowPanelTest.FakeBackend backend) {
		CalibrationRun run = backend.begun;
		assertNotNull(run);
		long t0 = 10_000_000_000L;
		long end = t0 + CalibrationRun.LEAD_NANOS + 8 * CalibrationRun.PERIOD_NANOS + 2_000 * MS;
		for (long wall = t0; wall < end; wall += HOP) {
			backend.clicks.now = wall;
			boolean onset = false;
			double f0 = Double.NaN;
			double confidence = 0.0;
			for (int k = 0; k < 8; k++) {
				long start = t0 + CalibrationRun.LEAD_NANOS + k * CalibrationRun.PERIOD_NANOS + (k % 2 == 0 ? 0 : 250) * MS;
				if (wall >= start && wall < start + 200 * MS) {
					f0 = (k % 2 == 0 ? 329.63 : 392.0);
					confidence = 0.95;
				}
				onset |= (wall == start + LATENCY);
			}
			run.onFrame(new AnalysisFrame(wall - t0, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0, confidence, onset), wall);
			if (backend.changed != null) {
				backend.changed.run();
			}
			if (run.state() != CalibrationRun.State.RUNNING) {
				break;
			}
		}
	}

	@Test
	public void aScatteredCalibrationInTheStartWindowSaysTheDelayIsSetByHandInTheModeTab() {
		open(FollowSettings.Mode.FOLLOW, true);
		SwtContractToolkit.userSelects(this.window.calibrateNowButton());
		playScattered(this.backend);
		assertEquals("practice.prerun.rhythm.unstable", text(this.window.rhythmProgressLabel()));
		assertTrue(visible(this.window.calibrateNowButton()), "the offer stays");
	}

	@Test
	public void theTextsSayTheModeTabInTheThreeLanguages() throws java.io.IOException {
		String[] files = { "messages.properties", "messages_fr.properties", "messages_es.properties" };
		String[] tabs = { "Mode", "Mode", "Modo" };
		for (int f = 0; f < files.length; f++) {
			java.util.Properties properties = FollowReasonStringsTest.load(files[f]);
			String text = properties.getProperty("practice.prerun.rhythm.unstable");
			assertTrue(text != null && text.contains(tabs[f]) && FollowReasonStringsTest.placeholders(text).contains("0"), files[f]);
		}
	}
}
