package app.tuxguitar.audio.engine.technique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.function.IntToDoubleFunction;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.technique.TechniqueEvidence.Verdict;

public class PitchTechniqueDetectorsTest {

	private static final double HOP = 256.0 / 44100.0;

	/** A segment of {@code seconds} whose pitch (MIDI) follows the function of the time. */
	private static NoteSegment pitch(double seconds, java.util.function.DoubleUnaryOperator midiAt) {
		int n = (int) (seconds / HOP);
		double[] midi = new double[n];
		double[] level = new double[n];
		for( int i = 0 ; i < n ; i ++ ) {
			midi[i] = midiAt.applyAsDouble(i * HOP);
			level[i] = -20.0;
		}
		return new NoteSegment(midi, level, new boolean[n], HOP);
	}

	private static NoteSegment levels(int n, IntToDoubleFunction levelAt, int onsetHop) {
		double[] midi = new double[n];
		double[] level = new double[n];
		boolean[] onset = new boolean[n];
		for( int i = 0 ; i < n ; i ++ ) {
			midi[i] = 60.0;
			level[i] = levelAt.applyAsDouble(i);
			onset[i] = (i == onsetHop);
		}
		return new NoteSegment(midi, level, onset, HOP);
	}

	private final VibratoDetector vibrato = new VibratoDetector(VibratoDetector.Parameters.defaults());
	private final BendDetector bend = new BendDetector(BendDetector.Parameters.defaults());
	private final SlideDetector slide = new SlideDetector(SlideDetector.Parameters.defaults());
	private final LegatoDetector legato = new LegatoDetector(LegatoDetector.Parameters.defaults());

	@Test
	public void aSixHertzWobbleIsAVibratoAndASteadyPitchIsNot() {
		NoteSegment wobble = pitch(1.0, t -> 60.0 + 0.30 * Math.sin(2 * Math.PI * 6.0 * t));
		TechniqueEvidence found = this.vibrato.evaluate(wobble);
		assertEquals(Verdict.DETECTED, found.getVerdict());
		assertEquals(6.0, found.getMeasurements().get("rateHz"), 1.0);
		assertTrue(found.getMeasurements().get("depthCents") > 30.0);

		assertEquals(Verdict.NOT_DETECTED, this.vibrato.evaluate(pitch(1.0, t -> 60.0 + 0.02 * Math.sin(2 * Math.PI * 6.0 * t))).getVerdict());
		assertEquals(Verdict.NOT_DETECTED, this.vibrato.evaluate(pitch(1.0, t -> 60.0)).getVerdict());
	}

	@Test
	public void aVibratoThatIsTooSlowOrTooShortIsNotCalledDetected() {
		TechniqueEvidence slow = this.vibrato.evaluate(pitch(2.0, t -> 60.0 + 0.30 * Math.sin(2 * Math.PI * 0.8 * t)));
		assertTrue(slow.getVerdict() != Verdict.DETECTED);
		assertEquals(Verdict.UNCERTAIN, this.vibrato.evaluate(pitch(0.2, t -> 60.0 + 0.30 * Math.sin(2 * Math.PI * 6.0 * t))).getVerdict());
	}

	@Test
	public void aRampingPitchIsABendAndASteadyOneIsNot() {
		TechniqueEvidence found = this.bend.evaluate(pitch(0.6, t -> 60.0 + Math.min(1.0, t / 0.3)));
		assertEquals(Verdict.DETECTED, found.getVerdict());
		assertEquals(100.0, found.getMeasurements().get("rangeCents"), 25.0);
		assertEquals(Verdict.NOT_DETECTED, this.bend.evaluate(pitch(0.6, t -> 60.0 + 0.05 * Math.sin(40 * t))).getVerdict());
	}

	@Test
	public void aGlideThroughTheIntermediatePitchesIsASlideAndAJumpIsNot() {
		TechniqueEvidence glide = this.slide.evaluate(pitch(0.6, t -> 60.0 + 3.0 * Math.min(1.0, t / 0.5)));
		assertEquals(Verdict.DETECTED, glide.getVerdict());
		assertTrue(glide.getMeasurements().get("monotony") > 0.75);
		// a straight jump of three semitones in one hop: most of the note is steady
		TechniqueEvidence jump = this.slide.evaluate(pitch(0.6, t -> t < 0.2 ? 60.0 : 63.0));
		assertTrue(jump.getVerdict() != Verdict.DETECTED);
	}

	@Test
	public void octaveErrorsAreNotTakenForPitchMotion() {
		NoteSegment glitchy = pitch(1.0, t -> ((int) (t / HOP)) % 10 == 0 ? 72.0 : 60.0);
		assertEquals(Verdict.NOT_DETECTED, this.bend.evaluate(glitchy).getVerdict());
		assertEquals(Verdict.NOT_DETECTED, this.vibrato.evaluate(glitchy).getVerdict());
	}

	@Test
	public void anUnvoicedSegmentIsUncertain() {
		NoteSegment mute = pitch(1.0, t -> Double.NaN);
		assertEquals(Verdict.UNCERTAIN, this.vibrato.evaluate(mute).getVerdict());
		assertEquals(Verdict.UNCERTAIN, this.bend.evaluate(mute).getVerdict());
		assertEquals(Verdict.UNCERTAIN, this.slide.evaluate(mute).getVerdict());
	}

	@Test
	public void aNoteStartedWithoutArticulationOrLevelJumpIsLegato() {
		assertEquals(Verdict.DETECTED, this.legato.evaluate(levels(20, i -> -20.0 - 0.1 * i, -1)).getVerdict());
		assertEquals(Verdict.NOT_DETECTED, this.legato.evaluate(levels(20, i -> -20.0, 3)).getVerdict());
		assertEquals(Verdict.NOT_DETECTED, this.legato.evaluate(levels(20, i -> -30.0 + 3.0 * i, -1)).getVerdict());
		assertEquals(Verdict.UNCERTAIN, this.legato.evaluate(levels(20, i -> -30.0 + 0.2 * i, -1)).getVerdict());
	}

	@Test
	public void evidenceIsValidated() {
		assertThrows(IllegalArgumentException.class, () -> new TechniqueEvidence(DetectableTechnique.BEND, Verdict.DETECTED, 1.5, Collections.<String, Double>emptyMap()));
		assertThrows(IllegalArgumentException.class, () -> new NoteSegment(new double[2], new double[3], new boolean[2], HOP));
	}
}
