package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Each clock value takes effect (spec 026, T023, SC-001): on a scripted sequencer (not the real one), every value of the section at one end of its range
 * changes what the clock says. The sequencer steps every 10 to 18 ms at a rate off the nominal one, is polled every 3 ms, pauses, jumps forward;
 * the clock's position, its stop and its rate are written down at each poll. The latency bound decides which offsets are accepted by hand and by a
 * calibration.
 */
public class ClockTakesEffectTest {

	private static final long MS = 1_000_000L;
	private static final double NOMINAL = 1920.0;
	private static final double RATE = 2000.0;

	@Test
	public void everyClockValueChangesWhatTheClockSays() {
		String recommended = decisions(RecognitionSettings.defaults());
		List<String> withoutEffect = new ArrayList<String>();
		for( RecognitionParameter parameter : RecognitionParameters.of(Section.CLOCK) ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			boolean changed = false;
			for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
				changed |= !decisions(RecognitionSettings.defaults().with(number, end)).equals(recommended);
			}
			if( !changed ) {
				withoutEffect.add(number.getKey());
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change nothing");
	}

	private static String decisions(RecognitionSettings settings) {
		StringBuilder s = new StringBuilder();
		TickInterpolator clock = new TickInterpolator(NOMINAL, TickInterpolator.Parameters.of(settings));
		// steps of 10, 14, 18 ms in turn; the sequencer plays for 1.5 s, pauses 150 ms, plays 0.5 s, jumps 1500 ticks forward, plays 0.5 s
		long[] steps = { 10, 14, 18 };
		long stepEnd = 0;
		int step = 0;
		long reported = 0;
		double ticks = 0.0;
		long previous = 0;
		for( long wall = 0 ; wall < 2_700 * MS ; wall += 3 * MS ) {
			boolean paused = wall >= 1_500 * MS && wall < 1_650 * MS;
			if( !paused ) {
				ticks += RATE * (wall - previous) / 1e9;
			}
			if( wall >= 2_150 * MS && wall < 2_153 * MS ) {
				ticks += 1500;
			}
			previous = wall;
			if( wall >= stepEnd ) {
				reported = (long) ticks;
				stepEnd = wall + steps[step++ % steps.length] * MS;
			}
			clock.observe(wall, reported);
			s.append(Math.round(clock.tickAt(wall + 5 * MS) * 10.0)).append(clock.isStalled(wall) ? "s" : "").append(' ')
					.append(Math.round(clock.ticksPerSecond())).append(';');
		}
		// the offsets accepted by hand and by a calibration
		double limit = settings.get(RecognitionParameters.CLOCK_LATENCY_LIMIT);
		for( double offset : new double[] { 150, 500, 1500, 2500 } ) {
			try {
				LatencyCompensation.manual(offset, limit);
				s.append(" manual ").append(offset);
			} catch( IllegalArgumentException refused ) {
				s.append(" refused ").append(offset);
			}
			CalibrationTaps taps = new CalibrationTaps(limit);
			for( double tap : Arrays.asList(offset - 4, offset - 2, offset, offset + 2, offset + 4) ) {
				taps.add(tap);
			}
			s.append(' ').append(taps.evaluate().status());
		}
		return s.toString();
	}
}
