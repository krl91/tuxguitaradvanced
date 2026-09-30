package app.tuxguitar.audio.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.audio.engine.testsupport.TechniqueSignals;

/** The synthetic generators must produce what their names say, since detectors are tested on them. */
public class TechniqueSignalsTest {

	private static final int RATE = 48000;

	private static double hz(int midi) {
		return SyntheticSignals.midiFrequency(midi);
	}

	@Test
	public void aFastPassagePlacesOneOnsetPerNoteAtTheRequestedRate() {
		TechniqueSignals.Passage passage = TechniqueSignals.fastPassage(new int[] { 52, 52, 53, 52 }, RATE, 8.0, 4800, 9600, 0.5, 1L);
		int[] onsets = passage.getOnsetSamples();
		assertEquals(4, onsets.length);
		assertEquals(4800, onsets[0]);
		assertEquals(6000, onsets[1] - onsets[0]);
		assertEquals(4800 + 3 * 6000 + 9600, passage.getSignal().length);
		assertArrayEquals(new int[] { 52, 52, 53, 52 }, passage.getMidi());
	}

	@Test
	public void generatorsAreDeterministic() {
		assertArrayEquals(TechniqueSignals.palmMutedPluck(52, RATE, 12000, 7L), TechniqueSignals.palmMutedPluck(52, RATE, 12000, 7L));
		assertArrayEquals(TechniqueSignals.vibrato(57, RATE, 12000, 5.5, 30.0), TechniqueSignals.vibrato(57, RATE, 12000, 5.5, 30.0));
	}

	@Test
	public void aLegatoStepChangesPitchWithoutANewAttack() {
		float[] step = TechniqueSignals.legatoStep(52, 55, RATE, 0.5, RATE);
		double before = TechniqueSignals.energyAt(step, 12000, 22000, RATE, hz(52));
		double after = TechniqueSignals.energyAt(step, 28000, 38000, RATE, hz(55));
		assertTrue(before > 0.0 && after > 0.0);
		// the level keeps decaying through the step: no new attack
		assertTrue(rms(step, 24000, 26000) < rms(step, 22000, 24000));
		assertTrue(rms(step, 26000, 28000) < rms(step, 22000, 24000));
	}

	@Test
	public void aSlideEndsOnItsTargetPitch() {
		float[] slide = TechniqueSignals.slide(52, 57, RATE, 0.2, 0.3, RATE);
		double atTarget = TechniqueSignals.energyAt(slide, 30000, 44000, RATE, hz(57));
		double atStart = TechniqueSignals.energyAt(slide, 30000, 44000, RATE, hz(52));
		assertTrue(atTarget > 20.0 * atStart);
	}

	@Test
	public void aBendReachesItsWrittenSemitones() {
		float[] bend = TechniqueSignals.bend(57, RATE, RATE, 0.0, 0.0, 0.4, 2.0);
		double bent = TechniqueSignals.energyAt(bend, 30000, 44000, RATE, hz(59));
		double unbent = TechniqueSignals.energyAt(bend, 30000, 44000, RATE, hz(57));
		assertTrue(bent > 20.0 * unbent);
	}

	@Test
	public void vibratoModulatesThePitchAndTheSteadyNoteDoesNot() {
		// pure sines, so that upward zero crossings give the pitch cycle by cycle
		float[] steady = TechniqueSignals.steadyNote(57, RATE, RATE, 1.0);
		float[] vibrato = TechniqueSignals.vibrato(57, RATE, RATE, 5.5, 40.0, 1.0);
		double steadySpread = pitchSpreadCents(steady);
		double vibratoSpread = pitchSpreadCents(vibrato);
		assertTrue(steadySpread < 2.0, "steady " + steadySpread);
		// a peak depth of 40 cents is about 28 cents of standard deviation
		assertEquals(28.0, vibratoSpread, 6.0);
	}

	/** Standard deviation, in cents, of the pitch measured cycle by cycle from upward zero crossings. */
	private static double pitchSpreadCents(float[] signal) {
		java.util.List<Double> crossings = new java.util.ArrayList<Double>();
		for( int i = 4000 ; i < signal.length - 1 ; i ++ ) {
			if( signal[i] <= 0.0f && signal[i + 1] > 0.0f ) {
				crossings.add(i + (-signal[i]) / (double) (signal[i + 1] - signal[i]));
			}
		}
		java.util.List<Double> cents = new java.util.ArrayList<Double>();
		double reference = 0.0;
		for( int i = 1 ; i < crossings.size() ; i ++ ) {
			double frequency = RATE / (crossings.get(i) - crossings.get(i - 1));
			reference += frequency;
			cents.add(frequency);
		}
		reference /= cents.size();
		double sum = 0.0;
		for( double frequency : cents ) {
			double deviation = 1200.0 * Math.log(frequency / reference) / Math.log(2.0);
			sum += deviation * deviation;
		}
		return Math.sqrt(sum / cents.size());
	}

	@Test
	public void aPalmMutedPluckDiesFasterThanAnOpenOne() {
		float[] open = SyntheticSignals.karplusStrong(hz(52), RATE, RATE / 2, 3L);
		float[] muted = TechniqueSignals.palmMutedPluck(52, RATE, RATE / 2, 3L);
		assertTrue(rms(muted, 12000, 18000) < 0.3 * rms(open, 12000, 18000));
	}

	@Test
	public void tremoloPickingRepeatsTheAttack() {
		float[] tremolo = TechniqueSignals.tremoloPicking(52, RATE, RATE, 12.0, 0.75, 5L);
		assertEquals(RATE, tremolo.length);
		assertTrue(rms(tremolo, 0, 4000) > 0.0);
	}

	@Test
	public void aNaturalHarmonicIsNearlyASine() {
		float[] harmonic = TechniqueSignals.naturalHarmonic(64, RATE, RATE);
		double fundamental = TechniqueSignals.energyAt(harmonic, 4000, 20000, RATE, hz(64));
		double octave = TechniqueSignals.energyAt(harmonic, 4000, 20000, RATE, 2.0 * hz(64));
		assertTrue(fundamental > 100.0 * octave);
	}

	@Test
	public void humAndScalingBehave() {
		float[] hum = TechniqueSignals.hum(RATE, RATE, 50.0, 0.01);
		assertTrue(TechniqueSignals.energyAt(hum, 0, RATE, RATE, 50.0) > 1e-5);
		float[] weak = TechniqueSignals.scaled(SyntheticSignals.sine(440.0, RATE, 4800, 0.5), 0.1);
		assertEquals(0.05, peak(weak), 1e-3);
	}

	private static double rms(float[] signal, int from, int to) {
		double sum = 0.0;
		for( int i = from ; i < to ; i ++ ) {
			sum += signal[i] * signal[i];
		}
		return Math.sqrt(sum / (to - from));
	}

	private static double peak(float[] signal) {
		double peak = 0.0;
		for( float sample : signal ) {
			peak = Math.max(peak, Math.abs(sample));
		}
		return peak;
	}
}
