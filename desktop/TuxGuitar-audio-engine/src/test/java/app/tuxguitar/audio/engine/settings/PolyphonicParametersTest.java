package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.PolyphonicAnalyzer;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.nativeengine.FrameRecords;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * The polyphonic family (spec 026, T015, inventory.md "POLYPHONIC_1" and "POLYPHONIC_2"): its defaults are the analyser's former values, the
 * analyser's parameters and the native tuning array carry the settings, the FFT size and the bands stay, and a frame read from the native engine
 * carries the parameters it was analysed with.
 */
public class PolyphonicParametersTest {

	@Test
	public void theDefaultsAreTheFormerValues() {
		PolyphonicAnalyzer.Parameters former = PolyphonicAnalyzer.Parameters.defaults();
		assertEquals(former.minCandidateMidi, RecognitionParameters.POLYPHONIC_CANDIDATE_LOW.getDefault().intValue());
		assertEquals(former.maxCandidateMidi, RecognitionParameters.POLYPHONIC_CANDIDATE_HIGH.getDefault().intValue());
		assertEquals(former.harmonics, RecognitionParameters.POLYPHONIC_HARMONICS.getDefault().intValue());
		assertEquals(former.maxHarmonicHz, RecognitionParameters.POLYPHONIC_MAX_HARMONIC.getDefault());
		assertEquals(former.harmonicToleranceCents, RecognitionParameters.POLYPHONIC_TOLERANCE.getDefault());
		assertEquals(former.maxCandidates, RecognitionParameters.POLYPHONIC_CANDIDATES.getDefault().intValue());
		assertEquals(former.minRelativeSalience, RecognitionParameters.POLYPHONIC_MIN_RELATIVE.getDefault());
		assertEquals(former.floorDb, RecognitionParameters.POLYPHONIC_FLOOR.getDefault());
		assertEquals(former.subharmonicRatio, RecognitionParameters.POLYPHONIC_SUBHARMONIC.getDefault());
		assertEquals(25.0, former.floorHalfWidthHz);
		assertEquals(former.floorHalfWidthHz, RecognitionParameters.POLYPHONIC_FLOOR_WIDTH.getDefault());
		assertEquals(2.0, former.floorFactor);
		assertEquals(former.floorFactor, RecognitionParameters.POLYPHONIC_FLOOR_FACTOR.getDefault());
		assertEquals(values(former), values(PolyphonicAnalyzer.Parameters.of(RecognitionSettings.defaults())));
	}

	@Test
	public void theAnalyserAndTheNativeArrayCarryTheSettings() {
		RecognitionSettings s = changed();
		PolyphonicAnalyzer.Parameters p = PolyphonicAnalyzer.Parameters.of(s);
		assertEquals(Arrays.asList(8192.0, 24.0, 112.0, 40.0, 100.0, 8.0, 3000.0, 15.0, 3.0, 0.2, -60.0, 0.5, 40.0, 3.0), values(p));
		double[] tuning = NativeParameters.tuning(s);
		assertEquals(40.0, tuning[NativeParameters.T_POLY_CANDIDATE_LOW]);
		assertEquals(100.0, tuning[NativeParameters.T_POLY_CANDIDATE_HIGH]);
		assertEquals(8.0, tuning[NativeParameters.T_POLY_HARMONICS]);
		assertEquals(3000.0, tuning[NativeParameters.T_POLY_MAX_HARMONIC]);
		assertEquals(15.0, tuning[NativeParameters.T_POLY_TOLERANCE]);
		assertEquals(3.0, tuning[NativeParameters.T_POLY_CANDIDATES]);
		assertEquals(0.2, tuning[NativeParameters.T_POLY_MIN_RELATIVE]);
		assertEquals(-60.0, tuning[NativeParameters.T_POLY_FLOOR]);
		assertEquals(0.5, tuning[NativeParameters.T_POLY_SUBHARMONIC]);
		assertEquals(40.0, tuning[NativeParameters.T_POLY_FLOOR_WIDTH]);
		assertEquals(3.0, tuning[NativeParameters.T_POLY_FLOOR_FACTOR]);
		assertEquals(values(p), values(NativeParameters.polyphonic(tuning)), "the parameters read back from the array");
	}

