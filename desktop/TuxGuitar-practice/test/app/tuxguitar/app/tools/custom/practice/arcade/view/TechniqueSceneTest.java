package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.song.models.TGNote;

/** The technique marks in the picture (spec 003 FR-078): in flight and on the neck, for a single note only, and nothing else changes. */
public class TechniqueSceneTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	private static final Role[] MARKS = { Role.TECH_ARROW, Role.TECH_HEAD, Role.TECH_GHOST, Role.TECH_RAIL, Role.TECH_LABEL, Role.TECH_PILL, Role.TECH_WAVE, Role.TECH_TICK };

	/** Three notes on string 3: A (fret 5, written with the technique), B (fret 7, the same technique), then a plain note on another string. */
	private static SceneFixture fixture(Consumer<TGNote> technique, int firstFret, int secondFret) {
		SongFixture song = new SongFixture(3);
		technique.accept(song.quarter(1, 0, 3, firstFret));
		technique.accept(song.quarter(1, 2, 3, secondFret));
		song.quarter(1, 3, 2, 3);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static int count(Scene scene, int event) {
		int n = 0;
		for (Role role : MARKS) {
			n += scene.forEvent(event, role).size();
		}
		return n;
	}

	private static Scene coming(SceneFixture f, QualityTier tier) {
		return f.scene(false).build(f.onset(0) - 700 * MS, SCREEN, null, 100, tier);
	}

	private static Scene onNeck(SceneFixture f, QualityTier tier) {
		return f.scene(false).build(f.onset(0), SCREEN, null, 100, tier);
	}

	@Test
	public void aHammerOnComingHasAnArcAndItsLetterAndOnTheNeckAGhostAtTheNextNote() {
		SceneFixture f = fixture(n -> n.getEffect().setHammer(true), 5, 7);
		for (QualityTier tier : QualityTier.values()) {
			Scene flight = coming(f, tier);
			assertFalse(flight.forEvent(1, Role.TECH_ARROW).isEmpty(), tier + ": the arc in flight");
			assertEquals("H/P", flight.forEvent(1, Role.TECH_LABEL).get(0).text(), tier + ": the second note has no next note on its string");
			assertTrue(flight.forEvent(1, Role.TECH_GHOST).isEmpty(), tier + ": the ghost is on the neck for the active note only");
			assertEquals(0, count(flight, 2), tier + ": a plain note has no mark");
			Scene neck = onNeck(f, tier);
			SceneItem target = neck.forEvent(0, Role.TARGET_RECT).get(0);
			SceneItem ghost = neck.forEvent(0, Role.TECH_GHOST).get(0);
			Camera camera = f.camera.at(f.onset(0));
			assertEquals(new IsometricProjection(SCREEN).neckX(f.neck.fretCentreX(7), camera), ghost.value(0), 1.5, tier + ": the ghost is the cell of the next note");
			assertEquals(target.value(1), ghost.value(1), 0.5, "on the same string");
			assertEquals("H", neck.forEvent(0, Role.TECH_LABEL).get(0).text());
		}
	}

	@Test
	public void aPullOffGoesToTheLeftWithItsLetter() {
		SceneFixture f = fixture(n -> n.getEffect().setHammer(true), 8, 5);
		Scene neck = onNeck(f, QualityTier.FULL);
		SceneItem target = neck.forEvent(0, Role.TARGET_RECT).get(0);
		SceneItem ghost = neck.forEvent(0, Role.TECH_GHOST).get(0);
		assertTrue(ghost.value(0) < target.value(0), "the next note is lower: the ghost is to the left");
		assertEquals("P", neck.forEvent(0, Role.TECH_LABEL).get(0).text());
		assertEquals("H/P", coming(f, QualityTier.FULL).forEvent(1, Role.TECH_LABEL).get(0).text(), "the second note has no next note on its string: the direction is not known");
	}

	@Test
	public void aSlideHasARailAlongTheStringToTheNextNote() {
		SceneFixture f = fixture(n -> n.getEffect().setSlide(true), 5, 9);
		Scene neck = onNeck(f, QualityTier.FULL);
		SceneItem target = neck.forEvent(0, Role.TARGET_RECT).get(0);
		SceneItem ghost = neck.forEvent(0, Role.TECH_GHOST).get(0);
		SceneItem rail = neck.forEvent(0, Role.TECH_RAIL).get(0);
		assertEquals(SceneItem.Shape.LINE, rail.shape());
		assertEquals(target.value(1), rail.value(1), 0.5, "the rail is on the string");
		assertEquals(rail.value(1), rail.value(3), 1e-9, "and straight");
		assertTrue(rail.value(2) <= ghost.value(0) && rail.value(0) >= target.value(0), "between the target and the ghost");
		assertEquals(2, neck.forEvent(0, Role.TECH_HEAD).size());
		assertEquals("S", neck.forEvent(0, Role.TECH_LABEL).get(0).text());
		assertFalse(coming(f, QualityTier.FULL).forEvent(1, Role.TECH_RAIL).isEmpty() && coming(f, QualityTier.FULL).forEvent(1, Role.TECH_ARROW).isEmpty(), "and a small one in flight");
	}

	@Test
	public void aVibratoIsAWaveInFlightAndAlongTheStringOnTheNeck() {
		SceneFixture f = fixture(n -> n.getEffect().setVibrato(true), 5, 7);
		for (QualityTier tier : QualityTier.values()) {
			assertTrue(coming(f, tier).forEvent(1, Role.TECH_WAVE).size() >= 4, tier + ": the wave in flight");
			Scene neck = onNeck(f, tier);
			assertTrue(neck.forEvent(0, Role.TECH_WAVE).size() >= 4, tier + ": the wave on the neck");
			assertTrue(neck.forEvent(0, Role.TECH_GHOST).isEmpty(), "no ghost: nothing moves to another note");
			SceneItem target = neck.forEvent(0, Role.TARGET_RECT).get(0);
			for (SceneItem segment : neck.forEvent(0, Role.TECH_WAVE)) {
				assertTrue(Math.min(segment.value(0), segment.value(2)) >= target.value(0) + target.value(2) - 1.0, "after the target, along the string");
			}
		}
	}

	@Test
	public void aPalmMuteAndATappingArePillsAboveTheRectangleInFlightAndOnTheNeck() {
		SceneFixture mute = fixture(n -> n.getEffect().setPalmMute(true), 5, 7);
		SceneFixture tap = fixture(n -> n.getEffect().setTapping(true), 5, 7);
		for (Object[] c : new Object[][] { { mute, "P.M." }, { tap, "T" } }) {
			SceneFixture f = (SceneFixture) c[0];
			Scene flight = coming(f, QualityTier.FULL);
			assertEquals(c[1], flight.forEvent(1, Role.TECH_LABEL).get(0).text());
			assertTrue(flight.forEvent(1, Role.TECH_LABEL).get(0).value(1) < flight.forEvent(1, Role.MARKER).get(0).value(1), "above the rectangle in flight");
			Scene neck = onNeck(f, QualityTier.FULL);
			assertEquals(c[1], neck.forEvent(0, Role.TECH_LABEL).get(0).text());
			assertTrue(neck.forEvent(0, Role.TECH_LABEL).get(0).value(1) < neck.forEvent(0, Role.TARGET_RECT).get(0).value(1), "and above the target on the neck");
			assertFalse(neck.forEvent(0, Role.TECH_PILL).isEmpty());
		}
	}

	@Test
	public void aTremoloPickingIsThreeShortStrokesAboveTheRectangle() {
		SceneFixture f = fixture(n -> n.getEffect().setTremoloPicking(new SongFixture(1).factory().newEffectTremoloPicking()), 5, 7);
		assertEquals(3, coming(f, QualityTier.FULL).forEvent(1, Role.TECH_TICK).size());
		Scene neck = onNeck(f, QualityTier.FULL);
		assertEquals(3, neck.forEvent(0, Role.TECH_TICK).size());
		SceneItem target = neck.forEvent(0, Role.TARGET_RECT).get(0);
		for (SceneItem tick : neck.forEvent(0, Role.TECH_TICK)) {
			assertTrue(Math.max(tick.value(1), tick.value(3)) < target.value(1) - target.value(3) + 1e-9, "above the target");
		}
	}

	@Test
	public void aChordAndAHarmonicShowNoMarkAndTheHarmonicSaysItsWord() {
		SongFixture song = new SongFixture(2);
		song.quarter(1, 0, 3, 5).getEffect().setSlide(true);
		song.chord(1, 1, new int[] { 6, 5 }, new int[] { 3, 3 });
		song.naturalHarmonic(1, 2, 3, 12);
		song.quarter(1, 3, 3, 5).getEffect().setDeadNote(true);
		SceneFixture f = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		Scene scene = f.scene(false).build(f.onset(0) - 1200 * MS, SCREEN, null, 100, QualityTier.FULL);
		assertEquals(0, count(scene, 1), "a chord shows none");
		assertEquals("Harmonic ◇", scene.forEvent(2, Role.EVENT_TEXT).get(0).text(), "the harmonic says what it is");
		assertEquals("Automatic", scene.forEvent(3, Role.EVENT_TEXT).get(0).text(), "a dead note stays automatic");
	}

	@Test
	public void theMarksChangeNeitherTheTargetNorTheMarkerOfTheNote() {
		SceneFixture marked = fixture(n -> n.getEffect().setSlide(true), 5, 7);
		SceneFixture plain = fixture(n -> { }, 5, 7);
		Scene a = onNeck(marked, QualityTier.FULL);
		Scene b = onNeck(plain, QualityTier.FULL);
		for (Role role : new Role[] { Role.MARKER, Role.TARGET_RECT, Role.MARKER_THREAD, Role.MARKER_FOOTPRINT }) {
			List<SceneItem> x = a.forEvent(0, role);
			List<SceneItem> y = b.forEvent(0, role);
			assertEquals(y.size(), x.size(), role.name());
			for (int i = 0; i < y.size(); i++) {
				for (int k = 0; k < 4; k++) {
					assertEquals(y.get(i).value(k), x.get(i).value(k), 1e-9, role + " " + k);
				}
			}
		}
	}

	@Test
	public void theLabelsOfTheMarksOverlapNoOtherLabelWhateverTheMomentAndTheTier() {
		SongFixture song = new SongFixture(3);
		song.quarter(1, 0, 3, 5).getEffect().setHammer(true);
		song.quarter(1, 1, 3, 7).getEffect().setPalmMute(true);
		song.quarter(1, 2, 2, 5).getEffect().setSlide(true);
		song.quarter(1, 3, 2, 8).getEffect().setTapping(true);
		song.quarter(2, 0, 4, 3).getEffect().setVibrato(true);
		SceneFixture f = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		int measured = 0;
		for (QualityTier tier : QualityTier.values()) {
			for (long offset = -2400; offset <= 600; offset += 100) {
				Scene scene = f.scene(false).build(f.onset(0) + offset * MS, SCREEN, null, 100, tier);
				assertEquals(null, SceneBoxes.firstOverlap(SceneBoxes.labels(scene)), tier + " at " + offset);
				measured += scene.withRole(Role.TECH_LABEL).size();
			}
		}
		assertTrue(measured > 20, "technique labels were really measured: " + measured);
	}
}
