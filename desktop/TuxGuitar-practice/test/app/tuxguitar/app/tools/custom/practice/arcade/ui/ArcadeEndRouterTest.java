package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowPlayback;
import app.tuxguitar.app.tools.custom.practice.session.FollowRun;

/**
 * Bug (owner, 2026-10-11): the button Restart of the game showed the preparation window. The run that is given back reports its end later, on the UI thread,
 * when the new run (and its controller) already exists: that end must not reach the new run.
 */
public class ArcadeEndRouterTest {

	private static final class Run implements ArcadeRunPort {
		public FollowPlayback.Result start() {
			return FollowPlayback.Result.STARTED;
		}

		public void pause() {
		}

		public boolean resume() {
			return true;
		}

		public void stop() {
		}
	}

	private static final class Heard implements ArcadeController.Listener {
		final List<String> events = new ArrayList<String>();

		public void stateChanged(ArcadeController.State state) {
			this.events.add("state " + state);
		}

		public void ended(FollowRun.Ended reason, ArcadeResult result) {
			this.events.add("ended " + reason);
		}

		public void startFailed(FollowPlayback.Result result) {
			this.events.add("failed");
		}
	}

	private static ArcadeController controller(Heard heard) {
		ArcadeController controller = new ArcadeController(new Run(), new ArcadeController.Outcome() {
			public boolean isComplete() {
				return false;
			}

			public ArcadeResult result() {
				return null;
			}
		}, heard);
		controller.start();
		return controller;
	}

	@Test
	public void theEndOfTheCurrentRunReachesItsController() {
		ArcadeEndRouter router = new ArcadeEndRouter();
		Heard heard = new Heard();
		ArcadeEndRouter.Run run = router.next();
		run.attach(controller(heard));
		run.ended(FollowRun.Ended.PLAYER_STOPPED);
		assertEquals("ended PLAYER_STOPPED", heard.events.get(heard.events.size() - 1));
	}

	@Test
	public void theLateEndOfAGivenBackRunNeverReachesTheNewRun() {
		ArcadeEndRouter router = new ArcadeEndRouter();
		Heard first = new Heard();
		ArcadeEndRouter.Run old = router.next();
		old.attach(controller(first));
		// the restart: the new run exists before the old one reports its end
		Heard second = new Heard();
		ArcadeEndRouter.Run current = router.next();
		current.attach(controller(second));
		old.ended(FollowRun.Ended.STOPPED);
		assertEquals(List.of("state RUNNING"), second.events, "the new run goes on");
		assertEquals(List.of("state RUNNING"), first.events, "and the old controller, given back, is not told either");
	}

	@Test
	public void anEndBeforeTheControllerIsAttachedIsIgnoredAndDoesNotFail() {
		ArcadeEndRouter router = new ArcadeEndRouter();
		router.next().ended(FollowRun.Ended.FAILED);
	}
}
