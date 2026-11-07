package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.InputAssessor;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.calibration.RecordingQuality;
import app.tuxguitar.audio.engine.calibration.SoundDetection;
import app.tuxguitar.audio.engine.settings.OrderedPair;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The calibration and input-quality families (spec 026, T027, inventory.md "CALIBRATION_1" to "INPUT_QUALITY"): their defaults are the former
 * constants of the delay calibration, of the profile calibration, of the level check and of the weak-input check, and the values carry the settings.
 */
public class CalibrationParametersTest {

	private static final long MS = 1_000_000L;

	@Test
	public void theDefaultsAreTheFormerValues() {
		RecognitionSettings d = RecognitionSettings.defaults();
		CalibrationRun.Parameters run = CalibrationRun.Parameters.of(d);
		assertEquals(Arrays.asList((double) CalibrationRun.EARLY_WINDOW_NANOS, (double) CalibrationRun.LATE_WINDOW_NANOS, 25.0 * MS, 30.0 * MS, 0.5, 0.8,
				70.0, 1400.0), values(run));
		assertEquals(values(CalibrationRun.Parameters.defaults()), values(run));
		assertEquals(Arrays.asList((double) CalibrationTaps.MIN_USABLE, CalibrationTaps.OUTLIER_DEVIATIONS, CalibrationTaps.OUTLIER_FLOOR_MILLIS,
				CalibrationTaps.UNSTABLE_SPREAD_MILLIS), values(run.taps));
		assertEquals(RecognitionRate.THRESHOLD_PERCENT, (int) d.get(RecognitionParameters.CALIBRATION_RECOGNITION_RATE));
		assertEquals(RecordingQuality.CALM_BELOW_DBFS, d.get(RecognitionParameters.CALIBRATION_CALM_BELOW));
		assertEquals(RecordingQuality.LOUD_ABOVE_DBFS, d.get(RecognitionParameters.CALIBRATION_LOUD_ABOVE));
		assertEquals(SoundDetection.REFRACTORY_MILLIS, (int) d.get(RecognitionParameters.CALIBRATION_SOUND_REFRACTORY));
		assertEquals(SoundDetection.SEGMENT_LEAD_MILLIS, (int) d.get(RecognitionParameters.CALIBRATION_SOUND_LEAD));
		InputAssessor.Parameters former = new InputAssessor.Parameters();
		InputAssessor.Parameters level = InputAssessor.Parameters.of(d);
		assertEquals(Arrays.asList(former.playingConfidence, (double) former.requiredPlayingNanos, former.suboptimalShare, (double) former.timeoutNanos,
				former.unusableClippingShare, former.tooLowShare, former.digitalSilenceDbfs), Arrays.asList(level.playingConfidence,
				(double) level.requiredPlayingNanos, level.suboptimalShare, (double) level.timeoutNanos, level.unusableClippingShare, level.tooLowShare,
				level.digitalSilenceDbfs));
		PracticeSession.WaitParameters wait = PracticeSession.WaitParameters.of(d);
		assertEquals(Arrays.asList((double) InputQuality.ATTACKS, (double) InputQuality.REMEMBER_NANOS, (double) InputQuality.ATTACK_WINDOW_NANOS),
				Arrays.asList((double) wait.inputQualityAttacks, (double) wait.inputQualityRememberNanos, (double) wait.inputQualityAttackWindowNanos));
	}

	@Test
	public void theValuesCarryTheSettings() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.CALIBRATION_EARLY_WINDOW, 300)
				.with(RecognitionParameters.CALIBRATION_MIN_FREQUENCY, 90).with(RecognitionParameters.CALIBRATION_MIN_USABLE, 6)
				.with(RecognitionParameters.CALIBRATION_UNSTABLE_SPREAD, 80).with(RecognitionParameters.LEVEL_CHECK_REQUIRED_PLAYING, 900)
				.with(RecognitionParameters.INPUT_QUALITY_ATTACKS, 5).with(RecognitionParameters.INPUT_QUALITY_ATTACK_WINDOW, 300);
		CalibrationRun.Parameters run = CalibrationRun.Parameters.of(s);
		assertEquals(300 * MS, run.earlyWindowNanos);
		assertEquals(90.0, run.minHz);
		assertEquals(6, run.taps.minUsable);
		assertEquals(80.0, run.taps.unstableSpreadMillis);
		assertEquals(900 * MS, InputAssessor.Parameters.of(s).requiredPlayingNanos);
		assertEquals(5, PracticeSession.WaitParameters.of(s).inputQualityAttacks);
		assertEquals(300 * MS, PracticeSession.WaitParameters.of(s).inputQualityAttackWindowNanos);
	}

	@Test
	public void theSectionsHoldTheirValuesAndTheNoiseWordsStayOrdered() {
		assertEquals(7, RecognitionParameters.of(Section.CALIBRATION_1).size());
		assertEquals(6, RecognitionParameters.of(Section.CALIBRATION_2).size());
		assertEquals(4, RecognitionParameters.of(Section.CALIBRATION_3).size());
		assertEquals(3, RecognitionParameters.of(Section.LEVEL_CHECK).size());
		assertEquals(3, RecognitionParameters.of(Section.INPUT_QUALITY).size());
		boolean declared = false;
		for( OrderedPair pair : RecognitionParameters.ORDERED_PAIRS ) {
			declared |= pair.getLower() == RecognitionParameters.CALIBRATION_CALM_BELOW && pair.getUpper() == RecognitionParameters.CALIBRATION_LOUD_ABOVE;
		}
		assertTrue(declared, "calm-below <= loud-above is declared");
	}

	private static List<Double> values(CalibrationRun.Parameters p) {
		return Arrays.asList((double) p.earlyWindowNanos, (double) p.lateWindowNanos, (double) p.pitchPatienceNanos, (double) p.pitchBeforeNanos,
				p.samePitchSemitones, p.minConfidence, p.minHz, p.maxHz);
	}

	private static List<Double> values(CalibrationTaps.Parameters p) {
		return Arrays.asList((double) p.minUsable, p.outlierDeviations, p.outlierFloorMillis, p.unstableSpreadMillis);
	}
}
