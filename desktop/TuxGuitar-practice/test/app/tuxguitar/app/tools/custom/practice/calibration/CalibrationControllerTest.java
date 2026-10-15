package app.tuxguitar.app.tools.custom.practice.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController.RefusalCause;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController.ResultKind;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationFlow.State;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.calibration.ProfilePersistence;
import app.tuxguitar.audio.engine.calibration.RecognitionProfile;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.ProfileParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Spec 013 (T033): the calibration from the first offer to the saved profile, with a fake input that plays a signal into the listening mode, a fake tuner,
 * an in-memory library and a scoring by a function of the settings (the audio judging is tested elsewhere). The controller does what each step needs and
 * never saves before the end; a cancel, a refusal or a device lost leaves the existing profile as it was (FR-005, FR-009, FR-012, FR-014, FR-017, SC-007).
 */
public class CalibrationControllerTest {

	private static final int RATE = 48000;
	private static final DeviceKey KEY = DeviceKey.of("Micro MacBook Pro", null, 0);
	private static final RecognitionSettings DEFAULTS = RecognitionSettings.defaults();

	// ---------------------------------------------------------------- fakes

	/** plays a signal into the listeners as soon as they are given, hop by hop, until the listening says stop */
	private static final class FakePort implements CalibrationController.ListeningPort {
		float[] signal = new float[0];
		/** seconds of silence that follow the signal, as a live input goes on delivering */
		int tailSeconds;
		FrontEndState frontEnd = new FrontEndState(1.5, true);
		int starts;
		int stops;
		boolean stopRequested;
		HopListener[] listeners;

		public void start(HopListener... listeners) {
			this.starts++;
			this.stopRequested = false;
			this.listeners = listeners;
			for (HopListener listener : listeners) {
				listener.configure(RATE);
			}
			float[] hop = new float[256];
			int total = this.signal.length + this.tailSeconds * RATE;
			for (int offset = 0; offset + 256 <= total && !this.stopRequested; offset += 256) {
				if (offset + 256 <= this.signal.length) {
					System.arraycopy(this.signal, offset, hop, 0, 256);
				} else {
					java.util.Arrays.fill(hop, 0f);
				}
				for (HopListener listener : listeners) {
					listener.hop(hop, 0, 256);
				}
			}
		}

		public void stop() {
			this.stops++;
			this.stopRequested = true;
		}

		public FrontEndState frontEnd() {
			return this.frontEnd;
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
		boolean failWrite;

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			if (this.failWrite) {
				throw new IllegalStateException("read-only");
			}
			this.values = new HashMap<String, String>(values);
		}
	}

	/** a scoring of the settings by a function; counts how many scorers were made (a refused recording makes none) */
	private static final class Scorers implements CalibrationController.ScorerFactory {
		int made;
		java.util.function.ToIntFunction<RecognitionSettings> correct = settings -> 5;

		public ProfileSearch.Scoring create(List<float[]> segments, int sampleRate) {
			this.made++;
			return settings -> score(this.correct.applyAsInt(settings));
		}
	}

	private static CalibrationScore score(int correct) {
		return new CalibrationScore(correct, 0, new RecognitionRate(Math.min(correct, 10), 10, Math.max(0, correct - 10), 3));
	}

	private static double farFromDefault() {
		RecognitionParameter.Number p = RecognitionParameters.NOTE_ACCEPT;
		return p.getDefault() - p.getMinimum() > p.getMaximum() - p.getDefault() ? p.getMinimum() : p.getMaximum();
	}

	/** the acceptance in cents near a far target is worth 12 events of 13; the default is worth 5 */
	private static java.util.function.ToIntFunction<RecognitionSettings> betterTowardsTarget() {
		return settings -> {
			double step = (RecognitionParameters.NOTE_ACCEPT.getMaximum() - RecognitionParameters.NOTE_ACCEPT.getMinimum()) / 16.0;
			double distance = Math.abs(settings.get(RecognitionParameters.NOTE_ACCEPT) - farFromDefault()) / step;
			return (int) Math.max(0, Math.round(12 - distance));
		};
	}

