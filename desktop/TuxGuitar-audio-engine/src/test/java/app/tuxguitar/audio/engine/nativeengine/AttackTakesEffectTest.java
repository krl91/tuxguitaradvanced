package app.tuxguitar.audio.engine.nativeengine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Each attack value takes effect (spec 026, T016, SC-001): on a synthetic take of plucked notes and chords, every value of {@code ATTACK_1} at one end
 * of its range changes the attack evidence of the recommended values, alike in the Java engine and the native one (every field within 1e-4); every
 * value of {@code ATTACK_2} (the offset measure, read by the practice) at one end changes the measured offsets; and a frame carrying the spectrum and
 * the attack evidence with both at their highest frequencies still fits a native record.
 */
public class AttackTakesEffectTest {

	private static final int RATE = 44100;
	private static final int[] CANDIDATES = AttackEvidenceParityTest.candidates(40, 45, 52, 57, 64);

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void everyAnalyserValueChangesTheEvidenceAlikeInBothEngines() {
		float[] take = take();
		RecognitionSettings defaults = RecognitionSettings.defaults();
		String recommended = signature(check(take, defaults, "the recommended values"));
		List<String> withoutEffect = new ArrayList<String>();
		for( RecognitionParameter parameter : RecognitionParameters.of(Section.ATTACK_1) ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			boolean changed = false;
			for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
				changed |= !signature(check(take, defaults.with(number, end), number.getKey() + " at " + end)).equals(recommended);
			}
			if( !changed ) {
				withoutEffect.add(number.getKey());
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change no evidence");
	}

	@Test
	public void everyOffsetValueChangesTheMeasuredOffsets() {
		List<AttackEvidence> evidence = check(take(), RecognitionSettings.defaults(), "the recommended values");
		String recommended = offsets(evidence, RecognitionSettings.defaults());
		List<String> withoutEffect = new ArrayList<String>();
		for( RecognitionParameter parameter : RecognitionParameters.of(Section.ATTACK_2) ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			boolean changed = false;
			for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
				changed |= !offsets(evidence, RecognitionSettings.defaults().with(number, end)).equals(recommended);
			}
			if( !changed ) {
				withoutEffect.add(number.getKey());
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change no offset");
		StringBuilder former = new StringBuilder();
		for( AttackEvidence e : evidence ) {
			for( int midi : new int[] { 40, 45, 52 } ) {
				former.append(String.format("%.6g ", e.offsetCents(midi)));
			}
		}
		assertEquals(former.toString(), recommended, "the recommended values measure as before");
	}

	@Test
	public void aFrameWithTheSpectrumAndTheAttackAtTheirHighestStillFits() {
		RecognitionSettings highest = RecognitionSettings.defaults()
				.with(RecognitionParameters.POLYPHONIC_MAX_HARMONIC, RecognitionParameters.POLYPHONIC_MAX_HARMONIC.getMaximum())
				.with(RecognitionParameters.ATTACK_MAX_FREQUENCY, RecognitionParameters.ATTACK_MAX_FREQUENCY.getMaximum())
				.with(RecognitionParameters.POLYPHONIC_CANDIDATES, RecognitionParameters.POLYPHONIC_CANDIDATES.getMaximum());
		float[] take = take();
		List<AnalysisFrame> java = OfflineAnalysis.analyze(take, RATE, highest.with(RecognitionParameters.ENGINE_PITCH, PitchEngineCatalog.FASTYIN), true,
				CANDIDATES);
		List<AnalysisFrame> nativeFrames = OfflineAnalysis.analyze(take, RATE, highest.with(RecognitionParameters.ENGINE_PITCH, PitchEngineCatalog.NATIVE), true,
				CANDIDATES);
		assertEquals(java.size(), nativeFrames.size(), "no native record was refused");
		for( int i = 0 ; i < nativeFrames.size() ; i ++ ) {
			assertNotNull(nativeFrames.get(i).getSpectrum(), "frame " + i + " has its spectrum");
		}
	}

	/** The Java evidence, checked against the native one. */
	private static List<AttackEvidence> check(float[] take, RecognitionSettings settings, String label) {
		List<AnalysisFrame> java = OfflineAnalysis.analyze(take, RATE, settings.with(RecognitionParameters.ENGINE_PITCH, PitchEngineCatalog.FASTYIN), false,
				CANDIDATES);
		List<AnalysisFrame> nativeFrames = OfflineAnalysis.analyze(take, RATE, settings.with(RecognitionParameters.ENGINE_PITCH, PitchEngineCatalog.NATIVE), false,
				CANDIDATES);
		assertEquals(java.size(), nativeFrames.size(), label);
		List<AttackEvidence> evidence = new ArrayList<AttackEvidence>();
		for( int i = 0 ; i < java.size() ; i ++ ) {
			AttackEvidence a = java.get(i).getAttackEvidence();
			AttackEvidence b = nativeFrames.get(i).getAttackEvidence();
			String at = label + " frame " + i;
			assertEquals(a == null, b == null, at + ": evidence in one engine only");
			if( a == null ) {
				continue;
			}
			assertArrayEquals(a.getPitches(), b.getPitches(), at);
			for( int k = 0 ; k < a.getPitches().length ; k ++ ) {
				SpectrumParityTest.assertClose(at + " amplitude " + a.getPitches()[k], a.getAmplitude(k), b.getAmplitude(k), a.getMaximum());
			}
			SpectrumParityTest.assertClose(at + " strength", a.getAttackStrength(), b.getAttackStrength(), 1.0);
			assertEquals(a.getGrowth() == null, b.getGrowth() == null, at + " growth");
			if( a.getGrowth() != null ) {
				assertEquals(a.getGrowth().length, b.getGrowth().length, at);
				double max = 0.0;
				for( double g : a.getGrowth() ) {
					max = Math.max(max, g);
				}
				for( int k = 0 ; k < a.getGrowth().length ; k ++ ) {
					SpectrumParityTest.assertClose(at + " growth " + k, a.getGrowth()[k], b.getGrowth()[k], max);
				}
			}
			evidence.add(a);
		}
		assertTrue(evidence.size() > 50, label);
		return evidence;
	}

	private static String signature(List<AttackEvidence> evidence) {
		StringBuilder s = new StringBuilder();
		for( AttackEvidence e : evidence ) {
			s.append(e.getGrowth() == null ? -1 : e.getGrowth().length).append(':');
			for( int k = 0 ; k < e.getPitches().length ; k ++ ) {
				s.append(String.format("%.6g ", e.getAmplitude(k)));
			}
			s.append(String.format("%.6g", e.getAttackStrength())).append('\n');
		}
		return s.toString();
	}

	private static String offsets(List<AttackEvidence> evidence, RecognitionSettings settings) {
		StringBuilder s = new StringBuilder();
		for( AttackEvidence e : evidence ) {
			for( int midi : new int[] { 40, 45, 52 } ) {
				s.append(String.format("%.6g ", e.offsetCents(midi, settings)));
			}
		}
		return s.toString();
	}

	/** Single notes (E2, A2 slightly sharp, E3), then chords re-struck over ringing strings. */
	private static float[] take() {
		float[] signal = new float[(int) (6.0 * RATE)];
		double t = 0.2;
		long seed = 21;
		for( double hz : new double[] { SyntheticSignals.midiFrequency(40), SyntheticSignals.midiFrequency(45) * Math.pow(2, 20 / 1200.0),
				SyntheticSignals.midiFrequency(52) } ) {
			SyntheticSignals.mix(signal, scaled(SyntheticSignals.karplusStrong(hz, RATE, (int) (1.0 * RATE), seed ++), 0.4), (int) (t * RATE));
			t += 0.9;
		}
		for( int[] chord : new int[][] { { 40, 47, 52, 56, 59, 64 }, { 45, 52, 57, 61, 64 }, { 40, 47, 52, 56, 59, 64 } } ) {
			SyntheticSignals.mix(signal, SyntheticSignals.strum(chord, RATE, (int) (1.2 * RATE), RATE / 120, 0.25, seed ++), (int) (t * RATE));
			t += 0.9;
		}
		float[] noise = SyntheticSignals.noise(signal.length, Math.pow(10, -60.0 / 20.0), 4L);
		for( int i = 0 ; i < signal.length ; i ++ ) {
			signal[i] += noise[i];
		}
		return signal;
	}

	private static float[] scaled(float[] s, double gain) {
		for( int i = 0 ; i < s.length ; i ++ ) {
			s[i] *= (float) gain;
		}
		return s;
	}
}
