package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ChordEvidence;
import app.tuxguitar.audio.engine.analysis.PitchCandidate;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * With the native engine the frames carry the evidence of the expected chord (spec 023, T044, contract §4): the matcher uses it instead of computing
 * it, and the rule that follows is the same.
 */
public class ChordMatcherFrameEvidenceTest {

	private static final int RATE = 48000;
	private static final int[] E_MAJOR = { 40, 47, 52, 56, 59, 64 };
	private static final int[] NONE = new int[0];
	/** The Java engine: the frames carry no chord evidence, as today. */
	private static final RecognitionSettings JAVA = RecognitionSettings.defaults().with(RecognitionParameters.ENGINE_PITCH, "fastyin");

	private final ChordMatcher matcher = new ChordMatcher(ChordMatcher.Parameters.defaults());

	@Test
	public void theFramesEvidenceForTheChordBeingMatchedIsUsedInsteadOfComputed() {
		AnalysisFrame silent = lastFrame(new float[RATE]);
		assertFalse(this.matcher.check(silent, E_MAJOR, NONE).isComplete(), "computed from the silent spectrum: nothing is heard");
		AnalysisFrame claimed = withEvidence(silent, everyPitchClear(E_MAJOR));
		ChordMatcher.Result result = this.matcher.check(claimed, E_MAJOR, NONE);
		assertTrue(result.isComplete(), "the frame's evidence decides, not a new evaluation of the spectrum");
		assertArrayEquals(E_MAJOR, result.getHeard());
	}

	@Test
	public void theEvidenceOfAnotherChordIsIgnored() {
		AnalysisFrame silent = lastFrame(new float[RATE]);
		AnalysisFrame otherChord = withEvidence(silent, everyPitchClear(new int[] { 45, 52, 57 }));
		assertFalse(this.matcher.check(otherChord, E_MAJOR, NONE).isComplete());
	}

	@Test
	public void withTheEvaluatedEvidenceTheResultIsTheSameAsWithoutIt() {
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(strum(E_MAJOR), RATE, JAVA, true, null);
		int complete = 0;
		for( AnalysisFrame frame : frames ) {
			if( frame.getSpectrum() == null ) {
				continue;
			}
			ChordMatcher.Result computed = this.matcher.check(frame, E_MAJOR, NONE);
			assertEquals(this.matcher.check(frame.getSpectrum(), E_MAJOR, NONE).isComplete(), computed.isComplete(), "the frame's own path");
			ChordEvidence evidence = ChordEvidence.evaluate(frame.getSpectrum(), E_MAJOR, NONE);
			ChordMatcher.Result carried = this.matcher.check(withEvidence(frame, evidence), E_MAJOR, NONE);
			assertArrayEquals(computed.getHeard(), carried.getHeard());
			assertArrayEquals(computed.getMissing(), carried.getMissing());
			assertArrayEquals(computed.getUnverifiable(), carried.getUnverifiable());
			complete += computed.isComplete() ? 1 : 0;
		}
		assertTrue(complete > 0, "the strum is heard complete in some frames");
	}

	private static AnalysisFrame lastFrame(float[] mono) {
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(mono, RATE, JAVA, true, null);
		return frames.get(frames.size() - 1);
	}

	private static AnalysisFrame withEvidence(AnalysisFrame f, ChordEvidence evidence) {
		return new AnalysisFrame(f.getTimestampNanos(), f.getRmsDbfs(), f.getPeakDbfs(), f.getClippedRatio(), f.getLevelState(), f.getF0Hz(), f.getConfidence(),
				f.isOnset(), f.getSpectrum(), f.getTargetEvidence(), f.getTechniqueFeatures(), f.getAttackEvidence(), f.getCaptureSampleEnd(), evidence);
	}

	private static ChordEvidence everyPitchClear(int[] pitches) {
		List<ChordEvidence.Pitch> list = new ArrayList<ChordEvidence.Pitch>();
		for( int midi : pitches ) {
			list.add(ChordEvidence.pitch(midi, 1.0, 1.0, false, 1.0, 1.0, 8, 1.0, 8));
		}
		return ChordEvidence.of(list, Collections.<PitchCandidate>emptyList());
	}

	/** Silence, then a Karplus–Strong strum, 10 ms between strings (as {@code ChordPracticeTest}). */
	private static float[] strum(int[] pitches) {
		int start = (int) (0.3 * RATE);
		float[] signal = new float[start + (int) (1.2 * RATE)];
		for( int i = 0; i < pitches.length; i++ ) {
			int onset = start + i * RATE / 100;
			int period = Math.max(2, (int) Math.round(RATE / (440.0 * Math.pow(2.0, (pitches[i] - 69) / 12.0))));
			Random random = new Random(11L + i);
			float[] delay = new float[period];
			for( int k = 0; k < delay.length; k++ ) {
				delay[k] = (float) (random.nextDouble() * 2.0 - 1.0);
			}
			for( int j = onset; j < signal.length; j++ ) {
				int current = (j - onset) % period;
				signal[j] += 0.2f * delay[current];
				delay[current] = 0.996f * 0.5f * (delay[current] + delay[(current + 1) % period]);
			}
		}
		return signal;
	}
}
