package app.tuxguitar.app.tools.custom.practice.arcade.view;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;

/** A public door to the helper views of the scene tests, for the tests of other packages. */
public final class SceneBoxesAccess {

	private SceneBoxesAccess() {
	}

	/** A run in which every event before the given one is completed: the given one is the active event of the neck. */
	public static ArcadeRun.View completedBefore(int index, int total) {
		return ArcadeSceneTest.completedBefore(index, total);
	}
}
