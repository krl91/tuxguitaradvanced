package app.tuxguitar.audio.engine.monitor;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

/**
 * The sound file {@code .guitarfx} (spec 027, contracts/sound-file.md, FR-023), shared with GuitarFX's {@code sound_file.rs}: a comment
 * line, then {@code format=1}, {@code name=…} and {@code chain=…} (the text of {@link ChainSettings#toText()}). The two programs write the
 * same bytes for the same sound (SC-011).
 */
public final class SoundFile {

	public static final String EXTENSION = "guitarfx";
	public static final int FORMAT = 1;
	/** A name's longest length, in characters (as {@code AmpSoundStore.NAME_LENGTH}). */
	public static final int NAME_LENGTH = 40;
	private static final String COMMENT = "# GuitarFX sound";

	/** Why a file was refused: "made by a newer version", or "not a sound file". */
	public enum Problem {
		NEWER, NOT_A_SOUND_FILE
	}

	public static final class ReadException extends Exception {
		private static final long serialVersionUID = 1L;
		private final Problem problem;

		ReadException(Problem problem) {
			super(problem.name());
			this.problem = problem;
		}

		public Problem problem() {
			return this.problem;
		}
	}

	/** A sound read from a file. */
	public static final class Sound {
		private final String name;
		private final ChainSettings chain;

		Sound(String name, ChainSettings chain) {
			this.name = name;
			this.chain = chain;
		}

		public String name() {
			return this.name;
		}

		public ChainSettings chain() {
			return this.chain;
		}
	}

	private SoundFile() {
	}

	/** The sound in that text; knobs out of range are clamped by {@link ChainSettings#parse}. */
	public static Sound read(String text) throws ReadException {
		Properties properties = new Properties();
		try {
			properties.load(new StringReader(text));
		} catch (IOException | IllegalArgumentException e) {
			throw new ReadException(Problem.NOT_A_SOUND_FILE);
		}
		int format;
		try {
			format = Integer.parseInt(String.valueOf(properties.getProperty("format")).trim());
		} catch (NumberFormatException e) {
			throw new ReadException(Problem.NOT_A_SOUND_FILE);
		}
		if (format > FORMAT) {
			throw new ReadException(Problem.NEWER);
		}
		if (format != FORMAT) {
			throw new ReadException(Problem.NOT_A_SOUND_FILE);
		}
		String name = properties.getProperty("name");
		String chainText = properties.getProperty("chain");
		if (name == null || chainText == null) {
			throw new ReadException(Problem.NOT_A_SOUND_FILE);
		}
		name = name.trim();
		int length = name.codePointCount(0, name.length());
		if (length == 0 || length > NAME_LENGTH || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
			throw new ReadException(Problem.NOT_A_SOUND_FILE);
		}
		Optional<ChainSettings> chain = ChainSettings.parse(chainText);
		if (!chain.isPresent()) {
			throw new ReadException(Problem.NOT_A_SOUND_FILE);
		}
		return new Sound(name, chain.get());
	}

	/** A file that cannot be read, or is not UTF-8, is not a sound file. */
	public static Sound read(Path file) throws ReadException {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(file);
		} catch (IOException e) {
			throw new ReadException(Problem.NOT_A_SOUND_FILE);
		}
		try {
			return read(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());
		} catch (java.nio.charset.CharacterCodingException e) {
			throw new ReadException(Problem.NOT_A_SOUND_FILE);
		}
	}

	/** The name escaped as {@link Properties} reads it: {@code \}, {@code =} and {@code :}. */
	private static String escape(String name) {
		StringBuilder out = new StringBuilder();
		for (char c : name.trim().toCharArray()) {
			if (c == '\\' || c == '=' || c == ':') {
				out.append('\\');
			}
			out.append(c);
		}
		return out.toString();
	}

	/** The file's text, as GuitarFX writes it. */
	public static String text(String name, ChainSettings chain) {
		return COMMENT + "\nformat=" + FORMAT + "\nname=" + escape(name) + "\nchain=" + chain.toText() + "\n";
	}

	/** Writes the sound to a temporary file in the same folder, then renames it. */
	public static void write(Path file, String name, ChainSettings chain) throws IOException {
		Path folder = file.toAbsolutePath().getParent();
		Path temporary = Files.createTempFile(folder, "." + file.getFileName(), ".tmp");
		try {
			Files.write(temporary, text(name, chain).getBytes(StandardCharsets.UTF_8));
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
