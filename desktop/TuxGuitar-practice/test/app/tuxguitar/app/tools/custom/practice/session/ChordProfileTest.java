package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.PitchCandidate;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;

/**
 * The chord's pitch-class profile (spec 024, data model "Chord profile", task T009): the level of each note name summed over the strum, matched by
 * cosine against the required names, and the weak names. Frames are synthetic semitone bands.
 */
public class ChordProfileTest {

	private static final int MIN_BAND = 24;
	private static final int BANDS = 89;
	private static final int TOP = 84;
	private static final double[] SHARES = { 0.05, 0.10, 0.20, 0.30, 0.40, 0.50 };

	/** A frame whose listed pitches sound at the given level (dBFS) and every other band is silent. */
	static SpectrumFrame frame(double db, int... pitches) {
		float[] bands = new float[BANDS];
		Arrays.fill(bands, Float.NEGATIVE_INFINITY);
		for( int pitch : pitches ) {
			bands[pitch - MIN_BAND] = (float) db;
		}
		return SpectrumFrame.of(48000.0 / 8192.0, MIN_BAND, bands, MIN_BAND, new float[0], Collections.<PitchCandidate>emptyList(), new float[0]);
	}

	/** A frame from several groups of pitches, each at its own level: {db, pitch, pitch…}. */
	static SpectrumFrame frame(double[]... groups) {
		float[] bands = new float[BANDS];
		Arrays.fill(bands, Float.NEGATIVE_INFINITY);
		for( double[] group : groups ) {
			for( int i = 1 ; i < group.length ; i ++ ) {
				bands[(int) group[i] - MIN_BAND] = (float) group[0];
			}
		}
		return SpectrumFrame.of(48000.0 / 8192.0, MIN_BAND, bands, MIN_BAND, new float[0], Collections.<PitchCandidate>emptyList(), new float[0]);
	}

	private static ChordProfile profile(int[] required, int[] tolerated, SpectrumFrame... frames) {
		ChordProfile profile = new ChordProfile();
		profile.reset(required, tolerated, TOP, 0L);
		for( SpectrumFrame frame : frames ) {
			profile.add(frame);
		}
		return profile;
	}

	@Test
	public void aFrameWhereOnlyTheExpectedNamesSoundMatchesOne() {
		// A minor, one string per name: A2 E3 C4
		ChordProfile profile = profile(new int[] { 45, 52, 60 }, new int[0], frame(-20, 45, 52, 60));
		assertEquals(1.0, profile.match(), 1e-9);
	}

	@Test
	public void anOctaveMovedInsideTheRangeDoesNotChangeTheMatch() {
		int[] required = { 45, 52, 60 };
		double low = profile(required, new int[0], frame(-20, 45, 52, 60), frame(-26, 45, 52, 60)).match();
		double high = profile(required, new int[0], frame(-20, 45, 64, 60), frame(-26, 45, 64, 60)).match();
		assertEquals(low, high, 1e-12);
	}

	@Test
	public void twoVoicingsWithTheSameNamesMatchAlike() {
		SpectrumFrame played = frame(new double[] { -20, 45, 52, 57 }, new double[] { -30, 60, 64 });
		double open = profile(new int[] { 45, 52, 57, 60, 64 }, new int[0], played).match();
		double barre = profile(new int[] { 45, 52, 57, 60 }, new int[0], played).match();
		assertEquals(open, barre, 1e-12);
	}

	@Test
	public void aChordSharingAtMostOneNameWithAThreeNameTemplateMatchesAtMostOneOverRootThree() {
		int[] cMajor = { 48, 52, 55 };
		// C F A#: only C in common
		assertTrue(profile(cMajor, new int[0], frame(-20, 48, 53, 58)).match() <= 1.0 / Math.sqrt(3.0) + 1e-9);
		// D F# A: nothing in common
		assertEquals(0.0, profile(cMajor, new int[0], frame(-20, 50, 54, 57)).match(), 1e-12);
	}

	@Test
	public void twoMissingNamesOfAFourOrFiveNameChordAreTwoWeakNamesWhateverTheShare() {
		// G7 (G B D F) played as G B only, and C9 (C E G A# D) played as C E G only, the missing names silent
		ChordProfile seventh = profile(new int[] { 43, 47, 50, 53 }, new int[0], frame(-20, 43, 47, 55, 59));
		ChordProfile ninth = profile(new int[] { 48, 52, 55, 58, 62 }, new int[0], frame(-20, 48, 52, 55, 60, 64));
		assertTrue(seventh.match() >= 0.70, "a high match: " + seventh.match());
		for( double share : SHARES ) {
			assertTrue(seventh.weakNames(share) >= 2, "share " + share);
			assertTrue(ninth.weakNames(share) >= 2, "share " + share);
		}
		// the Normal share (0.40) and the lowest of the sweep
		assertEquals(2, seventh.weakNames(0.40));
		assertEquals(2, ninth.weakNames(0.05));
	}

