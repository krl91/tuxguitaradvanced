package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowRun.Ended;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * Regression (owner, gate G2 of spec 016): at the end of a run of Follow the palette offers Restart, but the run had ended with the song, its session
 * thread was stopped, and the command was dropped: the button did nothing. A Restart after the end still asks the window for a new run.
 */
public class FollowRunRestartAfterEndTest {

	private TGContext context;
	private FakePlayerRun fake;
	private final List<Ended> ends = new ArrayList<Ended>();

	@BeforeEach
	public void setUp() {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		this.fake = new FakePlayerRun(fixture, 100);
	}

	@AfterEach
	public void tearDown() {
		TGThreadManager.getInstance(this.context).dispose();
	}

	@Test
	public void aRestartAfterTheSongEndedAsksTheWindowForANewRun() {
		int[] restarts = new int[1];
		FollowRun run = new FollowRun(this.context, this.fake.session, this.fake.player, FakePlayerRun.TRACK, 100, FakePlayerRun.BASE_TICK, snapshot -> {
		}, Runnable::run, this.ends::add, null, () -> restarts[0]++);
		run.start();
		this.fake.player.advance(3_000 * MS);
		this.fake.player.stopsByItself();
		assertEquals(List.of(Ended.PLAYER_STOPPED), this.ends);
		assertTrue(run.isFinished());

		run.postCommand(PracticeCommand.RESTART);

		assertEquals(1, restarts[0], "Restart at the end of the song starts a new run");
	}

	@Test
	public void otherCommandsAfterTheEndStillDoNothing() {
		int[] restarts = new int[1];
		FollowRun run = new FollowRun(this.context, this.fake.session, this.fake.player, FakePlayerRun.TRACK, 100, FakePlayerRun.BASE_TICK, snapshot -> {
		}, Runnable::run, this.ends::add, null, () -> restarts[0]++);
		run.start();
		this.fake.player.stopsByItself();
		run.postCommand(PracticeCommand.RESUME);
		run.postCommand(PracticeCommand.SKIP);
		assertEquals(0, restarts[0]);
		assertEquals(List.of(Ended.PLAYER_STOPPED), this.ends, "nothing else happens");
	}
}
