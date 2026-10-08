package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.song.models.TGChord;
import app.tuxguitar.song.models.TGStroke;

/** The scene of the chords and the verdict badges (spec 003 T038, FR-028, FR-030): one bar and one diagram, a ghost on the neck, chord-level badges. */
public class ArcadeSceneChordTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	/** An A5 chord (low E 5, A 7, D 7) then a note, so that the next event exists. */
	private static SceneFixture a5() {
		SongFixture song = new SongFixture(1);
		song.chord(0, 0, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		song.quarter(0, 1, 1, 3);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	@Test
	public void aChordIsOneNeutralBarAndOneDiagramAndNeverIndependentNotes() {
		SceneFixture fixture = a5();
		Scene picture = fixture.scene(false).build(fixture.onset(0) - 1000 * MS, SCREEN, null, 100);

		assertEquals(1, picture.forEvent(0, Role.TIMING_BAR).size());
		assertEquals(1, picture.forEvent(0, Role.CARTOUCHE_FRAME).size());
		assertEquals(1, picture.forEvent(0, Role.STEM).size());
		assertTrue(picture.forEvent(0, Role.MARKER).isEmpty(), "no flying note for a chord");
		assertTrue(picture.forEvent(0, Role.TRAJECTORY).isEmpty());
		assertEquals(ArcadeScene.BAR_COLOUR, picture.forEvent(0, Role.TIMING_BAR).get(0).colour(), "the bar is neutral");
		assertEquals(0, picture.forEvent(0, Role.TIMING_BAR).get(0).tgString());

		List<SceneItem> dots = picture.forEvent(0, Role.CARTOUCHE_DOT);
		assertEquals(3, dots.size());
		assertEquals(fixture.colours.of(6), colourOfString(dots, 6));
		assertEquals(fixture.colours.of(5), colourOfString(dots, 5));
		assertEquals(fixture.colours.of(4), colourOfString(dots, 4));
		assertEquals("5fr", picture.forEvent(0, Role.CARTOUCHE_FRET_LABEL).get(0).text());
		assertEquals("Chord", picture.forEvent(0, Role.CARTOUCHE_NAME).get(0).text(), "no name written, none invented");
		assertEquals(6, picture.forEvent(0, Role.CARTOUCHE_STRING).size(), "the vertical lines of the track");
	}

	private static int colourOfString(List<SceneItem> dots, int tgString) {
		for (SceneItem dot : dots) {
			if (dot.tgString() == tgString) {
				return dot.colour();
			}
		}
		throw new AssertionError("no dot for string " + tgString);
	}

	@Test
	public void theBarReachesTheHitLineAtTheOnsetAndTheDiagramIsAttachedToItByTheStem() {
		SceneFixture fixture = a5();
		ArcadeScene scene = fixture.scene(false);
		IsometricProjection projection = new IsometricProjection(SCREEN);
		for (long offset : new long[] { -2500, -1500, -600, -100, 0 }) {
			Scene picture = scene.build(fixture.onset(0) + offset * MS, SCREEN, null, 100);
			SceneItem bar = picture.forEvent(0, Role.TIMING_BAR).get(0);
			SceneItem stem = picture.forEvent(0, Role.STEM).get(0);
			SceneItem frame = picture.forEvent(0, Role.CARTOUCHE_FRAME).get(0);
			double barCentre = (bar.value(0) + bar.value(2)) / 2.0;
			assertEquals(barCentre, stem.value(0), 1e-9, "the stem is at the centre of the bar at " + offset + " ms");
			assertEquals(bar.value(5), stem.value(1), 1e-9, "the stem starts on the bar");
			assertEquals(frame.value(5), stem.value(3), 1e-9, "and ends on the diagram");
			double frameCentre = (frame.value(0) + frame.value(2)) / 2.0;
			assertEquals(barCentre, frameCentre, 1e-9, "the diagram sits on the bar");
			if (offset == 0) {
				assertEquals(projection.hitLineY(), bar.value(1), 0.0, "the leading edge is on the hit line at the onset");
			} else {
				assertTrue(bar.value(1) < projection.hitLineY());
			}
		}
	}

	@Test
	public void theGhostIsOnTheNeckOnlyWhileTheChordIsTheActiveEventAndWithAllAndOnlyItsPositions() {
		SceneFixture fixture = a5();
		ArcadeScene scene = fixture.scene(false);
		for (long offset : new long[] { -2900, -1000, -100, 100 }) {
			List<SceneItem> ghosts = scene.build(fixture.onset(0) + offset * MS, SCREEN, null, 100).forEvent(0, Role.TARGET_DISK);
			assertEquals(3, ghosts.size(), "the three positions of the active chord, at " + offset + " ms");
			assertEquals(fixture.colours.of(6), colourOfString(ghosts, 6));
			assertEquals(fixture.colours.of(4), colourOfString(ghosts, 4));
		}
		Scene afterIt = scene.build(fixture.onset(0) - 100 * MS, SCREEN, ArcadeSceneTest.viewOf(ArcadeVerdict.ON_TIME, ArcadeVerdict.WAITING), 100);
		assertTrue(afterIt.forEvent(0, Role.TARGET_DISK).isEmpty(), "completed: no longer on the neck");
		assertEquals(1, afterIt.forEvent(1, Role.TARGET_DISK).size(), "the following note takes its place");
	}

	@Test
	public void theDiagramNeverShrinksBelowItsMinimumSizeNorLeavesTheScreen() {
		SceneFixture fixture = a5();
		ArcadeScene scene = fixture.scene(false);
		for (Viewport viewport : new Viewport[] { SCREEN, new Viewport(480, 320), new Viewport(2560, 1440) }) {
			for (long offset = -2900; offset <= 0; offset += 100) {
				SceneItem frame = scene.build(fixture.onset(0) + offset * MS, viewport, null, 100).forEvent(0, Role.CARTOUCHE_FRAME).get(0);
				assertTrue(frame.value(2) - frame.value(0) >= ChordCartouche.MIN_WIDTH_PX - 1e-9, "width at " + offset + " ms in " + viewport.width());
				assertTrue(frame.value(5) - frame.value(1) >= ChordCartouche.MIN_HEIGHT_PX - 1e-9, "height at " + offset + " ms in " + viewport.width());
				assertTrue(frame.value(0) >= 0 && frame.value(2) <= viewport.width(), "inside the width");
				assertTrue(frame.value(1) >= 0, "inside the top");
			}
		}
	}

	@Test
	public void theWrittenNameAndStrokeAreShownAndNothingElse() {
		SongFixture song = new SongFixture(1);
		song.chord(0, 0, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		song.chord(0, 1, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		TGChord named = song.factory().newChord(6);
		named.setName("A5");
		song.track().getMeasure(0).getBeat(0).setChord(named);
		song.track().getMeasure(0).getBeat(0).getStroke().setDirection(TGStroke.STROKE_DOWN);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);

		Scene picture = fixture.scene(false).build(fixture.onset(0) - 500 * MS, SCREEN, null, 100);
		assertEquals("A5", picture.forEvent(0, Role.CARTOUCHE_NAME).get(0).text());
		assertEquals("↓", picture.forEvent(0, Role.CARTOUCHE_STROKE).get(0).text());
		// the first chord is judged; the second, which was only a compact bar, is promoted
		Scene other = fixture.scene(false).build(fixture.onset(1) - 300 * MS, SCREEN, ArcadeSceneTest.completedBefore(1, 2), 100);
		assertEquals("Chord", other.forEvent(1, Role.CARTOUCHE_NAME).get(0).text());
		assertTrue(other.forEvent(1, Role.CARTOUCHE_STROKE).isEmpty(), "no stroke written, no arrow");
	}

	@Test
	public void aChordWithSharedPitchesIsSaidNotToBeCheckedSeparately() {
		SongFixture song = new SongFixture(1);
		song.quarter(0, 0, 1, 0);
		song.quarter(0, 0, 2, 5);
		song.quarter(0, 0, 3, 2);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		Scene picture = fixture.scene(false).build(fixture.onset(0) - 500 * MS, SCREEN, null, 100);
		assertEquals("Not checked separately", picture.forEvent(0, Role.CARTOUCHE_NOTE).get(0).text());
		assertTrue(a5Scene(500).forEvent(0, Role.CARTOUCHE_NOTE).isEmpty());
	}

	private static Scene a5Scene(long msBefore) {
		SceneFixture fixture = a5();
		return fixture.scene(false).build(fixture.onset(0) - msBefore * MS, SCREEN, null, 100);
	}

	@Test
	public void everyVerdictHasItsOwnBadgeWithASymbolAndAWordAndTheWaitingOneHasNone() {
		SceneFixture fixture = a5();
		ArcadeScene scene = fixture.scene(false);
		Set<String> symbols = new java.util.HashSet<String>();
		Set<Integer> colours = new java.util.HashSet<Integer>();
		for (ArcadeVerdict verdict : ArcadeVerdict.values()) {
			ArcadeRun.View run = ArcadeSceneTest.viewOf(verdict, ArcadeVerdict.WAITING);
			Scene picture = scene.build(fixture.onset(0) + 200 * MS, SCREEN, run, 100);
			if (verdict == ArcadeVerdict.WAITING) {
				assertTrue(picture.forEvent(0, Role.BADGE).isEmpty());
				continue;
			}
			assertEquals(1, picture.forEvent(0, Role.BADGE).size(), verdict.name());
			assertEquals(ArcadeScene.verdictSymbol(verdict), picture.forEvent(0, Role.BADGE_SYMBOL).get(0).text());
			assertEquals(SceneFixture.ENGLISH.verdict(verdict), picture.forEvent(0, Role.BADGE_TEXT).get(0).text());
			assertEquals(ArcadeScene.verdictColour(verdict), picture.forEvent(0, Role.BADGE).get(0).colour());
			symbols.add(ArcadeScene.verdictSymbol(verdict));
			colours.add(ArcadeScene.verdictColour(verdict));
			for (int s = 1; s <= 6; s++) {
				assertNotEquals(fixture.colours.of(s), ArcadeScene.verdictColour(verdict), "a verdict colour is never a string colour: " + verdict);
			}
		}
		assertEquals(ArcadeVerdict.values().length - 1, symbols.size(), "a different symbol for every verdict");
	}

	@Test
	public void anIncompleteChordNamesTheMissingPitchesAndNeverSaysWhichStringWasHeard() {
		SceneFixture fixture = a5();
		ArcadeRun.View run = ArcadeSceneTest.viewOf(new int[][] { { 45, 52 }, {} }, ArcadeVerdict.INCOMPLETE, ArcadeVerdict.WAITING);
		Scene picture = fixture.scene(false).build(fixture.onset(0) + 200 * MS, SCREEN, run, 100);
		assertEquals("Incomplete · Missing: A2 E3", picture.forEvent(0, Role.BADGE_TEXT).get(0).text());
		for (SceneItem item : picture.forEvent(0)) {
			assertNotEquals(Role.MARKER, item.role(), "no per-string marker of what was heard");
		}
		assertEquals(1, picture.forEvent(0, Role.BADGE).size(), "one badge for the whole chord");
	}

	@Test
	public void aNotJudgedEventIsNeutralWithoutACrossAndItsWordIsNotJudged() {
		SceneFixture fixture = a5();
		Scene picture = fixture.scene(false).build(fixture.onset(0) + 200 * MS, SCREEN, ArcadeSceneTest.viewOf(ArcadeVerdict.NOT_JUDGED, ArcadeVerdict.WAITING), 100);
		assertEquals("Not judged", picture.forEvent(0, Role.BADGE_TEXT).get(0).text());
		assertNotEquals(ArcadeScene.verdictSymbol(ArcadeVerdict.WRONG), picture.forEvent(0, Role.BADGE_SYMBOL).get(0).text());
		assertNotEquals(ArcadeScene.verdictSymbol(ArcadeVerdict.MISSED), picture.forEvent(0, Role.BADGE_SYMBOL).get(0).text());
		assertTrue(new SceneItemColour(picture.forEvent(0, Role.BADGE).get(0)).isNeutral(), "grey, not red or green");
	}

	@Test
	public void aVerdictDoesNotAppearBeforeTheEventIsClose() {
		SceneFixture fixture = a5();
		ArcadeRun.View run = ArcadeSceneTest.viewOf(ArcadeVerdict.ON_TIME, ArcadeVerdict.WAITING);
		assertTrue(fixture.scene(false).build(fixture.onset(0) - 2500 * MS, SCREEN, run, 100).forEvent(0, Role.BADGE).isEmpty());
		assertFalse(fixture.scene(false).build(fixture.onset(0) - 100 * MS, SCREEN, run, 100).forEvent(0, Role.BADGE).isEmpty(), "an early match shows at once");
	}

	@Test
	public void twoBadgesAtTheSameColumnDoNotCoverEachOther() {
		SongFixture song = new SongFixture(1);
		song.quarter(0, 0, 1, 3);
		song.quarter(0, 1, 1, 3);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		long now = fixture.onset(1) + 50 * MS;
		Scene picture = fixture.scene(false).build(now, SCREEN, ArcadeSceneTest.viewOf(ArcadeVerdict.ON_TIME, ArcadeVerdict.LATE), 100);
		List<SceneItem> badges = picture.withRole(Role.BADGE);
		assertEquals(2, badges.size());
		SceneItem a = badges.get(0);
		SceneItem b = badges.get(1);
		boolean overlap = a.value(0) < b.value(2) && b.value(0) < a.value(2) && a.value(1) < b.value(5) && b.value(1) < a.value(5);
		assertFalse(overlap, "stacked, not overlapped");
	}

	@Test
	public void anAutomaticEventIsShownAsWhatItIsAndNotAsARecognisedChord() {
		SongFixture song = new SongFixture(1);
		song.quarter(0, 0, 2, 5).getEffect().setDeadNote(true);
		song.quarter(0, 1, 1, 3);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		assertEquals(ArcadeRenderEvent.Kind.AUTOMATIC, fixture.events.get(0).kind());
		Scene picture = fixture.scene(false).build(fixture.onset(0) - 500 * MS, SCREEN, null, 100);
		assertEquals("Automatic", picture.forEvent(0, Role.EVENT_TEXT).get(0).text());
		assertTrue(picture.forEvent(0, Role.CARTOUCHE_FRAME).isEmpty());
		assertTrue(picture.forEvent(0, Role.MARKER).isEmpty());
		assertEquals(1, picture.forEvent(0, Role.TIMING_BAR).size());
	}

	/** Colour helper: neutral when the three channels are close (grey-ish). */
	private static final class SceneItemColour {
		private final int rgb;

		SceneItemColour(SceneItem item) {
			this.rgb = item.colour();
		}

		boolean isNeutral() {
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;
			return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 40;
		}
	}

	@SuppressWarnings("unused")
	private static final Set<Role> UNUSED = EnumSet.noneOf(Role.class);
}
