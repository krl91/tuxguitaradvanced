package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxes.Box;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.song.models.TGChord;

/**
 * The chord preview lane (spec 003 FR-063, SC-020): all the chords on one axis in the middle of the approach lane, ordered by their onset and their depth
 * alone; one expanded diagram at a time, the nearest upcoming chord, whole inside the window; the others bars without diagram, at most three, the rest absent; promotion in onset order without a frame
 * of two diagrams; nothing of this moves an impact, whatever the frets of the chords.
 */
public class ArcadeSceneChordLaneTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);
	private static final int[] COUNTS = { 2, 4, 8 };

	/** {@code count} chords, one every 350 ms, alternating low shapes and high ones; {@code shift} moves every fret up (the same onsets, other places on the neck). */
	static SceneFixture chords(int count, int shift, boolean named) {
		SongFixture song = new SongFixture(count / 4 + 3);
		song.tempoChange(0, 171);
		for (int i = 0; i < count; i++) {
			int base = i % 2 == 0 ? 1 : 12;
			song.chord(1 + i / 4, i % 4, new int[] { 6, 5, 4, 3 }, new int[] { base + shift, base + 2 + shift, base + 2 + shift, base + 1 + shift });
			if (named) {
				TGChord chord = song.factory().newChord(6);
				chord.setName(i % 3 == 2 ? "  " : "Ch" + i);
				song.track().getMeasure(1 + i / 4).getBeat(i % 4).setChord(chord);
			}
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

	private static double[] centreAndEdge(SceneItem quad) {
		Box box = SceneBoxes.ofQuad(quad);
		return new double[] { (box.x1 + box.x2) / 2.0, box.y2 };
	}

	@Test
	public void exactlyOneChordHasItsDiagramWhateverTheNumberOfChordsTheTierAndTheColour() {
		for (int count : COUNTS) {
			SceneFixture fixture = chords(count, 0, true);
			for (long t = fixture.onset(0) - 2900 * MS; t <= fixture.onset(count - 1) + 900 * MS; t += 40 * MS) {
				for (Scene scene : variants(fixture, t, null)) {
					int frames = scene.withRole(Role.CARTOUCHE_FRAME).size();
					int headers = scene.withRole(Role.CARTOUCHE_HEADER).size();
					boolean anyVisible = false;
					for (int i = 0; i < count; i++) {
						anyVisible |= !scene.forEvent(i).isEmpty();
					}
					assertEquals(anyVisible ? 1 : 0, frames, count + " chords at " + (t - fixture.onset(0)) / MS + " ms");
					assertEquals(frames, headers);
				}
			}
		}
	}

	@Test
	public void noCartoucheIsCutByTheWindowAndTheNameStaysInsideItsHeader() {
		for (int count : COUNTS) {
			SceneFixture fixture = chords(count, 0, true);
			for (Viewport viewport : new Viewport[] { SCREEN, new Viewport(480, 320), new Viewport(2560, 1440) }) {
				for (long t = fixture.onset(0) - 2900 * MS; t <= fixture.onset(count - 1) + 900 * MS; t += 50 * MS) {
					Scene scene = fixture.scene(false).build(t, viewport, null, 100);
					for (SceneItem frame : scene.withRole(Role.CARTOUCHE_FRAME)) {
						Box box = SceneBoxes.ofQuad(frame);
						assertTrue(box.x1 >= 0 && box.y1 >= 0 && box.x2 <= viewport.width() && box.y2 <= viewport.height(), "cut: " + box + " in " + viewport.width());
						List<SceneItem> names = scene.forEvent(frame.eventIndex(), Role.CARTOUCHE_NAME);
						assertTrue(SceneBoxes.ofQuad(scene.forEvent(frame.eventIndex(), Role.CARTOUCHE_HEADER).get(0)).contains(SceneBoxes.ofText(names.get(0))));
					}
				}
			}
		}
	}

	@Test
	public void everyChordBarCentreIsOnTheSameDocumentedAxis() {
		for (int count : COUNTS) {
			for (int shift : new int[] { 0, 9 }) {
				SceneFixture fixture = chords(count, shift, false);
				for (long t = fixture.onset(0) - 2900 * MS; t <= fixture.onset(count - 1) + 500 * MS; t += 60 * MS) {
					for (Scene scene : variants(fixture, t, null)) {
						int bars = 0;
						for (SceneItem item : scene.items()) {
							if (item.role() == Role.TIMING_BAR || item.role() == Role.CHORD_BAR) {
								assertEquals(SCREEN.width() * ArcadeScene.CHORD_AXIS, centreAndEdge(item)[0], 1e-6, "bar of chord " + item.eventIndex() + " at " + (t - fixture.onset(0)) / MS);
								bars++;
							}
						}
						assertTrue(bars > 0 || scene.forEvent(0).isEmpty() && scene.forEvent(count - 1).isEmpty() || t > fixture.onset(count - 1));
					}
				}
			}
		}
	}

	@Test
	public void theFretsOfAChordNeverMoveItsBarOrItsDiagramSideways() {
		for (int count : COUNTS) {
			SceneFixture low = chords(count, 0, true);
			SceneFixture high = chords(count, 9, true);
			for (long t = low.onset(0) - 2900 * MS; t <= low.onset(count - 1); t += 70 * MS) {
				Scene a = low.scene(false).build(t, SCREEN, null, 100);
				Scene b = high.scene(false).build(t, SCREEN, null, 100);
				for (int i = 0; i < count; i++) {
					List<SceneItem> barsA = a.forEvent(i, Role.TIMING_BAR);
					barsA = barsA.isEmpty() ? a.forEvent(i, Role.CHORD_BAR) : barsA;
					List<SceneItem> barsB = b.forEvent(i, Role.TIMING_BAR);
					barsB = barsB.isEmpty() ? b.forEvent(i, Role.CHORD_BAR) : barsB;
					if (barsA.isEmpty()) {
						assertTrue(barsB.isEmpty());
						continue;
					}
					for (int v = 0; v < 8; v += 2) {
						assertEquals(barsA.get(0).value(v), barsB.get(0).value(v), 1e-9, "x of the bar of chord " + i);
					}
					List<SceneItem> frameA = a.forEvent(i, Role.CARTOUCHE_FRAME);
					List<SceneItem> frameB = b.forEvent(i, Role.CARTOUCHE_FRAME);
					assertEquals(frameA.size(), frameB.size());
					if (!frameA.isEmpty()) {
						assertEquals(frameA.get(0).value(0), frameB.get(0).value(0), 1e-9, "x of the diagram of chord " + i);
						assertEquals(frameA.get(0).value(2), frameB.get(0).value(2), 1e-9);
					}
				}
			}
		}
	}

	@Test
	public void theDepthOfTheBarsFollowsTheOrderOfTheOnsetsStrictly() {
		for (int count : COUNTS) {
			SceneFixture fixture = chords(count, 0, false);
			for (long t = fixture.onset(0) - 2900 * MS; t < fixture.onset(0); t += 45 * MS) {
				Scene scene = fixture.scene(false).build(t, SCREEN, null, 100);
				double previousY = Double.MAX_VALUE;
				for (int i = 0; i < count; i++) {
					List<SceneItem> bars = scene.forEvent(i, Role.TIMING_BAR);
					bars = bars.isEmpty() ? scene.forEvent(i, Role.CHORD_BAR) : bars;
					if (bars.isEmpty()) {
						continue;
					}
					double y = centreAndEdge(bars.get(0))[1];
					assertTrue(y < previousY, "chord " + i + " must be farther (higher) than chord " + (i - 1) + " at " + (t - fixture.onset(0)) / MS + " ms: " + y + " vs " + previousY);
					previousY = y;
				}
			}
		}
	}

	@Test
	public void chordsAreExpandedInTheOrderOfTheEventsOneAfterTheOtherWithNeverTwoAtOnce() {
		for (int count : COUNTS) {
			SceneFixture fixture = chords(count, 0, true);
			int last = -1;
			TreeSet<Integer> seen = new TreeSet<Integer>();
			for (long t = fixture.onset(0) - 2900 * MS; t <= fixture.onset(count - 1) + 900 * MS; t += 10 * MS) {
				// each chord is judged 200 ms after its onset, as the run would report it
				ArcadeVerdict[] verdicts = new ArcadeVerdict[fixture.events.size()];
				for (int i = 0; i < verdicts.length; i++) {
					verdicts[i] = t >= fixture.onset(i) + 200 * MS ? ArcadeVerdict.ON_TIME : ArcadeVerdict.WAITING;
				}
				for (Scene scene : variants(fixture, t, ArcadeSceneTest.viewOf(verdicts))) {
					java.util.Set<Integer> expanded = new TreeSet<Integer>();
					for (SceneItem frame : scene.withRole(Role.CARTOUCHE_FRAME)) {
						expanded.add(frame.eventIndex());
					}
					assertTrue(expanded.size() <= 1, "two diagrams at " + (t - fixture.onset(0)) / MS + " ms: " + expanded);
					for (int index : expanded) {
						assertTrue(index >= last, "the expanded chord went back from " + last + " to " + index);
						last = Math.max(last, index);
						seen.add(index);
					}
				}
			}
			assertEquals(count, seen.size(), "every chord was expanded in its turn: " + seen);
		}
	}

	@Test
	public void theImpactOfEveryChordIsOnTheHitLineAtItsTimelineOnsetWhetherItIsExpandedOrCompactAndWithOrWithoutTheBudget() {
		for (int count : COUNTS) {
			SceneFixture fixture = chords(count, 0, true);
			IsometricProjection projection = new IsometricProjection(SCREEN);
			for (int i = 0; i < count; i++) {
				for (boolean budget : new boolean[] { true, false }) {
					for (Scene scene : variants(fixture, fixture.onset(i), null)) {
						List<SceneItem> bars = scene.forEvent(i, Role.TIMING_BAR);
						bars = bars.isEmpty() ? scene.forEvent(i, Role.CHORD_BAR) : bars;
						assertFalse(bars.isEmpty());
						assertEquals(projection.hitLineY(), centreAndEdge(bars.get(0))[1], 0.0, "chord " + i + " at its onset");
					}
					Scene before = fixture.scene(false).withDensityBudget(budget).build(fixture.onset(i) - 300 * MS, SCREEN, null, 100);
					Scene after = fixture.scene(false).withDensityBudget(!budget).build(fixture.onset(i) - 300 * MS, SCREEN, null, 100);
					List<SceneItem> a = before.forEvent(i, Role.TIMING_BAR);
					a = a.isEmpty() ? before.forEvent(i, Role.CHORD_BAR) : a;
					List<SceneItem> b = after.forEvent(i, Role.TIMING_BAR);
					b = b.isEmpty() ? after.forEvent(i, Role.CHORD_BAR) : b;
					assertEquals(centreAndEdge(a.get(0))[1], centreAndEdge(b.get(0))[1], 0.0, "the budget moves nothing in depth");
				}
			}
		}
	}

	@Test
	public void onlyTheActiveChordProjectsItsPositionsOnTheNeckAndACompactChordNeverShowsADiagram() {
		SceneFixture fixture = chords(8, 0, true);
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 1000 * MS, SCREEN, null, 100);
		for (SceneItem item : scene.items()) {
			if (item.role() == Role.TARGET_DISK) {
				assertEquals(0, item.eventIndex());
			}
		}
		assertEquals(4, scene.forEvent(0, Role.TARGET_DISK).size());
		for (int i = 1; i < 8; i++) {
			for (SceneItem item : scene.forEvent(i)) {
				assertTrue(item.role() == Role.CHORD_BAR || item.role() == Role.CHORD_CHIP, "chord " + i + " is a bar without diagram, perhaps with its name: " + item);
			}
		}
	}

	@Test
	public void aCompactChordShowsItsNameOnlyWhenItIsFreeAndNeverAListOfNotes() {
		SceneFixture fixture = chords(8, 0, true);
		for (long t = fixture.onset(0) - 2900 * MS; t <= fixture.onset(7); t += 60 * MS) {
			Scene scene = fixture.scene(false).build(t, SCREEN, null, 100);
			assertNull(SceneBoxes.firstOverlap(SceneBoxes.labels(scene)), "at " + (t - fixture.onset(0)) / MS + " ms");
			for (SceneItem chip : scene.withRole(Role.CHORD_CHIP)) {
				ChordNameResolver.Display display = fixture.events.get(chip.eventIndex()).display();
				assertEquals(display.text(), chip.text(), "the identity of the chord: its written name, or the name derived from its notes");
				assertTrue(display.source() != ChordNameResolver.Source.PITCH_SET && !chip.text().trim().isEmpty(), "a short name, never a list of notes");
				assertTrue(scene.forEvent(chip.eventIndex(), Role.CARTOUCHE_FRAME).isEmpty(), "and no diagram with it");
			}
		}
	}
}
