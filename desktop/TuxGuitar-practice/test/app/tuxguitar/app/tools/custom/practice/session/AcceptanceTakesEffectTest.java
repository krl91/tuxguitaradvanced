package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Each acceptance value takes effect (spec 026, T020, SC-001): on scripted attack evidence (no audio), every value of the three sections at one end of
 * its range changes when the expected notes are accepted or where their attack is said to begin. The trials: a note struck from silence that fades,
 * a note heard with short misses, three chords (a weak string, a louder foreign pitch, a loud neighbour), a note measured 40 cents flat, a note after
 * a burst and a rising background, and a note on the first hop after silence.
 */
public class AcceptanceTakesEffectTest {

	private static final long HOP = 5_800_000L;
	private static final long START = 300_000_000L;
	private static final int[] PITCHES = { 43, 45, 52, 55, 56, 57, 59, 60 };

	@Test
	public void everyAcceptanceValueChangesTheDecisions() {
		String recommended = decisions(RecognitionSettings.defaults());
		assertTrue(recommended.contains("@"), "the recommended values accept notes: " + recommended);
		List<String> withoutEffect = new ArrayList<String>();
		for( Section section : new Section[] { Section.ACCEPTANCE_1, Section.ACCEPTANCE_2, Section.ACCEPTANCE_3 } ) {
			for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
				RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
				boolean changed = false;
				for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
					changed |= !decisions(RecognitionSettings.defaults().with(number, end)).equals(recommended);
				}
				if( !changed ) {
					withoutEffect.add(number.getKey());
				}
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change no decision");
	}

	private static String decisions(RecognitionSettings settings) {
		StringBuilder s = new StringBuilder();
		s.append("silence ").append(run(settings, fromSilence(), new int[] { 57 }, null)).append('\n');
		s.append("misses ").append(run(settings, withMisses(), new int[] { 57 }, null)).append('\n');
		int[] chord = { 45, 52, 55 };
		s.append("weak string ").append(run(settings, steady(chordAmplitudes(1.0, 0.6, 0.02, 0.05, 0.05), null), chord, null)).append('\n');
		s.append("foreign ").append(run(settings, steady(chordAmplitudes(1.0, 0.7, 0.7, 1.2, 0.05), null), chord, null)).append('\n');
		s.append("neighbour ").append(run(settings, steady(chordAmplitudes(1.0, 0.7, 0.7, 0.05, 0.3), null), chord, null)).append('\n');
		s.append("flat ").append(run(settings, steady(note(57, 0.2), flatGrowth(57, -40.0)), new int[] { 57 }, new TolerancePolicy(5.0, 30.0)))
				.append('\n');
		s.append("rising ").append(run(settings, risingBackground(), new int[] { 57 }, null)).append('\n');
		s.append("first hop ").append(run(settings, steady(note(57, 0.03), null), new int[] { 57 }, null)).append('\n');
		return s.toString();
	}

