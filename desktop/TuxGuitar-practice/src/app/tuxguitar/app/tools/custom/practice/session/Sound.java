package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.monitor.ChainSettings;

/**
 * A named amp sound (spec 022 FR-008, FR-009, data-model "Sound"): one of the ready-made ones of {@code ReadyMadeChains}, perhaps changed
 * by the learner, or one of their own.
 */
public final class Sound {

	private final String name;
	private final ChainSettings chain;
	private final boolean readyMade;
	private final boolean changed;

	Sound(String name, ChainSettings chain, boolean readyMade, boolean changed) {
		this.name = name;
		this.chain = chain;
		this.readyMade = readyMade;
		this.changed = changed;
	}

	public String name() {
		return this.name;
	}

	public ChainSettings chain() {
		return this.chain;
	}

	public boolean readyMade() {
		return this.readyMade;
	}

	/** A ready-made sound whose chain is no longer the shipped one; never true for an own sound. */
	public boolean changed() {
		return this.changed;
	}

	@Override
	public String toString() {
		return this.name;
	}
}