	private static final class Rig {
		final FakePort port = new FakePort();
		final FakeTuner tuner = new FakeTuner();
		final Memory file = new Memory();
		final ProfileLibrary library = new ProfileLibrary(this.file);
		final Scorers scorers = new Scorers();
		final List<CalibrationController.Snapshot> seen = new ArrayList<CalibrationController.Snapshot>();
		final List<Runnable> queued = new ArrayList<Runnable>();
		boolean synchronous = true;
		CalibrationController controller;

		Rig(int expected, float[] signal) {
			this.port.signal = signal;
			this.controller = new CalibrationController(expected, KEY, this.library, () -> DEFAULTS, this.port, this.tuner, this.scorers, task -> {
				if (this.synchronous) {
					task.run();
				} else {
					this.queued.add(task);
				}
			}, () -> 1760000000000L, snapshot -> this.seen.add(snapshot));
		}

		State state() {
			return this.controller.getSnapshot().getState();
		}

		/** the commands up to the quality question, with a recording that has the right number of sounds */
		void toQuality() {
			this.controller.calibrate();
			this.controller.answerTuning(true);
			this.controller.ok();
			this.controller.ok();
		}
	}

	private static float[] plucks(double[] seconds, double length) {
		int[] onsets = new int[seconds.length];
		for (int i = 0; i < seconds.length; i++) {
			onsets[i] = (int) (seconds[i] * RATE);
		}
		float[] signal = SyntheticSignals.repeatedPlucks(110.0, RATE, (int) (length * RATE), onsets, 7L);
		float max = 0f;
		for (float value : signal) {
			max = Math.max(max, Math.abs(value));
		}
		for (int i = 0; i < signal.length; i++) {
			signal[i] *= 0.5f / max;
		}
		return signal;
	}

	private static float[] fourSounds() {
		return plucks(new double[] { 1.0, 3.0, 5.0, 7.0 }, 10.0);
	}

	private static RecognitionProfile existingProfile() {
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		values.put(RecognitionParameters.NOTE_ACCEPT, RecognitionParameters.NOTE_ACCEPT.getMinimum());
		return new RecognitionProfile(KEY, RATE, 1700000000000L, values, new RecognitionRate(9, 10, 2, 3), new RecognitionRate(7, 10, 1, 3),
				new FrontEndState(1.0, false), RecognitionProfile.Origin.SEARCHED);
	}

	// ---------------------------------------------------------------- the happy path

	@Test
	public void theWholePathWithAPlayedRecordingGivesASavedProfileThatStoresTheFrontEndState() {
		Rig rig = new Rig(4, fourSounds());
		rig.scorers.correct = betterTowardsTarget();
		assertEquals(State.OFFER, rig.state());
		rig.controller.calibrate();
		assertEquals(State.TUNING, rig.state());
		rig.controller.answerTuning(true);
		assertEquals(State.ADVICE, rig.state());
		rig.controller.ok();
		assertEquals(State.PIECE, rig.state());
		rig.controller.ok();
		assertEquals(State.QUALITY, rig.state(), "the listening ended by itself with the four sounds");
		assertEquals(4, rig.controller.getSnapshot().getHeard());
		assertNotNull(rig.controller.getSnapshot().getQuality());
		assertNull(rig.library.get(KEY), "nothing is saved before the end of the analysis");
		rig.controller.answerQuality(true);
		assertEquals(State.RESULT, rig.state());
		RecognitionProfile saved = rig.library.get(KEY);
		assertNotNull(saved);
		assertEquals(RecognitionProfile.Origin.SEARCHED, saved.getOrigin());
		assertTrue(saved.getFoundRate().meetsThreshold());
		assertEquals(new FrontEndState(1.5, true), saved.getFrontEnd(), "the state of the input at the recording");
		assertEquals(RATE, saved.getSampleRate());
		assertEquals(ResultKind.SAVED, rig.controller.getSnapshot().getResultKind());
		assertTrue(saved.getFoundRate().totalPercent() > saved.getDefaultRate().totalPercent());
		assertFalse(rig.controller.isRecordingHeld(), "the recording is dropped once the analysis is done");
		rig.controller.ok();
		assertEquals(State.DONE, rig.state());
	}

