package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.monitor.MonitorDevice;
import app.tuxguitar.audio.engine.monitor.MonitorDeviceException;
import app.tuxguitar.audio.engine.monitor.MonitorEngine;
import app.tuxguitar.audio.engine.monitor.MonitorState;
import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;

/**
 * Spec 022 (T061, SC-002, owner's request of 2026-11-01): the dataset benchmarks of this folder give the same outcomes with the amp sound off and with it on.
 * They run twice through their own entry points, unchanged: first alone, then while four amp engines (one per ready-made sound) play real recordings of the
 * datasets through fake duplex devices in the same process. The outcome files of {@code target/dataset-benchmark} must hold the same lines (in any order: the benchmarks
 * analyse the recordings in parallel).
 *
 * <p>Data: {@code -Dtuxguitar.datasets=<dir>} (default {@code ~/tuxguitar-datasets}); skipped with a message when absent. Run with
 * {@code -Dgroups=benchmark -DexcludedGroups= -Dtest=DatasetRecognitionIdentityTest}.</p>
 */
@Tag("benchmark")
public class DatasetRecognitionIdentityTest {

	private static final String[] BENCHMARKS = { "DatasetBenchmarkTest#practiceRecognisesTheAnnotatedRecordingsOfThePublicDatasets",
			"GuitarSetChordBenchmarkTest#completeAndIncompleteChordsOnTheHexaphonicRecordingsOfGuitarSet",
			"TechniqueDatasetBenchmarkTest#theDetectorsAgainstTheAnnotatedExpressionStylesOfIdmtDataset2",
			"OwnerChordsBenchmarkTest#everyChordThatWasPlayedIsAcceptedAndAnotherChordIsNot" };

	/** The files that hold outcomes only (the other reports also hold timings). */
	private static final List<String> OUTCOMES = Arrays.asList("outcomes.csv", "report.txt", "guitarset-chords-outcomes.csv", "technique-report.txt",
			"owner-chords.txt");

	private static final Path TARGET = Paths.get("target", "dataset-benchmark");

	/** A duplex device that plays a recording in a loop as the guitar's input, at the pace of real time, and throws the output away. */
	private static final class LoopDevice implements MonitorDevice {
		private final float[] recording;
		private final float rate;

		LoopDevice(float[] recording, float rate) {
			this.recording = recording;
			this.rate = rate;
		}

		public Opened open(String inputName, int inputChannel, String outputName, int blockFrames) {
			return new Opened() {
				private int position;
				private long next = System.nanoTime();

				public float sampleRate() {
					return LoopDevice.this.rate;
				}

				public int read(float[] block, int n) throws MonitorDeviceException {
					this.next += Math.round(n * 1_000_000_000.0 / LoopDevice.this.rate);
					long wait = this.next - System.nanoTime();
					if (wait > 0) {
						try {
							Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
						} catch (InterruptedException exception) {
							Thread.currentThread().interrupt();
							throw new MonitorDeviceException("interrupted");
						}
					}
					for (int i = 0; i < n; i++) {
						block[i] = LoopDevice.this.recording[this.position];
						this.position = (this.position + 1) % LoopDevice.this.recording.length;
					}
					return n;
				}

				public void write(float[] left, float[] right, int n) {
				}

				public int estimatedLatencyFrames() {
					return 3 * blockFrames;
				}

				public long droppedBlocks() {
					return 0;
				}

				public void close() {
				}
			};
		}
	}

	private static Path root() {
		return Paths.get(System.getProperty("tuxguitar.datasets", System.getProperty("user.home") + "/tuxguitar-datasets"));
	}

