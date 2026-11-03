package app.tuxguitar.app.tools.custom.practice.benchmark;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;
import app.tuxguitar.audio.engine.backend.JavaBackend;
import app.tuxguitar.audio.engine.backend.NativeBackend;
import app.tuxguitar.audio.engine.backend.RecognitionBackend;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CaptureClock;
import app.tuxguitar.audio.engine.input.CaptureService;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputDeviceCatalog;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * The Rocksmith cable cannot hear the speakers, so its delay is measured on the owner's playing (spec 023, T055, research R1, SC-002): the cable is
 * opened by both engines at once, a Java backend and a native backend; each note is an onset followed by a pitched frame in each engine within 300 ms
 * of each other; the gap between the Java frame and the native frame of the same onset, both on the {@code System.nanoTime} base of their capture
 * clocks, is reported with each engine's onset-to-pitched delay in {@code recognition-delay.md}. It waits for 20 notes, at most 2 minutes, and
 * fails only if an engine cannot open the input. Nothing is played. Excluded from the default run (it waits for the owner); run it with
 * {@code -Dtest=DualEngineCableProbeTest} and the native library dir.
 */
@Tag("benchmark")
public class DualEngineCableProbeTest {

	private static final int NOTES = 20;
	private static final long PAIR_NANOS = 300_000_000L;
	private static final long LIMIT_NANOS = 120_000_000_000L;

	/** One engine's notes: the wall time of the onset frame and of the first pitched frame after it. */
	private static final class Notes implements AnalysisService.Listener {
		final RecognitionBackend backend;
		final List<long[]> notes = new CopyOnWriteArrayList<long[]>();
		long onsetWall = Long.MIN_VALUE;

		Notes(RecognitionBackend backend) {
			this.backend = backend;
		}

		@Override
		public void analysisFrame(AnalysisFrame frame) {
			CaptureClock clock = this.backend.clock();
			long wall = (frame.getCaptureSampleEnd() == AnalysisFrame.NO_CAPTURE_SAMPLE ? CaptureClock.NOT_READY : clock.wallNanosOf(frame.getCaptureSampleEnd()));
			if( wall == CaptureClock.NOT_READY ) {
				return;
			}
			if( frame.isOnset() ) {
				this.onsetWall = wall;
			}
			if( this.onsetWall != Long.MIN_VALUE && Double.isFinite(frame.getF0Hz()) && frame.getF0Hz() > 0 ) {
				if( wall - this.onsetWall <= PAIR_NANOS ) {
					this.notes.add(new long[] { this.onsetWall, wall });
				}
				this.onsetWall = Long.MIN_VALUE;
			}
		}
	}

