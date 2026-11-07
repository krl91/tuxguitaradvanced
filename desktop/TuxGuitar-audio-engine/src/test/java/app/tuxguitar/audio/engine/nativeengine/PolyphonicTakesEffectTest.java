package app.tuxguitar.audio.engine.nativeengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.PitchCandidate;
import app.tuxguitar.audio.engine.analysis.PolyphonicAnalyzer;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Each polyphonic value takes effect, alike in both engines (spec 026, T015, SC-001): on a synthetic take (a low strum, a high note, a quiet
 * chord), every value of the sections {@code POLYPHONIC_1} and {@code POLYPHONIC_2} at one end of its range changes the spectra of the recommended
 * values, and the offline analysis of the Java engine and that of the native one give the same spectra (bands, salience, candidates and peaks within
 * 1e-4) for both ends; the native frames carry the parameters of the settings.
 */
public class PolyphonicTakesEffectTest {

	private static final int RATE = 44100;

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void everyPolyphonicValueChangesTheSpectraAlikeInBothEngines() {
		float[] take = take();
		RecognitionSettings defaults = RecognitionSettings.defaults();
		String recommended = signature(check(take, defaults, "the recommended values"));
		List<String> withoutEffect = new ArrayList<String>();
		for( Section section : new Section[] { Section.POLYPHONIC_1, Section.POLYPHONIC_2 } ) {
			for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
				RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
				boolean changed = false;
				for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
					changed |= !signature(check(take, defaults.with(number, end), number.getKey() + " at " + end)).equals(recommended);
				}
				if( !changed ) {
					withoutEffect.add(number.getKey());
				}
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change no spectrum");
	}

	/** The Java spectra, checked against the native ones. */
	private static List<SpectrumFrame> check(float[] take, RecognitionSettings settings, String label) {
		List<AnalysisFrame> java = OfflineAnalysis.analyze(take, RATE, settings.with(RecognitionParameters.ENGINE_PITCH, PitchEngineCatalog.FASTYIN), true, null);
		List<AnalysisFrame> nativeFrames = OfflineAnalysis.analyze(take, RATE, settings.with(RecognitionParameters.ENGINE_PITCH, PitchEngineCatalog.NATIVE), true,
				null);
		assertEquals(java.size(), nativeFrames.size(), label);
		PolyphonicAnalyzer.Parameters expected = PolyphonicAnalyzer.Parameters.of(settings);
		List<SpectrumFrame> spectra = new ArrayList<SpectrumFrame>();
		for( int i = 0 ; i < java.size() ; i ++ ) {
			SpectrumFrame a = java.get(i).getSpectrum();
			SpectrumFrame b = nativeFrames.get(i).getSpectrum();
			SpectrumParityTest.assertSame(label + " frame " + i, a, b);
			assertEquals(String.valueOf(values(expected)), String.valueOf(values(b.getParameters())), label + ": the native frame's parameters");
			spectra.add(a);
		}
		assertTrue(spectra.size() > 50, label);
		return spectra;
	}

	private static String signature(List<SpectrumFrame> spectra) {
		StringBuilder s = new StringBuilder();
		for( SpectrumFrame frame : spectra ) {
			s.append(frame.getSalienceCount()).append('/').append(frame.getPeakLength()).append(':');
			for( PitchCandidate c : frame.getCandidates() ) {
				s.append(c.getMidi()).append('=').append(String.format("%.6g", c.getSalience())).append(' ');
			}
			double peaks = 0.0;
			for( int k = 0 ; k < frame.getPeakLength() ; k ++ ) {
				peaks += frame.getPeak(k);
			}
			s.append(String.format("%.6g", peaks)).append('\n');
		}
		return s.toString();
	}

	private static List<Double> values(PolyphonicAnalyzer.Parameters p) {
		List<Double> v = new ArrayList<Double>();
		v.add((double) p.minCandidateMidi);
		v.add((double) p.maxCandidateMidi);
		v.add((double) p.harmonics);
		v.add(p.maxHarmonicHz);
		v.add(p.harmonicToleranceCents);
		v.add((double) p.maxCandidates);
		v.add(p.minRelativeSalience);
		v.add(p.floorDb);
		v.add(p.subharmonicRatio);
		v.add(p.floorHalfWidthHz);
		v.add(p.floorFactor);
		return v;
	}

	/**
	 * An open low E chord strummed (E2 B2 E3 G#3 B3 E4) with noise, then a high note alone (E6, above the recommended candidates), then a quiet chord
	 * (A2 E3 A3 C#4) at falling levels over a low floor, and a low note alone (C#2, under the recommended candidates).
	 */
	private static float[] take() {
		float[] signal = new float[(int) (5.0 * RATE)];
		SyntheticSignals.mix(signal, SyntheticSignals.strum(new int[] { 40, 47, 52, 56, 59, 64 }, RATE, (int) (1.2 * RATE), RATE / 100, 0.3, 5L), 0);
		SyntheticSignals.mix(signal, SyntheticSignals.harmonics(SyntheticSignals.midiFrequency(88), RATE, (int) (0.8 * RATE), 0.2, 0.05), (int) (1.2 * RATE));
		float[] quiet = SyntheticSignals.strum(new int[] { 45, 52, 57, 61 }, RATE, (int) (1.5 * RATE), RATE / 80, 0.01, 9L);
		SyntheticSignals.mix(signal, quiet, (int) (2.0 * RATE));
		SyntheticSignals.mix(signal, SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(37), RATE, (int) (1.4 * RATE), 13L), (int) (3.5 * RATE));
		float[] noise = SyntheticSignals.noise(signal.length, Math.pow(10, -60.0 / 20.0), 3L);
		for( int i = 0 ; i < signal.length ; i ++ ) {
			signal[i] += noise[i];
		}
		return signal;
	}
}
