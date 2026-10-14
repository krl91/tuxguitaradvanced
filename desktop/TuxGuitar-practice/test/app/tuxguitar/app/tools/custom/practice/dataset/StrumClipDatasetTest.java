package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The strum meter on recordings (spec 010 T010, SC-001, contract invariants 5 and 15): through the real analysis loop ({@link OfflineAnalysis}) and with the
 * real clip level of the recognition settings (0.99), (a) the 180 clean microphone recordings of GuitarSet (first 5 seconds of each: the first strum is all the meter judges)
 * are never "clipping"; (b) the owner's takes at input volume 0.99 are "clipping" for at least 19 of 21; (c) the owner's takes at 0.40 are neither "clipping"
 * nor "too weak". The takes are the owner's playing and are not committed ({@code specs/004-attack-difference-recognition/benchmark/owner-chords*}); GuitarSet is read from
 * {@code ~/tuxguitar-datasets} ({@code -Dtuxguitar.datasets=dir}). The table of the verdicts goes to {@code specs/010-pre-run-check/benchmark/strum-clip-figures.txt}.
 */
@Tag("dataset")
public class StrumClipDatasetTest {

	private static final Path OWNER = Paths.get("../../specs/004-attack-difference-recognition/benchmark");
	private static final Path REPORT = Paths.get("../../specs/010-pre-run-check/benchmark/strum-clip-figures.txt");
	private static final double FIRST_SECONDS = 5.0;

	private static Path datasets() {
		return Paths.get(System.getProperty("tuxguitar.datasets", System.getProperty("user.home") + "/tuxguitar-datasets"));
	}

	/** What the meter says about the start of a recording; {@code floor} false removes the arming floor (to show what it is for). */
	private static StrumClipMeter run(RecordedAudio audio, double seconds, boolean floor) {
		RecognitionSettings settings = RecognitionSettings.defaults();
		StrumClipMeter.Parameters real = StrumClipMeter.Parameters.of(settings);
		StrumClipMeter.Parameters parameters = floor ? real : new StrumClipMeter.Parameters(real.windowNanos, -120.0, real.armQuietHops, real.attackRiseDb, real.clipShare,
				real.clipSample, real.weakDbfs, real.loudDbfs);
		StrumClipMeter meter = new StrumClipMeter(parameters);
		int length = Math.min(audio.samples.length, (int) Math.round(seconds * audio.sampleRate));
		float[] samples = new float[length];
		System.arraycopy(audio.samples, 0, samples, 0, length);
		OfflineAnalysis.analyze(samples, audio.sampleRate, settings, false, null, meter);
		return meter;
	}

	private static List<Path> wavs(Path folder) throws IOException {
		if (!Files.isDirectory(folder)) {
			return Collections.emptyList();
		}
		try (Stream<Path> files = Files.list(folder)) {
			return files.filter(p -> p.getFileName().toString().endsWith(".wav")).sorted().collect(Collectors.toList());
		}
	}

	@Test
	public void theCleanRecordingsOfGuitarSetAreNeverClippingAndTheOwnersTakesAreCaughtAtTheHighGainAndNotAtTheLowOne() throws Exception {
		Path guitarSet = datasets().resolve("guitarset/GuitarSet/audio/audio_mic");
		List<Path> clean = wavs(guitarSet);
		List<Path> high = wavs(OWNER.resolve("owner-chords"));
		List<Path> low = wavs(OWNER.resolve("owner-chords-2"));
		assumeTrue(!clean.isEmpty() || !high.isEmpty(), "neither GuitarSet (" + guitarSet + ") nor the owner's takes are here");
		StringBuilder report = new StringBuilder("Strum meter through OfflineAnalysis, clip level of the recognition settings, spec 010 T010 (clipping at 0.5 % of the 300 ms after the strum)\n\n");

		if (!clean.isEmpty()) {
			assertEquals(180, clean.size(), "the 180 microphone recordings of GuitarSet");
			int clipping = 0;
			int silent = 0;
			double worst = 0.0;
			for (Path file : clean) {
				StrumClipMeter meter = run(RecordedAudio.load(file), FIRST_SECONDS, true);
				if (meter.verdict() == StrumVerdict.CLIPPING) {
					clipping++;
				}
				if (meter.verdict() == StrumVerdict.NO_STRUM) {
					silent++;
				}
				worst = Math.max(worst, meter.clippedShare());
			}
			report.append(String.format(Locale.ROOT, "GuitarSet, %d microphone recordings, first %.0f s of each: clipping %d, no strum found %d, worst clipped share of a first strum %.3f %%%n%n", clean.size(),
					FIRST_SECONDS, clipping, silent, 100.0 * worst));
			assertEquals(0, clipping, "no false warning on clean recordings");
		}

		if (!high.isEmpty()) {
			assertEquals(21, high.size());
			assertEquals(21, low.size());
			int clippingHigh = countAndReport(report, "owner-chords (input volume 0.99)", high, true, StrumVerdict.CLIPPING);
			int clippingNoFloor = countAndReport(new StringBuilder(), "", high, false, StrumVerdict.CLIPPING);
			int clippingLow = countAndReport(report, "owner-chords-2 (input volume 0.40)", low, true, StrumVerdict.CLIPPING);
			int weakLow = countAndReport(new StringBuilder(), "", low, true, StrumVerdict.TOO_WEAK);
			int weakHigh = countAndReport(new StringBuilder(), "", high, true, StrumVerdict.TOO_WEAK);
			report.append(String.format("%nclipping at 0.99: %d of 21; clipping at 0.40: %d of 21; too weak at 0.40: %d of 21, at 0.99: %d of 21%n", clippingHigh, clippingLow, weakLow, weakHigh));
			report.append(String.format("without the arming floor (hop RMS >= -40 dBFS) only %d of 21 at 0.99 are clipping: the meter arms on noise or a click before the strum (research R14)%n", clippingNoFloor));
			write(report);
			assertTrue(clippingHigh >= 19, "at least 19 of the 21 takes at gain 0.99 are clipping: " + clippingHigh);
			assertEquals(0, clippingLow, "no take at gain 0.40 is clipping");
			assertEquals(0, weakLow, "and none is too weak, so the check would not block them");
			assertTrue(clippingNoFloor < clippingHigh, "the floor is what makes the figure: " + clippingNoFloor + " without it, " + clippingHigh + " with it");
		} else {
			write(report);
		}
	}

	private static int countAndReport(StringBuilder report, String title, List<Path> takes, boolean floor, StrumVerdict wanted) throws IOException {
		int count = 0;
		StringBuilder rows = new StringBuilder();
		for (Path take : takes) {
			StrumClipMeter meter = run(RecordedAudio.load(take), 5.0, floor);
			StrumVerdict verdict = meter.verdict();
			count += verdict == wanted ? 1 : 0;
			rows.append(String.format(Locale.ROOT, "  %-12s %-9s clipped %.2f %%  mean %.1f dBFS%n", take.getFileName(), verdict, 100.0 * meter.clippedShare(), meter.rmsDbfs()));
		}
		if (floor && wanted == StrumVerdict.CLIPPING && !title.isEmpty()) {
			report.append(title).append('\n').append(rows).append('\n');
		}
		return count;
	}

	private static void write(StringBuilder report) throws IOException {
		Files.createDirectories(REPORT.getParent());
		Files.write(REPORT, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report);
	}
}