	@Test
	public void aChordMissingOneNameOfFourHasOneWeakName() {
		ChordProfile profile = profile(new int[] { 43, 47, 50, 53 }, new int[0], frame(-20, 43, 47, 50, 55));
		for( double share : SHARES ) {
			assertEquals(1, profile.weakNames(share), "share " + share);
		}
	}

	@Test
	public void aToleratedNameAddsNothing() {
		int[] cMajor = { 48, 52, 55 };
		int[] ringingA = { 57 };
		double without = profile(cMajor, new int[0], frame(-20, 48, 52, 55)).match();
		// silent: as without it
		assertEquals(without, profile(cMajor, ringingA, frame(-20, 48, 52, 55)).match(), 1e-12);
		// still ringing, loud: it does not lower the match either
		ChordProfile ringing = profile(cMajor, ringingA, frame(new double[] { -20, 48, 52, 55 }, new double[] { -10, 57 }));
		assertEquals(without, ringing.match(), 1e-12);
		assertEquals(0, ringing.weakNames(0.40));
		// a tolerated pitch whose name is required is required
		assertEquals(1.0, profile(cMajor, new int[] { 60 }, frame(-20, 48, 52, 55)).match(), 1e-12);
	}

	@Test
	public void aBandOutsideTheRangeAddsNothing() {
		int[] cMajor = { 48, 52, 55 };
		// a semitone under the lowest required pitch is still summed; two under and above the top are not
		assertEquals(1.0, profile(cMajor, new int[0], frame(new double[] { -20, 48, 52, 55 }, new double[] { -10, 46, 86 })).match(), 1e-12);
		assertTrue(profile(cMajor, new int[0], frame(new double[] { -20, 48, 52, 55 }, new double[] { -10, 47 })).match() < 0.99);
	}

	@Test
	public void resetEmptiesTheSums() {
		ChordProfile profile = profile(new int[] { 48, 52, 55 }, new int[0], frame(-20, 48, 52, 55));
		assertTrue(profile.match() > 0.99);
		profile.reset(new int[] { 45, 52, 60 }, new int[0], TOP, 1_000L);
		assertTrue(profile.isEmpty());
		assertEquals(0.0, profile.match(), 0.0);
		assertEquals(4_000L, profile.summedNanos(5_000L));
	}

	@Test
	public void theWeakestNamesGiveTheirRequiredPitches() {
		// E loud, A loud, C soft: C is the weakest; both A pitches share a name
		ChordProfile profile = profile(new int[] { 45, 52, 57, 60 }, new int[0], frame(new double[] { -20, 45, 52, 57 }, new double[] { -50, 60 }));
		assertArrayEquals(new int[] { 60 }, profile.weakest(1));
		assertEquals(1, profile.weakNames(0.40));
	}

	@Test
	public void anEmptyProfileMatchesZero() {
		ChordProfile profile = profile(new int[] { 48, 52, 55 }, new int[0]);
		assertEquals(0.0, profile.match(), 0.0);
		assertTrue(profile.isEmpty());
		profile.add(frame(-20));
		assertEquals(0.0, profile.match(), 0.0);
	}

	@Test
	public void addingAFrameAllocatesNothing() {
		ChordProfile profile = profile(new int[] { 45, 52, 57, 60, 64 }, new int[] { 40 });
		SpectrumFrame frame = frame(new double[] { -20, 45, 52, 57 }, new double[] { -30, 60, 64 });
		for( int i = 0 ; i < 1_000 ; i ++ ) {
			profile.add(frame);
			profile.match();
			profile.weakNames(0.40);
		}
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		long thread = Thread.currentThread().getId();
		long before = threads.getThreadAllocatedBytes(thread);
		for( int i = 0 ; i < 10_000 ; i ++ ) {
			profile.add(frame);
			profile.match();
			profile.weakNames(0.40);
		}
		long allocated = threads.getThreadAllocatedBytes(thread) - before;
		// the measurement itself may allocate one small object
		assertTrue(allocated < 1_000, allocated + " bytes over 10 000 frames");
	}
}
