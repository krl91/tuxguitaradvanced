package app.tuxguitar.app.tools.custom.practice.arcade.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;
import app.tuxguitar.app.tools.custom.practice.session.TimingVerdict;

/**
 * The verdict of an event from the result of the following session (spec 003 T012, research R6): total, unique, and the same wherever the pitch,
 * the timing and the techniques come from. The gap between `visual-design.md` (chord feedback of the wait mode) and the following mode is closed here.
 */
public class ArcadeVerdictTest {

	@Test
	public void aMatchedNoteIsOnTimeEarlyOrLateByItsTiming() {
		assertEquals(ArcadeVerdict.ON_TIME, ArcadeVerdict.of(Status.MATCHED, TimingVerdict.ON_TIME, false, false));
		assertEquals(ArcadeVerdict.EARLY, ArcadeVerdict.of(Status.MATCHED, TimingVerdict.EARLY, false, false));
		assertEquals(ArcadeVerdict.LATE, ArcadeVerdict.of(Status.MATCHED, TimingVerdict.LATE, false, false));
		assertEquals(ArcadeVerdict.ON_TIME, ArcadeVerdict.of(Status.MATCHED, TimingVerdict.ON_TIME, true, false), "a complete chord is on time like a note");
	}

	@Test
	public void aWrongNoteIsWrongAndAChordWithMissingPitchesIsIncomplete() {
		assertEquals(ArcadeVerdict.WRONG, ArcadeVerdict.of(Status.WRONG_PITCH, TimingVerdict.ON_TIME, false, false));
		assertEquals(ArcadeVerdict.WRONG, ArcadeVerdict.of(Status.WRONG_PITCH, TimingVerdict.LATE, true, false), "a chord that is wrong without a missing pitch is wrong");
		assertEquals(ArcadeVerdict.INCOMPLETE, ArcadeVerdict.of(Status.WRONG_PITCH, TimingVerdict.ON_TIME, true, true));
		assertEquals(ArcadeVerdict.WRONG, ArcadeVerdict.of(Status.WRONG_PITCH, TimingVerdict.ON_TIME, false, true), "missing pitches mean nothing for a single note");
	}

	@Test
	public void theOtherStatusesKeepTheirOwnVerdict() {
		assertEquals(ArcadeVerdict.MISSED, ArcadeVerdict.of(Status.MISSED, TimingVerdict.MISSED, false, false));
		assertEquals(ArcadeVerdict.NOT_JUDGED, ArcadeVerdict.of(Status.NOT_JUDGED, null, false, false));
		assertEquals(ArcadeVerdict.AUTO_PASSED, ArcadeVerdict.of(Status.AUTO_PASSED, null, false, false));
		assertEquals(ArcadeVerdict.WAITING, ArcadeVerdict.of(Status.PENDING, null, false, false));
	}

	@Test
	public void theMappingIsTotalAndAMatchedEventNeverBecomesAnError() {
		Set<ArcadeVerdict> seen = new HashSet<ArcadeVerdict>();
		for (Status status : Status.values()) {
			for (TimingVerdict timing : new TimingVerdict[] { null, TimingVerdict.ON_TIME, TimingVerdict.EARLY, TimingVerdict.LATE, TimingVerdict.MISSED, TimingVerdict.EXTRA }) {
				for (boolean chord : new boolean[] { false, true }) {
					for (boolean missing : new boolean[] { false, true }) {
						ArcadeVerdict verdict = ArcadeVerdict.of(status, timing, chord, missing);
						seen.add(verdict);
						if (status == Status.MATCHED) {
							assertEquals(true, EnumSet.of(ArcadeVerdict.ON_TIME, ArcadeVerdict.EARLY, ArcadeVerdict.LATE).contains(verdict), status + "/" + timing);
						}
					}
				}
			}
		}
		assertEquals(EnumSet.allOf(ArcadeVerdict.class), seen, "every verdict can be produced and none is left over");
	}
}
