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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CaptureClock;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;
import app.tuxguitar.util.properties.TGProperties;
import app.tuxguitar.util.properties.TGPropertiesManager;

/**
 * The live delay probe of spec 023 (research R1, task T003): how long the recognition takes from a tone to its frame, per engine and per input.
 *
 * It plays 20 short plucked tones (A2, D3, G3, B3, E4 in turn, 120 ms, 1.5 s apart) through the default output (Java Sound) while the live
 * {@link AudioEngine} listens on the built-in microphone, then on a USB microphone when one is listed (never the Rocksmith cable, which cannot
 * hear the speakers). The tone's first sample is dated on the wall clock from the output line's frame position, read after every block written.
 * For each tone it measures to (a) the first frame pitched within ±50 cents of the tone and (b) the first onset frame after the tone, twice:
 * when the frame reached the listener (the verdict's moment, the figure of SC-002) and at its capture time ({@code CaptureClock}).
 *
 * The engine is the one of {@code -Dtuxguitar.recognition.engine} (java by default); the settings are the defaults, in memory, never the
 * owner's files. The tones are audible. It only measures: it fails only if no output line or input can be opened. A dated section is appended
 * to {@code specs/023-native-recognition-engine/benchmark/recognition-delay.md}.
 */
@Tag("benchmark")
public class RecognitionDelayProbeTest {

	private static final float OUT_RATE = 48000f;
	private static final double[] TONES_HZ = { 110.0, 146.83, 196.0, 246.94, 329.63 };
	private static final String[] TONE_NAMES = { "A2", "D3", "G3", "B3", "E4" };
	private static final int TONES = 20;
	private static final int TONE_FRAMES = (int) (0.120 * OUT_RATE);
	private static final int SPACING_FRAMES = (int) (1.5 * OUT_RATE);
	private static final int BLOCK_FRAMES = 256;
	private static final long SEARCH_NANOS = 600_000_000L;

	@Test
	public void probe() throws Exception {
		String engineName = System.getProperty("tuxguitar.recognition.engine", "");
		engineName = engineName.trim().isEmpty() ? "java" : engineName.trim();
		TGContext context = newContext();
		AudioEngine engine = AudioEngine.getInstance(context);
		List<AudioInputDescriptor> inputs = pickInputs(engine.getCatalog().listInputs());
		Assertions.assertFalse(inputs.isEmpty(), "no input can be opened: " + engine.getCatalog().listInputs());
		StringBuilder report = new StringBuilder();
		report.append("\n## ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append(", engine ").append(engineName).append("\n\n");
		report.append("- Java ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name")).append(' ')
				.append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch")).append('\n');
		report.append("- ").append(TONES).append(" plucked tones (A2, D3, G3, B3, E4 in turn), 120 ms, 1.5 s apart, through the default output (Java Sound, 48 kHz).\n");
		report.append("- Received: the frame reached the listener. Dated: the frame's last sample on the wall clock (CaptureClock). From the tone's first sample at the output.\n\n");
		report.append("| Input | Tones found (pitch) | Pitch received median / p90 (ms) | Pitch dated median / p90 (ms) | Onsets found | Onset received median / p90 (ms) | Onset dated median / p90 (ms) | Dropped samples |\n");
		report.append("|---|---|---|---|---|---|---|---|\n");
		try {
			for( AudioInputDescriptor input : inputs ) {
				report.append(run(engine, input)).append('\n');
			}
		} finally {
			TGThreadManager.getInstance(context).dispose();
		}
		append(report.toString());
	}

