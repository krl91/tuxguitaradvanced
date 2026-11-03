package app.tuxguitar.audio.engine.nativeengine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

/**
 * The recordings the parity tests run through both engines (spec 023, tasks T016 to T024): the module's own recordings, the owner's chords of spec 004,
 * the owner's own takes (follow, calibration and practice checks, in the home folder) and samples of the datasets (IDMT, GuitarSet) when present. Each
 * absent source is skipped, as the dataset tests do. By default a few files per source, cut to {@link #DEFAULT_SECONDS}; {@code -Dtuxguitar.parity.all=true}
 * takes every file whole.
 */
final class ParityRecordings {

	static final double DEFAULT_SECONDS = 15.0;
	private static final boolean ALL = Boolean.getBoolean("tuxguitar.parity.all");
	private static final Path HOME = Paths.get(System.getProperty("user.home"));
	private static final Path DATASETS = HOME.resolve("tuxguitar-datasets");
	private static final Path SPEC_004 = Paths.get("..", "..", "specs", "004-attack-difference-recognition", "benchmark");

	static final class Recording {
		final String name;
		final float[] mono;
		final int rate;

		Recording(String name, float[] mono, int rate) {
			this.name = name;
			this.mono = mono;
			this.rate = rate;
		}

		@Override
		public String toString() {
			return this.name + " (" + this.rate + " Hz, " + String.format("%.1f", this.mono.length / (double) this.rate) + " s)";
		}
	}

	private static List<Recording> cache;

	private ParityRecordings() {
	}

	/** The native library, or the test is skipped; when the tests were given the crate's folder, a missing library fails instead. */
	static void requireNativeLibrary() {
		String failure = NativeRecognitionLibrary.load();
		String directory = System.getProperty(NativeLibrary.DIRECTORY_PROPERTY);
		if (failure != null && directory != null && !directory.trim().isEmpty()) {
			Assertions.fail("the native library was not loaded from " + directory + ": " + failure);
		}
		Assumptions.assumeTrue(failure == null, "the native library is not loaded (-Dtuxguitar.native.library.dir): " + failure);
	}

	/** Every recording found, in a fixed order. */
	static synchronized List<Recording> all() throws IOException {
		if (cache == null) {
			List<Recording> found = new ArrayList<Recording>();
			add(found, "module", Paths.get("src", "test", "resources", "benchmark"), Integer.MAX_VALUE, null);
			add(found, "owner-chords", SPEC_004.resolve("owner-chords"), Integer.MAX_VALUE, null);
			add(found, "owner-chords-2", SPEC_004.resolve("owner-chords-2"), Integer.MAX_VALUE, null);
			add(found, "follow-takes", HOME.resolve("tuxguitar-follow-takes"), ALL ? Integer.MAX_VALUE : 2, null);
			add(found, "calibration-takes", HOME.resolve("tuxguitar-calibration-takes"), ALL ? Integer.MAX_VALUE : 1, null);
			add(found, "practice-checks", HOME.resolve("tuxguitar-practice-checks"), ALL ? Integer.MAX_VALUE : 4, null);
			add(found, "idmt-guitar", DATASETS.resolve("guitar2"), ALL ? 40 : 4, null);
			add(found, "idmt-effects", DATASETS.resolve("effects"), ALL ? 40 : 4, null);
			add(found, "guitarset", DATASETS.resolve("guitarset"), ALL ? 20 : 2, "_mic.wav");
			cache = found;
		}
		return cache;
	}

	/** The first {@code count} recordings (the module's first), for the costlier stages. */
	static List<Recording> first(int count) throws IOException {
		List<Recording> every = all();
		return every.subList(0, Math.min(count, every.size()));
	}

	private static void add(List<Recording> into, String source, Path folder, int limit, String suffix) throws IOException {
		if (!Files.isDirectory(folder)) {
			return;
		}
		List<Path> files;
		try (Stream<Path> walk = Files.walk(folder)) {
			files = walk.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase().endsWith(suffix != null ? suffix : ".wav")).sorted()
					.collect(Collectors.toList());
		}
		int taken = 0;
		for (Path file : files) {
			if (taken >= limit) {
				break;
			}
			Recording recording = read(source + "/" + folder.relativize(file), file.toFile());
			if (recording != null) {
				into.add(recording);
				taken++;
			}
		}
	}

	/** The file as mono floats (the channels averaged), cut to the default length unless all is asked; null when Java Sound cannot read it. */
	static Recording read(String name, File file) throws IOException {
		try (AudioInputStream source = AudioSystem.getAudioInputStream(file)) {
			AudioFormat format = source.getFormat();
			int channels = format.getChannels();
			int rate = Math.round(format.getSampleRate());
			AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.getSampleRate(), 16, channels, 2 * channels, format.getSampleRate(), false);
			try (AudioInputStream stream = AudioSystem.getAudioInputStream(pcm, source)) {
				byte[] bytes = stream.readAllBytes();
				int frames = bytes.length / (2 * channels);
				int keep = ALL ? frames : (int) Math.min(frames, Math.round(DEFAULT_SECONDS * rate));
				float[] mono = new float[keep];
				for (int i = 0; i < keep; i++) {
					double sum = 0.0;
					for (int c = 0; c < channels; c++) {
						int at = 2 * (i * channels + c);
						sum += (short) ((bytes[at] & 0xFF) | (bytes[at + 1] << 8)) / 32768.0;
					}
					mono[i] = (float) (sum / channels);
				}
				return new Recording(name, mono, rate);
			}
		} catch (UnsupportedAudioFileException | IllegalArgumentException unreadable) {
			return null;
		}
	}
}
