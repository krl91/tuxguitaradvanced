package app.tuxguitar.audio.engine.nativeengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ChordEvidence;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Each chord-evidence value takes effect, alike in both engines (spec 026, T017, SC-001): the two values of the chord evidence at one end of their
 * range change the evidence of an expected chord (the Java and native evidence the same: pitches, flags and counts equal, amplitudes within 1e-4),
 * and the four values of the strum meter at one end change the reading of a strum after a rising noise (the Java meter's reading and the native one
 * the same).
 */
public class ChordEvidenceTakesEffectTest {

	private static final int RATE = 44100;
	private static final int[] E_MAJOR = { 40, 47, 52, 56, 59, 64 };

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void theChordEvidenceValuesChangeTheEvidenceAlikeInBothEngines() {
		float[] take = chordTake();
		String recommended = chordSignature(take, RecognitionSettings.defaults(), "the recommended values");
		List<String> withoutEffect = new ArrayList<String>();
		for( RecognitionParameter.Number number : new RecognitionParameter.Number[] { RecognitionParameters.CHORD_EVIDENCE_PARTIAL_PRESENCE,
				RecognitionParameters.CHORD_EVIDENCE_NEIGHBOUR_MARGIN } ) {
			boolean changed = false;
			for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
				changed |= !chordSignature(take, RecognitionSettings.defaults().with(number, end), number.getKey() + " at " + end).equals(recommended);
			}
			if( !changed ) {
				withoutEffect.add(number.getKey());
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change no evidence");
	}

	@Test
	public void theStrumValuesChangeTheReadingAlikeInBothEngines() {
		float[] take = strumTake();
		String recommended = strumReading(take, RecognitionSettings.defaults(), "the recommended values");
		List<String> withoutEffect = new ArrayList<String>();
		for( RecognitionParameter.Number number : new RecognitionParameter.Number[] { RecognitionParameters.CHORD_EVIDENCE_STRUM_WINDOW,
				RecognitionParameters.CHORD_EVIDENCE_STRUM_QUIET, RecognitionParameters.CHORD_EVIDENCE_STRUM_RISE,
				RecognitionParameters.CHORD_EVIDENCE_STRUM_CLIP_SHARE } ) {
			boolean changed = false;
			for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
				changed |= !strumReading(take, RecognitionSettings.defaults().with(number, end), number.getKey() + " at " + end).equals(recommended);
			}
			if( !changed ) {
				withoutEffect.add(number.getKey());
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change no reading");
	}

	private static String chordSignature(float[] take, RecognitionSettings settings, String label) {
		int[] tolerated = new int[0];
		List<AnalysisFrame> java = OfflineAnalysis.analyze(take, RATE, settings.with(RecognitionParameters.ENGINE_PITCH, PitchEngineCatalog.FASTYIN), true, null,
				E_MAJOR, tolerated);
		List<AnalysisFrame> nativeFrames = OfflineAnalysis.analyze(take, RATE, settings.with(RecognitionParameters.ENGINE_PITCH, PitchEngineCatalog.NATIVE), true,
				null, E_MAJOR, tolerated);
		assertEquals(java.size(), nativeFrames.size(), label);
		StringBuilder s = new StringBuilder();
		int evidence = 0;
		for( int i = 0 ; i < java.size() ; i ++ ) {
			// the Java offline analysis carries no chord evidence: it is evaluated from its spectrum, as the Java chord check does
			ChordEvidence a = (java.get(i).getSpectrum() != null ? ChordEvidence.evaluate(java.get(i).getSpectrum(), E_MAJOR, tolerated) : null);
			ChordEvidence b = nativeFrames.get(i).getChordEvidence();
			String at = label + " frame " + i;
			assertEquals(a == null, b == null, at + ": evidence in one engine only");
			if( a == null ) {
				continue;
			}
			evidence ++;
			assertEquals(a.getPitches().size(), b.getPitches().size(), at);
			for( int k = 0 ; k < a.getPitches().size() ; k ++ ) {
				ChordEvidence.Pitch p = a.getPitches().get(k);
				ChordEvidence.Pitch q = b.getPitches().get(k);
				String pitch = p.getMidi() + "/" + p.isHarmonicOfLower() + "/" + p.getUniquePartials() + "/" + p.getUniqueClearPartials();
				assertEquals(pitch, q.getMidi() + "/" + q.isHarmonicOfLower() + "/" + q.getUniquePartials() + "/" + q.getUniqueClearPartials(), at);
				SpectrumParityTest.assertClose(at + " salience", p.getSalience(), q.getSalience(), p.getSalience());
				SpectrumParityTest.assertClose(at + " independent", p.getIndependentSalience(), q.getIndependentSalience(), p.getSalience());
				SpectrumParityTest.assertClose(at + " present", p.getUniquePresentFraction(), q.getUniquePresentFraction(), 1.0);
				s.append(pitch).append('=').append(String.format("%.5g/%.5g ", p.getIndependentSalience(), p.getUniquePresentFraction()));
			}
			s.append('\n');
		}
		assertTrue(evidence > 50, label);
		return s.toString();
	}

	private static String strumReading(float[] take, RecognitionSettings settings, String label) {
		StrumClipMeter meter = new StrumClipMeter(StrumClipMeter.Parameters.of(settings));
		meter.configure(RATE);
		for( int offset = 0 ; offset + ParityEngines.HOP <= take.length ; offset += ParityEngines.HOP ) {
			meter.hop(take, offset, ParityEngines.HOP);
		}
		StrumClipMeter.Reading expected = meter.snapshot();
		float[] actual = NativeRecognitionLibrary.strumClipOfflineTuned(take, RATE, NativeParameters.of(settings), NativeParameters.tuning(settings));
		assertEquals(3, actual.length, label);
		assertEquals(expected.verdict().ordinal(), (int) actual[0], label + " verdict");
		assertEquals(expected.clippedShare(), actual[1], 1e-6, label + " clipped share");
		if( Double.isNaN(expected.rmsDbfs()) ) {
			assertTrue(Float.isNaN(actual[2]), label + " no level before a verdict");
		} else {
			assertEquals(expected.rmsDbfs(), actual[2], 1e-3, label + " level");
		}
		return expected.verdict() + String.format(" %.6g %.4f", expected.clippedShare(), expected.rmsDbfs());
	}

	/** E major strummed, then the same shape a whole tone up (F# major), then E major with its G# missing. */
	private static float[] chordTake() {
		float[] signal = new float[(int) (4.5 * RATE)];
		SyntheticSignals.mix(signal, SyntheticSignals.strum(E_MAJOR, RATE, (int) (1.4 * RATE), RATE / 100, 0.25, 31L), (int) (0.1 * RATE));
		SyntheticSignals.mix(signal, SyntheticSignals.strum(new int[] { 42, 49, 54, 58, 61, 66 }, RATE, (int) (1.4 * RATE), RATE / 100, 0.25, 41L),
				(int) (1.5 * RATE));
		SyntheticSignals.mix(signal, SyntheticSignals.strum(new int[] { 40, 47, 52, 59, 64 }, RATE, (int) (1.4 * RATE), RATE / 100, 0.25, 51L), (int) (3.0 * RATE));
		float[] noise = SyntheticSignals.noise(signal.length, Math.pow(10, -60.0 / 20.0), 6L);
		for( int i = 0 ; i < signal.length ; i ++ ) {
			signal[i] += noise[i];
		}
		return signal;
	}

	/** A noise rising from -70 to -20 dBFS over two seconds, then a loud strum clipped on a few samples. */
	private static float[] strumTake() {
		int ramp = 2 * RATE;
		float[] signal = new float[ramp + 2 * RATE];
		float[] noise = SyntheticSignals.noise(signal.length, 1.0, 8L);
		for( int i = 0 ; i < ramp ; i ++ ) {
			double db = -70.0 + 50.0 * i / ramp;
			signal[i] = (float) (noise[i] * Math.pow(10, db / 20.0));
		}
		float[] strum = SyntheticSignals.strum(E_MAJOR, RATE, 2 * RATE, RATE / 100, 0.6, 61L);
		for( int i = 0 ; i < strum.length ; i ++ ) {
			double value = strum[i] * 1.3 + noise[ramp + i] * 0.1;
			signal[ramp + i] = (float) Math.max(-1.0, Math.min(1.0, value));
		}
		return signal;
	}
}
