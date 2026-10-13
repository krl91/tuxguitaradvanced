package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * A series of chords (spec 003 FR-062, FR-074, SC-020, SC-028): every chord of the visual set has its panel, whatever the number of chords, the tier and the
 * colour; each lands on the hit line at its onset; only the active chord has rectangles on the neck; the names never overlap each other nor a label.
 */
public class ArcadeSceneChordLaneTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	/** {@code count} chords, 250 ms apart: all inside the look-ahead of five seconds. */
	private static SceneFixture chords(int count) {
		SongFixture song = new SongFixture(2 + count / 4);
		song.tempoChange(0, 240);
		for (int i = 0; i < count; i++) {
			song.chord(1 + i / 4, i % 4, new int[] { 6, 5, 4 }, new int[] { 3 + i % 5, 5 + i % 5, 5 + i % 5 });
		}
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static List<Scene> variants(SceneFixture fixture, long now, ArcadeRun.View run) {
		List<Scene> scenes = new ArrayList<Scene>();
		for (boolean mono : new boolean[] { false, true }) {
			for (QualityTier tier : QualityTier.values()) {
				scenes.add(fixture.scene(mono).build(now, SCREEN, run, 100, tier));
			}
		}
		return scenes;
	}

	@Test
	public void everyChordOfTheSetHasExactlyOnePanelWhateverTheNumberOfChordsTheTierAndTheColour() {
		for (int count : new int[] { 2, 4, 8 }) {
			SceneFixture fixture = chords(count);
			long look = CameraTrack.Settings.standard().lookAheadNanos;
			for (long before = 2900; before >= 0; before -= 300) {
				long now = fixture.onset(0) - before * MS;
				for (Scene scene : variants(fixture, now, null)) {
					for (int index = 0; index < count; index++) {
						boolean due = fixture.onset(index) - now <= look;
						assertEquals(due ? 1 : 0, scene.forEvent(index, Role.CHORD_PANEL).size(), count + " chords at -" + before + " ms: the panel of chord " + index);
					}
				}
			}
		}
	}

	@Test
	public void theImpactOfEveryChordIsOnTheHitLineAtItsTimelineOnset() {
		SceneFixture fixture = chords(4);
		IsometricProjection projection = new IsometricProjection(SCREEN);
		for (int index = 0; index < 4; index++) {
			ArcadeRun.View run = ArcadeSceneTest.completedBefore(index, 4);
			for (QualityTier tier : QualityTier.values()) {
				Scene scene = fixture.scene(false).build(fixture.onset(index), SCREEN, run, 100, tier);
				SceneItem panel = scene.forEvent(index, Role.CHORD_PANEL).get(0);
				double bottom = Math.max(Math.max(panel.value(1), panel.value(3)), Math.max(panel.value(5), panel.value(7)));
				assertEquals(Landing.footY(SCREEN), bottom, 0.5, "chord " + index + " " + tier);
			}
		}
	}

	@Test
	public void onlyTheActiveChordHasRectanglesOnTheNeckAndFretNumbersOnItsPanel() {
		SceneFixture fixture = chords(4);
		long now = fixture.onset(0) - 300 * MS;
		for (int active = 0; active < 3; active++) {
			ArcadeRun.View run = ArcadeSceneTest.completedBefore(active, 4);
			Scene scene = fixture.scene(false).build(now + active * 250 * MS, SCREEN, run, 100, QualityTier.FULL);
			Set<Integer> owners = new TreeSet<Integer>();
			for (SceneItem rect : scene.withRole(Role.TARGET_RECT)) {
				owners.add(rect.eventIndex());
			}
			assertEquals(Set.of(active), owners, "the neck belongs to the active chord");
			Set<Integer> numbered = new TreeSet<Integer>();
			for (SceneItem number : scene.withRole(Role.PANEL_FRET)) {
				numbered.add(number.eventIndex());
			}
			assertEquals(Set.of(active), numbered, "and so do the fret numbers");
		}
	}

	@Test
	public void theNamesAndTheLabelsNeverOverlapWhateverTheNumberOfChords() {
		for (int count : new int[] { 4, 8, 16 }) {
			SceneFixture fixture = chords(count);
			for (long before = 2900; before >= 0; before -= 200) {
				for (Scene scene : variants(fixture, fixture.onset(0) - before * MS, null)) {
					assertNull(SceneBoxes.firstOverlap(SceneBoxes.labels(scene)), count + " chords at -" + before + " ms");
					assertTrue(scene.forEvent(0, Role.PANEL_NAME).size() == 1, "the active chord always has its name");
				}
			}
		}
	}
}
