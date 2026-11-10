package app.tuxguitar.app.tools.custom.practice.session;

import java.nio.file.Path;

import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;
import app.tuxguitar.audio.engine.monitor.SoundFile;

/**
 * Importing a sound file into the learner's sounds (spec 027, FR-023, contracts/sound-file.md "Import"), the same rule as GuitarFX's {@code sounds.rs}: a new
 * name is added; the name of an own sound changes nothing until the learner chooses to replace it or keep both ("<name> 2", then 3…, shortened to fit); a
 * ready-made name is imported as "<name> (imported)"; a file of a newer format, or not a sound file, is refused and changes nothing.
 */
public final class AmpSoundImport {

	/** The text after a ready-made name, so that an imported sound never replaces a ready-made one. */
	static final String IMPORTED = " (imported)";

	public enum Outcome {
		ADDED, CLASH, REFUSED
	}

	public static final class Result {
		private final Outcome outcome;
		private final String name;
		private final SoundFile.Sound sound;
		private final SoundFile.Problem problem;

		Result(Outcome outcome, String name, SoundFile.Sound sound, SoundFile.Problem problem) {
			this.outcome = outcome;
			this.name = name;
			this.sound = sound;
			this.problem = problem;
		}

		public Outcome outcome() {
			return this.outcome;
		}

		/** The name the sound was added under, or the own sound's name it clashes with; null when refused. */
		public String name() {
			return this.name;
		}

		/** The sound read, for {@link AmpSoundImport#replace} or {@link AmpSoundImport#keepBoth}; null when refused. */
		public SoundFile.Sound sound() {
			return this.sound;
		}

		/** Why the file was refused; null otherwise. */
		public SoundFile.Problem problem() {
			return this.problem;
		}
	}

	private AmpSoundImport() {
	}

	/** The name an imported sound takes: its own, or "<name> (imported)" for a ready-made name. */
	static String importName(String name) {
		ReadyMadeChains.Named ready = null;
		for (ReadyMadeChains.Named named : ReadyMadeChains.all()) {
			if (named.name().equalsIgnoreCase(name.trim())) {
				ready = named;
			}
		}
		return (ready != null ? ready.name() + IMPORTED : name.trim());
	}

	public static Result importFile(AmpSoundStore store, Path file) {
		SoundFile.Sound sound;
		try {
			sound = SoundFile.read(file);
		} catch (SoundFile.ReadException exception) {
			return new Result(Outcome.REFUSED, null, null, exception.problem());
		}
		String name = importName(sound.name());
		if (store.nameProblem(name, null) == AmpSoundStore.NameProblem.TAKEN) {
			return new Result(Outcome.CLASH, store.sound(name).name(), sound, null);
		}
		store.saveAs(name, sound.chain());
		return new Result(Outcome.ADDED, name, sound, null);
	}

	/** The learner chose to replace the own sound of that name; returns its name. */
	public static String replace(AmpSoundStore store, SoundFile.Sound sound) {
		String name = importName(sound.name());
		Sound existing = store.sound(name);
		String kept = (existing != null ? existing.name() : name);
		store.saveAs(kept, sound.chain());
		return kept;
	}

	/** The learner chose to keep both: the import is named "<name> 2", then 3…, the name shortened to fit; returns the name given. */
	public static String keepBoth(AmpSoundStore store, SoundFile.Sound sound) {
		String name = importName(sound.name());
		for (int number = 2;; number++) {
			String suffix = " " + number;
			String base = name.substring(0, Math.min(name.length(), Math.max(0, AmpSoundStore.NAME_LENGTH - suffix.length())));
			String candidate = base.replaceAll("\\s+$", "") + suffix;
			if (store.nameProblem(candidate, null) == null) {
				store.saveAs(candidate, sound.chain());
				return candidate;
			}
		}
	}
}
