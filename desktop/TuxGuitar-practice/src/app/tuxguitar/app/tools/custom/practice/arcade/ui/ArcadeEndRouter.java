package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import app.tuxguitar.app.tools.custom.practice.session.FollowRun;

/**
 * Brings the end of a run to the controller of that same run, and to no other (owner, 2026-10-11, the button Restart). A run that is given back reports its
 * end later, on the UI thread, when the next run and its controller already exist: only the latest run is listened to. UI thread only.
 */
public final class ArcadeEndRouter {

	/** One run: its end is told to its controller only while it is the latest. */
	public final class Run {
		private ArcadeController controller;

		/** The controller of this run, once it is built (the run exists before its controller does). */
		public void attach(ArcadeController controller) {
			this.controller = controller;
		}

		/** The end of this run, from its own listener. */
		public void ended(FollowRun.Ended reason) {
			if (ArcadeEndRouter.this.latest == this && this.controller != null) {
				this.controller.ended(reason);
			}
		}
	}

	private Run latest;

	/** A new run starts: the ones before it are not listened to any more. */
	public Run next() {
		this.latest = new Run();
		return this.latest;
	}
}