	@Test
	public void probe() throws Exception {
		Assertions.assertNull(NativeRecognitionLibrary.load(), "the native library must be built (-Dtuxguitar.native.library.dir)");
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		InputDeviceCatalog catalog = new InputDeviceCatalog();
		CurrentInputSelection selection = CurrentInputSelection.getInstance(context);
		AudioInputDescriptor cable = pickCable(catalog.listInputs());
		Assertions.assertNotNull(cable, "no input listed");
		selection.select(cable, null);

		SampleRingBuffer ring = new SampleRingBuffer(AudioEngine.DEFAULT_RING_BUFFER_CAPACITY);
		RecognitionSettings settings = RecognitionSettings.defaults();
		AnalysisService analysis = new AnalysisService(context, ring, AudioEngine.levelAnalyzer(settings), AudioEngine.articulationDetector(settings),
				new TarsosFastYinEstimator(48000f, TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE, TarsosFastYinEstimator.DEFAULT_HOP_SIZE), 48000, TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE, TarsosFastYinEstimator.DEFAULT_HOP_SIZE);
		CaptureService capture = new CaptureService(context, catalog, selection, ring);
		// the Java stages, at the rate the Java capture negotiates (as AudioEngine without the native engine)
		capture.setFormatListener(format -> analysis.reconfigure(Math.round(format.getSampleRate()), new TarsosFastYinEstimator(format.getSampleRate(), TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE, TarsosFastYinEstimator.DEFAULT_HOP_SIZE),
				ring.getWrittenSampleCount()));
		JavaBackend java = new JavaBackend(capture, analysis, ring);
		NativeBackend nativeBackend = new NativeBackend(context, selection, NativeRecognitionLibrary.Api.JNI, () -> NativeParameters.of(settings));
		Notes javaNotes = new Notes(java);
		Notes nativeNotes = new Notes(nativeBackend);
		List<CaptureService.EventType> javaEvents = new CopyOnWriteArrayList<CaptureService.EventType>();
		java.addCaptureListener(event -> javaEvents.add(event.getType()));
		java.subscribe(javaNotes);
		nativeBackend.subscribe(nativeNotes);
		List<double[]> pairs = new ArrayList<double[]>();
		try {
			nativeBackend.open();
			nativeBackend.acquire("cable-probe");
			java.acquire("cable-probe");
			System.out.println("cable probe: play " + NOTES + " single notes on " + cable.getDisplayName() + ", about one a second");
			long start = System.nanoTime();
			while( pairs.size() < NOTES && System.nanoTime() - start < LIMIT_NANOS ) {
				Assertions.assertFalse(javaEvents.contains(CaptureService.EventType.CAPTURE_ERROR), "the Java capture could not open the input");
				Thread.sleep(200L);
				pairs = pair(javaNotes.notes, nativeNotes.notes);
			}
		} finally {
			java.unsubscribe(javaNotes);
			java.release("cable-probe");
			nativeBackend.release("cable-probe");
			nativeBackend.close();
			TGThreadManager.getInstance(context).dispose();
		}
		List<Double> gaps = new ArrayList<Double>();
		List<Double> javaDelays = new ArrayList<Double>();
		List<Double> nativeDelays = new ArrayList<Double>();
		for( double[] pair : pairs ) {
			gaps.add(pair[0]);
			javaDelays.add(pair[1]);
			nativeDelays.add(pair[2]);
		}
		String section = String.format(Locale.ROOT, "%n## Rocksmith cable, both engines at once (T055), %s%n%nInput: %s. Notes paired: %d of %d.%n%n"
				+ "| Measure (ms) | Median / 90th percentile |%n|---|---|%n| Java pitched frame − native pitched frame | %s |%n"
				+ "| Java onset to pitched | %s |%n| Native onset to pitched | %s |%n",
				LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), cable.getDisplayName(), pairs.size(), NOTES, stats(gaps), stats(javaDelays),
				stats(nativeDelays));
		append(section);
		System.out.println(section);
	}

	/** Notes of both engines whose onsets are within 300 ms: the gap (Java − native pitched), and each engine's onset-to-pitched delay, in ms. */
	private static List<double[]> pair(List<long[]> java, List<long[]> nativeNotes) {
		List<double[]> pairs = new ArrayList<double[]>();
		int from = 0;
		for( long[] j : java ) {
			for( int n = from; n < nativeNotes.size(); n++ ) {
				long[] k = nativeNotes.get(n);
				if( Math.abs(j[0] - k[0]) <= PAIR_NANOS ) {
					pairs.add(new double[] { (j[1] - k[1]) / 1e6, (j[1] - j[0]) / 1e6, (k[1] - k[0]) / 1e6 });
					from = n + 1;
					break;
				}
			}
		}
		return pairs;
	}

	private static AudioInputDescriptor pickCable(List<AudioInputDescriptor> all) {
		for( AudioInputDescriptor input : all ) {
			if( (input.getDeviceName() + " " + input.getDisplayName()).toLowerCase(Locale.ROOT).contains("rocksmith") ) {
				return input;
			}
		}
		return all.isEmpty() ? null : all.get(0);
	}

	private static String stats(List<Double> values) {
		if( values.isEmpty() ) {
			return "-";
		}
		double[] sorted = values.stream().mapToDouble(Double::doubleValue).toArray();
		Arrays.sort(sorted);
		return String.format(Locale.ROOT, "%.1f / %.1f", percentile(sorted, 0.5), percentile(sorted, 0.9));
	}

	private static double percentile(double[] sorted, double p) {
		int index = (int) Math.ceil(p * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
	}

	private static void append(String section) throws Exception {
		Path repository = null;
		for( Path path = Paths.get(System.getProperty("user.dir")).toAbsolutePath(); path != null; path = path.getParent() ) {
			if( Files.isDirectory(path.resolve("specs/023-native-recognition-engine")) ) {
				repository = path;
				break;
			}
		}
		Assertions.assertNotNull(repository, "repository root not found from " + System.getProperty("user.dir"));
		Path output = repository.resolve("specs/023-native-recognition-engine/benchmark/recognition-delay.md");
		Files.createDirectories(output.getParent());
		if( !Files.exists(output) ) {
			Files.write(output, "# Live recognition delay (spec 023, research R1)\n".getBytes(StandardCharsets.UTF_8));
		}
		Files.write(output, section.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
	}
}
