package app.tuxguitar.audio.engine.monitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The four ready-made chains (spec 022 FR-008, data-model "Ready-made sounds"), in one place for the engine's tests and for the sounds of
 * the practice window. They are the project's own starting points (FR-018), tuned at the owner's gate.
 */
public final class ReadyMadeChains {

	/** A ready-made chain and its name. */
	public static final class Named {
		private final String name;
		private final ChainSettings chain;

		private Named(String name, ChainSettings chain) {
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

	public static final ChainSettings CLEAN = new ChainSettings(
			Arrays.asList(PedalSettings.of(EffectKind.GATE, true, -70, 150)),
			new AmpSettings(AmpSettings.Character.CLEAN, 3, 5, 5, 6, 6, true),
			Arrays.asList(PedalSettings.of(EffectKind.REVERB, true, 4, 5, 20)));

	public static final ChainSettings CRUNCH = new ChainSettings(
			Arrays.asList(PedalSettings.of(EffectKind.GATE, true, -60, 150)),
			new AmpSettings(AmpSettings.Character.CRUNCH, 6, 6, 5, 6, 6, true),
			Collections.<PedalSettings>emptyList());

	public static final ChainSettings LEAD = new ChainSettings(
			Arrays.asList(PedalSettings.of(EffectKind.GATE, true, -55, 150), PedalSettings.of(EffectKind.COMPRESSOR, true, 4, 10, 5)),
			new AmpSettings(AmpSettings.Character.HIGH_GAIN, 7, 6, 6, 6, 6, true),
			Arrays.asList(PedalSettings.of(EffectKind.ECHO, true, 380, 3, 5, 25)));

	public static final ChainSettings CLEAN_ECHO = new ChainSettings(
			Arrays.asList(PedalSettings.of(EffectKind.COMPRESSOR, true, 5, 10, 5)),
			new AmpSettings(AmpSettings.Character.CLEAN, 3, 5, 5, 6, 6, true),
			Arrays.asList(PedalSettings.of(EffectKind.CHORUS, true, 0.8, 4, 40), PedalSettings.of(EffectKind.ECHO, true, 450, 4, 4, 35),
					PedalSettings.of(EffectKind.REVERB, true, 4, 5, 20)));

	private static final List<Named> ALL = Collections.unmodifiableList(Arrays.asList(new Named("Clean", CLEAN), new Named("Crunch", CRUNCH),
			new Named("Lead", LEAD), new Named("Clean echo", CLEAN_ECHO)));

	private ReadyMadeChains() {
	}

	/** Clean, Crunch, Lead and Clean echo, in that order. */
	public static List<Named> all() {
		return ALL;
	}

	/** The ready-made chain of that name (case ignored), or null. */
	public static ChainSettings named(String name) {
		for( Named named : ALL ) {
			if( named.name.equalsIgnoreCase(name) ) {
				return named.chain;
			}
		}
		return null;
	}
}