	@Test
	public void theListeningCountsTheSoundsAsTheyAreHeard() {
		Rig rig = new Rig(4, fourSounds());
		rig.toQuality();
		List<Integer> counts = new ArrayList<Integer>();
		for (CalibrationController.Snapshot snapshot : rig.seen) {
			if (snapshot.getState() == State.LISTENING) {
				counts.add(snapshot.getHeard());
			}
		}
		assertTrue(counts.contains(1) && counts.contains(2) && counts.contains(3) && counts.contains(4), "3 of 13 style progress: " + counts);
	}

	// ---------------------------------------------------------------- the quality question and the refusals

	@Test
	public void sayingNoToTheQualityQuestionSavesNothingAndOffersTheThreeChoices() {
		Rig rig = new Rig(4, fourSounds());
		rig.library.save(existingProfile());
		rig.toQuality();
		rig.controller.answerQuality(false);
		assertEquals(State.REFUSED, rig.state());
		assertEquals(RefusalCause.NOT_GOOD_ENOUGH, rig.controller.getSnapshot().getRefusalCause());
		assertEquals(existingProfile(), rig.library.get(KEY), "the profile that existed is untouched");
		assertFalse(rig.controller.isRecordingHeld());
		assertEquals(0, rig.scorers.made, "nothing was analysed");
		rig.controller.recordAgain();
		assertEquals(State.PIECE, rig.state());
		rig.controller.ok();
		assertEquals(State.QUALITY, rig.state(), "a new recording");
	}

	private void refusalIsOfferedWithThreeChoices(float[] signal, int expected, RefusalCause cause, int heard) {
		refusalIsOfferedWithThreeChoices(signal, expected, cause, heard, 0);
	}

	private void refusalIsOfferedWithThreeChoices(float[] signal, int expected, RefusalCause cause, int heard, int tailSeconds) {
		Rig rig = new Rig(expected, signal);
		rig.port.tailSeconds = tailSeconds;
		rig.library.save(existingProfile());
		rig.controller.calibrate();
		rig.controller.answerTuning(true);
		rig.controller.ok();
		rig.controller.ok();
		assertEquals(State.REFUSED, rig.state(), String.valueOf(cause));
		assertEquals(cause, rig.controller.getSnapshot().getRefusalCause());
		if (heard >= 0) {
			assertEquals(heard, rig.controller.getSnapshot().getHeard());
			assertEquals(expected, rig.controller.getSnapshot().getExpected());
		}
		assertEquals(0, rig.scorers.made, "no analysis is tried on a refused recording");
		assertFalse(rig.controller.isRecordingHeld(), "and the recording is dropped");
		assertEquals(existingProfile(), rig.library.get(KEY));
		// the three choices
		rig.controller.recordAgain();
		assertEquals(State.PIECE, rig.state());
		rig.controller.cancel();
		assertEquals(State.CANCELLED, rig.state());

		Rig again = new Rig(expected, signal);
		again.port.tailSeconds = tailSeconds;
		again.library.save(existingProfile());
		again.controller.calibrate();
		again.controller.answerTuning(true);
		again.controller.ok();
		again.controller.ok();
		again.controller.useDefault();
		assertEquals(State.DONE, again.state());
		assertEquals(RecognitionProfile.Origin.DEFAULT_CHOSEN, again.library.get(KEY).getOrigin(), "the default saved as the profile, replacing the old one");

		Rig stop = new Rig(expected, signal);
		stop.port.tailSeconds = tailSeconds;
		stop.library.save(existingProfile());
		stop.controller.calibrate();
		stop.controller.answerTuning(true);
		stop.controller.ok();
		stop.controller.ok();
		stop.controller.stop();
		assertEquals(State.CANCELLED, stop.state());
		assertEquals(existingProfile(), stop.library.get(KEY), "stop saves nothing");
	}