	@Test
	public void aFrameReadFromTheNativeEngineCarriesItsParameters() {
		PolyphonicAnalyzer.Parameters p = PolyphonicAnalyzer.Parameters.of(changed());
		PolyphonicAnalyzer analyzer = new PolyphonicAnalyzer(p, 48000);
		float[] tone = SyntheticSignals.harmonics(110.0, 48000, 8192, 0.3, 0.2, 0.1);
		analyzer.push(tone, 0, tone.length);
		SpectrumFrame spectrum = analyzer.analyze();
		float[] records = new float[FrameRecords.CAPACITY];
		FrameRecords.write(new AnalysisFrame(0L, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, 110.0, 0.9, false, spectrum), 0L, records, 0);
		assertSame(p, FrameRecords.read(records, 0, p).getSpectrum().getParameters());
		assertEquals(values(PolyphonicAnalyzer.Parameters.defaults()), values(FrameRecords.read(records, 0).getSpectrum().getParameters()));
	}

	@Test
	public void theTwoSectionsHoldTheirValuesAndThePairStaysOrdered() {
		assertEquals(Arrays.asList(RecognitionParameters.POLYPHONIC_CANDIDATE_LOW, RecognitionParameters.POLYPHONIC_CANDIDATE_HIGH,
				RecognitionParameters.POLYPHONIC_HARMONICS, RecognitionParameters.POLYPHONIC_MAX_HARMONIC, RecognitionParameters.POLYPHONIC_TOLERANCE,
				RecognitionParameters.POLYPHONIC_CANDIDATES), RecognitionParameters.of(Section.POLYPHONIC_1));
		assertEquals(Arrays.asList(RecognitionParameters.POLYPHONIC_MIN_RELATIVE, RecognitionParameters.POLYPHONIC_FLOOR,
				RecognitionParameters.POLYPHONIC_SUBHARMONIC, RecognitionParameters.POLYPHONIC_FLOOR_WIDTH, RecognitionParameters.POLYPHONIC_FLOOR_FACTOR),
				RecognitionParameters.of(Section.POLYPHONIC_2));
		boolean declared = false;
		for( OrderedPair pair : RecognitionParameters.ORDERED_PAIRS ) {
			declared |= pair.getLower() == RecognitionParameters.POLYPHONIC_CANDIDATE_LOW && pair.getUpper() == RecognitionParameters.POLYPHONIC_CANDIDATE_HIGH;
		}
		assertTrue(declared, "candidate-low <= candidate-high is declared");
		// every value of the two sections at either end builds an analyser: the bands always hold the candidates
		for( Section section : new Section[] { Section.POLYPHONIC_1, Section.POLYPHONIC_2 } ) {
			for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
				RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
				for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
					new PolyphonicAnalyzer(PolyphonicAnalyzer.Parameters.of(RecognitionSettings.defaults().with(number, end)), 44100);
				}
			}
		}
	}

	private static RecognitionSettings changed() {
		return RecognitionSettings.defaults().with(RecognitionParameters.POLYPHONIC_CANDIDATE_LOW, 40).with(RecognitionParameters.POLYPHONIC_CANDIDATE_HIGH, 100)
				.with(RecognitionParameters.POLYPHONIC_HARMONICS, 8).with(RecognitionParameters.POLYPHONIC_MAX_HARMONIC, 3000)
				.with(RecognitionParameters.POLYPHONIC_TOLERANCE, 15).with(RecognitionParameters.POLYPHONIC_CANDIDATES, 3)
				.with(RecognitionParameters.POLYPHONIC_MIN_RELATIVE, 0.2).with(RecognitionParameters.POLYPHONIC_FLOOR, -60)
				.with(RecognitionParameters.POLYPHONIC_SUBHARMONIC, 0.5).with(RecognitionParameters.POLYPHONIC_FLOOR_WIDTH, 40)
				.with(RecognitionParameters.POLYPHONIC_FLOOR_FACTOR, 3);
	}

	private static List<Double> values(PolyphonicAnalyzer.Parameters p) {
		return Arrays.asList((double) p.fftSize, (double) p.minBandMidi, (double) p.maxBandMidi, (double) p.minCandidateMidi, (double) p.maxCandidateMidi,
				(double) p.harmonics, p.maxHarmonicHz, p.harmonicToleranceCents, (double) p.maxCandidates, p.minRelativeSalience, p.floorDb, p.subharmonicRatio,
				p.floorHalfWidthHz, p.floorFactor);
	}
}
