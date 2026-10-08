package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The quality tiers, the order in which a crowded screen loses detail and the controller that picks the tier (spec 003 T040, FR-053, FR-054, FR-055):
 * only decoration changes with the tier, and the information never does.
 */
public class ArcadeSceneQualityTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);
	private static final long BUDGET = 16_700_000L;

	private static SceneFixture mixed() {
		SongFixture song = new SongFixture(2);
		song.quarter(0, 0, 6, 3);
		song.chord(0, 1, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		song.quarter(0, 2, 2, 8);
		song.chord(0, 3, new int[] { 4, 3, 2 }, new int[] { 2, 2, 2 });
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static List<SceneItem> information(Scene scene) {
		List<SceneItem> kept = new ArrayList<SceneItem>();
		for (SceneItem item : scene.items()) {
			if (!item.role().isDecoration()) {
				kept.add(item);
			}
		}
		return kept;
	}

	@Test
	public void onlyTheDecorationDiffersFromOneTierToTheOtherAndTheInformationIsIdentical() {
		SceneFixture fixture = mixed();
		ArcadeScene scene = fixture.scene(false);
		int fullDecoration = 0;
		int balancedDecoration = 0;
		for (long now = -1000 * MS; now < fixture.onset(3) + 900 * MS; now += 53 * MS) {
			Scene full = scene.build(now, SCREEN, null, 100, QualityTier.FULL);
			Scene balanced = scene.build(now, SCREEN, null, 100, QualityTier.BALANCED);
			Scene performance = scene.build(now, SCREEN, null, 100, QualityTier.PERFORMANCE);
			assertEquals(information(full).toString(), information(balanced).toString(), "balanced at " + now);
			assertEquals(information(full).toString(), information(performance).toString(), "performance at " + now);
			assertEquals(information(performance).size(), performance.items().size(), "the cheapest tier has no decoration at all");
			fullDecoration += full.items().size() - information(full).size();
			balancedDecoration += balanced.items().size() - information(balanced).size();
		}
		assertTrue(fullDecoration > balancedDecoration && balancedDecoration > 0, "full " + fullDecoration + " > balanced " + balancedDecoration + " > none");
	}

	@Test
	public void theTiersAskForLessAndLessFromThePainter() {
		assertTrue(QualityTier.FULL.antialias() && QualityTier.BALANCED.antialias() && !QualityTier.PERFORMANCE.antialias());
		assertEquals(1.0, QualityTier.FULL.fieldResolution(), 0.0);
		assertTrue(QualityTier.PERFORMANCE.fieldResolution() < QualityTier.BALANCED.fieldResolution());
		assertEquals(QualityTier.PERFORMANCE, QualityTier.PERFORMANCE.cheaper());
		assertEquals(QualityTier.FULL, QualityTier.FULL.richer());
	}

	/** A dense passage: notes and chords at 400 quarters a minute played at 300 %, twenty events a second, so that dozens are on screen at once. */
	private static SceneFixture dense() {
		SongFixture song = new SongFixture(16);
		song.tempoChange(0, 400);
		for (int i = 0; i < 64; i++) {
			if (i % 3 == 0) {
				song.chord(i / 4, i % 4, new int[] { 6, 5, 4 }, new int[] { i % 12, 2 + i % 12, 2 + i % 12 });
			} else {
				song.quarter(i / 4, i % 4, 1 + i % 6, i % 15);
			}
		}
		return new SceneFixture(song, SceneFixture.sixStrings(), 300);
	}

	@Test
	public void aCrowdedScreenLosesDetailFromTheFarthestEventsAndNeverFromTheNearOnes() {
		SceneFixture fixture = dense();
		ArcadeScene scene = fixture.scene(false);
		CameraTrack.Settings settings = CameraTrack.Settings.standard();
		boolean crowdedSeen = false;
		for (long now = 0; now < fixture.onset(60); now += 97 * MS) {
			Scene picture = scene.build(now, SCREEN, null, 300);
			int far = 0;
			for (int i = 0; i < fixture.events.size(); i++) {
				ArcadeRenderEvent event = fixture.events.get(i);
				RenderState state = RenderState.of(event.onsetNanos(), now, settings.lookAheadNanos, settings.holdAfterNanos);
				if (state == RenderState.HIDDEN || state == RenderState.EXPIRED) {
					assertTrue(picture.forEvent(i).isEmpty());
					continue;
				}
				boolean isFar = state == RenderState.DISTANT || state == RenderState.READABLE;
				boolean drawn = !picture.forEvent(i, Role.MARKER).isEmpty() || !picture.forEvent(i, Role.TIMING_BAR).isEmpty();
				if (!isFar) {
					assertTrue(drawn, "event " + i + " in state " + state + " is never dropped");
					if (event.kind() == ArcadeRenderEvent.Kind.CHORD) {
						assertFalse(picture.forEvent(i, Role.CARTOUCHE_FRAME).isEmpty(), "its diagram stays");
					} else {
						assertFalse(picture.forEvent(i, Role.TRAJECTORY).isEmpty(), "its trajectory stays");
					}
					continue;
				}
				int rank = far++;
				assertEquals(rank < ArcadeScene.FAR_EVENT_LIMIT, drawn, "far event of rank " + rank);
				if (event.kind() == ArcadeRenderEvent.Kind.CHORD && drawn) {
					assertEquals(rank < ArcadeScene.FAR_CARTOUCHE_LIMIT, !picture.forEvent(i, Role.CARTOUCHE_FRAME).isEmpty(), "diagram of rank " + rank);
				}
				if (event.kind() == ArcadeRenderEvent.Kind.NOTE && drawn) {
					assertEquals(rank < ArcadeScene.FAR_TRAJECTORY_LIMIT, !picture.forEvent(i, Role.TRAJECTORY).isEmpty(), "trajectory of rank " + rank);
					assertFalse(picture.forEvent(i, Role.TARGET_RING).isEmpty(), "the target is kept, it carries the string and the fret");
				}
			}
			crowdedSeen |= far > ArcadeScene.FAR_EVENT_LIMIT;
		}
		assertTrue(crowdedSeen, "the fixture really crowds the screen beyond the last limit");
	}

	@Test
	public void theCurrentEventKeepsEverythingEvenOnTheMostCrowdedScreenAndAtEveryTier() {
		SceneFixture fixture = dense();
		ArcadeScene scene = fixture.scene(false);
		for (QualityTier tier : QualityTier.values()) {
			for (int index : new int[] { 5, 20, 41 }) {
				Scene picture = scene.build(fixture.onset(index), SCREEN, null, 300, tier);
				boolean chord = fixture.events.get(index).kind() == ArcadeRenderEvent.Kind.CHORD;
				assertFalse(picture.forEvent(index, chord ? Role.CARTOUCHE_FRAME : Role.MARKER).isEmpty(), "event " + index + " at " + tier);
				assertFalse(picture.forEvent(index, chord ? Role.TIMING_BAR : Role.TARGET_RING).isEmpty());
			}
		}
	}

	@Test
	public void theVerdictBadgeSurvivesTheDensityRules() {
		SceneFixture fixture = dense();
		ArcadeVerdict[] verdicts = new ArcadeVerdict[fixture.events.size()];
		java.util.Arrays.fill(verdicts, ArcadeVerdict.WAITING);
		verdicts[10] = ArcadeVerdict.ON_TIME;
		Scene picture = fixture.scene(false).build(fixture.onset(10) + 100 * MS, SCREEN, ArcadeSceneTest.viewOf(verdicts), 300, QualityTier.PERFORMANCE);
		assertEquals(1, picture.forEvent(10, Role.BADGE).size());
	}

	@Test
	public void theControllerStaysFullWhileFramesAreCheapAndFallsOneTierAtATimeUnderLoad() {
		QualityController cheap = new QualityController(BUDGET);
		for (int i = 0; i < 5000; i++) {
			assertEquals(QualityTier.FULL, cheap.frame(BUDGET / 4));
		}
		assertEquals(0, cheap.changes());

		QualityController loaded = new QualityController(BUDGET);
		List<QualityTier> seen = new ArrayList<QualityTier>();
		for (int i = 0; i < 40; i++) {
			QualityTier tier = loaded.frame((long) (BUDGET * 1.2));
			if (seen.isEmpty() || seen.get(seen.size() - 1) != tier) {
				seen.add(tier);
			}
		}
		assertEquals(List.of(QualityTier.FULL, QualityTier.BALANCED, QualityTier.PERFORMANCE), seen, "one tier at a time, in order");
		QualityController counted = new QualityController(BUDGET);
		List<Integer> changeFrames = new ArrayList<Integer>();
		QualityTier last = QualityTier.FULL;
		for (int i = 1; i <= 40; i++) {
			QualityTier tier = counted.frame((long) (BUDGET * 1.2));
			if (tier != last) {
				changeFrames.add(i);
				last = tier;
			}
		}
		assertEquals(List.of(QualityController.WINDOW, 2 * QualityController.WINDOW), changeFrames, "each fall is decided on a whole window of frames of the new tier");
		assertEquals(QualityTier.PERFORMANCE, loaded.frame(BUDGET * 5), "never below the cheapest tier");
		assertEquals(2, loaded.changes());
	}

	@Test
	public void aSingleSlowFrameDoesNotChangeTheTier() {
		QualityController controller = new QualityController(BUDGET);
		for (int i = 0; i < 100; i++) {
			controller.frame(BUDGET / 3);
		}
		assertEquals(QualityTier.FULL, controller.frame(BUDGET * 3));
		for (int i = 0; i < 100; i++) {
			assertEquals(QualityTier.FULL, controller.frame(BUDGET / 3));
		}
	}

	@Test
	public void theControllerRecoversSlowlyAndDoesNotOscillateWhenTheRichTierIsTooExpensive() {
		// the cost per tier, as a share of the budget: the full tier is too expensive, the balanced one is comfortable
		double[] cost = { 0.95, 0.40, 0.25 };
		QualityController controller = new QualityController(BUDGET);
		int lastChange = 0;
		int previousChanges = 0;
		List<Integer> gaps = new ArrayList<Integer>();
		for (int frame = 1; frame <= 60_000; frame++) {
			QualityTier tier = controller.tier();
			controller.frame((long) (BUDGET * cost[tier.ordinal()]));
			if (controller.changes() != previousChanges) {
				previousChanges = controller.changes();
				gaps.add(frame - lastChange);
				lastChange = frame;
			}
		}
		// the waits before trying the rich tier again (odd gaps: a fall, then the wait before the recovery) double until the cap
		List<Integer> waits = new ArrayList<Integer>();
		for (int i = 1; i < gaps.size(); i += 2) {
			waits.add(gaps.get(i));
		}
		assertTrue(waits.get(0) >= QualityController.RECOVERY_FRAMES, "the first wait is at least the base wait: " + waits);
		for (int i = 1; i < waits.size(); i++) {
			assertTrue(waits.get(i) >= Math.min(QualityController.MAX_RECOVERY_FRAMES, waits.get(i - 1) * 2) - 1, "the wait doubles until the cap: " + waits);
		}
		assertTrue(waits.get(waits.size() - 1) >= QualityController.MAX_RECOVERY_FRAMES, "and ends at the cap: " + waits);
		int doublings = (int) Math.round(Math.log((double) QualityController.MAX_RECOVERY_FRAMES / QualityController.RECOVERY_FRAMES) / Math.log(2));
		int bound = 2 * (doublings + 1) + 2 * (60_000 / QualityController.MAX_RECOVERY_FRAMES + 1);
		assertTrue(controller.changes() <= bound, "changes " + controller.changes() + " within the bound " + bound + " of a capped exponential backoff");
		assertTrue(controller.changes() < 60_000 / 40, "never one change every few dozen frames");
	}

	@Test
	public void theSameCostsAlwaysGiveTheSameTiers() {
		java.util.Random random = new java.util.Random(3);
		long[] costs = new long[4000];
		for (int i = 0; i < costs.length; i++) {
			costs[i] = (long) (BUDGET * (0.2 + random.nextDouble() * 0.9));
		}
		QualityController a = new QualityController(BUDGET);
		QualityController b = new QualityController(BUDGET);
		for (long cost : costs) {
			assertEquals(a.frame(cost), b.frame(cost));
		}
		assertEquals(a.changes(), b.changes());
	}
}
