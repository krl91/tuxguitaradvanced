package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/** The summary of a Follow run counts the refusals by reason (spec 016 FR-002): one count per event not accepted on time, adding up to them. */
public class FollowSummaryReasonsTest {

	private static SongFixture sixNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		fixture.quarter(1, 1, 1, 5);
		return fixture;
	}

	@Test
	public void aRunWithOneOfEachRefusalCountsOneOfEach() {
		FakePlayerRun run = new FakePlayerRun(sixNotes(), 100);
		run.start();
		run.playEvent(0, 0);                                     // on time
		run.play(run.target(1), 70, 300 * MS);                   // another pitch
		// event 2 is not played
		run.play(run.target(3) + 250 * MS, 67, 150 * MS);        // the right note, after its window
		run.playEvent(4, 0);                                     // on time
		run.play(run.target(5) - 250 * MS, 69, 150 * MS);        // the right note, before its window (and nothing in it)
		run.runAll();

		FollowSummary summary = FollowSummary.of(run.last);
		assertEquals(1, summary.reasonCount(RefusalReason.OTHER_PITCH));
		assertEquals(1, summary.reasonCount(RefusalReason.NO_ATTACK));
		assertEquals(1, summary.reasonCount(RefusalReason.LATE_OUTSIDE));
		assertEquals(1, summary.reasonCount(RefusalReason.EARLY_OUTSIDE));
		assertEquals(0, summary.reasonCount(RefusalReason.UNCLEAR));
		assertEquals(0, summary.reasonCount(RefusalReason.NOT_JUDGED));
		assertEquals(0, summary.reasonCount(RefusalReason.NONE), "NONE is not a refusal");
		assertTotalOfReasons(summary);
	}

	@Test
	public void aRunPlayedRightHasNoRefusal() {
		FakePlayerRun run = new FakePlayerRun(sixNotes(), 100);
		run.start();
		for (int i = 0; i < 6; i++) {
			run.playEvent(i, 0);
		}
		run.runAll();
		FollowSummary summary = FollowSummary.of(run.last);
		assertEquals(0, summary.refused());
		assertTotalOfReasons(summary);
	}

	private static final app.tuxguitar.app.tools.custom.practice.ui.FollowView.Text KEYS = (key, arguments) -> arguments.length == 0 ? key
			: key + java.util.Arrays.toString(arguments);

	@Test
	public void theSummaryOfAFollowRunNamesItsPositionAndItsTimeToPlay() {
		// spec 016 T033, FR-007
		FakePlayerRun run = new FakePlayerRun(sixNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.runAll();
		FollowSummary summary = FollowSummary.of(run.last);
		String text = app.tuxguitar.app.tools.custom.practice.ui.FollowView.summaryText(summary, KEYS, FollowLeniencyPosition.VERY_PERMISSIVE, 450);
		assertTrue(text.contains("practice.follow.summary.settings[practice.follow.leniency.very_permissive, 450]"), text);
		assertEquals(app.tuxguitar.app.tools.custom.practice.ui.FollowView.summaryText(summary, KEYS) + "\n"
				+ "practice.follow.summary.settings[practice.follow.leniency.very_permissive, 450]", text, "one line more, at the end");
	}

	@Test
	public void aSummaryWithoutAPositionHasNoSuchLine() {
		FakePlayerRun run = new FakePlayerRun(sixNotes(), 100);
		run.start();
		run.runAll();
		FollowSummary summary = FollowSummary.of(run.last);
		String text = app.tuxguitar.app.tools.custom.practice.ui.FollowView.summaryText(summary, KEYS, null, -1);
		assertEquals(app.tuxguitar.app.tools.custom.practice.ui.FollowView.summaryText(summary, KEYS), text);
		assertFalse(text.contains("practice.follow.summary.settings"));
	}

	private static void assertTotalOfReasons(FollowSummary summary) {
		int total = 0;
		for (RefusalReason reason : RefusalReason.values()) {
			total += summary.reasonCount(reason);
		}
		assertEquals(summary.wrong() + summary.missed() + summary.notJudged(), total, "the counts add up to the events not accepted on time");
		assertEquals(total, summary.refused());
	}
}
