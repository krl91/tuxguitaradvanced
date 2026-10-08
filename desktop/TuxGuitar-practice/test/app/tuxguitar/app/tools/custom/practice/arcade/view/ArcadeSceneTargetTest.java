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
 * The target on the neck (spec 003 FR-060, FR-065, SC-017, SC-023): the active event alone has one filled, fully opaque disk on each written string and
 * fret, of the size the rule gives, centred on the intersection, in the colour of its string, with a contrasting outline of two to four pixels, the same in
 * every quality tier, in every theme and in monochrome, at every window size and density of display; the disks of a chord are all and only its positions.
 */
public class ArcadeSceneTargetTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);
	private static final Viewport[] SIZES = { new Viewport(640, 360), SCREEN, new Viewport(1920, 1080), new Viewport(2560, 1440) };

	/** A note, a chord and notes after them, all on a six-string guitar. */
	private static SceneFixture mixed() {
		SongFixture song = new SongFixture(3);
		song.quarter(1, 0, 3, 5);
		song.chord(1, 1, new int[] { 6, 5, 4, 2 }, new int[] { 3, 5, 5, 4 });
		song.quarter(1, 2, 1, 12);
		song.quarter(1, 3, 6, 0);
		song.quarter(2, 0, 2, 8);
		song.quarter(2, 1, 4, 9);
		song.quarter(2, 2, 5, 7);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static double[] expectedCentre(SceneFixture fixture, long now, Viewport viewport, int tgString, int fret) {
		IsometricProjection projection = new IsometricProjection(viewport);
		ScreenPoint at = projection.neck(fixture.neck.fretCentreX(fret), fixture.neck.layout().rowOf(tgString), fixture.neck.layout().count(), fixture.camera.at(now));
		return new double[] { at.x(), at.y() };
	}

	static double expectedDiameter(Viewport viewport, int strings, double uiScale) {
		IsometricProjection projection = new IsometricProjection(viewport);
		double spacing = Math.abs(projection.stringY(1, strings) - projection.stringY(0, strings));
		return Math.max(24.0 * uiScale, Math.min(42.0 * uiScale, 0.60 * spacing));
	}

	private static List<Scene> everyKind(SceneFixture fixture, long now, ArcadeRun.View run, Viewport viewport, double uiScale) {
		List<Scene> scenes = new ArrayList<Scene>();
		for (Theme theme : Theme.values()) {
			for (boolean mono : new boolean[] { false, true }) {
				for (QualityTier tier : QualityTier.values()) {
					scenes.add(fixture.scene(mono).withTheme(theme).withUiScale(uiScale).build(now, viewport, run, 100, tier));
				}
			}
		}
		return scenes;
	}

	private static double contrast(int a, int b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double luminance(int rgb) {
		double[] c = { ((rgb >> 16) & 0xFF) / 255.0, ((rgb >> 8) & 0xFF) / 255.0, (rgb & 0xFF) / 255.0 };
		for (int i = 0; i < 3; i++) {
			c[i] = c[i] <= 0.03928 ? c[i] / 12.92 : Math.pow((c[i] + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
	}

	@Test
	public void aSingleNoteHasExactlyOneFilledOpaqueDiskCentredOnItsIntersectionWithTheRightSizeColourAndOutline() {
		SceneFixture fixture = mixed();
		long now = fixture.onset(0) - 800 * MS;
		for (Viewport viewport : SIZES) {
			for (double uiScale : new double[] { 1.0, 2.0 }) {
				for (Scene scene : everyKind(fixture, now, null, viewport, uiScale)) {
					List<SceneItem> disks = scene.forEvent(0, Role.TARGET_DISK);
					assertEquals(1, disks.size(), "one disk for a note");
					assertEquals(1, scene.withRole(Role.TARGET_DISK).size(), "and nothing else on the neck");
					SceneItem disk = disks.get(0);
					assertEquals(SceneItem.Shape.DISC, disk.shape());
					assertEquals(1.0, disk.alpha(), 0.0, "fully opaque");
					assertEquals(expectedDiameter(viewport, 6, uiScale), 2.0 * disk.value(2), 1e-9, "the diameter of the rule at " + viewport.width() + " x" + uiScale);
					assertEquals(disk.value(2), disk.value(3), 0.0, "a disk, not an ellipse");
					double[] centre = expectedCentre(fixture, now, viewport, 3, 5);
					assertEquals(centre[0], disk.value(0), 1e-9, "the exact fret");
					assertEquals(centre[1], disk.value(1), 1e-9, "the exact string");
					assertEquals(3, disk.tgString());

					SceneItem outline = scene.forEvent(0, Role.TARGET_OUTLINE).get(0);
					assertEquals(SceneItem.Shape.RING, outline.shape());
					assertTrue(outline.lineWidth() >= 2.0 && outline.lineWidth() <= 4.0, "an outline of 2 to 4 pixels: " + outline.lineWidth());
					assertEquals(1.0, outline.alpha(), 0.0);
					assertEquals(disk.value(2) + outline.lineWidth() / 2.0, outline.value(2), 1e-9, "the outline is just outside the core");
					assertEquals(disk.value(0), outline.value(0), 0.0);
					assertEquals(disk.value(1), outline.value(1), 0.0);
				}
			}
		}
	}

	@Test
	public void theCoreHasTheColourOfItsStringAndTheOutlineStandsOutFromTheNeckInEveryRendering() {
		SceneFixture fixture = mixed();
		long now = fixture.onset(0) - 800 * MS;
		for (Theme theme : Theme.values()) {
			for (boolean mono : new boolean[] { false, true }) {
				Scene scene = fixture.scene(mono).withTheme(theme).build(now, SCREEN, null, 100, QualityTier.FULL);
				SceneItem disk = scene.forEvent(0, Role.TARGET_DISK).get(0);
				SceneItem outline = scene.forEvent(0, Role.TARGET_OUTLINE).get(0);
				SceneItem neck = scene.withRole(Role.NECK_BODY).get(0);
				if (!mono) {
					assertEquals(fixture.colours.of(3), disk.colour(), "the stable colour of the string");
				} else {
					assertTrue(disk.isGrey() && outline.isGrey());
				}
				assertTrue(contrast(outline.colour(), neck.colour()) >= 7.0, theme + " mono=" + mono + ": the outline against the neck: " + contrast(outline.colour(), neck.colour()));
				assertTrue(contrast(disk.colour(), outline.colour()) >= 1.6, "the core is distinct from its outline");
				assertTrue(disk.colour() != neck.colour(), "and from the neck");
			}
		}
	}

	@Test
	public void theTargetIsTheSameInEveryQualityTierAndOnlyTheHaloIsDecoration() {
		SceneFixture fixture = mixed();
		long now = fixture.onset(0) - 800 * MS;
		for (Viewport viewport : SIZES) {
			String reference = null;
			for (QualityTier tier : QualityTier.values()) {
				Scene scene = fixture.scene(false).build(now, viewport, null, 100, tier);
				StringBuilder core = new StringBuilder();
				for (SceneItem item : scene.items()) {
					if (item.role() == Role.TARGET_DISK || item.role() == Role.TARGET_OUTLINE) {
						core.append(item).append('\n');
					}
				}
				if (reference == null) {
					reference = core.toString();
				}
				assertEquals(reference, core.toString(), "the core and its outline do not change with " + tier);
				assertEquals(tier.glow(), !scene.withRole(Role.TARGET_HALO).isEmpty(), "a halo only where there is decoration, at " + tier);
				for (SceneItem halo : scene.withRole(Role.TARGET_HALO)) {
					assertTrue(halo.role().isDecoration() && halo.alpha() < 1.0);
				}
			}
		}
	}

	@Test
	public void anActiveChordHasAllAndOnlyItsWrittenPositionsAsOpaqueDisksOfOneGroup() {
		SceneFixture fixture = mixed();
		long now = fixture.onset(1) - 300 * MS;
		ArcadeRun.View run = ArcadeSceneTest.completedBefore(1, fixture.events.size());
		for (Viewport viewport : SIZES) {
			for (Scene scene : everyKind(fixture, now, run, viewport, 1.0)) {
				List<SceneItem> disks = scene.withRole(Role.TARGET_DISK);
				assertEquals(4, disks.size(), "one disk for each written position, no more");
				Set<Integer> owners = new TreeSet<Integer>();
				Set<String> placed = new TreeSet<String>();
				for (SceneItem disk : disks) {
					owners.add(disk.eventIndex());
					assertEquals(1.0, disk.alpha(), 0.0);
					assertEquals(expectedDiameter(viewport, 6, 1.0), 2.0 * disk.value(2), 1e-9);
					placed.add(disk.tgString() + "@" + Math.round(disk.value(0) * 10) + "," + Math.round(disk.value(1) * 10));
				}
				assertEquals(Set.of(1), owners, "one event owns them all");
				Set<String> expected = new TreeSet<String>();
				int[][] written = { { 6, 3 }, { 5, 5 }, { 4, 5 }, { 2, 4 } };
				for (int[] position : written) {
					double[] c = expectedCentre(fixture, now, viewport, position[0], position[1]);
					expected.add(position[0] + "@" + Math.round(c[0] * 10) + "," + Math.round(c[1] * 10));
				}
				assertEquals(expected, placed, "exactly the written strings and frets");
			}
		}
	}

	@Test
	public void noFutureEventHasADiskAndTheTargetPassesToTheNextEventInOneUpdate() {
		SceneFixture fixture = mixed();
		long now = fixture.onset(0) - 800 * MS;
		Scene scene = fixture.scene(false).build(now, SCREEN, null, 100);
		Set<Integer> owners = new TreeSet<Integer>();
		for (SceneItem item : scene.items()) {
			if (item.role() == Role.TARGET_DISK || item.role() == Role.TARGET_OUTLINE || item.role() == Role.TARGET_HALO) {
				owners.add(item.eventIndex());
			}
		}
		assertEquals(Set.of(0), owners, "the neck belongs to the next event");
		for (int completed = 1; completed < 5; completed++) {
			Scene next = fixture.scene(false).build(now, SCREEN, ArcadeSceneTest.completedBefore(completed, fixture.events.size()), 100);
			Set<Integer> now2 = new TreeSet<Integer>();
			for (SceneItem item : next.items()) {
				if (item.role() == Role.TARGET_DISK) {
					now2.add(item.eventIndex());
				}
			}
			assertEquals(Set.of(completed), now2, "after " + completed + " events the target is that of event " + completed + " alone");
		}
	}

	@Test
	public void theDiskStaysOpaqueWhileTheEventIsActiveThroughImpactAndFeedback() {
		SceneFixture fixture = mixed();
		for (long offset = -2900; offset <= 780; offset += 20) {
			Scene scene = fixture.scene(false).build(fixture.onset(0) + offset * MS, SCREEN, null, 100);
			List<SceneItem> disks = scene.forEvent(0, Role.TARGET_DISK);
			assertEquals(1, disks.size(), "at " + offset);
			assertEquals(1.0, disks.get(0).alpha(), 0.0, "never faded, at " + offset);
		}
	}

	@Test
	public void theTargetIsPaintedAboveTheNeckAndEveryOtherEventItem() {
		SceneFixture fixture = mixed();
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 800 * MS, SCREEN, null, 100);
		int firstDisk = -1;
		int lastBefore = -1;
		for (int i = 0; i < scene.items().size(); i++) {
			SceneItem item = scene.items().get(i);
			if (item.role() == Role.TARGET_HALO || item.role() == Role.TARGET_DISK || item.role() == Role.TARGET_OUTLINE) {
				firstDisk = firstDisk < 0 ? i : firstDisk;
			} else if (item.eventIndex() >= 0 && item.role() != Role.BADGE && item.role() != Role.BADGE_TEXT && item.role() != Role.BADGE_SYMBOL || item.role() == Role.STRING
					|| item.role() == Role.NECK_BODY || item.role() == Role.FRET_WIRE) {
				lastBefore = i;
			}
		}
		assertTrue(firstDisk > lastBefore, "the disk is drawn after the neck and the events: " + firstDisk + " vs " + lastBefore);
		assertFalse(scene.withRole(Role.TARGET_OUTLINE).isEmpty());
	}

	@Test
	public void aFourStringNeckGetsALargerCoreThanASixStringOne() {
		SongFixture song = new SongFixture(2);
		song.quarter(1, 0, 4, 3);
		SceneFixture bass = new SceneFixture(song, TrackTuningFactory.any(43, 38, 33, 28), 100);
		Scene scene = bass.scene(false).build(bass.onset(0) - 500 * MS, SCREEN, null, 100);
		double diameter = 2.0 * scene.forEvent(0, Role.TARGET_DISK).get(0).value(2);
		assertEquals(expectedDiameter(SCREEN, 4, 1.0), diameter, 1e-9);
		assertTrue(diameter > expectedDiameter(SCREEN, 6, 1.0));
		assertTrue(diameter <= 42.0);
	}
}
