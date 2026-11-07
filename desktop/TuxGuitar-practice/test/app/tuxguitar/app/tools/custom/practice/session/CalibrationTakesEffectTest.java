package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.HOP;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LATENCY;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowHarness.Note;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.InputAssessor;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.calibration.RecordingQuality;
import app.tuxguitar.audio.engine.calibration.SoundSegmenter;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Each calibration and input-quality value takes effect (spec 026, T027, SC-001), on scripted frames, a fake click player and synthetic recordings (no
 * sound): every value of the five sections at one end of its range changes what one of them decides. The trials: delay calibrations whose notes come
 * early, late, low, high, slowly pitched, over a ringing note or a near pitch; offsets with an outlier, a wide spread or few notes; recognition rates;
 * noise floors; recordings of plucks close together; level checks with clipping; weak attacks louder after a while or far apart.
 */
public class CalibrationTakesEffectTest {

	private static final List<Section> SECTIONS = Arrays.asList(Section.CALIBRATION_1, Section.CALIBRATION_2, Section.CALIBRATION_3, Section.LEVEL_CHECK,
			Section.INPUT_QUALITY);

	@Test
	public void everyCalibrationValueChangesADecision() {
		String recommended = decisions(RecognitionSettings.defaults());
		List<String> withoutEffect = new ArrayList<String>();
		for( Section section : SECTIONS ) {
			for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
				RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
				boolean changed = false;
				for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
					changed |= !decisions(RecognitionSettings.defaults().with(number, end)).equals(recommended);
				}
				if( !changed ) {
					withoutEffect.add(number.getKey());
				}
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change no decision");
	}

	private static String decisions(RecognitionSettings s) {
		StringBuilder text = new StringBuilder();
		CalibrationRun.Parameters parameters = CalibrationRun.Parameters.of(s);
		// delay calibrations: (offset of the notes in ms, MIDI pitch, ms without pitch at the start of each note, a note before each one: ms before, its pitch)
		double[][] runs = { { 120, 64, 0, 0, 0 }, { -200, 64, 0, 0, 0 }, { 650, 64, 0, 0, 0 }, { 1200, 64, 0, 0, 0 }, { 120, 38, 0, 0, 0 },
				{ 120, 88, 0, 0, 0 }, { 120, 64, 100, 0, 0 }, { 120, 64, 0, 150, 64 }, { 120, 64, 0, 400, 64.3 }, { 120, 30, 0, 0, 0 },
				// a note 60 ms early (a note is placed once its click was played), a note over one ringing since 150 ms (heard by no click)
				{ -60, 64, 0, 0, 0 }, { 0, 64, 0, 150, 64 } };
		for( double[] r : runs ) {
			text.append(calibrate(parameters, r)).append(';');
		}
		text.append('\n');
		// the evaluation of offsets: steady, one outlier, wide spread, six notes, a large delay
		List<List<Double>> offsets = Arrays.asList(Arrays.asList(118.0, 120.0, 122.0, 119.0, 121.0, 120.0, 118.0, 122.0),
				Arrays.asList(118.0, 120.0, 122.0, 119.0, 121.0, 120.0, 118.0, 190.0), Arrays.asList(60.0, 180.0, 100.0, 140.0, 70.0, 170.0, 110.0, 130.0),
				Arrays.asList(118.0, 120.0, 122.0, 119.0, 121.0, 120.0), Arrays.asList(30.0, 230.0, 80.0, 180.0, 40.0, 220.0, 90.0, 170.0),
				Arrays.asList(110.0, 120.0, 130.0, 100.0, 140.0, 120.0, 115.0, 125.0), Arrays.asList(112.0, 120.0, 128.0, 119.0, 121.0, 120.0, 117.0, 205.0));
		for( List<Double> o : offsets ) {
			CalibrationTaps.Result result = CalibrationTaps.evaluate(o, 8, 1000.0, parameters.taps);
			text.append(result.status()).append(' ').append(Math.round(result.medianMillis())).append(' ').append(result.used()).append(';');
		}
		text.append('\n');
		int threshold = (int) s.get(RecognitionParameters.CALIBRATION_RECOGNITION_RATE);
		for( int accepted : new int[] { 1, 3, 6, 8, 10 } ) {
			text.append(new RecognitionRate(accepted, 10, 0, 0).meetsThreshold(threshold) ? 'G' : 'R');
		}
		for( double noise = -95.0 ; noise <= -15.0 ; noise += 5.0 ) {
			text.append(RecordingQuality.wordFor(noise, s.get(RecognitionParameters.CALIBRATION_CALM_BELOW), s.get(RecognitionParameters.CALIBRATION_LOUD_ABOVE))
					.name().charAt(0));
		}
		text.append('\n');
		for( int gap : new int[] { 120, 600 } ) {
			SoundSegmenter.Result cut = SoundSegmenter.segment(plucks(gap), RATE, 4, (int) s.get(RecognitionParameters.CALIBRATION_SOUND_REFRACTORY),
					(int) s.get(RecognitionParameters.CALIBRATION_SOUND_LEAD));
			text.append(cut.isAccepted() ? cut.getStarts().toString() : cut.getRefusal() + " " + cut.getHeard()).append(';');
		}
		text.append('\n');
		InputAssessor.Parameters level = InputAssessor.Parameters.of(s);
		for( double confidence : new double[] { 0.6, 0.9 } ) {
			for( int clipEvery : new int[] { 0, 30, 7 } ) {
				for( int playMillis : new int[] { 300, 1500, 4000 } ) {
					text.append(check(level, confidence, clipEvery, playMillis)).append(';');
				}
			}
		}
		text.append('\n');
		PracticeSession.WaitParameters wait = PracticeSession.WaitParameters.of(s);
		for( long spacing : new long[] { 400, 1500, 4000 } ) {
			for( long louderAfter : new long[] { 120, 400, 900 } ) {
				for( int attacks : new int[] { 2, 4, 12 } ) {
					text.append(weak(wait, spacing, louderAfter, attacks)).append(' ');
				}
			}
		}
		return text.toString();
	}

