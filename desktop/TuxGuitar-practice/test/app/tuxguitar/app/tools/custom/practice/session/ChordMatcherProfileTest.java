package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.PolyphonicAnalyzer;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;

/**
 * The chord rule by profile in {@link ChordMatcher} (spec 024, contracts/chord-profile-rule.md, task T010): complete when the match reaches the
 * threshold, at most one required name is weak and the settle time has passed; the per-string rule unchanged under {@code all-verifiable}.
 */
public class ChordMatcherProfileTest {

	private static final long MS = 1_000_000L;

	private static ChordMatcher.Parameters profileRule(double match, double names, long settleNanos) {
		return new ChordMatcher.Parameters(2, 1.0 / 3.0, 8, 600 * MS, RecognitionParameters.COMPLETENESS_PROFILE, match, names, 84, settleNanos, true);
	}

	private static ChordProfile summed(int[] required, long start, SpectrumFrame... frames) {
		ChordProfile profile = new ChordProfile();
		profile.reset(required, new int[0], 84, start);
		for( SpectrumFrame frame : frames ) {
			profile.add(frame);
		}
		return profile;
	}

	@Test
	public void aFrameIsCompleteWhenTheMatchReachesTheThresholdAfterTheSettleTime() {
		ChordMatcher matcher = new ChordMatcher(profileRule(0.79, 0.40, 200 * MS));
		ChordProfile right = summed(new int[] { 45, 52, 57, 60 }, 0L, ChordProfileTest.frame(-20, 45, 52, 57, 60));
		assertFalse(matcher.check(right, 100 * MS).isComplete(), "not settled yet");
		ChordMatcher.Result settled = matcher.check(right, 200 * MS);
		assertTrue(settled.isComplete());
		assertArrayEquals(new int[0], settled.getMissing());
		assertArrayEquals(new int[0], settled.getUnverifiable());
		// a match under the threshold: C major against A minor shares C and E only
		ChordProfile other = summed(new int[] { 45, 52, 57, 60 }, 0L, ChordProfileTest.frame(-20, 48, 52, 55, 60));
		ChordMatcher.Result refused = matcher.check(other, 300 * MS);
		assertFalse(refused.isComplete());
		assertTrue(refused.getMissing().length > 0);
	}

	@Test
	public void twoWeakNamesAreNeverCompleteEvenAtALowThreshold() {
		ChordMatcher matcher = new ChordMatcher(profileRule(0.70, 0.40, 0L));
		// G7 (G B D F), D and F silent: the match is about 0.71
		ChordProfile profile = summed(new int[] { 43, 47, 50, 53 }, 0L, ChordProfileTest.frame(-20, 43, 47, 55, 59));
		assertTrue(profile.match() >= 0.70);
		ChordMatcher.Result result = matcher.check(profile, 500 * MS);
		assertFalse(result.isComplete());
		assertArrayEquals(new int[] { 50, 53 }, result.getMissing());
		assertFalse(matcher.attackMayAccept(profile), "the guarded attack path refuses it too");
		// one name short of four is judged by its match alone
		ChordProfile oneShort = summed(new int[] { 43, 47, 50, 53 }, 0L, ChordProfileTest.frame(-20, 43, 47, 50, 55));
		assertTrue(matcher.attackMayAccept(oneShort));
	}

	@Test
	public void theAttackPathFollowsItsFlag() {
		ChordProfile right = summed(new int[] { 45, 52, 60 }, 0L, ChordProfileTest.frame(-20, 45, 52, 60));
		assertTrue(new ChordMatcher(profileRule(0.79, 0.40, 0L)).attackMayAccept(right));
		ChordMatcher off = new ChordMatcher(new ChordMatcher.Parameters(2, 1.0 / 3.0, 8, 600 * MS, RecognitionParameters.COMPLETENESS_PROFILE, 0.79, 0.40, 84, 0L,
				false));
		assertFalse(off.attackMayAccept(right));
		// the per-string rule keeps the attack path of spec 023, unguarded
		ChordMatcher perString = new ChordMatcher(ChordMatcher.Parameters.defaults());
		assertTrue(perString.attackMayAccept(new ChordProfile()));
	}

	@Test
	public void thePerStringRuleIsUnchanged() {
		SpectrumFrame spectrum = strum(new int[] { 40, 47, 52, 56, 59, 64 });
		int[] required = { 40, 47, 52, 56, 59, 64 };
		ChordMatcher old = new ChordMatcher(new ChordMatcher.Parameters(2, 1.0 / 3.0, 8, 600 * MS));
		ChordMatcher explicit = new ChordMatcher(new ChordMatcher.Parameters(2, 1.0 / 3.0, 8, 600 * MS, RecognitionParameters.COMPLETENESS_ALL, 0.79, 0.40, 84,
				200 * MS, true));
		ChordMatcher.Result a = old.check(spectrum, required, new int[0]);
		ChordMatcher.Result b = explicit.check(spectrum, required, new int[0]);
		assertEquals(a.isComplete(), b.isComplete());
		assertArrayEquals(a.getHeard(), b.getHeard());
		assertArrayEquals(a.getMissing(), b.getMissing());
		assertArrayEquals(a.getUnverifiable(), b.getUnverifiable());
		assertFalse(explicit.getParameters().usesProfile());
	}

	@Test
	public void theOldConstructorKeepsThePerStringRule() {
		assertEquals(RecognitionParameters.COMPLETENESS_ALL, new ChordMatcher.Parameters(2, 1.0 / 3.0, 8, 600 * MS).rule);
		assertEquals(RecognitionParameters.COMPLETENESS_ALL, ChordMatcher.Parameters.defaults().rule);
		assertFalse(ChordMatcher.Parameters.defaults().usesProfile());
		assertTrue(ChordMatcher.Parameters.defaults().attack);
	}

	/** The spectrum of sines at the given pitches, as the Java engine analyses it. */
	private static SpectrumFrame strum(int[] pitches) {
		int rate = 48000;
		PolyphonicAnalyzer analyzer = new PolyphonicAnalyzer(PolyphonicAnalyzer.Parameters.defaults(), rate);
		float[] samples = new float[rate / 2];
		for( int i = 0 ; i < samples.length ; i ++ ) {
			double t = (double) i / rate;
			double value = 0;
			for( int pitch : pitches ) {
				double f = 440.0 * Math.pow(2.0, (pitch - 69) / 12.0);
				for( int h = 1 ; h <= 4 ; h ++ ) {
					value += Math.sin(2.0 * Math.PI * f * h * t) / (h * pitches.length * 2.0);
				}
			}
			samples[i] = (float) value;
		}
		for( int offset = 0 ; offset + 256 <= samples.length ; offset += 256 ) {
			analyzer.push(samples, offset, 256);
		}
		return analyzer.analyze();
	}
}
