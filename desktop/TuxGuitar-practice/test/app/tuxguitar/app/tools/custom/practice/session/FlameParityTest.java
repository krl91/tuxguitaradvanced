package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.StringColours;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The stage flames change nothing of the judging (spec 009 FR-006, SC-004, contract invariant 8): nothing in {@code arcade/core} knows them, and the same
 * recorded play, run on the fake player with the flames off, discreet and full, gives the same judgements, score, streak and result. The pictures of the
 * flames are built at every step of the run, and they do show flames, so that the check is not an empty one.
 */
public class FlameParityTest {

	private static final Viewport SCREEN = new Viewport(1280, 720);
	private static final Path CORE = Paths.get("src", "app", "tuxguitar", "app", "tools", "custom", "practice", "arcade", "core");

	private static SongFixture passage() {
		SongFixture song = new SongFixture(10);
		song.tempoChange(0, 200);
		for (int i = 0; i < 40; i++) {
			if (i % 5 == 0) {
				song.chord(i / 4, i % 4, new int[] { 6, 5, 4 }, new int[] { 3, 5, 5 });
			} else {
				song.quarter(i / 4, i % 4, i % 3 == 0 ? 3 : 2, 5 + (i % 2));
			}
		}
		return song;
	}

	/** The digest of a run and the number of flame items seen in the pictures built during it. */
	private static String[] digestOf(FlameSetting flames) {
		SongFixture song = passage();
		FakePlayerRun run = new FakePlayerRun(song, 100);
		List<PracticeEvent> practice = run.sequence.getEvents();
		ArcadeRun arcade = new ArcadeRun(run.sequence, ArcadeScoring.standard());
		List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
		for (int i = 0; i < practice.size(); i++) {
			events.add(ArcadeRenderEvent.of(i, practice.get(i), run.timeline));
		}
		NeckModel neck = NeckModel.of(SceneFixture.sixStrings(), 24);
		ArcadeScene scene = new ArcadeScene(events, neck, StringColours.forStrings(6), CameraTrack.build(events, neck, CameraTrack.Settings.standard()), SceneFixture.ENGLISH, false);
		final FakePlayerRun held = run;
		ArcadeFrameSource source = new ArcadeFrameSource(scene, run.timeline, FakePlayerRun.BASE_TICK, run.clock, () -> held.last == null ? null : arcade.update(held.last),
				() -> 100, new QualityController(16_700_000L), 0L, 0L, flames, true, ArcadeScoring.standard());
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
		int flameItems = 0;
		for (long wall = FollowHarness.T0; wall < end; wall += 50 * FollowHarness.MS) {
			run.runUntil(wall);
			if (run.clock.isReady()) {
				Scene picture = source.frame(run.clock.nowNanos(), SCREEN);
				for (SceneItem item : picture.items()) {
					if (item.role() == SceneItem.Role.FLAME_TONGUE || item.role() == SceneItem.Role.FLAME_EMBER) {
						flameItems++;
					}
				}
			}
		}
		run.runAll();
		StringBuilder digest = new StringBuilder();
		for (FollowSession.EventResult result : run.last.results()) {
			digest.append(result.eventIndex()).append('|').append(result.status()).append('|').append(result.timing()).append('|').append(result.offsetNanos()).append('|')
					.append(java.util.Arrays.toString(result.missingPitches())).append('\n');
		}
		ArcadeResult result = arcade.result(run.last);
		digest.append("extras=").append(run.last.extras().size()).append(" score=").append(result.score()).append(" max=").append(result.maxScore()).append(" best=")
				.append(result.bestStreak()).append(" percent=").append(result.percent());
		return new String[] { digest.toString(), Integer.toString(flameItems) };
	}

	@Test
	public void theJudgementsTheScoreAndTheResultAreIdenticalWithTheFlamesOffDiscreetAndFull() {
		String[] off = digestOf(FlameSetting.OFF);
		String[] discreet = digestOf(FlameSetting.DISCREET);
		String[] full = digestOf(FlameSetting.FULL);
		assertTrue(off[0].contains("MATCHED") && off[0].contains("MISSED"), "the play has good and missed notes:\n" + off[0]);
		assertEquals("0", off[1], "no flame item when they are off");
		assertTrue(Integer.parseInt(discreet[1]) > 0 && Integer.parseInt(full[1]) > Integer.parseInt(discreet[1]), "the flames were really built: " + discreet[1] + " and " + full[1]);
		assertEquals(off[0], discreet[0], "the discreet flames changed a judgement");
		assertEquals(off[0], full[0], "the full flames changed a judgement");
	}

	@Test
	public void nothingUnderArcadeCoreMentionsTheFlames() throws IOException {
		assertTrue(Files.isDirectory(CORE), "the sources of arcade/core are there: " + CORE.toAbsolutePath());
		List<Path> files;
		try (Stream<Path> walk = Files.walk(CORE)) {
			files = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
		}
		assertFalse(files.isEmpty());
		for (Path file : files) {
			String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).toLowerCase();
			assertFalse(text.contains("flame"), file + " must not know the flames: the judging cannot depend on them");
		}
	}
}