	@Test
	public void aWrongNumberOfSoundsIsRefusedWithTheNumbersAndTheThreeChoices() {
		refusalIsOfferedWithThreeChoices(plucks(new double[] { 1.0, 3.0, 5.0, 7.0, 7.5 }, 11.0), 4, RefusalCause.WRONG_COUNT, 5);
	}

	@Test
	public void aSilentTakeEndsOnTheSilenceTimeoutAndIsRefused() {
		refusalIsOfferedWithThreeChoices(SyntheticSignals.silence(25 * RATE), 4, RefusalCause.SILENCE_TIMEOUT, -1);
	}

	@Test
	public void aClippedTakeIsRefused() {
		float[] loud = plucks(new double[] { 1.0, 3.0, 5.0, 7.0 }, 10.0);
		for (int i = 0; i < loud.length; i++) {
			loud[i] *= 80f;
		}
		refusalIsOfferedWithThreeChoices(SyntheticSignals.hardClip(loud, 1.0), 4, RefusalCause.CLIPPED, -1, 25);
	}

	@Test
	public void threeMinutesWithoutTheExpectedSoundsIsRefusedOnTheTotalTimeout() {
		double[] seconds = new double[19];
		for (int i = 0; i < seconds.length; i++) {
			seconds[i] = 5.0 + 10.0 * i;
		}
		refusalIsOfferedWithThreeChoices(plucks(seconds, 200.0), 100, RefusalCause.TOTAL_TIMEOUT, -1);
	}

	// ---------------------------------------------------------------- the offer and the default

	@Test
	public void theOfferIsMadeToEveryDeviceAndUsingTheDefaultRecordsNothing() {
		Rig fresh = new Rig(4, fourSounds());
		assertFalse(fresh.controller.getSnapshot().hasProfile(), "never calibrated");
		fresh.controller.useDefault();
		assertEquals(State.DONE, fresh.state());
		assertEquals(RecognitionProfile.Origin.DEFAULT_CHOSEN, fresh.library.get(KEY).getOrigin());
		assertEquals(0, fresh.port.starts, "nothing was recorded");

		Rig calibrated = new Rig(4, fourSounds());
		calibrated.library.save(existingProfile());
		// the controller reads the library when it is made: this one is made with a profile there
		calibrated.controller = new CalibrationController(4, KEY, calibrated.library, () -> DEFAULTS, calibrated.port, calibrated.tuner, calibrated.scorers, Runnable::run,
				() -> 1L, snapshot -> calibrated.seen.add(snapshot));
		assertTrue(calibrated.controller.getSnapshot().hasProfile(), "calibrated: the offer says Calibrate again");
		calibrated.controller.useDefault();
		assertEquals(State.DONE, calibrated.state());
		assertEquals(RecognitionProfile.Origin.DEFAULT_CHOSEN, calibrated.library.get(KEY).getOrigin(), "it replaces the calibration");
		assertEquals(0, calibrated.port.starts);
	}

	// ---------------------------------------------------------------- the tuner

	@Test
	public void sayingTheGuitarIsNotInTuneOpensTheTunerAndClosingItBringsTheQuestionBack() {
		Rig rig = new Rig(4, fourSounds());
		rig.controller.calibrate();
		rig.controller.answerTuning(false);
		assertEquals(State.TUNER_OPEN, rig.state());
		assertEquals(1, rig.tuner.opened);
		assertNotNull(rig.tuner.closed);
		rig.tuner.closed.run();
		assertEquals(State.TUNING, rig.state(), "never goes on by itself");
		assertEquals(0, rig.port.starts);
	}

	@Test
	public void aTunerThatCannotOpenBringsTheQuestionBackWithAMessage() {
		Rig rig = new Rig(4, fourSounds());
		rig.tuner.available = false;
		rig.controller.calibrate();
		rig.controller.answerTuning(false);
		assertEquals(State.TUNING, rig.state());
		assertTrue(rig.controller.getSnapshot().isTunerUnavailable());
	}

	// ---------------------------------------------------------------- the result and the choices

