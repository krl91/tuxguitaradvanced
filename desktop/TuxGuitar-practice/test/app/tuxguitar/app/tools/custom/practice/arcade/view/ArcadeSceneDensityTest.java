package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxes.Box;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.song.models.TGChord;

/**
 * The visual-density budget (spec 003 FR-060, FR-062, SC-017, SC-019): at 1280x720 at most four events are fully detailed, the active one always, the
 * labels of the admitted events never overlap or leave the screen, every other event is absent, exactly one event owns the targets of the neck, and
 * nothing depends on the quality tier or on colour.
 */
public class ArcadeSceneDensityTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	/**
	 * {@code future} events, one on every quarter note at the tempo that puts them all inside the look-ahead: chords (every fourth one, named in turn), a note
	 * repeated on the same string and fret, and notes close to each other on the neck, so that every kind of collision happens.
	 */
	static SceneFixture crowded(int future) {
		int measures = future / 4 + 4;
		SongFixture song = new SongFixture(measures);
		song.tempoChange(0, (int) Math.round(future / 2.8 * 60.0));
		for (int i = 0; i < future + 4; i++) {
			int m = i / 4;
			int q = i % 4;
			switch (i % 4) {
				case 0:
					song.chord(m, q, new int[] { 6, 5, 4, 3 }, new int[] { 3 + i % 3, 5, 5, 4 });
					if (i % 8 == 0) {
						TGChord named = song.factory().newChord(6);
						named.setName(i % 16 == 0 ? "G7" : "Cmaj7");
						song.track().getMeasure(m).getBeat(0).setChord(named);
					}
					break;
				case 1:
				case 2:
					song.quarter(m, q, 3, 5);
					break;
				default:
					song.quarter(m, q, 2 + (i / 4) % 3, 7 + i % 4);
					break;
			}
		}
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static final int[] FUTURE = { 8, 16, 32 };

	private static List<Scene> allVariants(SceneFixture fixture, long now, ArcadeRun.View run) {
		ArcadeScene colour = fixture.scene(false);
		ArcadeScene grey = fixture.scene(true);
		List<Scene> scenes = new ArrayList<Scene>();
		for (QualityTier tier : QualityTier.values()) {
			scenes.add(colour.build(now, SCREEN, run, 100, tier));
			scenes.add(grey.build(now, SCREEN, run, 100, tier));
		}
		return scenes;
	}

	@Test
	public void atMostFourEventsAreDetailedWhateverTheNumberOfFutureEventsTheTierAndTheColour() {
		for (int future : FUTURE) {
			SceneFixture fixture = crowded(future);
			for (long offset = -150; offset <= 300; offset += 75) {
				long now = fixture.onset(0) + offset * MS;
				for (Scene scene : allVariants(fixture, now, null)) {
					Set<Integer> detailed = SceneBoxes.detailed(scene);
					assertTrue(detailed.size() <= ArcadeScene.MAX_ADMITTED, future + " future events at " + offset + " ms: detailed " + detailed);
					assertFalse(detailed.isEmpty(), "something is detailed");
				}
			}
		}
	}

	@Test
	public void theActiveEventIsAlwaysFullyDetailedAndReadable() {
		for (int future : FUTURE) {
			SceneFixture fixture = crowded(future);
			for (long offset = -150; offset <= 300; offset += 50) {
				long now = fixture.onset(0) + offset * MS;
				for (int completed = 0; completed < 3; completed++) {
					ArcadeRun.View run = ArcadeSceneTest.completedBefore(completed, fixture.events.size());
					Scene scene = fixture.scene(false).build(now, SCREEN, run, 100);
					if (fixture.onset(completed) - now > 2_900 * MS || now - fixture.onset(completed) > 700 * MS) {
						continue;
					}
					ArcadeRenderEvent active = fixture.events.get(completed);
					if (active.kind() == ArcadeRenderEvent.Kind.NOTE) {
						assertFalse(scene.forEvent(completed, Role.MARKER_FRET).isEmpty(), "the fret number of the active note " + completed + " at " + offset);
						assertFalse(scene.forEvent(completed, Role.TARGET_DISK).isEmpty(), "and its ring on the neck");
					} else {
						assertFalse(scene.forEvent(completed, Role.CARTOUCHE_FRAME).isEmpty(), "the diagram of the active chord " + completed + " at " + offset);
						assertFalse(scene.forEvent(completed, Role.CARTOUCHE_NAME).isEmpty(), "and its header");
						assertFalse(scene.forEvent(completed, Role.TARGET_DISK).isEmpty(), "and its ghost on the neck");
					}
				}
			}
		}
	}

	@Test
	public void theLabelsOfTheEventsAreDisjointAndInsideTheScreenAndTheirNamesInsideTheirHeaders() {
		for (int future : FUTURE) {
			SceneFixture fixture = crowded(future);
			for (long offset = -150; offset <= 400; offset += 50) {
				long now = fixture.onset(0) + offset * MS;
				ArcadeRun.View run = ArcadeSceneTest.completedBefore(1, fixture.events.size());
				for (Scene scene : allVariants(fixture, now, run)) {
					List<Box> boxes = SceneBoxes.labels(scene);
					assertNull(SceneBoxes.firstOverlap(boxes), future + " future events at " + offset + " ms");
					for (Box box : boxes) {
						assertTrue(box.x1 >= 0 && box.y1 >= 0 && box.x2 <= SCREEN.width() && box.y2 <= SCREEN.height(), "clipped: " + box);
					}
					for (SceneItem name : scene.withRole(Role.CARTOUCHE_NAME)) {
						Box header = SceneBoxes.ofQuad(scene.forEvent(name.eventIndex(), Role.CARTOUCHE_HEADER).get(0));
						assertTrue(header.contains(SceneBoxes.ofText(name)), "the name \"" + name.text() + "\" does not fit its header " + header + " vs " + SceneBoxes.ofText(name));
					}
				}
			}
		}
	}

	@Test
	public void everyEventOutsideTheAdmittedSetIsAbsentFromThePicture() {
		for (int future : FUTURE) {
			SceneFixture fixture = crowded(future);
			for (long offset = -150; offset <= 300; offset += 150) {
				Scene scene = fixture.scene(false).build(fixture.onset(0) + offset * MS, SCREEN, null, 100, QualityTier.FULL);
				Set<Integer> painted = new TreeSet<Integer>();
				for (SceneItem item : scene.items()) {
					if (item.eventIndex() >= 0) {
						painted.add(item.eventIndex());
					}
				}
				assertTrue(painted.size() <= ArcadeScene.MAX_ADMITTED, future + " future events: " + painted.size() + " events painted " + painted);
				for (int index = 0; index < fixture.events.size(); index++) {
					if (!painted.contains(index)) {
						assertTrue(scene.forEvent(index).isEmpty(), "event " + index + " is outside the admitted set and left a primitive");
					}
				}
			}
		}
	}

	@Test
	public void exactlyOneEventOwnsTheTargetsOfTheNeckAndAdvancingReplacesThemAtOnce() {
		for (int future : FUTURE) {
			SceneFixture fixture = crowded(future);
			long now = fixture.onset(0) - 150 * MS;
			for (Scene scene : allVariants(fixture, now, null)) {
				Set<Integer> owners = new TreeSet<Integer>();
				for (SceneItem item : scene.items()) {
					if (item.role() == Role.TARGET_DISK) {
						owners.add(item.eventIndex());
					}
				}
				assertEquals(1, owners.size(), future + " future events: owners of the neck targets " + owners);
				assertEquals(0, (int) owners.iterator().next(), "the earliest actionable event");
			}
			// event 0 is judged: event 1 owns the targets in the very same scene, and event 0 owns none
			Scene next = fixture.scene(false).build(now, SCREEN, ArcadeSceneTest.completedBefore(1, fixture.events.size()), 100);
			Set<Integer> owners = new TreeSet<Integer>();
			for (SceneItem item : next.items()) {
				if (item.role() == Role.TARGET_DISK) {
					owners.add(item.eventIndex());
				}
			}
			assertEquals(Set.of(1), owners);
		}
	}

	@Test
	public void aChordOwnsAllAndOnlyItsPositionsOnTheNeck() {
		SceneFixture fixture = crowded(16);
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 150 * MS, SCREEN, null, 100);
		assertEquals(fixture.events.get(0).kind(), ArcadeRenderEvent.Kind.CHORD);
		assertEquals(fixture.events.get(0).positions().size(), scene.forEvent(0, Role.TARGET_DISK).size());
		for (SceneItem item : scene.items()) {
			if (item.role() == Role.TARGET_DISK) {
				assertEquals(0, item.eventIndex());
			}
		}
	}

	@Test
	public void theDetailedSetIsTheSameInEveryTierAndInMonochromeAndDependsOnTheClockAlone() {
		for (int future : FUTURE) {
			SceneFixture fixture = crowded(future);
			for (long offset = -150; offset <= 300; offset += 90) {
				long now = fixture.onset(0) + offset * MS;
				List<Scene> scenes = allVariants(fixture, now, null);
				Set<Integer> first = SceneBoxes.detailed(scenes.get(0));
				for (Scene scene : scenes) {
					assertEquals(first, SceneBoxes.detailed(scene), future + " future events at " + offset + " ms");
				}
			}
			// asked in another order, or again, the same picture
			long a = fixture.onset(0) - 100 * MS;
			long b = fixture.onset(0) + 200 * MS;
			ArcadeScene scene = fixture.scene(false);
			String first = SceneCapture.of(scene.build(a, SCREEN, null, 100));
			scene.build(b, SCREEN, null, 100);
			assertEquals(first, SceneCapture.of(scene.build(a, SCREEN, null, 100)), "no state is kept between frames");
		}
	}

	/** The identities that are in the picture of the events: every event that has a primitive other than a verdict badge. */
	private static Set<Integer> admitted(Scene scene) {
		Set<Integer> found = new TreeSet<Integer>();
		for (SceneItem item : scene.items()) {
			if (item.eventIndex() >= 0 && item.role() != Role.BADGE && item.role() != Role.BADGE_TEXT && item.role() != Role.BADGE_SYMBOL) {
				found.add(item.eventIndex());
			}
		}
		return found;
	}

	@Test
	public void aHiddenEventIsPromotedWhenAnEarlierOneIsCompletedAndThenHasItsCompleteLane() {
		SceneFixture fixture = crowded(16);
		ArcadeScene scene = fixture.scene(false);
		long now = fixture.onset(0) - 150 * MS;
		Scene first = scene.build(now, SCREEN, null, 100);
		Set<Integer> before = admitted(first);
		assertTrue(before.contains(0) && before.size() <= ArcadeScene.MAX_ADMITTED);
		// events 0 to 2 are completed: the set is the active event 3 and the three that follow it, in order, and the completed ones are gone but for their verdict
		long later = fixture.onset(0) + 900 * MS;
		Scene second = scene.build(later, SCREEN, ArcadeSceneTest.completedBefore(3, fixture.events.size()), 100);
		Set<Integer> after = admitted(second);
		assertEquals(3, (int) after.iterator().next(), "the active event comes first");
		for (int k = 0; k < after.size(); k++) {
			assertTrue(after.contains(3 + k), "the set is a prefix of the events in order of time: " + after);
		}
		assertTrue(after.size() <= ArcadeScene.MAX_ADMITTED);
		for (int index : after) {
			if (!before.contains(index) && fixture.events.get(index).kind() == app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent.Kind.NOTE) {
				// promoted: it was not painted at all before, and now it has its complete lane from the horizon to the top edge of its cell
				assertTrue(first.forEvent(index).isEmpty(), "note " + index + " was absent before it was promoted");
				assertEquals(1, second.forEvent(index, Role.TRAJECTORY).size(), "event " + index + " has its lane");
				assertEquals(1, second.forEvent(index, Role.MARKER).size());
			}
		}
	}

	@Test
	public void theBudgetChangesNothingOfTheEventsOnlyWhatIsPainted() {
		SceneFixture fixture = crowded(16);
		long now = fixture.onset(0) - 150 * MS;
		Scene reduced = fixture.scene(false).build(now, SCREEN, null, 100);
		Scene unreduced = fixture.scene(false).withDensityBudget(false).build(now, SCREEN, null, 100);
		Set<Integer> shown = new TreeSet<Integer>();
		Set<Integer> shownUnreduced = new TreeSet<Integer>();
		for (SceneItem item : reduced.items()) {
			if (item.eventIndex() >= 0) {
				shown.add(item.eventIndex());
			}
		}
		for (SceneItem item : unreduced.items()) {
			if (item.eventIndex() >= 0) {
				shownUnreduced.add(item.eventIndex());
			}
		}
		// what is painted is a part of the events, and only the nearest ones; the events themselves are the same objects of the same timeline
		assertTrue(shownUnreduced.containsAll(shown), "nothing is painted with the budget that the unreduced picture does not paint");
		assertTrue(shown.size() < shownUnreduced.size(), "some events are not painted");
		assertTrue(SceneBoxes.detailed(unreduced).size() > ArcadeScene.MAX_ADMITTED, "and the unreduced picture is the cluttered one");
		assertEquals(fixture.events.size(), crowded(16).events.size(), "no event was dropped from the timeline");
	}

	@Test
	public void aNarrowWindowKeepsTheActiveEventAndTheLabelsDisjoint() {
		for (Viewport narrow : new Viewport[] { new Viewport(360, 640), new Viewport(640, 360), new Viewport(2560, 1440) }) {
			SceneFixture fixture = crowded(32);
			for (long offset = -150; offset <= 300; offset += 75) {
				Scene scene = fixture.scene(false).build(fixture.onset(0) + offset * MS, narrow, null, 100);
				Set<Integer> detailed = SceneBoxes.detailed(scene);
				assertTrue(detailed.contains(0), "the active event stays at " + narrow.width() + "x" + narrow.height());
				assertTrue(detailed.size() <= ArcadeScene.MAX_ADMITTED);
				List<Box> boxes = SceneBoxes.labels(scene);
				assertNull(SceneBoxes.firstOverlap(boxes), narrow.width() + "x" + narrow.height() + " at " + offset);
			}
		}
	}

	@Test
	public void theAdmittedSetIsAPrefixOfTheEventsDueAndEveryLaterEventIsAbsent() {
		SongFixture song = new SongFixture(4);
		for (int i = 0; i < 12; i++) {
			song.quarter(i / 4, i % 4, 1 + i % 6, i % 2 == 0 ? 2 : 17);
		}
		song.tempoChange(0, 60);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		// the events are a second apart and all inside the look-ahead: the set is the first ones, at most four, and nothing else is painted
		long now = fixture.onset(0) - 1000 * MS;
		Scene scene = fixture.scene(false).build(now, SCREEN, null, 100);
		Set<Integer> set = admitted(scene);
		assertTrue(set.size() >= 3 && set.size() <= ArcadeScene.MAX_ADMITTED, "the nearest events are admitted, those due within five seconds, never more than the cap: " + set);
		for (int k = 0; k < set.size(); k++) {
			assertTrue(set.contains(k), "a prefix of the events in order of time, without a gap: " + set);
			assertEquals(1, scene.forEvent(k, Role.TRAJECTORY).size(), "event " + k + " has its lane");
		}
		for (int index = set.size(); index < 12; index++) {
			assertTrue(scene.forEvent(index).isEmpty(), "event " + index + " is outside the set: no marker, no halo, no lane, nothing");
		}
	}
}
