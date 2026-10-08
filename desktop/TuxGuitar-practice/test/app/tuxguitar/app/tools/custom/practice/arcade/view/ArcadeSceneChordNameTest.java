package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxes.Box;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.song.models.TGChord;

/**
 * The name of the chord on its diagram (spec 003 FR-061, SC-018): the exact written name from the first readable state to the end of the feedback, the
 * generic word when none is written, nothing on a single note, readable in every tier and in monochrome, distinct from the fret numbers and the verdicts,
 * and never covering the next playable event.
 */
public class ArcadeSceneChordNameTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	/** A named chord, an unnamed chord, a note, another named chord and a note: events 0 to 4, one every half second. */
	private static SceneFixture fixture() {
		SongFixture song = new SongFixture(2);
		song.chord(0, 0, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		song.chord(0, 1, new int[] { 5, 4, 3, 2 }, new int[] { 3, 2, 0, 1 });
		song.quarter(0, 2, 3, 5);
		song.chord(0, 3, new int[] { 6, 5, 4, 3, 2, 1 }, new int[] { 1, 3, 3, 2, 1, 1 });
		song.quarter(1, 0, 2, 9);
		TGChord first = song.factory().newChord(6);
		first.setName("A5");
		song.track().getMeasure(0).getBeat(0).setChord(first);
		TGChord blank = song.factory().newChord(6);
		blank.setName("   ");
		song.track().getMeasure(0).getBeat(1).setChord(blank);
		TGChord long_ = song.factory().newChord(6);
		long_.setName("F#m7b5/C#");
		song.track().getMeasure(0).getBeat(3).setChord(long_);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	/** One named chord alone: A5, a stroke, the fifth fret. */
	private static SceneFixture lone() {
		SongFixture song = new SongFixture(2);
		song.chord(1, 0, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		TGChord named = song.factory().newChord(6);
		named.setName("A5");
		song.track().getMeasure(1).getBeat(0).setChord(named);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static List<Scene> variants(SceneFixture fixture, long now, app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun.View run) {
		java.util.ArrayList<Scene> scenes = new java.util.ArrayList<Scene>();
		for (boolean mono : new boolean[] { false, true }) {
			for (QualityTier tier : QualityTier.values()) {
				scenes.add(fixture.scene(mono).build(now, SCREEN, run, 100, tier));
			}
		}
		return scenes;
	}

	@Test
	public void aNamedChordShowsExactlyItsWrittenNameInEveryTierAndInMonochrome() {
		SceneFixture fixture = fixture();
		for (Scene scene : variants(fixture, fixture.onset(0) - 1000 * MS, null)) {
			List<SceneItem> names = scene.forEvent(0, Role.CARTOUCHE_NAME);
			assertEquals(1, names.size());
			assertEquals("A5", names.get(0).text(), "the name as written");
		}
		for (Scene scene : variants(fixture, fixture.onset(3) - 300 * MS, ArcadeSceneTest.completedBefore(3, 5))) {
			assertEquals("F#m7b5/C#", scene.forEvent(3, Role.CARTOUCHE_NAME).get(0).text(), "a long name is shown whole");
		}
	}

	@Test
	public void aChordWithoutAUsableNameShowsTheNameDerivedFromItsWrittenNotesNeverTheBareGenericWord() {
		SceneFixture fixture = fixture();
		// the blank name of the second chord is no name: its written notes, C on the A string, E, G and C, give a C chord
		for (Scene scene : variants(fixture, fixture.onset(1) - 300 * MS, ArcadeSceneTest.completedBefore(1, 5))) {
			assertEquals("C", scene.forEvent(1, Role.CARTOUCHE_NAME).get(0).text(), "a blank name is no name");
			for (SceneItem item : scene.items()) {
				if (item.text() != null) {
					assertFalse(item.text().equals("Chord") || item.text().equals("Accord"), "the bare generic word is gone: " + item);
				}
			}
		}
	}

	@Test
	public void aSingleNoteNeverHasAChordHeader() {
		SceneFixture fixture = fixture();
		for (Scene scene : variants(fixture, fixture.onset(2) - 600 * MS, ArcadeSceneTest.completedBefore(2, 5))) {
			assertTrue(scene.forEvent(2, Role.CARTOUCHE_NAME).isEmpty());
			assertTrue(scene.forEvent(2, Role.CARTOUCHE_HEADER).isEmpty());
			assertTrue(scene.forEvent(2, Role.CARTOUCHE_FRAME).isEmpty());
			assertEquals("5", scene.forEvent(2, Role.MARKER_FRET).get(0).text(), "a note shows its fret, and only that");
		}
		for (SceneItem item : fixture.scene(false).build(fixture.onset(2) - 600 * MS, SCREEN, null, 100).items()) {
			assertFalse(item.eventIndex() == 2 && item.role() == Role.CARTOUCHE_NAME);
		}
	}

	@Test
	public void theHeaderIsThereFromTheFirstReadableStateThroughImpactAndFeedback() {
		SceneFixture fixture = lone();
		long onset = fixture.onset(0);
		CameraTrack.Settings settings = CameraTrack.Settings.standard();
		int seen = 0;
		for (long t = onset - settings.lookAheadNanos; t <= onset + settings.holdAfterNanos; t += 25 * MS) {
			RenderState state = RenderState.of(onset, t, settings.lookAheadNanos, settings.holdAfterNanos);
			Scene scene = fixture.scene(false).build(t, SCREEN, null, 100);
			if (state.ordinal() >= RenderState.READABLE.ordinal() && state != RenderState.EXPIRED) {
				assertEquals("A5", scene.forEvent(0, Role.CARTOUCHE_NAME).get(0).text(), "the name in state " + state + " at " + (t - onset) / MS + " ms");
				SceneItem name = scene.forEvent(0, Role.CARTOUCHE_NAME).get(0);
				assertTrue(name.alpha() >= 0.9, "and readable, not faded: " + name.alpha());
				seen++;
			}
		}
		assertTrue(seen > 50, "the whole life of the chord was checked");
	}

	@Test
	public void theHeaderIsAHeaderApartFromTheFretLabelTheFretNumbersAndTheVerdict() {
		SceneFixture fixture = lone();
		Scene scene = fixture.scene(false).build(fixture.onset(0) + 150 * MS, SCREEN, ArcadeSceneTest.viewOf(ArcadeVerdict.ON_TIME), 100);
		SceneItem name = scene.forEvent(0, Role.CARTOUCHE_NAME).get(0);
		SceneItem header = scene.forEvent(0, Role.CARTOUCHE_HEADER).get(0);
		SceneItem fretLabel = scene.forEvent(0, Role.CARTOUCHE_FRET_LABEL).get(0);
		SceneItem verdict = scene.forEvent(0, Role.BADGE_TEXT).get(0);
		assertNotEquals(name.text(), fretLabel.text());
		assertNotEquals(name.text(), verdict.text());
		assertEquals("5fr", fretLabel.text());
		Box headerBox = SceneBoxes.ofQuad(header);
		assertTrue(headerBox.contains(SceneBoxes.ofText(name)), "the name is in the header band");
		assertFalse(headerBox.overlaps(SceneBoxes.ofText(fretLabel)), "the fret label is not in the header band");
		assertFalse(headerBox.overlaps(SceneBoxes.ofQuad(scene.forEvent(0, Role.BADGE).get(0))), "nor is the verdict");
		assertNotEquals(ArcadeScene.TEXT_COLOUR, name.colour(), "its own style");
		assertTrue(name.value(2) >= 9.0, "a readable size: " + name.value(2));
	}

	@Test
	public void theHeaderNeverCoversTheNextPlayableEvent() {
		SceneFixture fixture = fixture();
		for (long offset = -1500; offset <= 0; offset += 50) {
			long now = fixture.onset(0) + offset * MS;
			Scene scene = fixture.scene(false).build(now, SCREEN, null, 100);
			List<SceneItem> frames = scene.forEvent(0, Role.CARTOUCHE_FRAME);
			if (frames.isEmpty()) {
				continue;
			}
			Box cartouche = SceneBoxes.ofQuad(frames.get(0));
			// the next playable event is event 1, a chord: its bar is on the same axis, behind the diagram, and still visible on both sides of it
			for (SceneItem item : scene.forEvent(1)) {
				if (item.role() == Role.TIMING_BAR || item.role() == Role.PREVIEW_BAR) {
					Box bar = SceneBoxes.ofQuad(item);
					double hidden = Math.max(0.0, Math.min(bar.x2, cartouche.x2) - Math.max(bar.x1, cartouche.x1));
					assertTrue(hidden <= 0.5 * (bar.x2 - bar.x1), "the diagram hides more than half of the bar of the next chord at " + offset + " ms");
				}
			}
		}
		// a note after a chord
		for (long offset = -1500; offset <= 0; offset += 50) {
			long now = fixture.onset(1) + offset * MS;
			Scene scene = fixture.scene(false).build(now, SCREEN, ArcadeSceneTest.completedBefore(1, 5), 100);
			List<SceneItem> frames = scene.forEvent(1, Role.CARTOUCHE_FRAME);
			List<SceneItem> markers = scene.forEvent(2, Role.MARKER);
			markers = markers.isEmpty() ? scene.forEvent(2, Role.PREVIEW) : markers;
			if (!frames.isEmpty() && !markers.isEmpty()) {
				assertFalse(SceneBoxes.ofQuad(frames.get(0)).overlaps(SceneBoxes.ofDisc(markers.get(0))), "the diagram covers the next note at " + offset + " ms");
			}
		}
	}

	@Test
	public void aVeryLongNameIsShownWholeInsideItsHeaderAtEverySizeOfTheDiagram() {
		SongFixture song = new SongFixture(1);
		song.chord(0, 0, new int[] { 4, 3 }, new int[] { 3, 5 });
		song.quarter(0, 2, 2, 4);
		TGChord named = song.factory().newChord(6);
		named.setName("Abmaj7#11/Gb");
		song.track().getMeasure(0).getBeat(0).setChord(named);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		for (long offset = -2900; offset <= 700; offset += 100) {
			for (Scene scene : variants(fixture, fixture.onset(0) + offset * MS, null)) {
				List<SceneItem> names = scene.forEvent(0, Role.CARTOUCHE_NAME);
				if (names.isEmpty()) {
					continue;
				}
				assertEquals("Abmaj7#11/Gb", names.get(0).text());
				Box header = SceneBoxes.ofQuad(scene.forEvent(0, Role.CARTOUCHE_HEADER).get(0));
				assertTrue(header.contains(SceneBoxes.ofText(names.get(0))), "inside its header at " + offset + " ms: " + header + " vs " + SceneBoxes.ofText(names.get(0)));
				assertTrue(header.x1 >= 0 && header.x2 <= SCREEN.width(), "not clipped by the screen");
			}
		}
	}
}
