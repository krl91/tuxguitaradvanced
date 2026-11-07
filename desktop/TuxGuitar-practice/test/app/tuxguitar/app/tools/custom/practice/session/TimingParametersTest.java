package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The timing family (spec 026, T024, inventory.md "TIMING"): its defaults are the former values of the timing window's reference beat, of the time to
 * play's advance and gap and of the unstable route's spread, and the values built from the settings carry them.
 */
public class TimingParametersTest {

	private static final long MS = 1_000_000L;

	@Test
	public void theDefaultsAreTheFormerValues() {
		PracticeParameters p = PracticeParameters.of(RecognitionSettings.defaults());
		assertEquals(500.0, p.referenceBeatMillis());
		assertEquals(PlayTimeWindow.ADVANCE_NANOS, p.playTimeWindow(600).advanceNanos());
		assertEquals(PlayTimeWindow.GAP_NANOS, p.playTimeWindow(600).gapNanos());
		assertEquals(ClockUncertainty.UNSTABLE_SPREAD_MILLIS, p.unstableSpreadMillis());
		// the same windows as before, at a beat where nothing is clamped
		TimingTolerance.Window before = TimingTolerance.ofMillis(75).windowFor(0L, 700 * MS, Long.MAX_VALUE, Long.MAX_VALUE);
		TimingTolerance.Window after = TimingTolerance.ofMillis(75, p.referenceBeatMillis()).windowFor(0L, 700 * MS, Long.MAX_VALUE, Long.MAX_VALUE);
		assertEquals(Arrays.asList(before.onTimeFrom(), before.onTimeTo(), before.outerFrom(), before.outerTo()),
				Arrays.asList(after.onTimeFrom(), after.onTimeTo(), after.outerFrom(), after.outerTo()));
	}

	@Test
	public void theValuesCarryTheSettings() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.TIMING_REFERENCE_BEAT, 750)
				.with(RecognitionParameters.TIMING_ADVANCE, 120).with(RecognitionParameters.TIMING_GAP, 35).with(RecognitionParameters.TIMING_CLOCK_SPREAD, 150);
		PracticeParameters p = PracticeParameters.of(s);
		assertEquals(750.0, p.referenceBeatMillis());
		assertEquals(120 * MS, p.playTimeWindow(600).advanceNanos());
		assertEquals(35 * MS, p.playTimeWindow(600).gapNanos());
		assertEquals(150.0, p.unstableSpreadMillis());
		assertEquals(600, p.playTimeWindow(600).playTimeMillis());
	}

	@Test
	public void theSectionHoldsItsValues() {
		assertEquals(Arrays.asList(RecognitionParameters.TIMING_REFERENCE_BEAT, RecognitionParameters.TIMING_ADVANCE, RecognitionParameters.TIMING_GAP,
				RecognitionParameters.TIMING_CLOCK_SPREAD), RecognitionParameters.of(Section.TIMING));
	}
}
