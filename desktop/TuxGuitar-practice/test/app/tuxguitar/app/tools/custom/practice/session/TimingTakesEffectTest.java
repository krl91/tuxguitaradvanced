package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Each timing value takes effect (spec 026, T024, SC-001): every value of the section at one end of its range changes the windows of Arcade's
 * timing tolerance (at a slow and a fast beat), the windows of Follow's time to play (a lone note and notes 150 and 400 ms apart) or which measured
 * spreads call the route unstable.
 */
public class TimingTakesEffectTest {

	private static final long MS = 1_000_000L;

	@Test
	public void everyTimingValueChangesTheWindows() {
		String recommended = decisions(RecognitionSettings.defaults());
		List<String> withoutEffect = new ArrayList<String>();
		for( RecognitionParameter parameter : RecognitionParameters.of(Section.TIMING) ) {
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
		PracticeParameters p = PracticeParameters.of(settings);
		StringBuilder s = new StringBuilder();
		for( long beat : new long[] { 300 * MS, 700 * MS, 1200 * MS } ) {
			s.append(window(TimingTolerance.ofMillis(75, p.referenceBeatMillis()).windowFor(0L, beat, Long.MAX_VALUE, Long.MAX_VALUE))).append(';');
		}
		PlayTimeWindow play = p.playTimeWindow(600);
		s.append(window(play.windowFor(0L, 500 * MS, Long.MAX_VALUE, Long.MAX_VALUE))).append(';');
		s.append(window(play.windowFor(0L, 500 * MS, 150 * MS, 150 * MS))).append(';');
		s.append(window(play.windowFor(0L, 500 * MS, 400 * MS, 400 * MS))).append(';');
		for( double spread : new double[] { 30, 60, 100, 200, 350 } ) {
			s.append(ClockUncertainty.ofMeasurement(spread, p.unstableSpreadMillis()).isUnstable() ? 'U' : 's');
		}
		return s.toString();
	}

	private static String window(TimingTolerance.Window w) {
		return (w.outerFrom() / MS) + " " + (w.onTimeFrom() / MS) + " " + (w.onTimeTo() / MS) + " " + (w.outerTo() / MS);
	}
}