	@Test
	public void theDatasetOutcomesAreTheSameWithTheAmpSoundOffAndOn() throws Exception {
		Assumptions.assumeTrue(Files.isDirectory(root().resolve("guitar2")) && Files.isDirectory(root().resolve("guitarset")),
				"the datasets are not in " + root() + " (GuitarSet and IDMT-SMT-GUITAR_V2): skipped");
		Map<String, List<String>> off = runAll();
		List<MonitorEngine> engines = startEngines();
		Map<String, List<String>> on;
		try {
			on = runAll();
			for (MonitorEngine engine : engines) {
				assertEquals(MonitorState.ON, engine.state(), "every amp engine played during the second pass");
			}
		} finally {
			for (MonitorEngine engine : engines) {
				engine.stop();
			}
		}
		List<String> report = new ArrayList<String>();
		for (String file : OUTCOMES) {
			assertTrue(off.containsKey(file), file + " was written by the benchmarks");
			assertEquals(off.get(file), on.get(file), file + ": the same outcomes with the amp sound off and on");
			report.add(file + ": " + off.get(file).size() + " lines, identical");
		}
		Files.write(TARGET.resolve("amp-identity.txt"), report, StandardCharsets.UTF_8);
	}

	private static Map<String, List<String>> runAll() throws Exception {
		for (String benchmark : BENCHMARKS) {
			String[] parts = benchmark.split("#");
			Class<?> type = Class.forName(DatasetRecognitionIdentityTest.class.getPackage().getName() + "." + parts[0]);
			Method method = type.getMethod(parts[1]);
			try {
				method.invoke(type.getConstructor().newInstance());
			} catch (InvocationTargetException exception) {
				// a benchmark's own guard may fail: its outcome files are written before, and they are what is compared
				if (!(exception.getCause() instanceof AssertionError)) {
					throw exception;
				}
			}
		}
		Map<String, List<String>> outcomes = new LinkedHashMap<String, List<String>>();
		for (String file : OUTCOMES) {
			Path path = TARGET.resolve(file);
			if (Files.exists(path)) {
				// the benchmarks analyse the recordings in parallel: the order of their rows changes from one run to the next, not the rows
				List<String> lines = new ArrayList<String>(Files.readAllLines(path, StandardCharsets.UTF_8));
				java.util.Collections.sort(lines);
				outcomes.put(file, lines);
			}
		}
		return outcomes;
	}

	/** One engine per ready-made sound, each playing a recording of IDMT dataset 3 through its own fake device. */
	private static List<MonitorEngine> startEngines() throws Exception {
		File[] recordings = root().resolve("guitar2/IDMT-SMT-GUITAR_V2/dataset3/audio").toFile().listFiles((dir, name) -> name.endsWith(".wav"));
		Assumptions.assumeTrue(recordings != null && recordings.length > 0, "no recordings in IDMT dataset 3: skipped");
		Arrays.sort(recordings);
		List<MonitorEngine> engines = new ArrayList<MonitorEngine>();
		int index = 0;
		for (ReadyMadeChains.Named named : ReadyMadeChains.all()) {
			File file = recordings[index++ % recordings.length];
			float[] samples = mono(file);
			MonitorEngine engine = new MonitorEngine(new LoopDevice(samples, rate(file)), MonitorEngine.BLOCK_FRAMES, System::nanoTime, runnable -> {
				Thread thread = new Thread(runnable, "amp-identity-" + named.name());
				thread.setDaemon(true);
				thread.start();
			}, Runnable::run);
			engine.setChain(named.chain());
			engine.start("", -1, "");
			engines.add(engine);
		}
		long deadline = System.currentTimeMillis() + 5000;
		for (MonitorEngine engine : engines) {
			while (engine.state() != MonitorState.ON && System.currentTimeMillis() < deadline) {
				Thread.sleep(20);
			}
		}
		return engines;
	}

	private static float rate(File file) throws Exception {
		try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
			return stream.getFormat().getSampleRate();
		}
	}

	/** The first channel of a 16-bit PCM recording, as floats. */
	private static float[] mono(File file) throws Exception {
		try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
			AudioFormat format = stream.getFormat();
			byte[] bytes = stream.readAllBytes();
			int frameSize = format.getFrameSize();
			int frames = bytes.length / frameSize;
			float[] samples = new float[frames];
			for (int i = 0; i < frames; i++) {
				int offset = i * frameSize;
				int low = bytes[offset + (format.isBigEndian() ? 1 : 0)] & 0xFF;
				int high = bytes[offset + (format.isBigEndian() ? 0 : 1)];
				samples[i] = ((high << 8) | low) / 32768f;
			}
			return samples;
		}
	}
}
