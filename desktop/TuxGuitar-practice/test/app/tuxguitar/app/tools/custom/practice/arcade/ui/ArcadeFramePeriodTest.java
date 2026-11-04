package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;

/**
 * The periods of Arcade follow the display (spec 023, T069, FR-015, research R7): a frame lasts one display cycle, the timer ticks twice a frame, and the
 * quality controller's budget is the frame; with no rate known, today's 60 Hz values.
 */
public class ArcadeFramePeriodTest {

	@Test
	public void at60HzAFrameLasts16_7MsAndTheTimerTicksTwiceAFrame() {
		FramePeriod period = FramePeriod.of(60.0);
		assertTrue(period.isKnown());
		assertEquals(60.0, period.rateHz(), 1e-9);
		assertEquals(Math.round(1e9 / 60.0), period.frameNanos());
		assertEquals(period.frameNanos() / 2L, period.tickNanos());
		assertEquals(ArcadeCanvas.FRAME_NANOS, period.frameNanos(), "the same values as today at 60 Hz");
		assertEquals(ArcadeCanvas.TICK_NANOS, period.tickNanos());
	}

	@Test
	public void at120HzAFrameLasts8_3MsAndTheTimerTicksTwiceAFrame() {
		FramePeriod period = FramePeriod.of(120.0);
		assertEquals(Math.round(1e9 / 120.0), period.frameNanos());
		assertEquals(8_333_333L, period.frameNanos());
		assertEquals(period.frameNanos() / 2L, period.tickNanos());
		assertTrue(period.tickNanos() < ArcadeCanvas.TICK_NANOS, "faster than at 60 Hz");
	}

	@Test
	public void withNoRateKnownTodays60HzValues() {
		for (double unknown : new double[] { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
			FramePeriod period = FramePeriod.of(unknown);
			assertFalse(period.isKnown(), "rate " + unknown);
			assertEquals(ArcadeCanvas.FRAME_NANOS, period.frameNanos());
			assertEquals(ArcadeCanvas.TICK_NANOS, period.tickNanos());
		}
		assertEquals(ArcadeCanvas.FRAME_NANOS, FramePeriod.UNKNOWN.frameNanos());
	}

	@Test
	public void theQualityBudgetFollowsTheFrame() {
		for (double rate : new double[] { 60.0, 120.0, 0.0 }) {
			FramePeriod period = FramePeriod.of(rate);
			QualityController quality = period.quality();
			assertEquals(period.frameNanos(), quality.budgetNanos(), "rate " + rate);
			assertEquals(period.frameNanos(), ArcadeWindow.newQuality(period).budgetNanos());
		}
		// a paint of 7 ms is comfortable at 60 Hz and pressing at 120 Hz: the tier falls only at 120
		QualityController at60 = FramePeriod.of(60.0).quality();
		QualityController at120 = FramePeriod.of(120.0).quality();
		for (int i = 0; i < QualityController.WINDOW; i++) {
			at60.frame(7_000_000L);
			at120.frame(7_000_000L);
		}
		assertEquals(QualityTier.FULL, at60.tier());
		assertEquals(QualityTier.FULL.cheaper(), at120.tier());
	}
}