	/** A delay calibration of eight clicks, each answered by a note as described; its result. */
	private static String calibrate(CalibrationRun.Parameters parameters, double[] r) {
		FakeClicks clicks = new FakeClicks();
		CalibrationRun run = new CalibrationRun(clicks, LATENCY);
		run.setParameters(parameters);
		List<Note> notes = new ArrayList<Note>();
		for( int k = 0 ; k < 8 ; k ++ ) {
			long click = T0 + CalibrationRun.LEAD_NANOS + k * CalibrationRun.PERIOD_NANOS;
			final double midi = r[1] + (k % 2) * 3;
			final double silent = r[2] / 1000.0;
			if( r[3] > 0 ) {
				final double before = r[4] + (k % 2) * 3;
				notes.add(new Note(click + (long) r[0] * MS - (long) r[3] * MS, (long) r[3] * MS, seconds -> before));
			}
			DoubleUnaryOperator pitch = seconds -> seconds < silent ? Double.NaN : midi;
			notes.add(new Note(click + (long) r[0] * MS, 250 * MS, pitch));
		}
		Set<Note> flagged = new HashSet<Note>();
		run.start();
		long end = T0 + CalibrationRun.LEAD_NANOS + 7 * CalibrationRun.PERIOD_NANOS + 3_000 * MS;
		for( long now = T0 ; now < end ; now += HOP ) {
			clicks.now = now;
			AnalysisFrame frame = FollowHarness.frameOf(notes, flagged, now);
			// the harness reads every pitch with 0.95: these trials need a pitch read with 0.9
			run.onFrame(new AnalysisFrame(frame.getTimestampNanos(), frame.getRmsDbfs(), frame.getPeakDbfs(), 0.0, frame.getLevelState(), frame.getF0Hz(),
					Double.isNaN(frame.getF0Hz()) ? 0.0 : 0.9, frame.isOnset()), now);
		}
		CalibrationTaps.Result result = run.result();
		return run.state() + " " + run.notesHeard() + (result == null ? "" : " " + result.status() + " " + Math.round(result.medianMillis()));
	}

	private static final class FakeClicks implements ClickPlayer {
		long now;

		public long click() {
			return this.now;
		}

		public void silence() {
		}
	}

	private static final int RATE = 44100;

	/** Four plucks: two {@code gapMillis} apart, then two more a second later; decaying 220 Hz tones over a faint noise. */
	private static float[] plucks(int gapMillis) {
		float[] samples = new float[RATE * 5];
		java.util.Random random = new java.util.Random(7);
		for( int i = 0 ; i < samples.length ; i ++ ) {
			samples[i] = (float) (0.0005 * random.nextGaussian());
		}
		int[] starts = { RATE / 2, RATE / 2 + gapMillis * RATE / 1000, RATE * 2, RATE * 3 + RATE / 2 };
		for( int start : starts ) {
			for( int i = 0 ; start + i < samples.length && i < RATE ; i ++ ) {
				samples[start + i] += (float) (0.4 * Math.exp(-i / (0.15 * RATE)) * Math.sin(2 * Math.PI * 220.0 * i / RATE));
			}
		}
		return samples;
	}

	/** A level check: frames every 10 ms, a pitch read with {@code confidence} while playing, one frame in {@code clipEvery} clipping (0: none). */
	private static String check(InputAssessor.Parameters parameters, double confidence, int clipEvery, int playMillis) {
		final InputAssessment[] result = { null };
		InputAssessor assessor = new InputAssessor(parameters, assessment -> result[0] = assessment);
		assessor.runLevelCheck();
		for( int k = 0 ; k < 1500 && result[0] == null ; k ++ ) {
			boolean playing = k * 10 < playMillis;
			AnalysisFrame.LevelState state = (clipEvery > 0 && k % clipEvery == 0 ? AnalysisFrame.LevelState.CLIPPING : AnalysisFrame.LevelState.SUITABLE);
			assessor.analysisFrame(new AnalysisFrame(k * 10 * MS, playing ? -20.0 : -60.0, -10.0, 0.0, playing ? state : AnalysisFrame.LevelState.TOO_LOW,
					playing ? 220.0 : Double.NaN, playing ? confidence : 0.0, false));
		}
		return result[0] == null ? "none" : result[0].getStatus() + "/" + result[0].getReason();
	}

	/** Attacks every {@code spacing} ms at -45 dBFS that grow to -25 dBFS {@code louderAfter} ms after they begin: is the input called weak? */
	private static String weak(PracticeSession.WaitParameters p, long spacing, long louderAfter, int attacks) {
		InputQuality quality = new InputQuality(p.weakFastDbfs, p.inputQualityAttacks, p.inputQualityRememberNanos, p.inputQualityAttackWindowNanos);
		for( long t = 0 ; t < attacks * spacing + 1000 ; t += 10 ) {
			long since = t % spacing;
			boolean onset = since == 0 && t / spacing < attacks;
			double level = since < louderAfter ? -45.0 : (since < louderAfter + 100 ? -25.0 : -70.0);
			quality.offer(new AnalysisFrame(t * MS, level, level + 6.0, 0.0, AnalysisFrame.LevelState.SUITABLE, 220.0, 0.9, onset));
		}
		return quality.isPoor() ? "P" : "ok";
	}
}
