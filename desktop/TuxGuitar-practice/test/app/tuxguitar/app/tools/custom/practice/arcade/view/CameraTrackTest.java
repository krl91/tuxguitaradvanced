package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;

/**
 * The anticipatory camera (spec 003 T035, FR-029, SC-010): every target of every visible event in view from its first frame to its judgement, a motion
 * that is continuous and deterministic, bounded zoom, nothing that depends on the order in which times are asked.
 */
public class CameraTrackTest {

	private static final long MS = 1_000_000L;
	private static final NeckModel NECK = NeckModel.of(new TrackTuning(new int[] { 64, 59, 55, 50, 45, 40 }, 0, null), 24);
	private static final CameraTrack.Settings SETTINGS = CameraTrack.Settings.standard();

	/** Events at the given onsets (ms) with the given frets (one note, or a chord when several frets are given). */
	private static List<ArcadeRenderEvent> events(int[][] frets) {
		return events(frets, 100);
	}

	/** Quarter notes (one every 500 ms at 100 %, 167 ms at 300 %) with the given frets: one fret is a note, several a chord. */
	private static List<ArcadeRenderEvent> events(int[][] frets, int percent) {
		SongFixture fixture = new SongFixture(Math.max(1, (frets.length + 3) / 4));
		for (int i = 0; i < frets.length; i++) {
			for (int j = 0; j < frets[i].length; j++) {
				fixture.quarter(i / 4, i % 4, 1 + j, frets[i][j]);
			}
		}
		FollowTimeline timeline = FollowTimeline.build(fixture.song, fixture.sequence(), percent);
		List<app.tuxguitar.app.tools.custom.practice.model.PracticeEvent> practice = fixture.sequence().getEvents();
		List<ArcadeRenderEvent> result = new ArrayList<ArcadeRenderEvent>();
		for (int i = 0; i < practice.size(); i++) {
			result.add(ArcadeRenderEvent.of(i, practice.get(i), timeline));
		}
		return result;
	}

	private static void assertEveryTargetInView(List<ArcadeRenderEvent> events, CameraTrack track) {
		for (ArcadeRenderEvent event : events) {
			for (long t = event.onsetNanos() - SETTINGS.lookAheadNanos; t <= event.onsetNanos() + SETTINGS.holdAfterNanos; t += 10 * MS) {
				Camera camera = track.at(t);
				for (ArcadeRenderEvent.Position position : event.positions()) {
					double x = NECK.fretCentreX(position.fret());
					assertTrue(camera.shows(x, SETTINGS.marginMm), "event " + event.index() + " fret " + position.fret() + " at " + (t - event.onsetNanos()) / MS
							+ " ms from its onset: " + String.format("%.1f..%.1f", camera.left(), camera.right()) + " does not show " + String.format("%.1f", x));
				}
			}
		}
	}

	@Test
	public void everyTargetOfEveryEventIsInViewFromItsFirstFrameToItsJudgementOnGeneratedTimelines() {
		for (long seed = 1; seed <= 12; seed++) {
			Random random = new Random(seed);
			int count = 24;
			int[][] frets = new int[count][];
			for (int i = 0; i < count; i++) {
				frets[i] = new int[1 + (random.nextInt(4) == 0 ? 1 + random.nextInt(3) : 0)];
				int base = random.nextInt(20);
				for (int j = 0; j < frets[i].length; j++) {
					frets[i][j] = Math.min(19, base + random.nextInt(4));
				}
			}
			List<ArcadeRenderEvent> events = events(frets);
			CameraTrack track = CameraTrack.build(events, NECK, SETTINGS);
			assertFalse(track.isUnfittable(), "seed " + seed + ": everything fits in the widest view");
			assertEveryTargetInView(events, track);
		}
	}

	@Test
	public void theGuaranteeHoldsAtTripleSpeedAndAtHalfSpeedToo() {
		int[][] frets = { { 0 }, { 17 }, { 2, 6 }, { 15 }, { 1 }, { 18 }, { 4 }, { 16 }, { 0 }, { 19 }, { 3 }, { 14 } };
		for (int percent : new int[] { 50, 300 }) {
			List<ArcadeRenderEvent> events = events(frets, percent);
			assertEveryTargetInView(events, CameraTrack.build(events, NECK, SETTINGS));
		}
	}

	@Test
	public void aLargeJumpIsAnticipatedBeforeTheEventAppears() {
		int[][] frets = { { 1 }, { 2 }, { 1 }, { 2 }, { 17 }, { 18 }, { 17 }, { 18 } };
		List<ArcadeRenderEvent> events = events(frets);
		CameraTrack track = CameraTrack.build(events, NECK, SETTINGS);

		ArcadeRenderEvent far = events.get(4);
		double x = NECK.fretCentreX(17);
		long appears = far.onsetNanos() - SETTINGS.lookAheadNanos;
		assertTrue(track.at(appears).shows(x, SETTINGS.marginMm), "in view when the note first appears");
		assertTrue(track.at(appears - SETTINGS.spawnLeadNanos / 2).shows(x, SETTINGS.marginMm), "and already before it appears: the camera did not wait");
		assertTrue(track.at(events.get(3).onsetNanos() - 5_000 * MS).centre() < track.at(far.onsetNanos()).centre(), "it panned towards the high frets");
	}

