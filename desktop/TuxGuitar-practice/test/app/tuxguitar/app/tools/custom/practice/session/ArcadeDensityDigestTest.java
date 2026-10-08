package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeFrameSource;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeScene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.NeckModel;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxes;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.StringColours;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The visual simplification changes nothing of the judging (spec 003 FR-062, SC-019): the same recorded play of a dense passage is run on the fake player
 * three times, with no picture, with the picture under the density budget and with the unreduced picture, and the complete digest of the judgements and
 * of the result is the same, event by event. Pictures are built at every step of the run, in every tier.
 */
public class ArcadeDensityDigestTest {

	private static final Viewport SCREEN = new Viewport(1280, 720);

	/** Notes and chords on every quarter at 330 quarters a minute played at 100 %: a dense passage with repeated notes, close frets and chords. */
	private static SongFixture passage() {
		SongFixture song = new SongFixture(10);
		song.tempoChange(0, 330);
		for (int i = 0; i < 40; i++) {
			if (i % 5 == 0) {
				song.chord(i / 4, i % 4, new int[] { 6, 5, 4 }, new int[] { 3, 5, 5 });
			} else {
				song.quarter(i / 4, i % 4, i % 3 == 0 ? 3 : 2, 5 + (i % 2));
			}
		}
		return song;
	}

	private static String digestOf(boolean picture, boolean budget) {
		SongFixture song = passage();
		FakePlayerRun run = new FakePlayerRun(song, 100);
		List<PracticeEvent> practice = run.sequence.getEvents();
		ArcadeRun arcade = new ArcadeRun(run.sequence, ArcadeScoring.standard());
		ArcadeFrameSource source = null;
		if (picture) {
			List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
			for (int i = 0; i < practice.size(); i++) {
				events.add(ArcadeRenderEvent.of(i, practice.get(i), run.timeline));
			}
			NeckModel neck = NeckModel.of(SceneFixture.sixStrings(), 24);
			ArcadeScene scene = new ArcadeScene(events, neck, StringColours.forStrings(6), CameraTrack.build(events, neck, CameraTrack.Settings.standard()),
					SceneFixture.ENGLISH, false).withDensityBudget(budget);
			final FakePlayerRun held = run;
			source = new ArcadeFrameSource(scene, run.timeline, FakePlayerRun.BASE_TICK, run.clock, () -> held.last == null ? null : arcade.update(held.last), () -> 100,
					new QualityController(16_700_000L));
		}
		run.start();
		for (int i = 0; i < practice.size(); i++) {
			if (practice.get(i).getKind() == PracticeEvent.Kind.EXPECTED_NOTE && i % 7 != 3) {
				run.playEvent(i, i % 4 == 0 ? 20 : -10);
			}
			if (i % 11 == 5) {
				run.play(run.target(i) + 200 * FollowHarness.MS, 70, 100 * FollowHarness.MS);
			}
		}
		long end = FollowHarness.T0 + run.lead + run.timeline.durationNanos() + 1_500 * FollowHarness.MS;
		int built = 0;
		for (long wall = FollowHarness.T0; wall < end; wall += 50 * FollowHarness.MS) {
			run.runUntil(wall);
			if (source != null && run.clock.isReady()) {
				source.frame(run.clock.nowNanos(), SCREEN);
				built++;
			}
		}
		run.runAll();
		if (source != null) {
			assertTrue(built > 100, "pictures were built throughout the run: " + built);
		}
		StringBuilder digest = new StringBuilder();
		for (FollowSession.EventResult result : run.last.results()) {
			digest.append(result.eventIndex()).append('|').append(result.status()).append('|').append(result.timing()).append('|').append(result.offsetNanos()).append('|')
					.append(java.util.Arrays.toString(result.missingPitches())).append('\n');
		}
		ArcadeResult result = arcade.result(run.last);
		digest.append("extras=").append(run.last.extras().size()).append(" score=").append(result.score()).append(" max=").append(result.maxScore()).append(" best=")
				.append(result.bestStreak()).append(" percent=").append(result.percent());
		return digest.toString();
	}

	@Test
	public void theJudgementsAndTheResultAreIdenticalWithoutAPictureWithTheBudgetAndWithoutTheBudget() {
		String none = digestOf(false, true);
		String reduced = digestOf(true, true);
		String unreduced = digestOf(true, false);
		assertTrue(none.contains("MATCHED") && none.contains("MISSED") && none.contains("WRONG_PITCH"), "the play has good, missed and wrong notes:\n" + none);
		assertEquals(none, reduced, "the picture with the density budget changed a judgement");
		assertEquals(none, unreduced, "the unreduced picture changed a judgement");
	}

	@Test
	public void theFixtureReallyCrowdsTheScreenWhenItIsNotReduced() {
		SongFixture song = passage();
		FakePlayerRun run = new FakePlayerRun(song, 100);
		List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
		List<PracticeEvent> practice = run.sequence.getEvents();
		for (int i = 0; i < practice.size(); i++) {
			events.add(ArcadeRenderEvent.of(i, practice.get(i), run.timeline));
		}
		NeckModel neck = NeckModel.of(SceneFixture.sixStrings(), 24);
		ArcadeScene scene = new ArcadeScene(events, neck, StringColours.forStrings(6), CameraTrack.build(events, neck, CameraTrack.Settings.standard()),
				SceneFixture.ENGLISH, false);
		long now = run.timeline.targetNanos(10);
		assertTrue(SceneBoxes.detailed(scene.withDensityBudget(false).build(now, SCREEN, null, 100)).size() > ArcadeScene.MAX_DETAILED);
		assertTrue(SceneBoxes.detailed(scene.build(now, SCREEN, null, 100)).size() <= ArcadeScene.MAX_DETAILED);
	}
}
