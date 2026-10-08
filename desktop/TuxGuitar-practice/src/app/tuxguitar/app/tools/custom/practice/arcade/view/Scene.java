package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The picture at one instant: the items in paint order, plus the facts a test or the canvas needs. Immutable. */
public final class Scene {

	private final List<SceneItem> items;
	private final long nowNanos;
	private final Viewport viewport;
	private final Theme theme;

	Scene(List<SceneItem> items, long nowNanos, Viewport viewport, Theme theme) {
		this.items = Collections.unmodifiableList(items);
		this.nowNanos = nowNanos;
		this.viewport = viewport;
		this.theme = theme;
	}

	public Theme theme() {
		return this.theme;
	}

	public List<SceneItem> items() {
		return this.items;
	}

	public long nowNanos() {
		return this.nowNanos;
	}

	public Viewport viewport() {
		return this.viewport;
	}

	public List<SceneItem> withRole(SceneItem.Role role) {
		List<SceneItem> found = new ArrayList<SceneItem>();
		for (SceneItem item : this.items) {
			if (item.role() == role) {
				found.add(item);
			}
		}
		return found;
	}

	public List<SceneItem> forEvent(int eventIndex) {
		List<SceneItem> found = new ArrayList<SceneItem>();
		for (SceneItem item : this.items) {
			if (item.eventIndex() == eventIndex) {
				found.add(item);
			}
		}
		return found;
	}

	public List<SceneItem> forEvent(int eventIndex, SceneItem.Role role) {
		List<SceneItem> found = new ArrayList<SceneItem>();
		for (SceneItem item : this.items) {
			if (item.eventIndex() == eventIndex && item.role() == role) {
				found.add(item);
			}
		}
		return found;
	}
}
