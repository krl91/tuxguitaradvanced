package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import app.tuxguitar.app.tools.custom.practice.session.FollowPlayback;
import app.tuxguitar.app.tools.custom.practice.session.FollowRun;

/** What the controller of Arcade asks of the run of the session: the same calls as {@link FollowRun}, so that a test can stand a fake in its place. */
public interface ArcadeRunPort {

	FollowPlayback.Result start();

	void pause();

	boolean resume();

	void stop();

	static ArcadeRunPort of(final FollowRun run) {
		return new ArcadeRunPort() {
			@Override
			public FollowPlayback.Result start() {
				return run.start();
			}

			@Override
			public void pause() {
				run.pause();
			}

			@Override
			public boolean resume() {
				return run.resume();
			}

			@Override
			public void stop() {
				run.stop();
			}
		};
	}
}
