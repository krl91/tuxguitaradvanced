package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	public void atMostFourEventsArePaintedAndAnExcludedNoteLeavesNoPrimitiveAtAll() {
		for (SceneFixture fixture : new SceneFixture[] { crowd(8), crowd(16), crowd(32), mixedCrowd(8), mixedCrowd(16), mixedCrowd(32) }) {
			int count = fixture.events.size();
			for (Viewport viewport : SIZES) {
				for (QualityTier tier : QualityTier.values()) {
					for (long before = 3_000; before >= -300; before -= 60) {
						Scene scene = fixture.scene(false).build(fixture.onset(0) - before * MS, viewport, null, 100, tier);
						Set<Integer> painted = new TreeSet<Integer>();
						for (SceneItem item : scene.items()) {
							if (item.eventIndex() >= 0) {
								painted.add(item.eventIndex());
							}
						}
						String where = count + " notes " + viewport.width() + " " + tier + " " + before + " ms before";
						int due = 0;
						for (int index = 0; index < fixture.events.size(); index++) {
							long until = fixture.onset(index) - (fixture.onset(0) - before * MS);
							due += (until > -80 * MS && until <= ArcadeScene.ADMITTED_WINDOW_NANOS) ? 1 : 0;
						}
						assertTrue(painted.size() <= Math.min(ArcadeScene.MAX_ADMITTED, Math.max(ArcadeScene.MIN_ADMITTED, due)),
								where + ": " + painted.size() + " events painted " + painted + " with " + due + " due within the window");
						// the painted ones are the nearest in time: no gap in the order of the notes
						int expected = painted.isEmpty() ? 0 : painted.iterator().next();
						for (int index : painted) {
							assertTrue(index >= expected, where);
						}
						for (int index = 0; index < fixture.events.size(); index++) {
							if (!painted.contains(index)) {
								// an event outside the admitted set has no primitive of any kind: not a marker, a halo, a lane, a bar, a dot, a label (no run: no verdict either)
								if (!scene.forEvent(index).isEmpty()) {
									assertTrue(false, where + ": event " + index + " left " + scene.forEvent(index).size() + " primitives, the first a " + scene.forEvent(index).get(0).role());
								}
							}
						}
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
						for (SceneItem marker : scene.withRole(Role.MARKER)) {
							int index = marker.eventIndex();
							int fret = fixture.events.get(index).positions().get(0).fret();
							double progress = Math.max(0.0, Math.min(1.0, 1.0 - (double) (fixture.onset(index) - now) / look));
							FretArrivalLaneMap.Lane lane = lanes.laneForFret(fret);
							ScreenPoint at = lane.at(progress);
							String where = count + " notes " + viewport.width() + " " + tier + " event " + index + " fret " + fret + " p=" + progress;
							assertEquals(at.x(), marker.value(0), 1e-6, where + ": the centre is on its lane");
							assertEquals(at.y(), marker.value(1), 1e-6, where);
							// the lane ends at the middle of the upper edge of the cell between the wires
							assertEquals((grid.cellLeftX(fret) + grid.cellRightX(fret)) / 2.0, lane.end().x(), 1e-9, where);
							assertEquals(grid.neckTopY(), lane.end().y(), 1e-9, where);
							if (progress >= 1.0) {
								assertEquals(lane.end().x(), marker.value(0), 1e-6, where + ": at the onset, the middle of the top of the cell");
								assertEquals(lane.end().y(), marker.value(1), 1e-6, where);
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
						if (item.role() == Role.TARGET_DISK || item.role() == Role.TARGET_OUTLINE || item.role() == Role.TARGET_HALO) {
							owners.add(item.eventIndex());
						}
					}
					assertTrue(owners.size() <= 1, count + " notes: several identities own targets: " + owners);
				}
				// when the active event is completed the next one owns the target, in the same scene
				Scene afterOne = fixture.scene(false).build(fixture.onset(0) + 10 * MS, viewport, ArcadeSceneTest.completedBefore(1, fixture.events.size()), 100);
				Set<Integer> owners = new HashSet<Integer>();
				for (SceneItem item : afterOne.items()) {
					if (item.role() == Role.TARGET_DISK) {
						owners.add(item.eventIndex());
					}
				}
				assertEquals(Set.of(1), owners, "the next event takes the target at once");
			}
		}
	}

	@Test
	public void everyNoteDueWithinFiveSecondsIsSeenComingFromTheHorizonOnItsWholeLane() {
		// one note a second: four are the base set, and a fifth is due within five seconds as the first ones come near
		SongFixture song = new SongFixture(4);
		song.tempoChange(0, 60);
		for (int i = 0; i < 12; i++) {
			song.quarter(i / 4, i % 4, 1 + (i * 5) % 6, 2 + i);
		}
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		long first = fixture.onset(0);
		Viewport screen = new Viewport(1280, 720);
		Scene before = fixture.scene(false).build(first - 1_700 * MS, screen, null, 100);
		assertEquals(Set.of(0, 1, 2, 3), admittedIdentities(before), "the base set of four: the fifth is 5.7 s away");
		assertTrue(before.forEvent(4).isEmpty());
		Scene after = fixture.scene(false).build(first - 700 * MS, screen, null, 100);
		assertEquals(Set.of(0, 1, 2, 3, 4), admittedIdentities(after), "the fifth is due within five seconds: it is there, whole");
		assertEquals(1, after.forEvent(4, Role.TRAJECTORY).size());
		assertEquals(1, after.forEvent(4, Role.MARKER).size());
		// it comes in at the far end of its lane, small, in the distance (the horizon): its marker is near the horizon, not half way
		IsometricProjection projection = new IsometricProjection(screen);
		double travelled = (after.forEvent(4, Role.MARKER).get(0).value(1) - projection.horizonY()) / (projection.hitLineY() - projection.horizonY());
		assertTrue(travelled < 0.3, "the fifth note has still most of its lane to travel: " + travelled);
		assertTrue(fixture.events.size() > 5 && after.forEvent(5).isEmpty(), "and the sixth, due in 5.7 s, is absent");
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
