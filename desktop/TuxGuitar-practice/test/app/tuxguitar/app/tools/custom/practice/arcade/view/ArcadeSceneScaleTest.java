package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;

/**
 * A change of scale of the display, as when a window moves to or from a Retina screen (spec 003 FR-059, T048): the scene is built in the points of the
 * window, so doubling both sides doubles every essential position and size, the hit line and the targets keep their relative place, and the labels stay
 * disjoint and inside. The toolkit then draws those points at its own density, which keeps the text and the lines sharp.
 */
public class ArcadeSceneScaleTest {

	@Test
	public void doublingTheWindowDoublesEveryEssentialPositionAndTextSizeAndKeepsTheLabelsApart() {
		for (ReferenceScenes.Reference reference : ReferenceScenes.all()) {
			Scene one = reference.fixture.scene(false).build(reference.nowNanos, new Viewport(1280, 720), reference.run, 100);
			Scene two = reference.fixture.scene(false).build(reference.nowNanos, new Viewport(2560, 1440), reference.run, 100);
			for (Role role : new Role[] { Role.HIT_LINE, Role.STRING, Role.NUT, Role.FRET_WIRE, Role.STRING_LABEL, Role.FRET_NUMBER }) {
				assertEquals(one.withRole(role).size(), two.withRole(role).size(), reference.name + " " + role);
				for (int i = 0; i < one.withRole(role).size(); i++) {
					SceneItem a = one.withRole(role).get(i);
					SceneItem b = two.withRole(role).get(i);
					for (int v = 0; v < a.valueCount(); v++) {
						assertEquals(2.0 * a.value(v), b.value(v), 1e-6, reference.name + " " + role + " value " + v);
					}
				}
			}
			assertNull(SceneBoxes.firstOverlap(SceneBoxes.labels(two)), reference.name + " at the doubled size");
			assertNull(SceneBoxes.firstOverlap(SceneBoxes.labels(one)), reference.name + " at the normal size");
			assertTrue(SceneBoxes.detailed(two).size() <= ArcadeScene.MAX_DETAILED);
			assertEquals(SceneBoxes.detailed(one), SceneBoxes.detailed(two), reference.name + ": the same events are detailed at both scales");
		}
	}
}
