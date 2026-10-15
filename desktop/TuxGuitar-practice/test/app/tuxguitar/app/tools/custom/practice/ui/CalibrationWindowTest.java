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
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationFlow.State;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationScore;
import app.tuxguitar.app.tools.custom.practice.calibration.ProfileSearch;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.calibration.ProfilePersistence;
import app.tuxguitar.audio.engine.calibration.RecognitionProfile;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Spec 013 (T035): the one window of the calibration on a headless toolkit. Each state shows its step, its text, its buttons and what they do; the
 * quality step shows the measures and has no replay; the refusals offer the three choices; the result shows the rates with the default and with the profile;
 * the window blocks during the analysis and closing it, at any step, asks "Stop the calibration?". The texts are the keys here; the three languages are
 * checked by {@code CalibrationStringsTest}. The native window is checked by the existing native tests of the Input tab.
 */
public class CalibrationWindowTest {

	private static final int RATE = 48000;
	private static final DeviceKey KEY = DeviceKey.of("Micro MacBook Pro", null, 0);
	private static final RecognitionSettings DEFAULTS = RecognitionSettings.defaults();

	private static final class FakePort implements CalibrationController.ListeningPort {
		float[] signal = new float[0];
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

	private static final class FakeTuner implements CalibrationController.TunerPort {
		boolean available = true;
		int opened;
		Runnable closed;

		public boolean available() {
			return this.available;
		}

		public void open() {
			this.opened++;
		}

