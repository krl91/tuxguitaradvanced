package app.tuxguitar.app.tools.custom.practice.functional;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * How well the existing chain hears bass notes (spec 003 T003, research R11): single plucked notes from B0 to G2 on synthetic strings, through the
 * real analysis (a 2048-sample window at 48 kHz: an E1 has fewer than two periods in it), judged frame by frame with the settings of the application.
 * Reported, not asserted against a target: it decides whether a four-string bass is promised in the first Arcade version (open question O4). The
 * analysis window is not changed by this task. Synthetic plucks only: a real bass may do better or worse.
 */
@Tag("benchmark")
public class BassRecognitionBenchmarkTest {

	private static final int RATE = 48000;
	private static final int[] PITCHES = { 23, 24, 28, 29, 30, 31, 33, 35, 38, 40, 43 };
	private static final String[] NAMES = { "B0", "C1", "E1", "F1", "F#1", "G1", "A1", "B1", "D2", "E2", "G2" };
	private static final long[] SEEDS = { 1, 2, 3 };

	@Test
	public void theExistingChainOnSyntheticBassNotesIsReported() throws IOException {
		NoteInterpreter interpreter = new NoteInterpreter(0.8);
		TolerancePolicy tolerance = new TolerancePolicy(5.0, 35.0);
		InputAssessment assessment = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
		StringBuilder report = new StringBuilder();
		report.append("Recognition of synthetic bass notes through the existing chain (spec 003 T003), run of 2026-10-08.\n");
		report.append("One plucked note (Karplus-Strong, gain 0.3, 1.2 s) per run, ").append(SEEDS.length).append(" seeds per pitch; frames from 0.35 s to 1.0 s after the pluck.\n");
		report.append("right = reliable reading within 35 cents of the expected pitch; octave = the right note one octave up or down; other = a reliable reading of another pitch; none = no reliable pitch.\n\n");
		report.append("| note | midi | frames | right | octave | other | none | first right frame after the pluck |\n|---|---|---|---|---|---|---|---|\n");
		int totalFrames = 0;
		for (int p = 0; p < PITCHES.length; p++) {
			int frames = 0, right = 0, octave = 0, other = 0, none = 0;
			double firstRightSum = 0.0;
			int firstRightCount = 0;
			for (long seed : SEEDS) {
				float[] pad = new float[RATE / 2];
				float[] note = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(PITCHES[p]), RATE, (int) (1.2 * RATE), seed);
				float[] signal = new float[pad.length + note.length];
				for (int i = 0; i < note.length; i++) {
					signal[pad.length + i] = 0.3f * note[i];
				}
				List<AnalysisFrame> list = OfflineAnalysis.analyze(signal, RATE, RecognitionSettings.defaults(), false, new int[] { PITCHES[p] });
				double first = Double.NaN;
				for (AnalysisFrame frame : list) {
					double seconds = frame.getTimestampNanos() / 1e9 - 0.5;
					PitchReading reading = interpreter.interpret(frame.getF0Hz(), frame.getConfidence(), assessment);
					boolean reliable = reading.hasPitch() && reading.isReliable();
					if (Double.isNaN(first) && reliable && tolerance.matches(reading, PITCHES[p])) {
						first = seconds;
					}
					if (seconds < 0.35 || seconds > 1.0) {
						continue;
					}
					frames++;
					if (!reliable) {
						none++;
					} else if (tolerance.matches(reading, PITCHES[p])) {
						right++;
					} else if (Math.abs(reading.getMidi() - PITCHES[p]) == 12) {
						octave++;
					} else {
						other++;
					}
				}
				if (!Double.isNaN(first)) {
					firstRightSum += first;
					firstRightCount++;
				}
			}
			totalFrames += frames;
			report.append(String.format(java.util.Locale.ROOT, "| %s | %d | %d | %d%% | %d%% | %d%% | %d%% | %s |%n", NAMES[p], PITCHES[p], frames, 100 * right / Math.max(1, frames),
					100 * octave / Math.max(1, frames), 100 * other / Math.max(1, frames), 100 * none / Math.max(1, frames),
					firstRightCount == 0 ? "never" : String.format(java.util.Locale.ROOT, "%.0f ms", 1000.0 * firstRightSum / firstRightCount)));
		}
		Path dir = Paths.get("../../specs/003-arcade-mode/benchmark");
		Files.createDirectories(dir);
		Files.write(dir.resolve("bass-recognition-2026-10-08.txt"), report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report);
		assertTrue(totalFrames > 0);
	}
}