	@Test
	public void theMotionIsContinuousThereIsNoTeleportBetweenTwoSamples() {
		int[][] frets = { { 0 }, { 19 }, { 0 }, { 19 }, { 5 }, { 18 }, { 1 }, { 17 }, { 0 }, { 19 }, { 3 }, { 15 } };
		CameraTrack track = CameraTrack.build(events(frets), NECK, SETTINGS);

		double maxStep = 0.0;
		Camera previous = track.at(-5_000 * MS);
		for (long t = -5_000 * MS; t < 30_000 * MS; t += 10 * MS) {
			Camera now = track.at(t);
			maxStep = Math.max(maxStep, Math.max(Math.abs(now.left() - previous.left()), Math.abs(now.right() - previous.right())));
			previous = now;
		}
		assertTrue(maxStep < 6.0, "no jump between two samples 10 ms apart: " + maxStep + " mm");
		assertTrue(maxStep > 0.0, "and it does move");
	}

	@Test
	public void adjacentFutureTargetsDoNotMakeTheCameraOscillate() {
		int[][] frets = new int[16][];
		for (int i = 0; i < 16; i++) {
			frets[i] = new int[] { 7 + (i % 2) };
		}
		CameraTrack track = CameraTrack.build(events(frets), NECK, SETTINGS);

		double lowest = Double.MAX_VALUE;
		double highest = -Double.MAX_VALUE;
		for (long t = 2_000 * MS; t < 7_000 * MS; t += 10 * MS) {
			lowest = Math.min(lowest, track.at(t).centre());
			highest = Math.max(highest, track.at(t).centre());
		}
		assertTrue(highest - lowest < 1.0, "frets 7 and 8 both fit the same view: the camera stays still (" + (highest - lowest) + " mm)");
	}

	@Test
	public void aWideChordZoomsOutWithinTheBoundAndShowsEveryNote() {
		int[][] frets = { { 3 }, { 0, 12 }, { 4 }, { 3 } };
		List<ArcadeRenderEvent> events = events(frets);
		CameraTrack track = CameraTrack.build(events, NECK, SETTINGS);

		assertFalse(track.isUnfittable());
		assertEveryTargetInView(events, track);
		double minZoom = 1.0;
		for (long t = 0; t < 12_000 * MS; t += 20 * MS) {
			double zoom = track.zoomAt(t);
			assertTrue(zoom >= SETTINGS.minZoom - 1e-9 && zoom <= 1.0 + 1e-9, "zoom within its bounds: " + zoom);
			minZoom = Math.min(minZoom, zoom);
		}
		assertTrue(minZoom < 1.0, "it zoomed out to hold fret 0 and fret 12 together");
	}

	@Test
	public void aDemandBeyondTheWidestViewIsReportedAndNeverHiddenAsASuccess() {
		int[][] frets = { { 0, 24 }, { 3 } };
		CameraTrack.Settings narrow = new CameraTrack.Settings(SETTINGS.lookAheadNanos, SETTINGS.spawnLeadNanos, SETTINGS.holdAfterNanos, SETTINGS.baseSpanMm, 0.7,
				SETTINGS.marginMm, SETTINGS.smoothingNanos, SETTINGS.stepNanos);
		List<ArcadeRenderEvent> events = events(frets);
		CameraTrack track = CameraTrack.build(events, NECK, narrow);

		assertTrue(track.isUnfittable(), "fret 0 and fret 24 together need more than the widest part this camera may show");
		assertEquals(0.7, track.zoomAt(events.get(0).onsetNanos()), 1e-6, "but the zoom stays within its bound");
		// the standard camera shows the open string with the 23rd fret together; the 24th, whose whole cell must be shown (the lane of a fret ends between its two
		// wires, FR-066), is beyond the widest part it may show
		assertFalse(CameraTrack.build(events(new int[][] { { 0, 23 }, { 3 } }), NECK, SETTINGS).isUnfittable(), "the standard camera shows them together");
	}

	@Test
	public void theTrackIsDeterministicAndIndependentOfTheOrderOfTheQuestions() {
		int[][] frets = { { 2 }, { 14 }, { 3 }, { 15 }, { 0, 5 }, { 12 }, { 1 }, { 13 }, { 2 }, { 16 } };
		List<ArcadeRenderEvent> events = events(frets);
		CameraTrack first = CameraTrack.build(events, NECK, SETTINGS);
		CameraTrack second = CameraTrack.build(events, NECK, SETTINGS);

		List<Long> times = new ArrayList<Long>();
		for (long t = -4_000 * MS; t < 25_000 * MS; t += 37 * MS) {
			times.add(t);
		}
		List<Long> shuffled = new ArrayList<Long>(times);
		java.util.Collections.shuffle(shuffled, new Random(7));
		for (long t : shuffled) {
			assertEquals(first.at(t).left(), second.at(t).left(), 0.0);
			assertEquals(first.at(t).right(), second.at(t).right(), 0.0);
		}
		// a restart or a seek is just another question
		assertEquals(first.at(9_000 * MS).left(), first.at(9_000 * MS).left(), 0.0);
	}

	@Test
	public void anEmptySequenceHasAStillCamera() {
		CameraTrack track = CameraTrack.build(new ArrayList<ArcadeRenderEvent>(), NECK, SETTINGS);
		assertEquals(track.at(0).left(), track.at(10_000 * MS).left(), 0.0);
		assertEquals(SETTINGS.baseSpanMm, track.at(0).span(), 1e-9);
	}
}