		public void setClosedListener(Runnable listener) {
			this.closed = listener;
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

	private static CalibrationScore score(int correct) {
		return new CalibrationScore(correct, 0, new RecognitionRate(Math.min(correct, 10), 10, Math.max(0, correct - 10), 3));
	}

	private static float[] fourSounds() {
		int[] onsets = { RATE, 3 * RATE, 5 * RATE, 7 * RATE };
		float[] signal = SyntheticSignals.repeatedPlucks(110.0, RATE, 10 * RATE, onsets, 7L);
		float max = 0f;
		for (float value : signal) {
			max = Math.max(max, Math.abs(value));
		}
		for (int i = 0; i < signal.length; i++) {
			signal[i] *= 0.5f / max;
		}
		return signal;
	}

	/** the texts as "key" or "key[arguments]" so that the test sees which text and which numbers */
	private static final CalibrationWindow.Texts TEXTS = (key, arguments) -> key + (arguments.length == 0 ? "" : Arrays.toString(arguments));

	private final SwtContractToolkit toolkit = new SwtContractToolkit();
	private final FakePort port = new FakePort();
	private final FakeTuner tuner = new FakeTuner();
	private final Memory file = new Memory();
	private final ProfileLibrary library = new ProfileLibrary(this.file);
	private final List<Runnable> queued = new ArrayList<Runnable>();
	private final List<State> finished = new ArrayList<State>();
	private boolean synchronousSearch = true;
	private int scoreOfFound = 8;
	private CalibrationController controller;
	private CalibrationWindow window;

	private CalibrationWindow open(boolean microphone, boolean withProfile) {
		this.port.signal = fourSounds();
		if (withProfile) {
			Map<RecognitionParameter.Number, Double> values = new java.util.LinkedHashMap<RecognitionParameter.Number, Double>();
			values.put(RecognitionParameters.NOTE_ACCEPT, RecognitionParameters.NOTE_ACCEPT.getMinimum());
			this.library.save(new RecognitionProfile(KEY, RATE, 1L, values, new RecognitionRate(9, 10, 2, 3), new RecognitionRate(7, 10, 1, 3),
					new FrontEndState(1.0, false), RecognitionProfile.Origin.SEARCHED));
		}
		this.window = new CalibrationWindow(this.toolkit.factory(), null, TEXTS, microphone, Arrays.asList("E2 line", "A2 line", "D3 line", "chord line"), listener -> {
			this.controller = new CalibrationController(4, KEY, this.library, () -> DEFAULTS, this.port, this.tuner, (segments, rate) -> {
				ProfileSearch.Scoring scoring = settings -> score(settings.equals(DEFAULTS) ? 2 : this.scoreOfFound);
				return scoring;
			}, task -> {
				if (this.synchronousSearch) {
					task.run();
				} else {
					this.queued.add(task);
				}
			}, () -> 1760000000000L, listener);
			return this.controller;
		}, Runnable::run, this.finished::add);
		return this.window;
	}

	private static String text(Object widget) {
		Object text = SwtContractToolkit.state(widget).properties.get("Text");
		return text == null ? "" : text.toString();
	}

	private static boolean visible(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Visible") != Boolean.FALSE;
	}

	private void press(int button) {
		SwtContractToolkit.userSelects(this.window.button(button));
	}

	private List<String> buttonTexts() {
		List<String> texts = new ArrayList<String>();
		for (int i = 0; i < 3; i++) {
			texts.add(visible(this.window.button(i)) ? text(this.window.button(i)) : null);
		}
		return texts;
	}

	private void toListening() {
		press(0); // calibrate
		press(0); // in tune
		press(0); // OK on the advice
		press(0); // OK on the piece: the listening runs
	}

	// ---------------------------------------------------------------- the steps

	@Test
	public void theOfferForANeverCalibratedInputHasThreeButtons() {
		open(false, false);
		assertEquals("practice.profile.offer.new", text(this.window.bodyLabel()));
		assertEquals(Arrays.asList("practice.profile.offer.calibrate", "practice.profile.offer.default", "practice.profile.cancel"), buttonTexts());
		assertEquals("", text(this.window.stepLabel()), "no step number on the offer");
	}

	@Test
	public void theOfferForACalibratedInputSaysCalibrateAgainAndThatTheDefaultReplacesTheCalibration() {
		open(false, true);
		assertEquals("practice.profile.offer.again", text(this.window.bodyLabel()));
		assertEquals(Arrays.asList("practice.profile.offer.calibrate-again", "practice.profile.offer.default-replaces", "practice.profile.cancel"), buttonTexts());
	}

	@Test
	public void theTuningQuestionIsTheStartWindowsAndTheTunerHidesTheWindowUntilItIsClosed() {
		open(false, false);
		press(0);
		assertEquals("practice.prerun.tuned.question", text(this.window.bodyLabel()), "the question of the start window");
		assertEquals(Arrays.asList("practice.prerun.tuned.yes", "practice.prerun.tuned.no", "practice.profile.cancel"), buttonTexts());
		assertEquals("practice.profile.step[1, 7]", text(this.window.stepLabel()));
		press(1);
		assertEquals(1, this.tuner.opened);
		assertFalse(visible(this.window.window()), "the window gives way to the tuner (it is modal)");
		this.tuner.closed.run();
		assertTrue(visible(this.window.window()));
		assertEquals("practice.prerun.tuned.question", text(this.window.bodyLabel()), "the question is asked again, never answered by itself");
	}

	@Test
	public void aTunerThatCannotOpenIsToldInTheQuestion() {
		open(false, false);
		this.tuner.available = false;
		press(0);
		press(1);
		assertTrue(text(this.window.bodyLabel()).contains("practice.profile.tuner.unavailable"), text(this.window.bodyLabel()));
	}

	@Test
	public void theAdviceMentionsTheQuietPlaceOnlyForAMicrophone() {
		open(true, false);
		press(0);
		press(0);
		assertTrue(text(this.window.bodyLabel()).contains("practice.profile.advice.microphone"), text(this.window.bodyLabel()));
		assertTrue(text(this.window.bodyLabel()).contains("practice.profile.advice"));
		assertEquals("practice.profile.step[2, 7]", text(this.window.stepLabel()));

		CalibrationWindow instrument = open(false, false);
		press(0);
		press(0);
		assertFalse(text(instrument.bodyLabel()).contains("practice.profile.advice.microphone"), "an instrument input: no quiet place to ask for");
	}

	@Test
	public void thePieceIsShownAsTheListOfItsEventsWithoutATempo() {
		open(false, false);
		press(0);
		press(0);
		press(0);
		assertEquals("practice.profile.piece.intro", text(this.window.bodyLabel()));
		String detail = text(this.window.detailLabel());
		assertTrue(detail.contains("1. E2 line") && detail.contains("4. chord line"), detail);
		assertEquals(Arrays.asList("practice.profile.ok", "practice.profile.cancel", null), buttonTexts());
	}

	@Test
	public void theQualityStepShowsTheMeasuresAndHasNoReplay() {
		open(false, false);
		toListening();
		assertEquals(State.QUALITY, this.controller.getSnapshot().getState());
		assertEquals("practice.profile.quality.question", text(this.window.bodyLabel()));
		String detail = text(this.window.detailLabel());
		assertTrue(detail.contains("practice.profile.quality.sounds[4, 4]"), detail);
		assertTrue(detail.contains("practice.profile.quality.peak["), detail);
		assertTrue(detail.contains("practice.profile.quality.noise["), detail);
		assertTrue(detail.contains("practice.profile.quality.clipping.none"), detail);
		assertEquals(Arrays.asList("practice.profile.quality.yes", "practice.profile.quality.no", "practice.profile.cancel"), buttonTexts());
	}

	@Test
	public void theListeningShowsTheSoundsHeardOnABarAndTheNextEvent() {
		open(false, false);
		this.port.signal = new float[0]; // nothing is played: the listening goes on
		press(0);
		press(0);
		press(0);
		press(0);
		assertEquals(State.LISTENING, this.controller.getSnapshot().getState());
		assertEquals(4, SwtContractToolkit.state(this.window.progressBar()).properties.get("Maximum"));
		assertEquals(Arrays.asList("practice.profile.cancel", null, null), buttonTexts());
		assertTrue(text(this.window.bodyLabel()).contains("practice.profile.listening[0, 4]"), text(this.window.bodyLabel()));
		assertTrue(text(this.window.detailLabel()).contains("practice.profile.listening.next[1. E2 line]"), text(this.window.detailLabel()));
	}

	@Test
	public void aRefusalGivesItsReasonWithTheNumbersAndTheThreeChoices() {
		open(false, true);
		toListening();
		press(1); // No: not good enough
		assertEquals(State.REFUSED, this.controller.getSnapshot().getState());
		assertTrue(text(this.window.bodyLabel()).contains("practice.profile.refused.NOT_GOOD_ENOUGH"), text(this.window.bodyLabel()));
		assertEquals(Arrays.asList("practice.profile.record-again", "practice.profile.offer.default-replaces", "practice.profile.stop"), buttonTexts());
	}

	@Test
	public void aWrongNumberOfSoundsIsToldWithTheNumbers() {
		open(false, false);
		float[] five = SyntheticSignals.repeatedPlucks(110.0, RATE, 11 * RATE, new int[] { RATE, 3 * RATE, 5 * RATE, 7 * RATE, 7 * RATE + RATE / 2 }, 7L);
		float max = 0f;
		for (float value : five) {
			max = Math.max(max, Math.abs(value));
		}
		for (int i = 0; i < five.length; i++) {
			five[i] *= 0.5f / max;
		}
		this.port.signal = five;
		toListening();
		assertEquals(State.REFUSED, this.controller.getSnapshot().getState());
		assertTrue(text(this.window.bodyLabel()).contains("practice.profile.refused.WRONG_COUNT[5, 4]"), text(this.window.bodyLabel()));
	}

	@Test
	public void theAnalysisShowsProgressAndACancelButtonAndTheResultShowsTheRates() {
		this.scoreOfFound = 12;
		this.synchronousSearch = false;
		open(false, false);
		toListening();
		press(0); // yes, analyse
		assertEquals(State.ANALYSING, this.controller.getSnapshot().getState());
		assertEquals(Arrays.asList("practice.profile.cancel", null, null), buttonTexts());
		assertTrue(text(this.window.bodyLabel()).contains("practice.profile.analysing["), text(this.window.bodyLabel()));
		for (Runnable task : this.queued) {
			task.run();
		}
		assertEquals(State.RESULT, this.controller.getSnapshot().getState());
		assertTrue(text(this.window.bodyLabel()).contains("practice.profile.result.saved"), text(this.window.bodyLabel()));
		String detail = text(this.window.detailLabel());
		assertTrue(detail.contains("practice.profile.result.rate.total["), detail);
		assertTrue(detail.contains("practice.profile.result.rate.notes[") && detail.contains("practice.profile.result.rate.chords["), detail);
		assertTrue(detail.contains("practice.profile.result.reserve"), "the reserve sentence is always there: " + detail);
		assertEquals(Arrays.asList("practice.profile.ok", null, null), buttonTexts());
		press(0);
		assertEquals(Arrays.asList(State.DONE), this.finished);
	}

	@Test
	public void theTwoMessagesOfTheChoiceAndItsThreeButtons() {
		this.scoreOfFound = 2;
		open(false, false);
		toListening();
		press(0);
		assertEquals(State.CHOICE, this.controller.getSnapshot().getState());
		assertTrue(text(this.window.bodyLabel()).contains("practice.profile.choice.NOT_BETTER"), text(this.window.bodyLabel()));
		assertEquals(Arrays.asList("practice.profile.choice.use-found", "practice.profile.record-again", "practice.profile.offer.default"), buttonTexts());
		press(1); // record again
		assertEquals(State.PIECE, this.controller.getSnapshot().getState());
	}

	@Test
	public void betterButUnderSixtyPercentNamesThePercentage() {
		this.scoreOfFound = 7;
		open(false, false);
		toListening();
		press(0);
		assertEquals(State.CHOICE, this.controller.getSnapshot().getState());
		assertTrue(text(this.window.bodyLabel()).contains("practice.profile.choice.BETTER_UNDER_60[54]"), text(this.window.bodyLabel()));
	}

	@Test
	public void aFailedSaveIsToldInTheResult() {
		this.port.signal = fourSounds();
		ProfilePersistence broken = new ProfilePersistence() {
			public Map<String, String> read() {
				return new HashMap<String, String>();
			}

			public void write(Map<String, String> values) {
				throw new IllegalStateException("read-only");
			}
		};
		this.window = new CalibrationWindow(this.toolkit.factory(), null, TEXTS, false, Arrays.asList("a", "b", "c", "d"), listener -> {
			this.controller = new CalibrationController(4, KEY, new ProfileLibrary(broken), () -> DEFAULTS, this.port, this.tuner,
					(segments, rate) -> settings -> score(settings.equals(DEFAULTS) ? 2 : 12), Runnable::run, () -> 1L, listener);
			return this.controller;
		}, Runnable::run, this.finished::add);
		toListening();
		press(0);
		assertEquals(State.RESULT, this.controller.getSnapshot().getState());
		assertTrue(text(this.window.bodyLabel()).contains("practice.profile.result.save-failed"), text(this.window.bodyLabel()));
	}

	// ---------------------------------------------------------------- the cross and the end

	@Test
	public void closingTheWindowAsksToStopAtEveryStepAndNoGoesBack() {
		open(false, false);
		String before = text(this.window.bodyLabel());
		this.window.closeRequested();
		assertEquals("practice.profile.stop.question", text(this.window.bodyLabel()));
		assertTrue(text(this.window.detailLabel()).contains("practice.profile.stop.note"));
		assertEquals(Arrays.asList("practice.profile.stop.yes", "practice.profile.stop.no", null), buttonTexts());
		press(1); // No
		assertEquals(before, text(this.window.bodyLabel()), "back to the same step");
		assertEquals(State.OFFER, this.controller.getSnapshot().getState());
		assertTrue(this.finished.isEmpty());
	}

	@Test
	public void closingTheWindowAndAnsweringYesStopsAndSavesNothing() {
		open(false, true);
		toListening();
		this.window.closeRequested();
		press(0); // Yes
		assertEquals(State.CANCELLED, this.controller.getSnapshot().getState());
		assertEquals(Arrays.asList(State.CANCELLED), this.finished);
		assertEquals(1, this.library.keys().size(), "the profile that existed is untouched");
	}

	@Test
	public void closingDuringTheAnalysisAsksToo_NoLetsTheSearchGoOn() {
		this.synchronousSearch = false;
		open(false, false);
		toListening();
		press(0);
		assertEquals(State.ANALYSING, this.controller.getSnapshot().getState());
		this.window.closeRequested();
		assertEquals("practice.profile.stop.question", text(this.window.bodyLabel()));
		press(1); // No
		assertEquals(State.ANALYSING, this.controller.getSnapshot().getState());
		for (Runnable task : this.queued) {
			task.run();
		}
		assertTrue(this.controller.getSnapshot().getState() == State.CHOICE || this.controller.getSnapshot().getState() == State.RESULT, "the search went on and ended");
	}

	@Test
	public void theCancelButtonCancelsWithoutAskingAndTheWindowCloses() {
		open(false, false);
		press(2);
		assertEquals(Arrays.asList(State.CANCELLED), this.finished);
	}

	@Test
	public void theWindowIsTold_OnceWhenTheFlowIsDone() {
		open(false, false);
		press(1); // use the default
		assertEquals(Arrays.asList(State.DONE), this.finished);
		assertEquals(RecognitionProfile.Origin.DEFAULT_CHOSEN, this.library.get(KEY).getOrigin());
	}
}
