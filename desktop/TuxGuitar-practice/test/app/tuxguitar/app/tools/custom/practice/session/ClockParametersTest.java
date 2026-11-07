package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The clock family (spec 026, T023, clarification 3, inventory.md "CLOCK"): its defaults are the former values of the tick interpolator and of the
 * latency bound, and the values built from the settings carry them.
 */
public class ClockParametersTest {

	private static final double MS = 1_000_000.0;

	@Test
	public void theDefaultsAreTheFormerValues() {
		assertEquals(Arrays.asList(600 * MS, 8.0, 150 * MS, 60 * MS, 30 * MS, 960.0), values(TickInterpolator.Parameters.defaults()));
		assertEquals(LatencyCompensation.LIMIT_MILLIS, RecognitionParameters.CLOCK_LATENCY_LIMIT.getDefault());
		assertEquals(LatencyCompensation.MAX_LIMIT_MILLIS, RecognitionParameters.CLOCK_LATENCY_LIMIT.getMaximum());
		assertEquals(TickInterpolator.Parameters.defaults(), new TickInterpolator(1920.0).parameters());
	}

	@Test
	public void theValuesCarryTheSettings() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.CLOCK_FIT_WINDOW, 900).with(RecognitionParameters.CLOCK_MIN_POINTS, 12)
				.with(RecognitionParameters.CLOCK_MIN_SPAN, 200).with(RecognitionParameters.CLOCK_STALL, 80).with(RecognitionParameters.CLOCK_EXTRAPOLATION, 40)
				.with(RecognitionParameters.CLOCK_SEEK_MARGIN, 1920);
		assertEquals(Arrays.asList(900 * MS, 12.0, 200 * MS, 80 * MS, 40 * MS, 1920.0), values(TickInterpolator.Parameters.of(s)));
	}

	@Test
	public void theSectionHoldsItsValues() {
		assertEquals(Arrays.asList(RecognitionParameters.CLOCK_FIT_WINDOW, RecognitionParameters.CLOCK_MIN_POINTS, RecognitionParameters.CLOCK_MIN_SPAN,
				RecognitionParameters.CLOCK_STALL, RecognitionParameters.CLOCK_EXTRAPOLATION, RecognitionParameters.CLOCK_SEEK_MARGIN,
				RecognitionParameters.CLOCK_LATENCY_LIMIT), RecognitionParameters.of(Section.CLOCK));
	}

	private static List<Double> values(TickInterpolator.Parameters p) {
		return Arrays.asList((double) p.fitWindowNanos, (double) p.minPoints, (double) p.minSpanNanos, (double) p.stallNanos, (double) p.extrapolationNanos,
				(double) p.seekMarginTicks);
	}
}