	@Test
	public void settingsNotBetterThanTheDefaultAreOfferedWithTheThreeChoicesAndSavedOnlyOnChoice() {
		Rig rig = new Rig(4, fourSounds());
		rig.library.save(existingProfile());
		rig.scorers.correct = settings -> 8;
		rig.toQuality();
		rig.controller.answerQuality(true);
		assertEquals(State.CHOICE, rig.state());
		assertEquals(ResultKind.NOT_BETTER, rig.controller.getSnapshot().getResultKind());
		assertEquals(existingProfile(), rig.library.get(KEY), "nothing is saved without a choice");
		rig.controller.useFound();
		assertEquals(State.DONE, rig.state());
		RecognitionProfile saved = rig.library.get(KEY);
		assertEquals(RecognitionProfile.Origin.SEARCHED, saved.getOrigin());
		assertEquals(8.0 / 13 * 100, saved.getFoundRate().totalPercent(), 1e-9);
	}

	@Test
	public void betterButUnderSixtyPercentHasItsOwnMessageAndTheSameChoices() {
		Rig rig = new Rig(4, fourSounds());
		java.util.function.ToIntFunction<RecognitionSettings> towards = betterTowardsTarget();
		// 3 events for the default, at most 7 (53.8 %) for anything better
		rig.scorers.correct = settings -> settings.equals(DEFAULTS) ? 3 : Math.min(7, towards.applyAsInt(settings));
		rig.toQuality();
		rig.controller.answerQuality(true);
		assertEquals(State.CHOICE, rig.state());
		assertEquals(ResultKind.BETTER_UNDER_60, rig.controller.getSnapshot().getResultKind());
		assertNull(rig.library.get(KEY));
		rig.controller.useFound();
		assertEquals(State.DONE, rig.state());
		assertFalse(rig.library.get(KEY).getFoundRate().meetsThreshold(), "kept although under 60 %: the dot will be red");
	}

	@Test
	public void recordingAgainFromTheChoiceGoesBackToThePiece() {
		Rig rig = new Rig(4, fourSounds());
		rig.scorers.correct = settings -> 8;
		rig.toQuality();
		rig.controller.answerQuality(true);
		rig.controller.recordAgain();
		assertEquals(State.PIECE, rig.state());
		assertNull(rig.library.get(KEY));
		assertFalse(rig.controller.isRecordingHeld());
	}

	@Test
	public void usingTheDefaultFromTheChoiceSavesTheDefaultChosen() {
		Rig rig = new Rig(4, fourSounds());
		rig.scorers.correct = settings -> 8;
		rig.toQuality();
		rig.controller.answerQuality(true);
		rig.controller.useDefault();
		assertEquals(State.DONE, rig.state());
		assertEquals(RecognitionProfile.Origin.DEFAULT_CHOSEN, rig.library.get(KEY).getOrigin());
	}

	@Test
	public void aSearchStoppedAtItsLimitIsToldInTheResult() {
		Rig rig = new Rig(4, fourSounds());
		rig.scorers.correct = betterTowardsTarget();
		Rig limited = rig;
		limited.controller = new CalibrationController(4, KEY, limited.library, () -> DEFAULTS, limited.port, limited.tuner, limited.scorers, Runnable::run, () -> 1L,
				snapshot -> limited.seen.add(snapshot), 6);
		limited.toQuality();
		limited.controller.answerQuality(true);
		assertTrue(limited.controller.getSnapshot().isStoppedAtLimit());
	}

	// ---------------------------------------------------------------- failure to save

	@Test
	public void aFailedSaveIsReportedAndTheOldProfileKept() {
		Rig rig = new Rig(4, fourSounds());
		rig.library.save(existingProfile());
		rig.scorers.correct = betterTowardsTarget();
		rig.toQuality();
		rig.file.failWrite = true;
		rig.controller.answerQuality(true);
		assertTrue(rig.controller.getSnapshot().isSaveFailed());
		assertEquals(existingProfile(), rig.library.get(KEY));
		rig.controller.ok();
		assertEquals(State.DONE, rig.state());
	}

	// ---------------------------------------------------------------- cancel and device lost

