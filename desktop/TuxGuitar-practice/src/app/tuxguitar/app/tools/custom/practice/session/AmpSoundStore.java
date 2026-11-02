package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;

/**
 * What the amp window keeps from one launch to the next (spec 022, contracts/amp-window.md, data-model "AmpSoundSettings", FR-009), in a
 * configuration module of its own, like the Arcade choices: the settings and the current chain (US1), the named sounds and the sound per
 * score (US3). A missing or unreadable value gives the default: never an error.
 */
public final class AmpSoundStore {

	static final String MODULE = "tuxguitar-practice-amp";
	static final String ENABLED = "enabled";
	static final String OUTPUT = "output";
	static final String GUITAR_VOLUME = "guitar.volume";
	static final String CURRENT_CHAIN = "chain.current";
	static final String WINDOW_BOUNDS = "window.bounds";
	static final String CURRENT_SOUND = "sound.current";
	static final String LAST_SOUND = "sound.last";
	public static final int NAME_LENGTH = 40;

	/** Why a name cannot be given to a sound (contracts/amp-window.md "Names"). */
	public enum NameProblem {
		EMPTY, TOO_LONG, READY_MADE, TAKEN
	}

	private final ArcadeChoices.Persistence persistence;

	public AmpSoundStore(ArcadeChoices.Persistence persistence) {
		this.persistence = persistence;
	}

	/** The store kept in the configuration of the application. */
	public static AmpSoundStore of(TGContext context) {
		return new AmpSoundStore(new ArcadeChoices.Persistence() {
			public String get(String key) {
				return new TGConfigManager(context, MODULE).getStringValue(key);
			}

			public void set(String key, String value) {
				TGConfigManager config = new TGConfigManager(context, MODULE);
				config.setValue(key, value);
				config.save();
			}
		});
	}

	public AmpSoundSettings settings() {
		AmpSoundSettings defaults = AmpSoundSettings.defaults();
		String enabled = read(ENABLED);
		String output = read(OUTPUT);
		return new AmpSoundSettings("true".equals(enabled), output != null ? output : defaults.output(), number(read(GUITAR_VOLUME), defaults.guitarVolume()),
				bounds(read(WINDOW_BOUNDS)));
	}

	public void save(AmpSoundSettings settings) {
		write(ENABLED, Boolean.toString(settings.enabled()));
		write(OUTPUT, settings.output());
		write(GUITAR_VOLUME, Double.toString(settings.guitarVolume()));
		int[] bounds = settings.windowBounds();
		write(WINDOW_BOUNDS, bounds == null ? "" : bounds[0] + "," + bounds[1] + "," + bounds[2] + "," + bounds[3]);
	}

	/** The chain in use, with the learner's unsaved changes; Clean when none was kept or it cannot be read. */
	public ChainSettings currentChain() {
		String text = read(CURRENT_CHAIN);
		return (text == null ? ReadyMadeChains.CLEAN : ChainSettings.parse(text).orElse(ReadyMadeChains.CLEAN));
	}

	/** Keeps the chain in use; while a ready-made sound is current, its changes are kept on it (owner's answer of 2026-11-02). */
	public void saveCurrentChain(ChainSettings chain) {
		write(CURRENT_CHAIN, chain.toText());
		Sound current = current();
		if (current.readyMade()) {
			ChainSettings shipped = ReadyMadeChains.named(current.name());
			write(readyKey(current.name()), chain.equals(shipped) ? "" : chain.toText());
		}
	}

	/** The ready-made sounds in their order, then the own sounds by name, case ignored. */
	public List<Sound> sounds() {
		List<Sound> sounds = new ArrayList<Sound>();
		for (ReadyMadeChains.Named named : ReadyMadeChains.all()) {
			sounds.add(readyMade(named));
		}
		List<Sound> own = ownSounds();
		Collections.sort(own, new Comparator<Sound>() {
			public int compare(Sound a, Sound b) {
				return a.name().compareToIgnoreCase(b.name());
			}
		});
		sounds.addAll(own);
		return sounds;
	}

	/** The sound of that name, case ignored, or null. */
	public Sound sound(String name) {
		if (name == null) {
			return null;
		}
		for (Sound sound : sounds()) {
			if (sound.name().equalsIgnoreCase(name.trim())) {
				return sound;
			}
		}
		return null;
	}

	/** The sound picked last, Clean when none was or it is gone. */
	public Sound current() {
		Sound sound = sound(read(CURRENT_SOUND));
		return (sound != null ? sound : sound("Clean"));
	}

	/** The current chain differs from the current sound's: an own sound's unsaved changes. */
	public boolean currentChanged() {
		return !currentChain().equals(current().chain());
	}

	/** Picks a sound: the current chain becomes its chain, so the unsaved changes of an own sound are dropped. */
	public void setCurrent(String name) {
		Sound sound = sound(name);
		if (sound == null) {
			throw new IllegalArgumentException("no sound named " + name);
		}
		write(CURRENT_SOUND, sound.name());
		write(CURRENT_CHAIN, sound.chain().toText());
	}

	/** What is wrong with that name for a new or renamed sound; {@code except} is the sound being renamed. Null when it can be used. */
	public NameProblem nameProblem(String name, String except) {
		String trimmed = (name == null ? "" : name.trim());
		if (trimmed.isEmpty()) {
			return NameProblem.EMPTY;
		}
		if (trimmed.length() > NAME_LENGTH) {
			return NameProblem.TOO_LONG;
		}
		if (ReadyMadeChains.named(trimmed) != null) {
			return NameProblem.READY_MADE;
		}
		Sound taken = sound(trimmed);
		return (taken != null && (except == null || !taken.name().equalsIgnoreCase(except.trim())) ? NameProblem.TAKEN : null);
	}

