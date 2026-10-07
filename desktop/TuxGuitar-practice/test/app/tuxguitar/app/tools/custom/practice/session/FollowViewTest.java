package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.ui.FollowView;
import app.tuxguitar.app.tools.custom.practice.ui.FollowView.Tone;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;

/**
 * The feedback of the palette in the tempo-following mode (spec 002 T098, T099), on whole runs of the fake player: one line for each kind of
 * result, the kinds told apart, the precision said without blocking, the summary at the end. The texts here are the keys with their arguments, so
 * that what is checked is the choice of the text and not a language (the three languages are checked by FollowStringsTest).
 */
public class FollowViewTest {

	private static final FollowView.Text KEYS = (key, arguments) -> arguments.length == 0 ? key : key + java.util.Arrays.toString(arguments);

	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	private static FollowView view(FakePlayerRun run) {
		return FollowView.of(run.last, run.sequence.getEvents(), -1, 0, KEYS);
	}

	/** The run played up to just after the given event, and the view of that moment. */
	private static FollowView viewAfter(FakePlayerRun run, int event) {
		run.runUntil(run.target(event) + 450 * MS);
		return view(run);
	}

	@Test
	public void eachKindOfResultHasItsOwnLineAndTone() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.playEvent(1, -150);
		run.playEvent(2, 150);
		run.play(run.target(3), 70, 600 * MS);
		// event 4 is not played

		FollowView onTime = viewAfter(run, 0);
		assertEquals(Tone.GOOD, onTime.tone());
		assertEquals("practice.follow.event.ON_TIME", onTime.latest());

		FollowView early = viewAfter(run, 1);
		assertEquals(Tone.GOOD, early.tone());
		assertEquals("practice.follow.event.EARLY[150]", early.latest(), "the right note, early, by how much");

		FollowView late = viewAfter(run, 2);
		assertEquals(Tone.GOOD, late.tone());
		assertEquals("practice.follow.event.LATE[150]", late.latest());

		FollowView wrong = viewAfter(run, 3);
		assertEquals(Tone.BAD, wrong.tone());
		assertEquals("practice.follow.event.WRONG_PITCH", wrong.latest());

