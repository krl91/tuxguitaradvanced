package app.tuxguitar.audio.engine.pitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.pitch.TolerancePolicy.TunerState;

public class TolerancePolicyTest {

	private final TolerancePolicy policy = new TolerancePolicy(5.0, 25.0);

	@Test
	public void constructorEnforcesToleranceInvariant() {
		assertThrows(IllegalArgumentException.class, () -> new TolerancePolicy(-1.0, 20.0));
		assertThrows(IllegalArgumentException.class, () -> new TolerancePolicy(20.0, 19.0));
		assertThrows(IllegalArgumentException.class, () -> new TolerancePolicy(5.0, 50.0));
	}

	@Test
	public void fretAllowanceWidensOnlyTheSharpSideInProportionToTheFret() {
		TolerancePolicy fretted = new TolerancePolicy(5.0, 25.0, 0.7);
		// open string: unchanged
		assertTrue(fretted.matches(reading(69, 25.0, true), 69, 0));
		assertFalse(fretted.matches(reading(69, 26.0, true), 69, 0));
		// fret 10: 25 + 7 cents on the sharp side
		assertTrue(fretted.matches(reading(69, 32.0, true), 69, 10));
		assertFalse(fretted.matches(reading(69, 33.0, true), 69, 10));
		// the flat side never changes
		assertTrue(fretted.matches(reading(69, -25.0, true), 69, 10));
		assertFalse(fretted.matches(reading(69, -26.0, true), 69, 10));
		// 0 switches it off
		assertFalse(new TolerancePolicy(5.0, 25.0, 0.0).matches(reading(69, 32.0, true), 69, 10));
	}

	@Test
	public void theSharpAllowanceNeverReachesTheNeighbouringNoteAndNeverAcceptsAnotherNote() {
		TolerancePolicy fretted = new TolerancePolicy(5.0, 35.0, 2.0);
		assertEquals(49.0, fretted.sharpLimitCents(24), 1e-9);
		assertFalse(fretted.matches(reading(70, -40.0, true), 69, 24));
		assertFalse(fretted.matches(reading(69, 25.0, false), 69, 12));
		assertThrows(IllegalArgumentException.class, () -> new TolerancePolicy(5.0, 25.0, -0.1));
	}

	@Test
	public void classifiesTunerStateFromReliableReading() {
		assertEquals(TunerState.FLAT, this.policy.tunerState(reading(69, -6.0, true)));
		assertEquals(TunerState.IN_TUNE, this.policy.tunerState(reading(69, 5.0, true)));
		assertEquals(TunerState.SHARP, this.policy.tunerState(reading(69, 6.0, true)));
		assertEquals(TunerState.NO_READING, this.policy.tunerState(reading(69, 0.0, false)));
		assertEquals(TunerState.NO_READING, this.policy.tunerState(PitchReading.noReading()));
	}

	@Test
	public void practiceMatchRequiresReliabilitySameMidiAndCentTolerance() {
		assertTrue(this.policy.matches(reading(69, 25.0, true), 69));
		assertFalse(this.policy.matches(reading(69, 25.1, true), 69));
		assertFalse(this.policy.matches(reading(69, 0.0, false), 69));
		assertFalse(this.policy.matches(reading(70, 0.0, true), 69));
	}

	@Test
	public void adjacentSemitoneAndWrongOctaveNeverMatch() {
		assertFalse(this.policy.matches(reading(70, -49.0, true), 69));
		assertFalse(this.policy.matches(reading(81, 0.0, true), 69));
	}

	private static PitchReading reading(int midi, double cents, boolean reliable) {
		return new PitchReading(midi, "A", midi / 12 - 1, cents, reliable);
	}
}