	/** Keeps a chain as an own sound; an own sound of the same name, case ignored, is replaced. */
	public void saveAs(String name, ChainSettings chain) {
		NameProblem problem = nameProblem(name, null);
		if (problem != null && problem != NameProblem.TAKEN) {
			throw new IllegalArgumentException(problem + ": " + name);
		}
		List<Sound> own = ownSounds();
		int index = indexOf(own, name.trim());
		Sound sound = new Sound(name.trim(), chain, false, false);
		if (index < 0) {
			own.add(sound);
		} else {
			own.set(index, sound);
		}
		writeOwn(own, own.size());
	}

	public void rename(String from, String to) {
		List<Sound> own = ownSounds();
		int index = indexOf(own, from);
		if (index < 0) {
			throw new IllegalArgumentException("not an own sound: " + from);
		}
		NameProblem problem = nameProblem(to, from);
		if (problem != null) {
			throw new IllegalArgumentException(problem + ": " + to);
		}
		boolean current = own.get(index).name().equalsIgnoreCase(String.valueOf(read(CURRENT_SOUND)));
		own.set(index, new Sound(to.trim(), own.get(index).chain(), false, false));
		writeOwn(own, own.size());
		if (current) {
			write(CURRENT_SOUND, to.trim());
		}
	}

	/** Deletes an own sound; the scores that remember it fall back to the last sound. */
	public void delete(String name) {
		List<Sound> own = ownSounds();
		int index = indexOf(own, name);
		if (index < 0) {
			throw new IllegalArgumentException("not an own sound: " + name);
		}
		int count = own.size();
		own.remove(index);
		writeOwn(own, count);
	}

	/** Brings a ready-made sound back as shipped, and the current chain too when it is the current sound. */
	public void restore(String readyMadeName) {
		ChainSettings shipped = ReadyMadeChains.named(readyMadeName);
		if (shipped == null) {
			throw new IllegalArgumentException("not a ready-made sound: " + readyMadeName);
		}
		Sound sound = sound(readyMadeName);
		write(readyKey(sound.name()), "");
		if (current().name().equals(sound.name())) {
			write(CURRENT_CHAIN, shipped.toText());
		}
	}

	/** The sound remembered for that score, else the last sound used, else Clean. */
	public Sound soundFor(Optional<ScoreKey> key) {
		Sound sound = (key.isPresent() ? sound(read(scoreKey(key.get()))) : null);
		if (sound == null) {
			sound = sound(read(LAST_SOUND));
		}
		return (sound != null ? sound : sound("Clean"));
	}

	/** Remembers the sound picked for that score, and as the last sound used. */
	public void remember(Optional<ScoreKey> key, String name) {
		if (key.isPresent()) {
			write(scoreKey(key.get()), name);
		}
		write(LAST_SOUND, name);
	}

	private Sound readyMade(ReadyMadeChains.Named named) {
		String text = read(readyKey(named.name()));
		ChainSettings chain = (text == null ? named.chain() : ChainSettings.parse(text).orElse(named.chain()));
		return new Sound(named.name(), chain, true, !chain.equals(named.chain()));
	}

	private List<Sound> ownSounds() {
		List<Sound> own = new ArrayList<Sound>();
		for (int n = 0;; n++) {
			String name = read(ownKey(n, "name"));
			if (name == null) {
				return own;
			}
			String text = read(ownKey(n, "chain"));
			ChainSettings fallback = ReadyMadeChains.named(name);
			fallback = (fallback != null ? fallback : ReadyMadeChains.CLEAN);
			own.add(new Sound(name, text == null ? fallback : ChainSettings.parse(text).orElse(fallback), false, false));
		}
	}

	/** Writes the own sounds with no gaps, and clears the places after them up to {@code count}. */
	private void writeOwn(List<Sound> own, int count) {
		for (int n = 0; n < Math.max(count, own.size()); n++) {
			boolean kept = n < own.size();
			write(ownKey(n, "name"), kept ? own.get(n).name() : "");
			write(ownKey(n, "chain"), kept ? own.get(n).chain().toText() : "");
		}
	}

	private static int indexOf(List<Sound> sounds, String name) {
		for (int i = 0; i < sounds.size(); i++) {
			if (name != null && sounds.get(i).name().equalsIgnoreCase(name.trim())) {
				return i;
			}
		}
		return -1;
	}

	private static String ownKey(int n, String part) {
		return "sound." + n + "." + part;
	}

	private static String readyKey(String name) {
		return "ready." + name + ".chain";
	}

	private static String scoreKey(ScoreKey key) {
		return "score." + key.value();
	}

	private String read(String key) {
		try {
			String value = this.persistence.get(key);
			return (value == null || value.trim().isEmpty() ? null : value.trim());
		} catch (RuntimeException exception) {
			// an unreadable configuration keeps the defaults
			return null;
		}
	}

	private void write(String key, String value) {
		try {
			this.persistence.set(key, value);
		} catch (RuntimeException exception) {
			// not kept this time: the window goes on with the value in memory
		}
	}

	private static double number(String text, double fallback) {
		if (text == null) {
			return fallback;
		}
		try {
			double value = Double.parseDouble(text);
			return Double.isNaN(value) ? fallback : Math.max(0, Math.min(1, value));
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static int[] bounds(String text) {
		if (text == null) {
			return null;
		}
		String[] parts = text.split(",");
		if (parts.length != 4) {
			return null;
		}
		int[] bounds = new int[4];
		try {
			for (int i = 0; i < 4; i++) {
				bounds[i] = Integer.parseInt(parts[i].trim());
			}
		} catch (NumberFormatException exception) {
			return null;
		}
		return (bounds[2] > 0 && bounds[3] > 0 ? bounds : null);
	}
}