	/** Offers the frames from {@link #START}, one hop apart; each acceptance is written as its hop and the onset it reports, then rearms. */
	private static String run(RecognitionSettings settings, List<AttackEvidence> frames, int[] expected, TolerancePolicy tolerance) {
		PolyphonicAcceptance acceptance = new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.of(settings), settings);
		acceptance.rearm(0L);
		StringBuilder s = new StringBuilder();
		for( int k = 0 ; k < frames.size() ; k ++ ) {
			long t = START + k * HOP;
			if( acceptance.offer(frames.get(k), t, expected, new int[expected.length], tolerance) ) {
				s.append(k).append('@').append(acceptance.getOnsetNanos() < START ? "none" : Long.toString((acceptance.getOnsetNanos() - START) / HOP)).append(' ');
				acceptance.rearm(t);
			}
		}
		return s.toString();
	}

	/** Fifteen quiet hops, then a note struck from silence (sudden for three hops) that fades by 5 % a hop. */
	private static List<AttackEvidence> fromSilence() {
		List<AttackEvidence> frames = new ArrayList<AttackEvidence>();
		for( int k = 0 ; k < 15 ; k ++ ) {
			frames.add(new AttackEvidence(PITCHES, equal(0.0015), 0.05));
		}
		for( int k = 0 ; k < 60 ; k ++ ) {
			frames.add(new AttackEvidence(PITCHES, note(57, 0.2 * Math.pow(0.95, k)), k < 3 ? 0.8 : 0.3));
		}
		return frames;
	}

	/** A steady note heard three hops out of four. */
	private static List<AttackEvidence> withMisses() {
		List<AttackEvidence> frames = new ArrayList<AttackEvidence>();
		for( int k = 0 ; k < 40 ; k ++ ) {
			double[] amplitudes = note(57, 0.2);
			if( k % 4 == 3 ) {
				amplitudes[index(57)] *= 0.05;
			}
			frames.add(new AttackEvidence(PITCHES, amplitudes, 0.3));
		}
		return frames;
	}

	/** A short burst (whose attack ends at once), fifty hops of a background rising from 0.012 to 0.031, then a note of total 0.09. */
	private static List<AttackEvidence> risingBackground() {
		List<AttackEvidence> frames = new ArrayList<AttackEvidence>();
		frames.add(new AttackEvidence(PITCHES, equal(0.2 / PITCHES.length), 0.3));
		for( int k = 0 ; k < 50 ; k ++ ) {
			frames.add(new AttackEvidence(PITCHES, equal((0.012 + 0.019 * k / 49.0) / PITCHES.length), 0.3));
		}
		for( int k = 0 ; k < 16 ; k ++ ) {
			frames.add(new AttackEvidence(PITCHES, note(57, 0.09), 0.3));
		}
		return frames;
	}

	private static List<AttackEvidence> steady(double[] amplitudes, double[] growth) {
		List<AttackEvidence> frames = new ArrayList<AttackEvidence>();
		for( int k = 0 ; k < 20 ; k ++ ) {
			frames.add(new AttackEvidence(PITCHES, amplitudes, 0.3, growth, growth == null ? 0.0 : BIN_HZ));
		}
		return frames;
	}

	/** {@code pitch} with the amplitude that makes the total {@code total}, every other candidate a tenth of it. */
	private static double[] note(int pitch, double total) {
		double main = total / (1.0 + 0.1 * (PITCHES.length - 1));
		double[] amplitudes = new double[PITCHES.length];
		for( int i = 0 ; i < PITCHES.length ; i ++ ) {
			amplitudes[i] = (PITCHES[i] == pitch ? main : 0.1 * main);
		}
		return amplitudes;
	}

	/** The chord 45, 52, 55 with the given levels (of 0.1), 60 and 56 as given, the others at 0.05. */
	private static double[] chordAmplitudes(double a45, double a52, double a55, double a60, double a56) {
		double[] amplitudes = new double[PITCHES.length];
		for( int i = 0 ; i < PITCHES.length ; i ++ ) {
			amplitudes[i] = 0.1 * 0.05;
		}
		amplitudes[index(45)] = 0.1 * a45;
		amplitudes[index(52)] = 0.1 * a52;
		amplitudes[index(55)] = 0.1 * a55;
		amplitudes[index(60)] = 0.1 * a60;
		amplitudes[index(56)] = 0.1 * a56;
		return amplitudes;
	}

	private static double[] equal(double amplitude) {
		double[] amplitudes = new double[PITCHES.length];
		java.util.Arrays.fill(amplitudes, amplitude);
		return amplitudes;
	}

	private static final double BIN_HZ = 0.5;

	/** A growth spectrum with a parabolic peak (read back exactly by the three-point fit) at each partial of {@code midi}, {@code cents} off. */
	private static double[] flatGrowth(int midi, double cents) {
		double[] growth = new double[8000];
		double f0 = 440.0 * Math.pow(2.0, (midi - 69) / 12.0) * Math.pow(2.0, cents / 1200.0);
		for( int h = 1 ; h <= 12 ; h ++ ) {
			double center = f0 * h / BIN_HZ;
			for( int k = (int) Math.floor(center) - 2 ; k <= (int) Math.ceil(center) + 2 ; k ++ ) {
				double d = (k - center) / 2.0;
				growth[k] = Math.max(growth[k], 1.0 - d * d);
			}
		}
		return growth;
	}

	private static int index(int pitch) {
		return java.util.Arrays.binarySearch(PITCHES, pitch);
	}
}
