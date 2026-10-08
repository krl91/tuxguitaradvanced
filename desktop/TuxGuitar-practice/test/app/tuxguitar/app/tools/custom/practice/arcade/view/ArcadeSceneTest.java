package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The scene of the notes (spec 003 T037, SC-012): the colour chain from the spawn to the impact, the exact hit line at the onset, the render states,
 * and no dependence on anything but the clock reading.
 */
public class ArcadeSceneTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	/** Six quarter notes, one per string, from the lowest to the highest, at frets 3, 5, 7, 5, 3, 0. */
	private static SceneFixture oneNotePerString(int percent) {
		SongFixture song = new SongFixture(2);
		int[] frets = { 3, 5, 7, 5, 3, 0 };
		for (int i = 0; i < 6; i++) {
			song.quarter(i / 4, i % 4, 6 - i, frets[i]);
		}
		return new SceneFixture(song, SceneFixture.sixStrings(), percent);
	}

	static ArcadeRun.View viewOf(ArcadeVerdict... verdicts) {
		return viewOf(new int[verdicts.length][0], verdicts);
	}

	static ArcadeRun.View viewOf(int[][] missing, ArcadeVerdict... verdicts) {
		List<ArcadeRun.EventState> states = new ArrayList<ArcadeRun.EventState>();
		for (int i = 0; i < verdicts.length; i++) {
			states.add(new ArcadeRun.EventState(i, verdicts[i], 0L, 0, 0, java.util.Collections.emptyList(), missing[i]));
		}
		return new ArcadeRun.View(states, 4321, 7, 7, 0, verdicts.length);
	}

	/** A run in which every event before the given one is completed and the others wait: the given one is the active event of the neck. */
	static ArcadeRun.View completedBefore(int index, int total) {
		ArcadeVerdict[] verdicts = new ArcadeVerdict[total];
		for (int i = 0; i < total; i++) {
			verdicts[i] = i < index ? ArcadeVerdict.ON_TIME : ArcadeVerdict.WAITING;
		}
		return viewOf(verdicts);
	}

	@Test
	public void markerTrajectoryTargetRingAndStringLabelShareTheDestinationStringColourFromSpawnToImpact() {
		SceneFixture fixture = oneNotePerString(100);
		ArcadeScene scene = fixture.scene(false);
		for (int index = 0; index < 6; index++) {
			int tgString = 6 - index;
			int expected = fixture.colours.of(tgString);
			for (long offset = -2900; offset <= 0; offset += 100) {
				Scene picture = scene.build(fixture.onset(index) + offset * MS, SCREEN, completedBefore(index, 6), 100);
				for (Role role : new Role[] { Role.MARKER, Role.TRAJECTORY, Role.TARGET_DISK }) {
					List<SceneItem> items = picture.forEvent(index, role);
					assertEquals(1, items.size(), "event " + index + " " + role + " at " + offset + " ms");
					assertEquals(expected, items.get(0).colour(), "event " + index + " " + role + " at " + offset + " ms");
					assertEquals(tgString, items.get(0).tgString());
				}
				SceneItem label = null;
				for (SceneItem item : picture.withRole(Role.STRING_LABEL)) {
					if (item.tgString() == tgString) {
						label = item;
					}
				}
				assertEquals(expected, label.colour(), "the label of string " + tgString);
			}
		}
		assertEquals(6, new java.util.HashSet<Integer>(java.util.Arrays.asList(colourList(fixture))).size(), "the six strings keep six distinct colours");
	}

	private static Integer[] colourList(SceneFixture fixture) {
		Integer[] colours = new Integer[6];
		for (int s = 1; s <= 6; s++) {
			colours[s - 1] = fixture.colours.of(s);
		}
		return colours;
	}

	@Test
	public void theMarkerIsExactlyOnTheHitLineAtTheOnsetAndAboveItBefore() {
		for (int percent : new int[] { 50, 100, 200 }) {
			SceneFixture fixture = oneNotePerString(percent);
			ArcadeScene scene = fixture.scene(false);
			IsometricProjection projection = new IsometricProjection(SCREEN);
			for (int index = 0; index < 6; index++) {
				SceneItem at = scene.build(fixture.onset(index), SCREEN, completedBefore(index, 6), percent).forEvent(index, Role.MARKER).get(0);
				assertEquals(projection.hitLineY(), at.value(1), 0.0, "event " + index + " at " + percent + " %");
				SceneItem ring = scene.build(fixture.onset(index), SCREEN, completedBefore(index, 6), percent).forEvent(index, Role.TARGET_DISK).get(0);
				assertEquals(ring.value(0), at.value(0), 1e-9, "the marker arrives on the column of its target");
				double previous = Double.NEGATIVE_INFINITY;
				for (long offset = -2900; offset <= 0; offset += 100) {
					SceneItem marker = scene.build(fixture.onset(index) + offset * MS, SCREEN, completedBefore(index, 6), percent).forEvent(index, Role.MARKER).get(0);
					assertTrue(marker.value(1) >= previous, "the marker only moves towards the hit line");
					assertTrue(marker.value(1) <= projection.hitLineY());
					previous = marker.value(1);
				}
			}
		}
	}

	@Test
	public void theRenderStatesFollowTheTimeLeftAndEveryStateHasItsBoundary() {
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		long hold = CameraTrack.Settings.standard().holdAfterNanos;
		long onset = 10_000 * MS;
		assertEquals(RenderState.HIDDEN, RenderState.of(onset, onset - look - 1, look, hold));
		assertEquals(RenderState.DISTANT, RenderState.of(onset, onset - look, look, hold));
		assertEquals(RenderState.DISTANT, RenderState.of(onset, onset - (long) (look * 0.70), look, hold));
		assertEquals(RenderState.READABLE, RenderState.of(onset, onset - (long) (look * 0.60), look, hold));
		assertEquals(RenderState.ARMED, RenderState.of(onset, onset - (long) (look * 0.20), look, hold));
		assertEquals(RenderState.ARMED, RenderState.of(onset, onset - 1, look, hold));
		assertEquals(RenderState.IMPACT, RenderState.of(onset, onset, look, hold));
		assertEquals(RenderState.IMPACT, RenderState.of(onset, onset + RenderState.IMPACT_NANOS, look, hold));
		assertEquals(RenderState.FEEDBACK, RenderState.of(onset, onset + RenderState.IMPACT_NANOS + 1, look, hold));
		assertEquals(RenderState.FEEDBACK, RenderState.of(onset, onset + hold, look, hold));
		assertEquals(RenderState.EXPIRED, RenderState.of(onset, onset + hold + 1, look, hold));
		assertEquals(0.0, RenderState.progress(onset, onset - look, look), 0.0);
		assertEquals(1.0, RenderState.progress(onset, onset, look), 0.0);
		assertEquals(1.0, RenderState.progress(onset, onset + 5 * MS, look), 0.0, "after the onset it stays on the line");
	}

	@Test
	public void anEventOutsideTheLookAheadOrExpiredIsNotDrawnAtAll() {
		SceneFixture fixture = oneNotePerString(100);
		ArcadeScene scene = fixture.scene(false);
		CameraTrack.Settings settings = CameraTrack.Settings.standard();
		Scene before = scene.build(fixture.onset(0) - settings.lookAheadNanos - 1, SCREEN, null, 100);
		assertTrue(before.forEvent(0).isEmpty(), "not yet");
		Scene after = scene.build(fixture.onset(0) + settings.holdAfterNanos + 1, SCREEN, null, 100);
		assertTrue(after.forEvent(0).isEmpty(), "gone");
		assertFalse(scene.build(fixture.onset(0), SCREEN, null, 100).forEvent(0).isEmpty());
	}

	@Test
	public void theSceneIsAFunctionOfTheClockReadingAloneWhateverTheOrderOfTheQuestions() {
		SceneFixture fixture = oneNotePerString(100);
		ArcadeScene scene = fixture.scene(false);
		long[] times = { 100 * MS, 1500 * MS, 800 * MS, 2200 * MS, 1500 * MS };
		List<String> first = new ArrayList<String>();
		for (long time : times) {
			first.add(scene.build(time, SCREEN, null, 100).items().toString());
		}
		ArcadeScene other = fixture.scene(false);
		for (int i = times.length - 1; i >= 0; i--) {
			assertEquals(first.get(i), other.build(times[i], SCREEN, null, 100).items().toString(), "time " + times[i]);
		}
		assertEquals(first.get(1), first.get(4), "the same instant, the same picture");
	}

	@Test
	public void noNumericScoreIsEverPartOfTheScene() {
		SceneFixture fixture = oneNotePerString(100);
		ArcadeScene scene = fixture.scene(false);
		ArcadeRun.View run = viewOf(ArcadeVerdict.ON_TIME, ArcadeVerdict.EARLY, ArcadeVerdict.WAITING, ArcadeVerdict.WAITING, ArcadeVerdict.WAITING,
				ArcadeVerdict.WAITING);
		for (long t = 0; t < 4000 * MS; t += 50 * MS) {
			for (SceneItem item : scene.build(t, SCREEN, run, 100).items()) {
				if (item.text() != null) {
					assertFalse(item.text().contains("4321"), "the score must not be drawn: " + item);
					assertFalse(item.text().toLowerCase().contains("score"), item.text());
				}
			}
		}
		Scene picture = scene.build(0, SCREEN, run, 100);
		assertEquals("Streak 7", picture.withRole(Role.HUD_STREAK).get(0).text());
		assertEquals("100 %", picture.withRole(Role.HUD_SPEED).get(0).text());
	}

	@Test
	public void theNeckShowsEveryStringOnceInPhysicalOrderAndFourStringsWorkToo() {
		SceneFixture six = oneNotePerString(100);
		Scene picture = six.scene(false).build(0, SCREEN, null, 100);
		assertEquals(6, picture.withRole(Role.STRING).size());
		List<SceneItem> labels = picture.withRole(Role.STRING_LABEL);
		assertEquals(List.of("E2", "A2", "D3", "G3", "B3", "E4"), texts(sortedByYDescending(labels)), "the low string at the bottom");

		SongFixture song = new SongFixture(1);
		song.quarter(0, 0, 4, 0);
		song.quarter(0, 1, 1, 3);
		SceneFixture bass = new SceneFixture(song, TrackTuningFactory.any(43, 38, 33, 28), 100);
		Scene fourStrings = bass.scene(false).build(0, SCREEN, null, 100);
		assertEquals(4, fourStrings.withRole(Role.STRING).size());
		assertEquals(List.of("E1", "A1", "D2", "G2"), texts(sortedByYDescending(fourStrings.withRole(Role.STRING_LABEL))));
		assertEquals(4, new java.util.HashSet<Integer>(java.util.Arrays.asList(bass.colours.of(1), bass.colours.of(2), bass.colours.of(3), bass.colours.of(4))).size());
		assertEquals("0", fourStrings.forEvent(0, Role.MARKER_FRET).get(0).text(), "an open string says 0");
	}

	private static List<SceneItem> sortedByYDescending(List<SceneItem> items) {
		List<SceneItem> sorted = new ArrayList<SceneItem>(items);
		sorted.sort((a, b) -> Double.compare(b.value(1), a.value(1)));
		return sorted;
	}

	private static List<String> texts(List<SceneItem> items) {
		List<String> texts = new ArrayList<String>();
		for (SceneItem item : items) {
			texts.add(item.text());
		}
		return texts;
	}

	@Test
	public void theFretIsReadableOnTheMarkerAndOnTheNeck() {
		SceneFixture fixture = oneNotePerString(100);
		Scene picture = fixture.scene(false).build(fixture.onset(1) - 500 * MS, SCREEN, null, 100);
		assertEquals("5", picture.forEvent(1, Role.MARKER_FRET).get(0).text());
		assertFalse(picture.withRole(Role.FRET_NUMBER).isEmpty(), "the fret numbers are under the neck");
		assertNotEquals(0, picture.withRole(Role.FRET_WIRE).size());
	}

	@Test
	public void theNumbersOfTheNeckAreWholeAndApartWhateverPartOfTheNeckIsInView() {
		for (int[] frets : new int[][] { { 0, 1, 2 }, { 2, 3, 5 }, { 14, 17, 19 }, { 20, 22, 24 }, { 0, 12, 24 } }) {
			SongFixture song = new SongFixture(2);
			for (int i = 0; i < frets.length; i++) {
				song.quarter(1, i, 1 + i, frets[i]);
			}
			SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
			for (Viewport viewport : new Viewport[] { SCREEN, new Viewport(480, 320), new Viewport(2560, 1440) }) {
				for (long offset = -2500; offset <= 400; offset += 100) {
					Scene scene = fixture.scene(false).build(fixture.onset(0) + offset * MS, viewport, null, 100);
					List<SceneBoxes.Box> boxes = new ArrayList<SceneBoxes.Box>();
					for (SceneItem number : scene.withRole(Role.FRET_NUMBER)) {
						SceneBoxes.Box box = SceneBoxes.ofText(number);
						assertTrue(box.x1 >= 0 && box.x2 <= viewport.width(), "a fret number cut by the window: " + box);
						for (SceneBoxes.Box other : boxes) {
							assertFalse(box.overlaps(other), "two fret numbers touch: " + box + " and " + other);
						}
						boxes.add(box);
					}
				}
			}
		}
	}
}
