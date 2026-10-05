package app.tuxguitar.app.tools.custom.tunernext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;

public class TunerSmootherTest {

	private static final long HOP = 5_800_000L;
	private static final double MIN = 0.8;
	private final TunerSmoother smoother = new TunerSmoother();
	private long now;

	private static double hz(double centsFromA440) {
		return 440.0 * Math.pow(2.0, centsFromA440 / 1200.0);
	}

	private AnalysisFrame frame(double f0, double confidence) {
		this.now += HOP;
		return new AnalysisFrame(this.now, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0, confidence, false);
	}

	private void feed(double centsFromA440, double confidence, int hops) {
		for( int i = 0 ; i < hops ; i ++ ) {
			this.smoother.offer(frame(hz(centsFromA440), confidence), MIN);
		}
	}

	private double shownCents() {
		AnalysisFrame shown = this.smoother.smooth(frame(Double.NaN, 0.0));
		return 1200.0 * Math.log(shown.getF0Hz() / 440.0) / Math.log(2.0);
	}

	@Test
	public void aShakyReadingBecomesSteady() {
		Random random = new Random(5);
		for( int i = 0 ; i < 60 ; i ++ ) {
			this.smoother.offer(frame(hz(12.0 + (random.nextDouble() * 2 - 1) * 8.0), 0.95), MIN);
		}
		assertEquals(12.0, shownCents(), 1.5);
	}

	@Test
	public void aSingleOctaveGlitchDoesNotMoveIt() {
		feed(10.0, 0.95, 30);
		this.smoother.offer(frame(hz(10.0) * 2, 0.95), MIN);
		this.smoother.offer(frame(hz(10.0) / 2, 0.9), MIN);
		feed(10.0, 0.95, 5);
		assertEquals(10.0, shownCents(), 0.5);
	}

	@Test
	public void unsureHopsDoNotFreezeTheReadingForAShortGap() {
		feed(-7.0, 0.95, 40);
		// a fading string: 150 ms of hops below the certainty threshold
		for( int i = 0 ; i < 26 ; i ++ ) {
			this.smoother.offer(frame(hz(-7.0), 0.4), MIN);
		}
		AnalysisFrame shown = this.smoother.smooth(frame(hz(-7.0), 0.4));
		assertTrue(shown.getConfidence() >= MIN, "still a confident reading after a short gap");
		assertEquals(-7.0, shownCents(), 0.5);
	}

	@Test
	public void aLongSilenceGivesNoReading() {
		feed(-7.0, 0.95, 40);
		for( int i = 0 ; i < 80 ; i ++ ) {
			this.smoother.offer(frame(Double.NaN, 0.0), MIN);
		}
		AnalysisFrame shown = this.smoother.smooth(frame(Double.NaN, 0.0));
		assertTrue(Double.isNaN(shown.getF0Hz()), "nothing to show after a silence");
	}

	@Test
	public void aNewNoteReplacesTheOldOneWithinAFewHops() {
		feed(0.0, 0.95, 40);
		feed(500.0, 0.95, 6);
		assertEquals(500.0, shownCents(), 1.0);
	}

	@Test
	public void aStepOfAFewCentsIsFollowedWithinAQuarterSecond() {
		feed(-20.0, 0.95, 40);
		feed(-5.0, 0.95, 44);
		assertEquals(-5.0, shownCents(), 1.0);
	}
}
