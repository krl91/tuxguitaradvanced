package app.tuxguitar.audio.engine.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.LevelMeasurement;
import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Owner requirement 2026-09-28: practice must never advance on silence, ambient noise or
 * transients, and needs a fresh onset plus a stable, confident pitch. This runs the production
 * analysis (level, FastYin, articulation, with the engine's and practice's provisional defaults)
 * over whole signals and applies the practice acceptance preconditions: an onset, then a run of
 * reliable frames at a usable level on one note within the practice tolerance lasting the
 * stability window. Negative signals must never meet them for any pitch; every real-guitar
 * reference clip must meet them on its played note (the stricter rule must not block real notes).
 */
class AcceptancePreconditionTest {

	private static final int RATE = BenchmarkFixtures.SAMPLE_RATE;
	/** PracticeWindow defaults: confidence 0.80, 20 hops, tuner ±5, practice ±35 cents. */
	private static final double CONFIDENCE_GATE = 0.80;
	private static final int STABILITY_HOPS = 20;
	private static final TolerancePolicy TOLERANCE = new TolerancePolicy(5.0, 35.0);

	@Test
	void silenceNeverMeetsTheAcceptancePreconditions() throws Exception {
		assertNoAcceptance("digital silence", SyntheticSignals.silence(3 * RATE));
		for (BenchmarkFixtures.Fixture fixture : BenchmarkFixtures.readManifest()) {
			if ("silence".equals(fixture.role)) {
				assertNoAcceptance(fixture.file, BenchmarkFixtures.readRecording(fixture).samples);
			}
		}
	}

	@Test
	void ambientNoiseNeverMeetsTheAcceptancePreconditions() {
		assertNoAcceptance("white noise -50 dBFS", SyntheticSignals.noise(5 * RATE, 0.0055, 1L));
		assertNoAcceptance("white noise -30 dBFS", SyntheticSignals.noise(5 * RATE, 0.055, 2L));
		assertNoAcceptance("brown noise", brownNoise(5 * RATE, 0.05, 3L));
		assertNoAcceptance("mains hum -55 dBFS", SyntheticSignals.harmonics(50.0, RATE, 5 * RATE, 0.0015, 0.001, 0.0005));
		float[] roomWithHum = SyntheticSignals.noise(5 * RATE, 0.01, 4L);
		SyntheticSignals.mix(roomWithHum, SyntheticSignals.harmonics(100.0, RATE, 5 * RATE, 0.001, 0.0006), 0);
		assertNoAcceptance("room noise and hum", roomWithHum);
	}

	@Test
	void transientsNeverMeetTheAcceptancePreconditions() {
		float[] clicks = SyntheticSignals.silence(5 * RATE);
		for (int i = RATE / 4; i < clicks.length; i += RATE * 3 / 10) {
			clicks[i] = 0.9f;
		}
		assertNoAcceptance("clicks", clicks);

		float[] knocks = SyntheticSignals.noise(5 * RATE, 0.003, 5L);
		Random random = new Random(6L);
		for (int start = RATE / 4; start < knocks.length - RATE / 10; start += RATE / 2) {
			for (int i = 0; i < RATE / 50; i++) {
				knocks[start + i] += (float) ((random.nextDouble() * 2.0 - 1.0) * 0.6 * Math.exp(-i / (RATE / 400.0)));
			}
		}
		assertNoAcceptance("body knocks", knocks);
	}

	@Test
	void realGuitarReferencePlucksStillMeetThemOnThePlayedNote() throws Exception {
		int checked = 0;
		for (BenchmarkFixtures.Fixture fixture : BenchmarkFixtures.readManifest()) {
			if ("reference".equals(fixture.role)) {
				int accepted = firstAcceptedMidi(BenchmarkFixtures.readRecording(fixture).samples);
				assertEquals(fixture.manifestMidi.intValue(), accepted, fixture.file + " first acceptable note");
				checked++;
			}
		}
		assertTrue(checked >= 10, "reference clips checked: " + checked);
	}

	@Test
	void aSyntheticPluckMeetsThem() {
		float[] pluck = SyntheticSignals.silence(RATE);
		SyntheticSignals.mix(pluck, SyntheticSignals.karplusStrong(329.63, RATE, RATE / 2, 7L), RATE / 4);
		assertEquals(64, firstAcceptedMidi(pluck));
	}

	private static void assertNoAcceptance(String name, float[] samples) {
		assertEquals(-1, firstAcceptedMidi(samples), name + " met the acceptance preconditions");
	}

	/** The first MIDI note meeting the practice preconditions, or -1. */
	private static int firstAcceptedMidi(float[] samples) {
		TarsosFastYinEstimator estimator = new TarsosFastYinEstimator(RATE, BenchmarkFixtures.WINDOW_SIZE, BenchmarkFixtures.HOP_SIZE);
		LevelAnalyzer levels = new LevelAnalyzer(AudioEngine.DEFAULT_TOO_LOW_RMS_DBFS, AudioEngine.DEFAULT_TOO_HIGH_RMS_DBFS,
				AudioEngine.DEFAULT_CLIPPED_SAMPLE_LEVEL, AudioEngine.DEFAULT_CLIPPING_RATIO);
		ArticulationDetector articulation = new ArticulationDetector();
		NoteInterpreter interpreter = new NoteInterpreter(CONFIDENCE_GATE);
		InputAssessment suitable = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
		float[] window = new float[BenchmarkFixtures.WINDOW_SIZE];
		boolean armed = false;
		int runMidi = -1;
		int runLength = 0;
		for (int offset = 0; offset + window.length <= samples.length; offset += BenchmarkFixtures.HOP_SIZE) {
			System.arraycopy(samples, offset, window, 0, window.length);
			LevelMeasurement level = levels.analyze(window);
			PitchEstimate estimate = estimator.estimate(window);
			armed |= articulation.process(window);
			PitchReading reading = interpreter.interpret(estimate.isPitched() ? estimate.getF0Hz() : Double.NaN,
					estimate.getProbability(), suitable);
			boolean usable = (level.getLevelState() == AnalysisFrame.LevelState.SUITABLE
					|| level.getLevelState() == AnalysisFrame.LevelState.TOO_HIGH);
			if (usable && reading.hasPitch() && TOLERANCE.matches(reading, reading.getMidi())) {
				runLength = (reading.getMidi() == runMidi ? runLength + 1 : 1);
				runMidi = reading.getMidi();
			} else {
				runMidi = -1;
				runLength = 0;
			}
			if (armed && runLength > STABILITY_HOPS) {
				return runMidi;
			}
		}
		return -1;
	}

	private static float[] brownNoise(int count, double amplitude, long seed) {
		Random random = new Random(seed);
		float[] signal = new float[count];
		double value = 0.0;
		for (int i = 0; i < count; i++) {
			value = 0.995 * value + (random.nextDouble() * 2.0 - 1.0) * 0.1;
			signal[i] = (float) (amplitude * value);
		}
		return signal;
	}
}