	@Test
	public void aCancelAtEveryStepLeavesTheExistingProfileAsItWas() {
		String[] names = { "OFFER", "TUNING", "TUNER_OPEN", "ADVICE", "PIECE", "LISTENING", "QUALITY", "REFUSED", "CHOICE" };
		for (int step = 0; step < names.length; step++) {
			// the listening is left unfinished (no signal) to stop at LISTENING; the choice needs a result that is not better than the default
			Rig rig = new Rig(4, names[step].equals("LISTENING") ? new float[0] : fourSounds());
			rig.library.save(existingProfile());
			rig.scorers.correct = settings -> 8;
			if (step >= 1) {
				rig.controller.calibrate();
			}
			if (step == 2) {
				rig.controller.answerTuning(false);
			}
			if (step >= 3) {
				rig.controller.answerTuning(true);
			}
			if (step >= 4) {
				rig.controller.ok();
			}
			if (step >= 5) {
				rig.controller.ok();
			}
			if (step == 7) {
				rig.controller.answerQuality(false);
			}
			if (step == 8) {
				rig.controller.answerQuality(true);
			}
			assertEquals(State.valueOf(names[step]), rig.state(), "the step reached by the script");
			rig.controller.cancel();
			assertEquals(State.CANCELLED, rig.state(), "cancelled from " + names[step]);
			assertEquals(existingProfile(), rig.library.get(KEY), names[step] + ": the profile that existed is untouched");
			assertFalse(rig.controller.isRecordingHeld(), names[step] + ": the recording is dropped");
		}
	}

	@Test
	public void aCancelDuringTheAnalysisSavesNothing() {
		Rig rig = new Rig(4, fourSounds());
		rig.library.save(existingProfile());
		rig.scorers.correct = betterTowardsTarget();
		rig.synchronous = false;
		rig.toQuality();
		rig.controller.answerQuality(true);
		assertEquals(State.ANALYSING, rig.state());
		rig.controller.cancel();
		assertEquals(State.CANCELLED, rig.state());
		for (Runnable task : rig.queued) {
			task.run();
		}
		assertEquals(State.CANCELLED, rig.state(), "the search that ran late changes nothing");
		assertEquals(existingProfile(), rig.library.get(KEY));
	}

	@Test
	public void aDeviceLostWhileListeningCancelsAndOneLostDuringTheAnalysisDoesNot() {
		Rig listening = new Rig(4, new float[0]);
		listening.library.save(existingProfile());
		listening.controller.calibrate();
		listening.controller.answerTuning(true);
		listening.controller.ok();
		listening.controller.ok();
		assertEquals(State.LISTENING, listening.state());
		listening.controller.deviceLost();
		assertEquals(State.CANCELLED, listening.state());
		assertEquals(existingProfile(), listening.library.get(KEY));

		Rig analysing = new Rig(4, fourSounds());
		analysing.scorers.correct = betterTowardsTarget();
		analysing.synchronous = false;
		analysing.toQuality();
		analysing.controller.answerQuality(true);
		assertEquals(State.ANALYSING, analysing.state());
		analysing.controller.deviceLost();
		assertEquals(State.ANALYSING, analysing.state(), "the analysis uses the recording only");
		for (Runnable task : analysing.queued) {
			task.run();
		}
		assertEquals(State.RESULT, analysing.state());
		assertNotNull(analysing.library.get(KEY), "saved under that device's key");
	}

	@Test
	public void aProfileIsWrittenOnlyInTheFourCasesOfTheSpec() {
		// the library is written by: a better and sufficient result (RESULT), use-the-settings-found, use-the-default (offer, refusal, choice); nothing else
		Rig rig = new Rig(4, fourSounds());
		rig.library.save(existingProfile());
		int writes = rig.file.values.size();
		rig.toQuality();
		rig.controller.answerQuality(false);
		rig.controller.recordAgain();
		rig.controller.ok();
		rig.controller.answerQuality(true);
		assertEquals(State.CHOICE, rig.state());
		assertEquals(writes, rig.file.values.size());
		assertEquals(existingProfile(), rig.library.get(KEY), "no write before a choice");
	}
}
