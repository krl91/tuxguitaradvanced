package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.ui.FollowView;

/**
 * The reason of a refused event in the palette (spec 016 FR-001, FR-002): on its own line, with its detail, and counted in the summary. The texts
 * are the keys with their arguments, as in FollowViewTest; the three languages are checked by FollowReasonStringsTest.
 */
public class FollowViewReasonTest {

	private static final FollowView.Text KEYS = (key, arguments) -> arguments.length == 0 ? key : key + java.util.Arrays.toString(arguments);

	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	@Test
	public void theReasonIsShownWithItsDetailAndTheSummaryCountsIt() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.playEvent(1, 0);
		run.play(run.target(2) + 250 * MS, 66, 150 * MS);   // the right note, 250 ms after its window's target
		run.play(run.target(3), 70, 300 * MS);              // another note
		// event 4 is not played
		run.runAll();

		FollowSession.EventResult late = run.last.results().get(2);
		assertEquals("practice.follow.reason.LATE_OUTSIDE[250]", FollowView.reasonText(late, run.sequence.getEvents().get(2), KEYS));
		FollowSession.EventResult wrong = run.last.results().get(3);
		assertEquals(70L, wrong.reasonDetail());
		String other = FollowView.reasonText(wrong, run.sequence.getEvents().get(3), KEYS);
		assertTrue(other.matches("practice\\.follow\\.reason\\.OTHER_PITCH\\[[^,\\]]+, [^,\\]]+\\]") && !other.contains("[, "), "the note heard and the expected one, named: " + other);
		assertEquals("practice.follow.reason.NO_ATTACK", FollowView.reasonText(run.last.results().get(4), run.sequence.getEvents().get(4), KEYS));
		assertEquals("", FollowView.reasonText(run.last.results().get(0), run.sequence.getEvents().get(0), KEYS), "an accepted note has no reason");

		FollowView view = FollowView.of(run.last, run.sequence.getEvents(), -1, 0, KEYS);
		assertEquals("practice.follow.reason.NO_ATTACK", view.reason(), "the latest event's reason, on its own line");
		assertEquals("practice.follow.event.MISSED", view.latest(), "the verdict line is unchanged");
		for (String line : new String[] { "practice.follow.summary.reason.LATE_OUTSIDE[1]", "practice.follow.summary.reason.OTHER_PITCH[1]", "practice.follow.summary.reason.NO_ATTACK[1]" }) {
			assertTrue(view.summary().contains(line), line + " in " + view.summary());
		}
	}
}
