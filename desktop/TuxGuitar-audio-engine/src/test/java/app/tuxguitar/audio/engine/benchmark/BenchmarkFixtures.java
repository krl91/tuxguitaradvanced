package app.tuxguitar.audio.engine.benchmark;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.PitchReading;

final class BenchmarkFixtures {

	static final int SAMPLE_RATE = 48000;
	static final int WINDOW_SIZE = 2048;
	static final int HOP_SIZE = 256;
	static final double CONFIDENCE_GATE = 0.80;
	static final int STABLE_FRAMES = 20;
	static final double ONSET_DBFS = -45.0;
	static final double PITCH_FLOOR_DBFS = -35.0;
	static final int MAX_OCTAVE_FRAMES = 20;
	static final double MAX_STABLE_LATENCY_SECONDS = 0.75;

	private static final String RESOURCE_ROOT = "benchmark/";

	private BenchmarkFixtures() {
	}

	static List<Fixture> readManifest() throws IOException {
		List<String> lines = readLines(RESOURCE_ROOT + "manifest.csv");
		if (lines.isEmpty()) {
			throw new IOException("benchmark manifest is empty");
		}
		List<String> headers = parseCsvLine(lines.get(0));
		Map<String, Integer> positions = new HashMap<>();
		for (int i = 0; i < headers.size(); i++) {
			positions.put(headers.get(i), i);
		}
		List<Fixture> fixtures = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			if (lines.get(i).isEmpty()) {
				continue;
			}
			List<String> values = parseCsvLine(lines.get(i));
			fixtures.add(new Fixture(
					value(values, positions, "file"),
					value(values, positions, "input_type"),
					value(values, positions, "fixture_role"),
					value(values, positions, "played_note"),
					parseOptionalInteger(value(values, positions, "midi")),
					value(values, positions, "sha256")));
		}
		return Collections.unmodifiableList(fixtures);
	}

	static Recording readRecording(Fixture fixture)
			throws IOException, UnsupportedAudioFileException {
		byte[] resourceBytes = readResource(RESOURCE_ROOT + fixture.file);
		String actualSha256 = sha256(resourceBytes);
		try (AudioInputStream stream = AudioSystem.getAudioInputStream(
				new java.io.ByteArrayInputStream(resourceBytes))) {
			AudioFormat format = stream.getFormat();
			if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
					|| format.getSampleRate() != SAMPLE_RATE
					|| format.getSampleSizeInBits() != 16
					|| format.getChannels() != 1
					|| format.isBigEndian()) {
				throw new IOException("unsupported benchmark format for " + fixture.file + ": " + format);
			}
			byte[] pcm = readAll(stream);
			float[] samples = new float[pcm.length / 2];
			for (int i = 0; i < samples.length; i++) {
				int low = pcm[i * 2] & 0xff;
				int high = pcm[i * 2 + 1];
				samples[i] = (short) (low | (high << 8)) / 32768f;
			}
			return new Recording(samples, actualSha256);
		}
	}

	static Result analyze(Recording recording) {
		PitchEstimator estimator = new TarsosFastYinEstimator(SAMPLE_RATE, WINDOW_SIZE, HOP_SIZE);
		NoteInterpreter interpreter = new NoteInterpreter(CONFIDENCE_GATE);
		InputAssessment assessment = new InputAssessment(
				InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
		float[] window = new float[WINDOW_SIZE];
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		int onsetFrame = -1;
		int stableMidi = -1;
		int stableFrame = -1;
		int runMidi = -1;
		int runLength = 0;
		List<Integer> reliableMidis = new ArrayList<>();

		for (int offset = 0, frame = 0;
				offset + WINDOW_SIZE <= recording.samples.length;
				offset += HOP_SIZE, frame++) {
			System.arraycopy(recording.samples, offset, window, 0, WINDOW_SIZE);
			double levelDbfs = rmsDbfs(window);
			if (onsetFrame < 0 && levelDbfs >= ONSET_DBFS) {
				onsetFrame = frame;
			}
			PitchEstimate estimate = estimator.estimate(window);
			PitchReading reading = interpreter.interpret(estimate, assessment);
			if (!reading.isReliable() || levelDbfs < PITCH_FLOOR_DBFS) {
				runMidi = -1;
				runLength = 0;
				continue;
			}
			int midi = reading.getMidi();
			reliableMidis.add(midi);
			counts.put(midi, counts.getOrDefault(midi, 0) + 1);
			if (midi == runMidi) {
				runLength++;
			} else {
				runMidi = midi;
				runLength = 1;
			}
			if (stableFrame < 0 && runLength >= STABLE_FRAMES) {
				stableMidi = midi;
				stableFrame = frame;
			}
		}

		int dominantMidi = -1;
		int dominantCount = 0;
		for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
			if (entry.getValue() > dominantCount) {
				dominantMidi = entry.getKey();
				dominantCount = entry.getValue();
			}
		}
		return new Result(onsetFrame, stableFrame, stableMidi, dominantMidi, reliableMidis);
	}

	private static double rmsDbfs(float[] samples) {
		double sumSquares = 0.0;
		for (float sample : samples) {
			sumSquares += sample * sample;
		}
		double rms = Math.sqrt(sumSquares / samples.length);
		return rms == 0.0 ? Double.NEGATIVE_INFINITY : 20.0 * Math.log10(rms);
	}

	private static List<String> readLines(String resource) throws IOException {
		String text = new String(readResource(resource), StandardCharsets.UTF_8);
		String[] split = text.split("\\R");
		List<String> lines = new ArrayList<>();
		Collections.addAll(lines, split);
		return lines;
	}

	private static byte[] readResource(String resource) throws IOException {
		ClassLoader loader = BenchmarkFixtures.class.getClassLoader();
		try (InputStream stream = loader.getResourceAsStream(resource)) {
			if (stream == null) {
				throw new IOException("missing benchmark resource: " + resource);
			}
			return readAll(stream);
		}
	}

	private static byte[] readAll(InputStream stream) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		for (int read; (read = stream.read(buffer)) >= 0;) {
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static String sha256(byte[] value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
			StringBuilder result = new StringBuilder(digest.length * 2);
			for (byte item : digest) {
				result.append(String.format("%02x", item & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static List<String> parseCsvLine(String line) {
		List<String> fields = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char character = line.charAt(i);
			if (character == '"') {
				if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				} else {
					quoted = !quoted;
				}
			} else if (character == ',' && !quoted) {
				fields.add(field.toString());
				field.setLength(0);
			} else {
				field.append(character);
			}
		}
		fields.add(field.toString());
		return fields;
	}

	private static String value(List<String> values, Map<String, Integer> positions, String name)
			throws IOException {
		Integer position = positions.get(name);
		if (position == null || position >= values.size()) {
			throw new IOException("missing benchmark manifest field: " + name);
		}
		return values.get(position);
	}

	private static Integer parseOptionalInteger(String value) {
		return value.isEmpty() ? null : Integer.valueOf(value);
	}

	static final class Fixture {
		final String file;
		final String inputType;
		final String role;
		final String playedNote;
		final Integer manifestMidi;
		final String sha256;

		Fixture(String file, String inputType, String role, String playedNote,
				Integer manifestMidi, String sha256) {
			this.file = file;
			this.inputType = inputType;
			this.role = role;
			this.playedNote = playedNote;
			this.manifestMidi = manifestMidi;
			this.sha256 = sha256;
		}
	}

	static final class Recording {
		final float[] samples;
		final String sha256;

		Recording(float[] samples, String sha256) {
			this.samples = samples;
			this.sha256 = sha256;
		}
	}

	static final class Result {
		final int onsetFrame;
		final int stableFrame;
		final int stableMidi;
		final int dominantMidi;
		final List<Integer> reliableMidis;

		Result(int onsetFrame, int stableFrame, int stableMidi, int dominantMidi,
				List<Integer> reliableMidis) {
			this.onsetFrame = onsetFrame;
			this.stableFrame = stableFrame;
			this.stableMidi = stableMidi;
			this.dominantMidi = dominantMidi;
			this.reliableMidis = Collections.unmodifiableList(reliableMidis);
		}

		double stableLatencySeconds() {
			if (onsetFrame < 0 || stableFrame < 0) {
				return Double.NaN;
			}
			return (stableFrame - onsetFrame) * HOP_SIZE / (double) SAMPLE_RATE;
		}

		long octaveFrames(int expectedMidi) {
			return reliableMidis.stream()
					.filter(midi -> Math.abs(midi - expectedMidi) == 12)
					.count();
		}

		int longestOctaveRun(int expectedMidi) {
			int longest = 0;
			int current = 0;
			for (int midi : reliableMidis) {
				if (Math.abs(midi - expectedMidi) == 12) {
					current++;
					longest = Math.max(longest, current);
				} else {
					current = 0;
				}
			}
			return longest;
		}
	}
}
