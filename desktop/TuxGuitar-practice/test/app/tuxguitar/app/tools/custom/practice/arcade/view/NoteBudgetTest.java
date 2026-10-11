package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The visibility budget of the notes (spec 003 FR-062, SC-019, T040): at most four approaching playable events are painted, the active one then the following ones
 * in order of time; a single note that is not admitted leaves no primitive at all (no dot, halo, marker, lane, label), there is no cloud, snake or constellation of
 * small dots, and the old preview budget ({@code MAX_COMPACT = 28}) or any equivalent of it fails here; every admitted note is exactly on its fret lane at every
 * progress, at the top edge of its cell at the onset, and smaller the farther it is; one identity alone owns the targets of the neck.
 */
public class NoteBudgetTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport[] SIZES = { new Viewport(640, 360), new Viewport(1280, 720), new Viewport(2560, 1440) };

	/** {@code count} single notes, 125 ms apart (all inside the look-ahead), the frets wandering over the neck so that the camera moves. */
	private static SceneFixture crowd(int count) {
		SongFixture song = new SongFixture(2 + count / 4);
		song.tempoChange(0, 480);
		for (int i = 0; i < count; i++) {
			int fret = (int) Math.round(10.0 + 9.0 * Math.sin(i * 0.7) + 3.0 * Math.sin(i * 2.3));
			song.quarter(1 + i / 4, i % 4, 1 + (i * 5) % 6, Math.max(0, Math.min(22, fret)));
		}
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	/** A crowd of notes and chords together: a chord on every third event. */
	private static SceneFixture mixedCrowd(int count) {
		SongFixture song = new SongFixture(2 + count / 4);
		song.tempoChange(0, 480);
		for (int i = 0; i < count; i++) {
			if (i % 3 == 2) {
				song.chord(1 + i / 4, i % 4, new int[] { 6, 5, 4 }, new int[] { 3 + i % 5, 5 + i % 5, 5 + i % 5 });
			} else {
				song.quarter(1 + i / 4, i % 4, 1 + (i * 5) % 6, (i * 3) % 15);
			}
		}
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	@Test
	public void theOldPreviewBudgetIsGoneAndNoRoleOfAFreeFloatingDotIsLeftInTheScenes() {
		for (java.lang.reflect.Field field : ArcadeScene.class.getDeclaredFields()) {
			assertFalse(field.getName().equals("MAX_COMPACT"), "the budget of compact previews of notes came back: " + field.getName());
			assertFalse(field.getName().contains("COMPACT") && field.getType() == int.class, "an equivalent of it came back: " + field.getName());
		}
		for (int count : new int[] { 8, 16, 32 }) {
			SceneFixture fixture = crowd(count);
			for (Viewport viewport : SIZES) {
				for (long before = 3_000; before >= -300; before -= 75) {
					Scene scene = fixture.scene(false).build(fixture.onset(0) - before * MS, viewport, null, 100);
					for (Role role : Role.values()) {
						assertFalse(role.name().contains("PREVIEW") || role.name().contains("COMPACT"), "a role of a preview form of an event came back: " + role);
					}
				}
			}
		}
	}

	@Test
	public void everyEventDueWithinTheLookAheadIsPaintedWholeAndNoOtherEventLeavesAPrimitive() {
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		long hold = CameraTrack.Settings.standard().holdAfterNanos;
		for (SceneFixture fixture : new SceneFixture[] { crowd(8), crowd(16), crowd(32), mixedCrowd(8), mixedCrowd(16), mixedCrowd(32) }) {
			int count = fixture.events.size();
			for (Viewport viewport : SIZES) {
				for (QualityTier tier : QualityTier.values()) {
					for (long before = 5_500; before >= -300; before -= 100) {
						long now = fixture.onset(0) - before * MS;
						Scene scene = fixture.scene(false).build(now, viewport, null, 100, tier);
						Set<Integer> painted = new TreeSet<Integer>();
						for (SceneItem item : scene.items()) {
							if (item.eventIndex() >= 0) {
								painted.add(item.eventIndex());
							}
						}
						String where = count + " events " + viewport.width() + " " + tier + " " + before + " ms before";
						for (int index = 0; index < fixture.events.size(); index++) {
							long until = fixture.onset(index) - now;
							boolean due = until <= look && until > -hold;
							assertEquals(due, painted.contains(index), where + ": event " + index + " is " + until / MS + " ms from its onset");
							if (!due && !scene.forEvent(index).isEmpty()) {
								assertTrue(false, where + ": event " + index + " left " + scene.forEvent(index).size() + " primitives, the first a " + scene.forEvent(index).get(0).role());
							}
							if (due && fixture.events.get(index).kind() == app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent.Kind.NOTE) {
								assertEquals(1, scene.forEvent(index, fixture.events.get(index).positions().get(0).fret() == 0 ? Role.OPEN_BAR : Role.MARKER).size(), where + ": the marker of event " + index + " is drawn, whatever its number of neighbours");
							}
						}
						assertNull(SceneBoxes.firstOverlap(SceneBoxes.labels(scene)), where + ": no two labels touch");
					}
				}
			}
		}
	}

	@Test
	public void everyAdmittedNoteIsOnItsFretLaneAtEveryProgressAndAtTheTopOfItsCellAtTheOnset() {
		for (int count : new int[] { 8, 16, 32 }) {
			SceneFixture fixture = crowd(count);
			long look = CameraTrack.Settings.standard().lookAheadNanos;
			for (Viewport viewport : SIZES) {
				IsometricProjection projection = new IsometricProjection(viewport);
				for (QualityTier tier : QualityTier.values()) {
					for (long before = 2_900; before >= 0; before -= 50) {
						long now = fixture.onset(0) - before * MS;
						Scene scene = fixture.scene(false).build(now, viewport, null, 100, tier);
						Camera camera = fixture.camera.at(now);
						PaintedFretGrid grid = PaintedFretGrid.of(fixture.neck, projection, camera);
						FretArrivalLaneMap lanes = FretArrivalLaneMap.fromPaintedFrets(grid, projection);
						for (SceneItem marker : scene.withRole(Role.MARKER_FOOTPRINT)) {
							int index = marker.eventIndex();
							int fret = fixture.events.get(index).positions().get(0).fret();
							double progress = Math.max(0.0, Math.min(1.0, 1.0 - (double) (fixture.onset(index) - now) / look));
							FretArrivalLaneMap.Lane lane = lanes.laneForFret(fret);
							ScreenPoint at = lane.at(progress);
							String where = count + " notes " + viewport.width() + " " + tier + " event " + index + " fret " + fret + " p=" + progress;
							assertEquals(at.x(), marker.value(0), 1e-6, where + ": the centre is on its lane");
							assertEquals(at.y(), marker.value(1), 1e-6, where);
							// the lane ends at the middle of the bottom edge of the cell between the wires, the foot of the neck
							assertEquals((grid.cellLeftX(fret) + grid.cellRightX(fret)) / 2.0, lane.end().x(), 1e-9, where);
							assertEquals(grid.neckBottomY(), lane.end().y(), 1e-9, where);
							if (progress >= 1.0) {
								assertEquals(lane.end().x(), marker.value(0), 1e-6, where + ": at the onset, the middle of its cell");
								assertEquals(viewport.height() * IsometricProjection.NECK_NEAR_EDGE, marker.value(1), 1e-6, where + ": at the foot of the neck");
							}
						}
					}
				}
			}
		}
	}

	@Test
	public void theSizeOfAMarkerGrowsWithItsApproachWithoutLeavingItsLane() {
		SceneFixture fixture = crowd(8);
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		double previous = 0.0;
		for (long before = (long) (look / MS); before >= 0; before -= 50) {
			Scene scene = fixture.scene(false).build(fixture.onset(0) - before * MS, SIZES[1], null, 100);
			if (!scene.forEvent(0, Role.MARKER).isEmpty()) {
				double radius = scene.forEvent(0, Role.MARKER).get(0).value(2);
				assertTrue(radius >= previous - 1e-9, "the marker never shrinks as it approaches: " + radius + " after " + previous);
				previous = radius;
			}
		}
		assertTrue(previous > 0.0);
	}

	@Test
	public void oneIdentityAloneOwnsTheTargetsOfTheNeckAndTheNextReplacesItAtOnce() {
		for (int count : new int[] { 8, 16, 32 }) {
			SceneFixture fixture = crowd(count);
			for (Viewport viewport : SIZES) {
				for (long before = 2_900; before >= -200; before -= 100) {
					long now = fixture.onset(0) - before * MS;
					Scene scene = fixture.scene(false).build(now, viewport, null, 100);
					Set<Integer> owners = new HashSet<Integer>();
					for (SceneItem item : scene.items()) {
						if (item.role() == Role.TARGET_RECT || item.role() == Role.TARGET_OUTLINE || item.role() == Role.TARGET_HALO) {
							owners.add(item.eventIndex());
						}
					}
					assertTrue(owners.size() <= 1, count + " notes: several identities own targets: " + owners);
				}
				// when the active event is completed the next one owns the target, in the same scene
				Scene afterOne = fixture.scene(false).build(fixture.onset(0) + 10 * MS, viewport, ArcadeSceneTest.completedBefore(1, fixture.events.size()), 100);
				Set<Integer> owners = new HashSet<Integer>();
				for (SceneItem item : afterOne.items()) {
					if (item.role() == Role.TARGET_RECT) {
						owners.add(item.eventIndex());
					}
				}
				assertEquals(Set.of(1), owners, "the next event takes the target at once");
			}
		}
	}

	@Test
	public void everyNoteIsSeenComingFromTheHorizonForFiveSecondsWhateverTheNumberOfNotes() {
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		assertEquals(5_000_000_000L, look, "five seconds to see, find and play a note");
		for (SceneFixture fixture : new SceneFixture[] { note1(), crowd(100 / 4 * 4) }) {
			IsometricProjection projection = new IsometricProjection(new Viewport(1280, 720));
			// a note that has just come inside the look-ahead is at the horizon, small; it then goes down its lane for the five seconds
			long now = fixture.onset(0) - look + 20 * MS;
			Scene entering = fixture.scene(false).build(now, new Viewport(1280, 720), null, 100);
			SceneItem marker = entering.forEvent(0, Role.MARKER_FOOTPRINT).get(0);
			double travelled = (marker.value(1) - projection.horizonY()) / (720 * IsometricProjection.NECK_NEAR_EDGE - projection.horizonY());
			assertTrue(travelled < 0.02, "the note comes in at the horizon: " + travelled);
			double previous = travelled;
			for (long left = look - 20 * MS; left >= 0; left -= 100 * MS) {
				Scene scene = fixture.scene(false).build(fixture.onset(0) - left, new Viewport(1280, 720), null, 100);
				double now2 = (scene.forEvent(0, Role.MARKER_FOOTPRINT).get(0).value(1) - projection.horizonY()) / (720 * IsometricProjection.NECK_NEAR_EDGE - projection.horizonY());
				assertTrue(now2 >= previous, "it only goes down");
				previous = now2;
			}
			Scene onset = fixture.scene(false).build(fixture.onset(0), new Viewport(1280, 720), null, 100);
			double arrived = (onset.forEvent(0, Role.MARKER_FOOTPRINT).get(0).value(1) - projection.horizonY()) / (720 * IsometricProjection.NECK_NEAR_EDGE - projection.horizonY());
			assertTrue(arrived >= previous);
			assertEquals(1.0, arrived, 1e-9, "and reaches the neck at its onset");
		}
	}

	private static SceneFixture note1() {
		SongFixture song = new SongFixture(2);
		song.quarter(1, 0, 3, 7);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static Set<Integer> admittedIdentities(Scene scene) {
		Set<Integer> found = new TreeSet<Integer>();
		for (SceneItem item : scene.items()) {
			if (item.eventIndex() >= 0) {
				found.add(item.eventIndex());
			}
		}
		return found;
	}
}
