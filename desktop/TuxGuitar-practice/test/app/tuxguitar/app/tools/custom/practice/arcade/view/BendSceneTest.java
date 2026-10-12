package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/** The bend in the picture (spec 003 FR-077, owner's variant A): in flight and on the neck, only for a note, and nothing else changes. */
public class BendSceneTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	/** A whole-tone bend at fret 5 of string 3, then a semitone bend at fret 7 of string 2 one beat and a half later, then a plain note. */
	private static SceneFixture bends() {
		SongFixture song = new SongFixture(3);
		song.bend(1, 0, 3, 5, 2);
		song.bend(1, 2, 2, 7, 1);
		song.quarter(1, 3, 1, 3);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	@Test
	public void aBentNoteComingHasASmallArrowAndItsAmountAndAPlainNoteHasNone() {
		SceneFixture fixture = bends();
		for (QualityTier tier : QualityTier.values()) {
			Scene scene = fixture.scene(false).build(fixture.onset(0) - 700 * MS, SCREEN, null, 100, tier);
			assertFalse(scene.forEvent(1, Role.BEND_ARROW).isEmpty(), tier + ": the arrow of the coming bend");
			assertEquals(2, scene.forEvent(1, Role.BEND_HEAD).size(), tier + ": its head");
			assertEquals("½", scene.forEvent(1, Role.BEND_LABEL).get(0).text(), tier.name());
			assertTrue(scene.forEvent(2, Role.BEND_ARROW).isEmpty() && scene.forEvent(2, Role.BEND_LABEL).isEmpty(), tier + ": a plain note has no bend mark");
			assertTrue(scene.forEvent(1, Role.BEND_GHOST).isEmpty(), tier + ": the ghost is on the neck, for the active note only");
		}
	}

	@Test
	public void theArrowRisesFromTheRightEndOfTheRectangleAndTheAmountSitsBesideItsHead() {
		SceneFixture fixture = bends();
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 700 * MS, SCREEN, null, 100, QualityTier.FULL);
		SceneItem marker = scene.forEvent(1, Role.MARKER).get(0);
		SceneItem arrow = scene.forEvent(1, Role.BEND_ARROW).get(0);
		SceneItem label = scene.forEvent(1, Role.BEND_LABEL).get(0);
		assertEquals(SceneItem.Shape.CURVE, arrow.shape());
		assertTrue(arrow.value(0) > marker.value(0), "it leaves the right half of the rectangle");
		assertTrue(arrow.value(1) < marker.value(1) + 1e-9 && arrow.value(7) < arrow.value(1), "and rises above it");
		assertTrue(label.value(0) >= arrow.value(6) - 1.0 || label.align() == SceneItem.Align.LEFT, "the amount is beside the head");
		assertFalse(Integer.valueOf(arrow.colour()).equals(scene.forEvent(1, Role.MARKER).get(0).colour()), "the mark is not in the colour of a string");
	}

	@Test
	public void theActiveBentNoteHasAGhostAtTheCellOfThePeakOnTheSameStringAndAnArrowToIt() {
		SceneFixture fixture = bends();
		Scene scene = fixture.scene(false).build(fixture.onset(0), SCREEN, null, 100, QualityTier.FULL);
		SceneItem target = scene.forEvent(0, Role.TARGET_RECT).get(0);
		SceneItem ghost = scene.forEvent(0, Role.BEND_GHOST).get(0);
		assertEquals(SceneItem.Shape.ROUNDED_RING, ghost.shape());
		assertEquals(target.value(1), ghost.value(1), 0.5, "on the same string");
		Camera camera = fixture.camera.at(fixture.onset(0));
		IsometricProjection projection = new IsometricProjection(SCREEN);
		assertEquals(projection.neckX(fixture.neck.fretCentreX(7), camera), ghost.value(0), 1.5, "the whole tone reaches the cell two frets higher");
		assertFalse(scene.forEvent(0, Role.BEND_ARROW).isEmpty());
		assertEquals("full", scene.forEvent(0, Role.BEND_LABEL).get(0).text());
		List<SceneItem> heads = scene.forEvent(0, Role.BEND_HEAD);
		assertEquals(2, heads.size());
	}

	@Test
	public void theBendMarksChangeNeitherTheTargetNorTheMarkerOfTheNote() {
		SceneFixture bent = bends();
		SongFixture plain = new SongFixture(3);
		plain.quarter(1, 0, 3, 5);
		plain.quarter(1, 2, 2, 7);
		plain.quarter(1, 3, 1, 3);
		SceneFixture flat = new SceneFixture(plain, SceneFixture.sixStrings(), 100);
		Scene a = bent.scene(false).build(bent.onset(0) - 300 * MS, SCREEN, null, 100, QualityTier.FULL);
		Scene b = flat.scene(false).build(flat.onset(0) - 300 * MS, SCREEN, null, 100, QualityTier.FULL);
		for (Role role : new Role[] { Role.MARKER, Role.TARGET_RECT, Role.MARKER_THREAD, Role.MARKER_FOOTPRINT }) {
			assertEquals(b.forEvent(0, role).size(), a.forEvent(0, role).size(), role.name());
			for (int i = 0; i < b.forEvent(0, role).size(); i++) {
				for (int k = 0; k < 4; k++) {
					assertEquals(b.forEvent(0, role).get(i).value(k), a.forEvent(0, role).get(i).value(k), 1e-9, role + " " + k);
				}
			}
		}
	}

	@Test
	public void aBentNoteInAChordShowsNothingOfItsBend() {
		SongFixture song = new SongFixture(2);
		song.bend(1, 0, 3, 5, 2);
		song.quarter(1, 0, 2, 5);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 300 * MS, SCREEN, null, 100, QualityTier.FULL);
		assertTrue(scene.withRole(Role.BEND_ARROW).isEmpty() && scene.withRole(Role.BEND_GHOST).isEmpty() && scene.withRole(Role.BEND_LABEL).isEmpty());
	}

	/** "Never drawn over a label" (FR-077): the amounts of the bends overlap no other label, whatever the moment and the tier. */
	@Test
	public void theAmountsOfTheBendsOverlapNoOtherLabel() {
		SongFixture song = new SongFixture(3);
		song.bend(1, 0, 3, 5, 2);
		song.bend(1, 1, 2, 7, 1);
		song.bend(1, 2, 4, 5, 3);
		song.quarter(1, 3, 1, 3);
		song.quarter(2, 0, 5, 9);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		int checked = 0;
		for (QualityTier tier : QualityTier.values()) {
			for (long offset = -2400; offset <= 600; offset += 100) {
				Scene scene = fixture.scene(false).build(fixture.onset(0) + offset * MS, SCREEN, null, 100, tier);
				assertEquals(null, SceneBoxes.firstOverlap(SceneBoxes.labels(scene)), tier + " at " + offset);
				checked += scene.withRole(Role.BEND_LABEL).size();
			}
		}
		assertTrue(checked > 20, "bend labels were really measured: " + checked);
	}
}