	private static String run(AudioEngine engine, AudioInputDescriptor input) throws Exception {
		List<long[]> received = new CopyOnWriteArrayList<long[]>();
		List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
		AnalysisService.Listener listener = frame -> {
			received.add(new long[] { System.nanoTime() });
			frames.add(frame);
		};
		engine.getSelection().select(input, null);
		long droppedBefore = engine.getDroppedSampleCount();
		engine.subscribe(listener);
		engine.acquire("recognition-delay-probe");
		List<long[]> tones = new ArrayList<long[]>();
		try {
			Thread.sleep(1500L);
			tones = play();
			Thread.sleep(1000L);
		} finally {
			engine.release("recognition-delay-probe");
			engine.unsubscribe(listener);
		}
		long dropped = engine.getDroppedSampleCount() - droppedBefore;
		CaptureClock clock = engine.getCaptureClock();
		List<Double> pitchReceived = new ArrayList<Double>();
		List<Double> pitchDated = new ArrayList<Double>();
		List<Double> onsetReceived = new ArrayList<Double>();
		List<Double> onsetDated = new ArrayList<Double>();
		for( long[] tone : tones ) {
			long start = tone[0];
			double hz = TONES_HZ[(int) tone[1]];
			int pitch = -1;
			int onset = -1;
			for( int i = 0 ; i < frames.size() && (pitch < 0 || onset < 0) ; i++ ) {
				long at = received.get(i)[0];
				if( at < start ) {
					continue;
				}
				if( at > start + SEARCH_NANOS ) {
					break;
				}
				AnalysisFrame frame = frames.get(i);
				double f0 = frame.getF0Hz();
				if( pitch < 0 && !Double.isNaN(f0) && f0 > 0 && Math.abs(1200.0 * Math.log(f0 / hz) / Math.log(2.0)) <= 50.0 ) {
					pitch = i;
				}
				if( onset < 0 && frame.isOnset() ) {
					onset = i;
				}
			}
			if( pitch >= 0 ) {
				pitchReceived.add((received.get(pitch)[0] - start) / 1e6);
				addDated(pitchDated, clock, frames.get(pitch), start);
			}
			if( onset >= 0 ) {
				onsetReceived.add((received.get(onset)[0] - start) / 1e6);
				addDated(onsetDated, clock, frames.get(onset), start);
			}
		}
		String row = "| " + input.getDisplayName() + " | " + pitchReceived.size() + " / " + tones.size() + " | " + stats(pitchReceived) + " | "
				+ stats(pitchDated) + " | " + onsetReceived.size() + " / " + tones.size() + " | " + stats(onsetReceived) + " | " + stats(onsetDated) + " | "
				+ dropped + " |";
		System.out.println("recognition delay probe " + row);
		return row;
	}

	private static void addDated(List<Double> values, CaptureClock clock, AnalysisFrame frame, long start) {
		if( frame.getCaptureSampleEnd() == AnalysisFrame.NO_CAPTURE_SAMPLE ) {
			return;
		}
		long wall = clock.wallNanosOf(frame.getCaptureSampleEnd());
		if( wall != CaptureClock.NOT_READY ) {
			values.add((wall - start) / 1e6);
		}
	}

	/** Plays the tones and returns, for each, its first sample's wall time and its tone index. */
	private static List<long[]> play() throws Exception {
		AudioFormat format = new AudioFormat(OUT_RATE, 16, 1, true, false);
		SourceDataLine out = AudioSystem.getSourceDataLine(format);
		List<long[]> written = new ArrayList<long[]>();
		List<long[]> positions = new ArrayList<long[]>();
		out.open(format, BLOCK_FRAMES * 2 * 8);
		try {
			out.start();
			long total = (long) TONES * SPACING_FRAMES;
			byte[] bytes = new byte[BLOCK_FRAMES * 2];
			for( long frame = 0 ; frame < total ; frame += BLOCK_FRAMES ) {
				Arrays.fill(bytes, (byte) 0);
				for( int i = 0 ; i < BLOCK_FRAMES ; i++ ) {
					long index = frame + i;
					int tone = (int) (index / SPACING_FRAMES);
					long within = index % SPACING_FRAMES;
					if( within == 0 ) {
						written.add(new long[] { index, tone % TONES_HZ.length });
					}
					if( within < TONE_FRAMES ) {
						short value = (short) Math.round(32767 * pluck(TONES_HZ[tone % TONES_HZ.length], within));
						bytes[2 * i] = (byte) value;
						bytes[2 * i + 1] = (byte) (value >> 8);
					}
				}
				out.write(bytes, 0, bytes.length);
				positions.add(new long[] { out.getLongFramePosition(), System.nanoTime() });
			}
			out.drain();
		} finally {
			out.stop();
			out.close();
		}
		List<long[]> tones = new ArrayList<long[]>();
		for( long[] tone : written ) {
			long wall = wallOf(positions, tone[0]);
			if( wall > 0 ) {
				tones.add(new long[] { wall, tone[1] });
			}
		}
		return tones;
	}