		FollowView missed = viewAfter(run, 4);
		assertEquals(Tone.BAD, missed.tone());
		assertEquals("practice.follow.event.MISSED", missed.latest());
	}

	@Test
	public void anExtraNoteIsShownWhenItIsNewerThanTheLastEventAndTheCountsSayAll() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.play(run.target(0) + 250 * MS, 71, 120 * MS);
		run.runUntil(run.target(0) + 700 * MS);

		FollowView first = FollowView.of(run.last, run.sequence.getEvents(), -1, 0, KEYS);
		FollowView afterExtra = FollowView.of(run.last, run.sequence.getEvents(), first.latestEvent(), 0, KEYS);

		assertEquals("practice.follow.event.EXTRA", afterExtra.latest());
		assertEquals(Tone.BAD, afterExtra.tone());
		assertEquals(1, afterExtra.latestExtras());
		assertTrue(afterExtra.counts().startsWith("practice.follow.counts[1, 0, 0, 0, 0, 1]"), afterExtra.counts());
		FollowView later = FollowView.of(run.last, run.sequence.getEvents(), first.latestEvent(), afterExtra.latestExtras(), KEYS);
		assertEquals("practice.follow.event.ON_TIME", later.latest(), "once shown, the extra note gives the line back to the events");
	}

	@Test
	public void anEventNotJudgedIsSaidToBeNotJudgedAndCountedApart() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.runUntil(run.target(0) + 450 * MS);
		run.session.pause(PracticeSession.PauseReason.INPUT_UNUSABLE);
		run.runUntil(run.target(2) + 300 * MS);
		FollowView paused = view(run);
		assertTrue(paused.state().contains("practice.follow.state.PAUSED"));
		assertTrue(paused.state().contains("practice.pause.INPUT_UNUSABLE"));
		run.session.resume(PracticeSession.PauseReason.INPUT_UNUSABLE);
		run.runUntil(run.target(3) - 50 * MS);

		FollowView resumed = view(run);

		assertEquals(Tone.NEUTRAL, resumed.tone(), "neither good nor bad");
		assertEquals("practice.follow.event.NOT_JUDGED", resumed.latest());
		assertTrue(resumed.counts().contains("practice.follow.counts.not-judged[2]"), resumed.counts());
		assertTrue(resumed.counts().startsWith("practice.follow.counts[1, 0, 0, 0, 0, 0]"), "no miss, no wrong note: " + resumed.counts());
	}

	@Test
	public void theNextEventIsNamedWhileFollowingAndTheStateWhileWaiting() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.runUntil(run.target(0) - 100 * MS);
		assertEquals("practice.follow.state.COUNT_IN", view(run).state());

		run.playEvent(0, 0);
		run.runUntil(run.target(0) + 100 * MS);
		FollowView following = view(run);
		assertEquals("practice.follow.state.FOLLOWING", following.state());
		assertTrue(following.next().startsWith("practice.follow.event.waiting["), following.next());
		assertEquals(" ", following.techniques());
	}

	@Test
	public void theReducedPrecisionIsSaidWithItsReasonsAndNothingIsSaidWhenItIsNormal() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.runUntil(run.target(0) + 100 * MS);

		FollowView notCalibrated = view(run);
		// the run of the fake player is calibrated (zero offset by hand of the harness): precision is normal
		assertEquals("", notCalibrated.precision());
		run.captureReady = false;
		run.runUntil(run.now + 100 * MS);
		assertEquals("practice.follow.precision.NOT_READY", view(run).precision());
	}

	@Test
	public void aRouteThatIsNotCalibratedOrNotStableIsSaidReducedWithEachReasonApartAndRunsOn() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.compensation = LatencyCompensation.none();
		run.spreadMillis = 150.0;
		run.start();
		run.playEvent(0, 0);
		run.runUntil(run.target(0) + 450 * MS);

		FollowView view = view(run);

		assertTrue(view.precision().startsWith("practice.follow.precision.reduced"), view.precision());
		assertTrue(view.precision().contains("practice.follow.precision.reason.NOT_CALIBRATED"));
		assertTrue(view.precision().contains("practice.follow.precision.reason.UNSTABLE_ROUTE"));
		assertEquals(Tone.GOOD, view.tone(), "reduced precision does not stop the verdicts");
		assertEquals("practice.follow.state.FOLLOWING", view.state());
	}

	@Test
	public void theTechniquesOfTheLatestEventAreAnotherLineThanItsTiming() {
		SongFixture fixture = new SongFixture(1);
		fixture.bend(0, 0, 3, 7, 2);
		fixture.quarter(0, 1, 1, 1);
		FakePlayerRun run = new FakePlayerRun(fixture, 100);
		run.session.setTechniqueJudge(new TechniqueJudge(TechniqueCatalog.of(new BendDetector(BendDetector.Parameters.defaults()), true, "test"), true));
		run.start();
		run.playMoving(run.target(0), 450 * MS, seconds -> 62.0 + 2.0 * Math.min(1.0, Math.max(0.0, (seconds - 0.1) / 0.2)));
		run.playEvent(1, 0);
		run.runUntil(run.target(1) + 450 * MS);
		FollowView bend = FollowView.of(run.last, run.sequence.getEvents(), -1, 0, KEYS);

		assertEquals("practice.follow.event.ON_TIME", bend.latest(), "the timing line says nothing of the bend");
		assertTrue(bend.techniques().contains("practice.technique.name.BEND"), bend.techniques());
		assertTrue(bend.techniques().contains("practice.technique.verdict.DETECTED"), bend.techniques());
	}

	@Test
	public void theSummaryAppearsAtTheEndOnlyAndSaysEveryKindApart() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.playEvent(1, -150);
		run.playEvent(2, 150);
		run.play(run.target(3), 70, 600 * MS);
		run.runUntil(run.target(1) + 450 * MS);
		assertEquals("", view(run).summary(), "no summary while the run goes on");

		run.runAll();
		FollowView ended = view(run);

		String summary = ended.summary();
		for (String key : new String[] { "title", "events[5]", "on-time[1]", "early[1]", "late[1]", "wrong[1]", "missed[1]", "extra[0]", "memory" }) {
			assertTrue(summary.contains("practice.follow.summary." + key), key + " in " + summary);
		}
		assertFalse(summary.contains("not-judged"), "no event was left unjudged");
	}
}
