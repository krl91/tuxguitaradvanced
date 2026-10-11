package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The chord panel (spec 003 FR-074, SC-028, the owner's references of 2026-10-11): a chord is a translucent panel across the lanes, in perspective, growing with
 * its progress and landing on the top edge of the neck, with a rounded rectangle for every fretted position at its fret and its string, a line across for an
 * open string, the fret number on the rectangles of the active chord only and the name above the left corner. No bar and no diagram remain.
 */
public class ChordPanelTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	/** An open-position chord with an open string, a chord high on the neck, then a note. */
	private static SceneFixture chords() {
		SongFixture song = new SongFixture(3);
		song.chord(1, 0, new int[] { 5, 4, 3, 2 }, new int[] { 3, 2, 0, 1 });
		song.chord(1, 2, new int[] { 6, 5, 4 }, new int[] { 8, 10, 10 });
		song.quarter(2, 0, 2, 5);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static double[] bounds(SceneItem quad) {
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			minX = Math.min(minX, quad.value(2 * i));
			maxX = Math.max(maxX, quad.value(2 * i));
			minY = Math.min(minY, quad.value(2 * i + 1));
			maxY = Math.max(maxY, quad.value(2 * i + 1));
		}
		return new double[] { minX, minY, maxX, maxY };
	}

	@Test
	public void aChordIsATranslucentPanelFromItsFirstFretToItsLastGrowingWithItsProgressAndLandingOnTheNeck() {
		SceneFixture fixture = chords();
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		IsometricProjection projection = new IsometricProjection(SCREEN);
		double previousHeight = 0.0;
		for (int step = 1; step <= 101; step++) {
			double progress = step / 101.0;
			long now = fixture.onset(0) - (long) ((1.0 - progress) * look);
			Scene scene = fixture.scene(false).build(now, SCREEN, null, 100, QualityTier.FULL);
			List<SceneItem> panels = scene.forEvent(0, Role.CHORD_PANEL);
			assertEquals(1, panels.size(), "one panel for the chord at p=" + progress);
			SceneItem panel = panels.get(0);
			assertEquals(SceneItem.Shape.QUAD, panel.shape());
			assertTrue(panel.alpha() > 0.0 && panel.alpha() <= 0.3, "translucent: the scene behind stays visible");
			Camera cam = fixture.camera.at(now);
			double[] box = bounds(panel);
			// the chord of the fixture is on frets 1 to 3: the panel spans the cells of the first to the last fret, inclusive (the owner, 2026-10-11)
			ScreenPoint left = projection.approach(fixture.neck.fretX(0), progress, cam);
			ScreenPoint right = projection.approach(fixture.neck.fretX(3), progress, cam);
			assertEquals(left.x(), box[0], 0.5, "from the first cell played (left)");
			assertEquals(right.x(), box[2], 0.5, "to the last cell played (right)");
			assertEquals(left.y(), box[3], 0.5, "its lower edge is on the line of the lanes");
			assertTrue(box[3] - box[1] >= previousHeight - 1e-9, "it grows as it comes");
			previousHeight = box[3] - box[1];
			assertEquals(4, scene.forEvent(0, Role.CHORD_PANEL_EDGE).size(), "a luminous edge on each side");
			assertTrue(scene.forEvent(0, Role.TIMING_BAR).isEmpty() && scene.forEvent(0, Role.MARKER).isEmpty(), "no bar and no flying marker for a chord");
		}
		Scene landed = fixture.scene(false).build(fixture.onset(0), SCREEN, null, 100, QualityTier.FULL);
		assertEquals(projection.neckTopY(), bounds(landed.forEvent(0, Role.CHORD_PANEL).get(0))[3], 0.5, "it lands on the top edge of the neck at the onset");
	}

	@Test
	public void everyFrettedPositionIsARoundedRectangleAtItsFretAndItsStringAndAnOpenStringIsALineAcross() {
		SceneFixture fixture = chords();
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		IsometricProjection projection = new IsometricProjection(SCREEN);
		int[][] written = { { 5, 3 }, { 4, 2 }, { 3, 0 }, { 2, 1 } };
		for (int step = 1; step <= 101; step += 10) {
			double progress = step / 101.0;
			long now = fixture.onset(0) - (long) ((1.0 - progress) * look);
			Scene scene = fixture.scene(false).build(now, SCREEN, null, 100, QualityTier.FULL);
			Camera cam = fixture.camera.at(now);
			double[] box = bounds(scene.forEvent(0, Role.CHORD_PANEL).get(0));
			List<SceneItem> notes = scene.forEvent(0, Role.PANEL_NOTE);
			assertEquals(3, notes.size(), "one rectangle per fretted position, none for the open string");
			for (int[] position : written) {
				int string = position[0];
				int fret = position[1];
				if (fret == 0) {
					List<SceneItem> lines = scene.forEvent(0, Role.PANEL_OPEN_LINE);
					assertEquals(1, lines.size(), "the open string is one line");
					SceneItem line = lines.get(0);
					assertEquals(string, line.tgString());
					assertEquals(box[0], Math.min(line.value(0), line.value(2)), 0.5, "from one side of the panel");
					assertEquals(box[2], Math.max(line.value(0), line.value(2)), 0.5, "to the other");
					assertEquals(StringColours.forStrings(6).of(string), line.colour());
					continue;
				}
				SceneItem rect = null;
				for (SceneItem note : notes) {
					if (note.tgString() == string) {
						rect = note;
					}
				}
				assertTrue(rect != null, "a rectangle for string " + string);
				assertEquals(SceneItem.Shape.ROUNDED, rect.shape());
				assertEquals(StringColours.forStrings(6).of(string), rect.colour(), "the colour of its string");
				ScreenPoint at = projection.approach(fixture.neck.fretCentreX(fret), progress, cam);
				assertEquals(at.x(), rect.value(0), 0.5, "at the horizontal position of its fret at this depth");
				int row = fixture.neck.layout().rowOf(string);
				double expectedY = box[1] + (box[3] - box[1]) * (row + 0.5) / 6.0;
				assertEquals(expectedY, rect.value(1), 0.5, "at the vertical position of its string: the lowest string at the top");
				assertTrue(rect.value(1) - rect.value(3) >= box[1] - 1e-9 && rect.value(1) + rect.value(3) <= box[3] + 1e-9, "inside the panel");
			}
		}
	}

	@Test
	public void theFretNumbersAreOnTheActiveChordOnlyAndAreTheFretsAndTheNameIsAboveTheLeftCorner() {
		SceneFixture fixture = chords();
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 700 * MS, SCREEN, null, 100, QualityTier.FULL);
		List<SceneItem> numbers = scene.forEvent(0, Role.PANEL_FRET);
		assertEquals(3, numbers.size(), "a number on each rectangle of the active chord");
		java.util.Set<String> texts = new java.util.TreeSet<String>();
		for (SceneItem number : numbers) {
			texts.add(number.text());
		}
		assertEquals(java.util.Set.of("3", "2", "1"), texts, "the frets, not fingerings");
		assertTrue(scene.forEvent(1, Role.PANEL_FRET).isEmpty(), "none on a chord that is not the active one");
		assertFalse(scene.forEvent(1, Role.CHORD_PANEL).isEmpty(), "but the later chord of the set has its panel");
		double[] box = bounds(scene.forEvent(0, Role.CHORD_PANEL).get(0));
		List<SceneItem> names = scene.forEvent(0, Role.PANEL_NAME);
		assertEquals(1, names.size(), "the name of the chord");
		SceneItem name = names.get(0);
		assertEquals(SceneItem.Align.LEFT, name.align(), "left aligned on the left corner");
		assertEquals(box[0], name.value(0), 0.5);
		assertTrue(name.value(1) < box[1], "above the panel");
		assertFalse(name.text().trim().isEmpty() || name.text().equals("Chord"), "never a bare generic word");
	}

	@Test
	public void noBarCartoucheOrDiagramRemainsAndAChordThatIsJudgedLeavesNoPanel() {
		SceneFixture fixture = chords();
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 700 * MS, SCREEN, null, 100, QualityTier.FULL);
		for (SceneItem item : scene.items()) {
			assertFalse(item.role().name().equals("CHORD_BAR") || item.role().name().equals("CHORD_CHIP") || item.role().name().equals("STEM") || item.role().name().startsWith("CARTOUCHE_"), "former form: " + item.role());
			assertFalse(item.role() == Role.TIMING_BAR && item.eventIndex() <= 1, "no timing bar for a chord");
		}
		ArcadeRun.View run = ArcadeSceneTest.completedBefore(1, fixture.events.size());
		Scene judged = fixture.scene(false).build(fixture.onset(1) - 600 * MS, SCREEN, run, 100, QualityTier.FULL);
		assertTrue(judged.forEvent(0, Role.CHORD_PANEL).isEmpty() && judged.forEvent(0, Role.PANEL_NOTE).isEmpty(), "a judged chord is gone, its badge stays");
	}

	@Test
	public void theTargetsOfTheActiveChordOnTheNeckAreTheFrettedPositionsAndTheStringsOfAllItsNotesAreLit() {
		SceneFixture fixture = chords();
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 300 * MS, SCREEN, null, 100, QualityTier.FULL);
		assertEquals(3, scene.withRole(Role.TARGET_RECT).size(), "one rectangle on the neck per fretted position");
		int lit = 0;
		for (SceneItem string : scene.withRole(Role.STRING)) {
			if (string.alpha() > 0.5) {
				lit++;
			}
		}
		assertEquals(4, lit, "all four written strings, the open one too");
	}
}