	/** The wall time at which the output line's position passed this frame, interpolated between two readings. */
	private static long wallOf(List<long[]> positions, long frame) {
		for( int i = 1 ; i < positions.size() ; i++ ) {
			long[] a = positions.get(i - 1);
			long[] b = positions.get(i);
			if( a[0] <= frame && b[0] >= frame ) {
				if( b[0] == a[0] ) {
					return a[1];
				}
				return a[1] + (long) ((double) (frame - a[0]) / (b[0] - a[0]) * (b[1] - a[1]));
			}
		}
		return -1L;
	}

	/** A plucked tone: the fundamental and three partials, a 2 ms rise and an exponential decay. */
	private static double pluck(double hz, long index) {
		double t = index / OUT_RATE;
		double envelope = Math.min(1.0, t / 0.002) * Math.exp(-t / 0.06);
		double value = Math.sin(2 * Math.PI * hz * t) + 0.5 * Math.sin(4 * Math.PI * hz * t) + 0.3 * Math.sin(6 * Math.PI * hz * t)
				+ 0.2 * Math.sin(8 * Math.PI * hz * t);
		return 0.35 * envelope * value;
	}

	/** The built-in microphone, then one USB microphone; never the Rocksmith cable. */
	private static List<AudioInputDescriptor> pickInputs(List<AudioInputDescriptor> all) {
		List<AudioInputDescriptor> picked = new ArrayList<AudioInputDescriptor>();
		AudioInputDescriptor usb = null;
		for( AudioInputDescriptor input : all ) {
			String name = (input.getDeviceName() + " " + input.getDisplayName()).toLowerCase(Locale.ROOT);
			System.out.println("recognition delay probe input candidate: " + input.getDisplayName());
			if( name.contains("rocksmith") || input.getChannelIndex().isPresent() && input.getChannelIndex().getAsInt() > 0 ) {
				continue;
			}
			if( picked.isEmpty() && (name.contains("macbook") || name.contains("built-in") || name.contains("imac") || name.contains("mac mini")) ) {
				picked.add(input);
			} else if( usb == null && (name.contains("usb") || name.contains("q2u")) ) {
				usb = input;
			}
		}
		if( usb != null ) {
			picked.add(usb);
		}
		return picked;
	}

	private static String stats(List<Double> values) {
		if( values.isEmpty() ) {
			return "-";
		}
		double[] sorted = new double[values.size()];
		for( int i = 0 ; i < sorted.length ; i++ ) {
			sorted[i] = values.get(i);
		}
		Arrays.sort(sorted);
		return String.format(Locale.ROOT, "%.1f / %.1f", percentile(sorted, 0.5), percentile(sorted, 0.9));
	}

	private static double percentile(double[] sorted, double p) {
		int index = (int) Math.ceil(p * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
	}

	private static void append(String section) throws Exception {
		Path repository = null;
		for( Path path = Paths.get(System.getProperty("user.dir")).toAbsolutePath() ; path != null ; path = path.getParent() ) {
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
		System.out.println("recognition delay report: " + output);
	}

	/** A context with the default settings in memory: the owner's configuration files are never read nor written. */
	private static TGContext newContext() {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		TGPropertiesManager manager = TGPropertiesManager.getInstance(context);
		manager.setPropertiesFactory(() -> new MapProperties());
		manager.addPropertiesReader(TGConfigManager.RESOURCE, (properties, module) -> {
		});
		manager.addPropertiesWriter(TGConfigManager.RESOURCE, (properties, module) -> {
		});
		return context;
	}

	private static final class MapProperties implements TGProperties {
		private final Map<String, String> values = new LinkedHashMap<String, String>();

		public String getValue(String key) {
			return this.values.get(key);
		}

		public void setValue(String key, String value) {
			this.values.put(key, value);
		}

		public void remove(String key) {
			this.values.remove(key);
		}

		public void clear() {
			this.values.clear();
		}

		public void update(java.util.Properties newProperties) {
			for( String key : newProperties.stringPropertyNames() ) {
				this.values.put(key, newProperties.getProperty(key));
			}
		}

		public java.util.Set<String> getStringKeys() {
			return this.values.keySet();
		}
	}
}
