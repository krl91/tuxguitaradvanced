package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;

/** Spec 002 FR-015: the weak-input message follows the level the learner plays at, only after several attacks, and recovers. */
public class InputQualityTest {

	private static final long HOP = 256L * 1_000_000_000L / 48000L;
	private long now;

	private static AnalysisFrame frame(long t, double rms, boolean onset, AnalysisFrame.LevelState state) {
		return new AnalysisFrame(t, rms, rms + 6.0, 0.0, state, Double.NaN, 0.0, onset);
	}

	/** One attack: an onset frame, then {@code 300 ms} of decaying level starting at {@code peak} dBFS, then silence of {@code gapMillis}. */
	private void attack(InputQuality quality, double peak, int gapMillis, AnalysisFrame.LevelState state) {
		long end = this.now + 300_000_000L;
		boolean first = true;
		while( this.now < end ) {
			this.now += HOP;
			double rms = peak - 8.0 * (300_000_000L - (end - this.now)) / 300_000_000.0;
			quality.offer(frame(this.now, first ? peak : rms, first, state));
			first = false;
		}
		long gap = this.now + gapMillis * 1_000_000L;
		while( this.now < gap ) {
			this.now += HOP;
			quality.offer(frame(this.now, -70.0, false, AnalysisFrame.LevelState.TOO_LOW));
		}
	}

	@Test
	public void weakNotesPlayedSeveralTimesMakeTheInputPoor() {
		InputQuality quality = new InputQuality(-30.0);
		attack(quality, -36.0, 400, AnalysisFrame.LevelState.SUITABLE);
		attack(quality, -37.0, 400, AnalysisFrame.LevelState.SUITABLE);
		assertFalse(quality.isPoor(), "two attacks are not enough to tell");
		attack(quality, -35.0, 400, AnalysisFrame.LevelState.SUITABLE);
		attack(quality, -36.0, 400, AnalysisFrame.LevelState.SUITABLE);
		assertTrue(quality.isPoor());
		assertEquals(-36.0, quality.getPlayedLevelDbfs(), 1.0);
	}

	@Test
	public void strongNotesAreNeverPoor() {
		InputQuality quality = new InputQuality(-30.0);
		for( int i = 0 ; i < 6 ; i ++ ) {
			attack(quality, -18.0, 400, AnalysisFrame.LevelState.SUITABLE);
		}
		assertFalse(quality.isPoor());
	}

	@Test
	public void theMessageGoesWhenTheLearnerPlaysLouder() {
		InputQuality quality = new InputQuality(-30.0);
		for( int i = 0 ; i < 4 ; i ++ ) {
			attack(quality, -38.0, 400, AnalysisFrame.LevelState.SUITABLE);
		}
		assertTrue(quality.isPoor());
		for( int i = 0 ; i < 3 ; i ++ ) {
			attack(quality, -20.0, 400, AnalysisFrame.LevelState.SUITABLE);
		}
		assertFalse(quality.isPoor());
	}

	@Test
	public void silenceAloneAndClippingAreNotAWeakInput() {
		InputQuality quality = new InputQuality(-30.0);
		for( int i = 0 ; i < 50 ; i ++ ) {
			this.now += HOP * 100;
			quality.offer(frame(this.now, -70.0, false, AnalysisFrame.LevelState.TOO_LOW));
		}
		assertFalse(quality.isPoor());
		for( int i = 0 ; i < 4 ; i ++ ) {
			attack(quality, -38.0, 400, AnalysisFrame.LevelState.CLIPPING);
		}
		assertFalse(quality.isPoor(), "clipping frames are ignored: another message handles them");
	}

	@Test
	public void oldAttacksAreForgotten() {
		InputQuality quality = new InputQuality(-30.0);
		for( int i = 0 ; i < 4 ; i ++ ) {
			attack(quality, -38.0, 400, AnalysisFrame.LevelState.SUITABLE);
		}
		assertTrue(quality.isPoor());
		// a long silence, then a single loud note
		for( int i = 0 ; i < 40 ; i ++ ) {
			this.now += 300_000_000L;
			quality.offer(frame(this.now, -70.0, false, AnalysisFrame.LevelState.TOO_LOW));
		}
		attack(quality, -20.0, 400, AnalysisFrame.LevelState.SUITABLE);
		assertFalse(quality.isPoor());
	}

	@Test
	public void resetForgetsEverything() {
		InputQuality quality = new InputQuality(-30.0);
		for( int i = 0 ; i < 4 ; i ++ ) {
			attack(quality, -38.0, 400, AnalysisFrame.LevelState.SUITABLE);
		}
		quality.reset();
		assertFalse(quality.isPoor());
	}
}
