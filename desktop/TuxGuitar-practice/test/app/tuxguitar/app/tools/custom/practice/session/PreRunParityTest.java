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
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.StringColours;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The pre-run check changes nothing of the recognition or the judging (spec 010 FR-011, SC-005, contract invariant 6): the same recorded play on the fake
 * player gives the same judgements, score and result with and without a strum meter fed at every step; the analysis gives the same frames with and without a
 * hop listener; and nothing in the recognition classes or in {@code arcade/core} mentions the meter or the check.
 */
public class PreRunParityTest {

	private static final Viewport SCREEN = new Viewport(1280, 720);
	private static final String[] SOURCES_THAT_MUST_NOT_KNOW = { "src/app/tuxguitar/app/tools/custom/practice/arcade/core", "src/app/tuxguitar/app/tools/custom/practice/session/PracticeSession.java",
			"../TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/pitch", "../TuxGuitar-audio-engine/src/main/java/app/tuxguitar/audio/engine/recognition" };

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

	/** The digest of a run; with {@code meterFed} a strum meter is fed with a clipping signal at every step of the run, as the analysis thread would. */
	private static String digestOf(boolean meterFed) {
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
				() -> 100, new QualityController(16_700_000L), 0L, 0L, FlameSetting.OFF, true, ArcadeScoring.standard());
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
		StrumClipMeter meter = new StrumClipMeter(StrumClipMeter.Parameters.defaults());
		meter.configure(44100);
		float[] hop = new float[256];
		long hops = 0;
		for (long wall = FollowHarness.T0; wall < end; wall += 50 * FollowHarness.MS) {
			run.runUntil(wall);
			if (run.clock.isReady()) {
				source.frame(run.clock.nowNanos(), SCREEN);
			}
			if (meterFed) {
				for (int k = 0; k < 8; k++, hops++) {
					for (int s = 0; s < hop.length; s++) {
						hop[s] = (float) Math.max(-1.0, Math.min(1.0, 1.4 * Math.sin(2 * Math.PI * 196.0 * (hops * hop.length + s) / 44100.0)));
					}
					meter.hop(hop, 0, hop.length);
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
		return digest.toString();
	}

	@Test
	public void theJudgementsTheScoreAndTheResultAreIdenticalWithAndWithoutAStrumMeterFed() {
		String without = digestOf(false);
		String with = digestOf(true);
		assertTrue(without.contains("MATCHED") && without.contains("MISSED"), "the play has good and missed notes:\n" + without);
		assertEquals(without, with, "feeding the strum meter changed a judgement");
	}

	@Test
	public void theAnalysisFramesAreTheSameWithAndWithoutAHopListener() {
		float[] mono = new float[44100 * 3];
		for (int i = 0; i < mono.length; i++) {
			double envelope = Math.exp(-(i % 22050) / 6000.0);
			mono[i] = (float) Math.max(-1.0, Math.min(1.0, envelope * (0.9 * Math.sin(2 * Math.PI * 110.0 * i / 44100.0) + 0.6 * Math.sin(2 * Math.PI * 165.0 * i / 44100.0)
					+ 0.4 * Math.sin(2 * Math.PI * 220.0 * i / 44100.0))));
		}
		RecognitionSettings settings = RecognitionSettings.defaults();
		List<AnalysisFrame> plain = OfflineAnalysis.analyze(mono, 44100, settings, true, null);
		StrumClipMeter meter = new StrumClipMeter(StrumClipMeter.Parameters.of(settings));
		List<AnalysisFrame> listened = OfflineAnalysis.analyze(mono, 44100, settings, true, null, meter);
		assertFalse(plain.isEmpty());
		assertEquals(plain.size(), listened.size());
		for (int i = 0; i < plain.size(); i++) {
			AnalysisFrame a = plain.get(i);
			AnalysisFrame b = listened.get(i);
			assertEquals(a.getTimestampNanos(), b.getTimestampNanos(), "frame " + i);
			assertEquals(Double.doubleToLongBits(a.getRmsDbfs()), Double.doubleToLongBits(b.getRmsDbfs()), "rms of frame " + i);
			assertEquals(Double.doubleToLongBits(a.getF0Hz()), Double.doubleToLongBits(b.getF0Hz()), "f0 of frame " + i);
			assertEquals(Double.doubleToLongBits(a.getConfidence()), Double.doubleToLongBits(b.getConfidence()), "confidence of frame " + i);
			assertEquals(a.isOnset(), b.isOnset(), "onset of frame " + i);
			assertEquals(a.getLevelState(), b.getLevelState(), "level state of frame " + i);
		}
	}

	@Test
	public void nothingInTheRecognitionOrTheJudgingMentionsTheStrumMeterOrTheCheck() throws IOException {
		for (String name : SOURCES_THAT_MUST_NOT_KNOW) {
			Path root = Paths.get(name);
			assertTrue(Files.exists(root), "the source is there: " + root.toAbsolutePath());
			List<Path> files;
			try (Stream<Path> walk = Files.walk(root)) {
				files = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
			}
			assertFalse(files.isEmpty(), name);
			for (Path file : files) {
				String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				for (String word : new String[] { "StrumClipMeter", "PreRunCheck", "StrumVerdict", "HopListener" }) {
					assertFalse(text.contains(word), file + " must not know " + word + ": the recognition cannot depend on the pre-run check");
				}
			}
		}
	}
}
