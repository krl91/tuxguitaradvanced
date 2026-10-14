package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.ui.PreRunCheck;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The whole path of the check on the owner's takes (spec 010 T017, SC-001, US1): each take goes through the real analysis loop into the strum meter, and its
 * verdict is given to a {@link PreRunCheck} whose tuning was answered yes. At input volume 0.99 at least 19 of 21 takes block the start (level bad); at 0.40
 * none does, and every take is ready. The takes are the owner's playing and are not committed; the test is skipped when they are absent.
 */
@Tag("dataset")
public class PreRunOwnerTakesTest {

	private static final Path OWNER = Paths.get("../../specs/004-attack-difference-recognition/benchmark");

	private static PreRunCheck.State stateAfter(Path take) {
		RecordedAudio audio = RecordedAudio.load(take);
		RecognitionSettings settings = RecognitionSettings.defaults();
		StrumClipMeter meter = new StrumClipMeter(StrumClipMeter.Parameters.of(settings));
		float[] samples = new float[Math.min(audio.samples.length, 5 * audio.sampleRate)];
		System.arraycopy(audio.samples, 0, samples, 0, samples.length);
		OfflineAnalysis.analyze(samples, audio.sampleRate, settings, false, null, meter);
		PreRunCheck check = new PreRunCheck(FollowSettings.Mode.WAIT);
		check.answerTuned(true);
		check.verdict(meter.verdict());
		return check.state();
	}

	private static List<Path> takes(String folder) throws Exception {
		Path directory = OWNER.resolve(folder);
		if (!Files.isDirectory(directory)) {
			return new ArrayList<Path>();
		}
		try (Stream<Path> files = Files.list(directory)) {
			return files.filter(p -> p.getFileName().toString().endsWith(".wav")).sorted().collect(Collectors.toList());
		}
	}

	@Test
	public void atTheHighGainTheStartIsBlockedForAlmostEveryTakeAndAtTheLowGainForNone() throws Exception {
		List<Path> high = takes("owner-chords");
		List<Path> low = takes("owner-chords-2");
		assumeTrue(high.size() == 21 && low.size() == 21, "the owner's takes are not here (they are not committed)");
		int blocked = 0;
		for (Path take : high) {
			blocked += stateAfter(take) == PreRunCheck.State.LEVEL_BAD ? 1 : 0;
		}
		assertTrue(blocked >= 19, "at gain 0.99 at least 19 of the 21 takes block the start: " + blocked);
		for (Path take : low) {
			assertEquals(PreRunCheck.State.READY, stateAfter(take), take.getFileName() + " at gain 0.40 goes on: no clipping and not too weak");
		}
	}
}
