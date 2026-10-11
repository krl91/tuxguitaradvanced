package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * The palette and the order of the strings, and the lit string (spec 003 FR-067, FR-068, SC-025, the owner's references of 2026-10-11): the lowest string is
 * the topmost row of the neck and red, the highest is violet at the bottom; a string is dim while no event of the active moment targets it and bright, with a
 * glow, while the active note or chord targets it; the others stay dim at the same instant.
 */
public class LitStringTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);
	/** The dim strings are drawn at this alpha at the most, the bright ones at full alpha: a clear difference on the dark and the light look. */
	private static final double DIM_MAX = 0.5;

	private static SceneFixture notes() {
		SongFixture song = new SongFixture(4);
		song.quarter(1, 0, 3, 5);
		song.quarter(1, 1, 6, 3);
		song.quarter(1, 2, 1, 7);
		song.chord(1, 3, new int[] { 6, 5, 4, 2 }, new int[] { 3, 5, 5, 4 });
		song.quarter(2, 0, 2, 8);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static SceneItem stringLine(Scene scene, int tgString) {
		for (SceneItem item : scene.withRole(Role.STRING)) {
			if (item.tgString() == tgString) {
				return item;
			}
		}
		throw new AssertionError("no line for string " + tgString);
	}

	private static Set<Integer> lit(Scene scene, int strings) {
		Set<Integer> lit = new TreeSet<Integer>();
		for (int string = 1; string <= strings; string++) {
			if (stringLine(scene, string).alpha() > DIM_MAX) {
				lit.add(string);
			}
		}
		return lit;
	}

	@Test
	public void theStringsAreTheOwnersPaletteAndTheLowestStringIsTheTopmostRow() {
		SceneFixture fixture = notes();
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 800 * MS, SCREEN, null, 100, QualityTier.FULL);
		int[] expected = { StringColours.RED, StringColours.YELLOW, StringColours.LIGHT_BLUE, StringColours.ORANGE, StringColours.GREEN, StringColours.VIOLET };
		double previousY = -1.0;
		for (int string = 6; string >= 1; string--) {
			SceneItem line = stringLine(scene, string);
			assertEquals(expected[6 - string], line.colour(), "the colour of string " + string);
			assertTrue(line.value(1) > previousY, "string " + string + " is below the string that is lower in pitch");
			previousY = line.value(1);
		}
		assertTrue(stringLine(scene, 6).value(1) < stringLine(scene, 1).value(1), "the lowest string is at the top, the violet one at the bottom");
	}

	@Test
	public void exactlyTheStringOfTheActiveNoteIsBrightAndTheOthersAreDimForEveryString() {
		for (int string : new int[] { 1, 3, 6 }) {
			SceneFixture fixture = notes();
			int event = (string == 3 ? 0 : string == 6 ? 1 : 2);
			for (QualityTier tier : QualityTier.values()) {
				ArcadeRun.View run = event == 0 ? null : ArcadeSceneTest.completedBefore(event, fixture.events.size());
				Scene scene = fixture.scene(false).build(fixture.onset(event) - 400 * MS, SCREEN, run, 100, tier);
				assertEquals(Set.of(string), lit(scene, 6), "tier " + tier + ": the string of the active note alone is lit");
				assertEquals(1.0, stringLine(scene, string).alpha(), 0.0, "at full brightness");
			}
		}
	}

	@Test
	public void theGlowAccompaniesTheLitStringAtFullAndBalancedAndIsDroppedWithTheDecorationAtPerformance() {
		SceneFixture fixture = notes();
		long now = fixture.onset(0) - 400 * MS;
		for (QualityTier tier : QualityTier.values()) {
			Scene scene = fixture.scene(false).build(now, SCREEN, null, 100, tier);
			List<SceneItem> glow = new ArrayList<SceneItem>();
			for (SceneItem item : scene.withRole(Role.GLOW)) {
				if (item.shape() == SceneItem.Shape.LINE && item.tgString() == 3) {
					glow.add(item);
				}
			}
			assertEquals(tier.glow() ? 1 : 0, glow.size(), "tier " + tier + ": the glow of the lit string");
			assertEquals(1.0, stringLine(scene, 3).alpha(), 0.0, "tier " + tier + ": the string itself is bright, with or without the glow");
			for (SceneItem g : glow) {
				assertEquals(StringColours.ORANGE, g.colour());
				assertTrue(g.lineWidth() > stringLine(scene, 3).lineWidth(), "wider than the string");
			}
		}
	}

	@Test
	public void aChordLightsEveryOneOfItsWrittenStringsTogetherAndNoOtherString() {
		SceneFixture fixture = notes();
		ArcadeRun.View run = ArcadeSceneTest.completedBefore(3, fixture.events.size());
		Scene scene = fixture.scene(false).build(fixture.onset(3) - 400 * MS, SCREEN, run, 100, QualityTier.FULL);
		assertEquals(Set.of(6, 5, 4, 2), lit(scene, 6), "the written strings of the chord");
	}

	@Test
	public void noStringIsLitWithoutAnActiveEventAndTheLightFollowsTheJudgement() {
		SceneFixture fixture = notes();
		// long before the first event: nothing is due, nothing is targeted
		Scene before = fixture.scene(false).build(fixture.onset(0) - 30_000 * MS, SCREEN, null, 100, QualityTier.FULL);
		assertEquals(Set.of(), lit(before, 6), "no event of the active moment: every string dim");
		// the first note is active; once it is judged, the string of the next note takes the light in the same frame
		long now = fixture.onset(1) - 300 * MS;
		Scene active = fixture.scene(false).build(now, SCREEN, null, 100, QualityTier.FULL);
		assertEquals(Set.of(3), lit(active, 6), "the first note, not judged, is still the active one");
		Scene judged = fixture.scene(false).build(now, SCREEN, ArcadeSceneTest.completedBefore(1, fixture.events.size()), 100, QualityTier.FULL);
		assertEquals(Set.of(6), lit(judged, 6), "after its judgement the next note's string is lit and the first one is dim again");
		// after the last event is judged nothing is lit
		Scene after = fixture.scene(false).build(fixture.onset(4) + 100 * MS, SCREEN, ArcadeSceneTest.completedBefore(5, fixture.events.size()), 100, QualityTier.FULL);
		assertEquals(Set.of(), lit(after, 6), "after the last judgement: every string dim");
	}

	@Test
	public void theLightDoesNotChangeTheLabelsNorTheTargetDisksAndStaysVisibleInMonochromeAndOnTheLightLook() {
		SceneFixture fixture = notes();
		long now = fixture.onset(0) - 400 * MS;
		for (Theme theme : Theme.values()) {
			for (boolean mono : new boolean[] { false, true }) {
				Scene scene = fixture.scene(mono).withTheme(theme).build(now, SCREEN, null, 100, QualityTier.FULL);
				assertEquals(1, scene.withRole(Role.TARGET_DISK).size(), "the target disk is still there");
				assertEquals(6, scene.withRole(Role.STRING_LABEL).size(), "and the six labels of the strings");
				SceneItem litLine = stringLine(scene, 3);
				SceneItem dimLine = stringLine(scene, 1);
				assertTrue(litLine.alpha() - dimLine.alpha() >= 0.4, theme + " mono=" + mono + ": a clear difference of brightness");
				assertFalse(litLine.lineWidth() < dimLine.lineWidth() * 0.99 && litLine.alpha() <= dimLine.alpha(), "never less visible than a dim string");
			}
		}
	}
}
