package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;

/** The rectangles of the labels of a scene, read from its items, for the tests of the density rules (spec 003 FR-061, FR-062, SC-018, SC-019). */
public final class SceneBoxes {

	/** A rectangle with the identity of the event it belongs to (-1 for the neck and the HUD) and what it is. */
	public static final class Box {
		public final double x1;
		public final double y1;
		public final double x2;
		public final double y2;
		public final int event;
		public final String what;

		Box(double x1, double y1, double x2, double y2, int event, String what) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.event = event;
			this.what = what;
		}

		public boolean overlaps(Box other) {
			return this.x1 < other.x2 && other.x1 < this.x2 && this.y1 < other.y2 && other.y1 < this.y2;
		}

		public boolean contains(Box other) {
			return other.x1 >= this.x1 - 0.5 && other.x2 <= this.x2 + 0.5 && other.y1 >= this.y1 - 0.5 && other.y2 <= this.y2 + 0.5;
		}

		@Override
		public String toString() {
			return String.format("%s of event %d [%.0f,%.0f - %.0f,%.0f]", this.what, this.event, this.x1, this.y1, this.x2, this.y2);
		}
	}

	private SceneBoxes() {
	}

	public static Box ofQuad(SceneItem item) {
		double x1 = Double.MAX_VALUE;
		double y1 = Double.MAX_VALUE;
		double x2 = -Double.MAX_VALUE;
		double y2 = -Double.MAX_VALUE;
		for (int i = 0; i < 8; i += 2) {
			x1 = Math.min(x1, item.value(i));
			x2 = Math.max(x2, item.value(i));
			y1 = Math.min(y1, item.value(i + 1));
			y2 = Math.max(y2, item.value(i + 1));
		}
		return new Box(x1, y1, x2, y2, item.eventIndex(), item.role().name());
	}

	public static Box ofDisc(SceneItem item) {
		return new Box(item.value(0) - item.value(2), item.value(1) - item.value(3), item.value(0) + item.value(2), item.value(1) + item.value(3), item.eventIndex(),
				item.role().name());
	}

	/** The room of a text: the widest letters of a bold sans-serif font, as the scene itself estimates it. */
	public static Box ofText(SceneItem item) {
		double width = item.text().length() * item.value(2) * 0.66;
		double x = item.value(0);
		double left = item.align() == SceneItem.Align.CENTRE ? x - width / 2.0 : item.align() == SceneItem.Align.RIGHT ? x - width : x;
		return new Box(left, item.value(1) - item.value(2) * 0.6, left + width, item.value(1) + item.value(2) * 0.6, item.eventIndex(), item.role().name());
	}

	/** The identities of the events that are fully detailed: those that show a fret number, a diagram or a label of their own. */
	public static java.util.Set<Integer> detailed(Scene scene) {
		java.util.Set<Integer> detailed = new java.util.TreeSet<Integer>();
		for (SceneItem item : scene.items()) {
			if (item.eventIndex() >= 0 && (item.role() == Role.MARKER_FRET || item.role() == Role.CARTOUCHE_FRAME || item.role() == Role.EVENT_TEXT
					|| item.role() == Role.BADGE)) {
				detailed.add(item.eventIndex());
			}
		}
		return detailed;
	}

	/** Every label that must not overlap another: the fret number of a note (its marker), the diagram of a chord with its header, the text of an automatic event, the verdict badges, the HUD and the neck's own numbers. */
	public static List<Box> labels(Scene scene) {
		java.util.Set<Integer> numbered = new java.util.HashSet<Integer>();
		for (SceneItem item : scene.items()) {
			if (item.role() == Role.MARKER_FRET) {
				numbered.add(item.eventIndex());
			}
		}
		List<Box> boxes = new ArrayList<Box>();
		for (SceneItem item : scene.items()) {
			switch (item.role()) {
				case MARKER:
					if (numbered.contains(item.eventIndex())) {
						boxes.add(ofDisc(item));
					}
					break;
				case CARTOUCHE_FRAME:
				case BADGE:
					boxes.add(ofQuad(item));
					break;
				case EVENT_TEXT:
				case HUD_STREAK:
				case HUD_SPEED:
				case FRET_NUMBER:
				case STRING_LABEL:
					boxes.add(ofText(item));
					break;
				default:
					break;
			}
		}
		return boxes;
	}

	/** The first pair of labels of different owners that overlap, or null. */
	public static String firstOverlap(List<Box> boxes) {
		for (int i = 0; i < boxes.size(); i++) {
			for (int j = i + 1; j < boxes.size(); j++) {
				Box a = boxes.get(i);
				Box b = boxes.get(j);
				boolean sameOwner = a.event >= 0 && a.event == b.event;
				if (!sameOwner && a.overlaps(b)) {
					return a + " overlaps " + b;
				}
			}
		}
		return null;
	}
}
