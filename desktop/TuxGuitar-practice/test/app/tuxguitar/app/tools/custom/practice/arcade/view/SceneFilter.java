package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Test helper: the same scene without the items of some roles, to measure what is painted under them. */
public final class SceneFilter {

	private SceneFilter() {
	}

	public static Scene without(Scene scene, SceneItem.Role... roles) {
		List<SceneItem.Role> left = Arrays.asList(roles);
		List<SceneItem> items = new ArrayList<SceneItem>();
		for (SceneItem item : scene.items()) {
			if (!left.contains(item.role())) {
				items.add(item);
			}
		}
		return new Scene(items, scene.nowNanos(), scene.viewport(), scene.theme());
	}

	/** The scene without the figure of the incoming note, and without the faint lanes and the approach band that go on under the neck, so that the target on the neck is measured alone. */
	public static Scene withoutTheMarker(Scene scene) {
		return without(scene, SceneItem.Role.MARKER, SceneItem.Role.GLOW, SceneItem.Role.MARKER_THREAD, SceneItem.Role.MARKER_FOOTPRINT, SceneItem.Role.MARKER_FRET, SceneItem.Role.TRAIL, SceneItem.Role.NECK_LANE, SceneItem.Role.APPROACH_BAND);
	}
}
