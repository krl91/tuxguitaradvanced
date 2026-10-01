package app.tuxguitar.audio.engine.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class GainScaleTest {

	@Test
	public void theScaleCoversExactlyTheAllowedGainRange() {
		assertEquals(-6, GainScale.MIN_DB);
		assertEquals(18, GainScale.MAX_DB);
		assertEquals(24, GainScale.POSITIONS);
		assertEquals(InputGain.MIN_MULTIPLIER, GainScale.toMultiplier(0), 1e-9);
		assertEquals(InputGain.MAX_MULTIPLIER, GainScale.toMultiplier(GainScale.POSITIONS), 1e-6);
		assertEquals(1.0, GainScale.toMultiplier(GainScale.NEUTRAL_POSITION), 1e-12);
	}

	@Test
	public void positionsAndMultipliersRoundTrip() {
		for( int position = 0 ; position <= GainScale.POSITIONS ; position ++ ) {
			assertEquals(position, GainScale.toPosition(GainScale.toMultiplier(position)));
		}
		assertEquals(GainScale.NEUTRAL_POSITION, GainScale.toPosition(1.0));
	}

	@Test
	public void outOfRangeInputsAreLimited() {
		assertEquals(0, GainScale.toPosition(0.01));
		assertEquals(GainScale.POSITIONS, GainScale.toPosition(100.0));
		assertEquals(GainScale.toMultiplier(0), GainScale.toMultiplier(-5), 0.0);
		assertEquals(GainScale.toMultiplier(GainScale.POSITIONS), GainScale.toMultiplier(99), 0.0);
	}

	@Test
	public void theGainIsShownInWholeDecibels() {
		assertEquals("0 dB", GainScale.format(1.0));
		assertEquals("+6 dB", GainScale.format(InputGain.fromDb(6.0)));
		assertEquals("-3 dB", GainScale.format(InputGain.fromDb(-3.0)));
		assertEquals("+18 dB", GainScale.format(InputGain.MAX_MULTIPLIER));
	}
}
