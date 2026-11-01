package app.tuxguitar.audio.engine.monitor;

import java.util.List;

import app.tuxguitar.audio.engine.monitor.fx.Amp;
import app.tuxguitar.audio.engine.monitor.fx.Cabinet;
import app.tuxguitar.audio.engine.monitor.fx.NoiseGate;

/**
 * Runs the elements of the chain in order on the output thread: the pedals before the amp, the amp, the cabinet, the pedals after it
 * (spec 022, research R3). An empty slot is a pass-through; with no settings ({@code null}) the chain is dry. Every element is made and
 * prepared once; applying settings only stores targets, so it never allocates.
 *
 * The noise gate is the only pedal so far; the others come with the pedals' user story and are pass-throughs until then.
 */
final class Chain {

	private final NoiseGate gate = new NoiseGate();
	private final Amp amp = new Amp();
	private final Cabinet cabinet = new Cabinet();
	private ChainSettings settings;
	private boolean gateBefore;
	private boolean gateAfter;

	void prepare(float sampleRate, int maxBlock) {
		this.gate.prepare(sampleRate, maxBlock);
		this.amp.prepare(sampleRate, maxBlock);
		this.cabinet.prepare(sampleRate, maxBlock);
	}

	/** Output thread only. */
	void apply(ChainSettings value) {
		this.settings = value;
		if( value == null ) {
			return;
		}
		this.gateBefore = applyGate(value.before());
		this.gateAfter = !this.gateBefore && applyGate(value.after());
		this.amp.set(value.amp());
		this.cabinet.setEnabled(value.amp().cabinet());
	}

	private boolean applyGate(List<PedalSettings> pedals) {
		for( PedalSettings pedal : pedals ) {
			if( pedal.kind() == EffectKind.GATE && pedal.enabled() ) {
				this.gate.set(pedal);
				return true;
			}
		}
		return false;
	}

	ChainSettings settings() {
		return this.settings;
	}

	void process(float[] buffer, int n) {
		if( this.settings == null ) {
			return;
		}
		if( this.gateBefore ) {
			this.gate.process(buffer, buffer, n);
		}
		this.amp.process(buffer, buffer, n);
		this.cabinet.process(buffer, buffer, n);
		if( this.gateAfter ) {
			this.gate.process(buffer, buffer, n);
		}
	}

	void reset() {
		this.gate.reset();
		this.amp.reset();
		this.cabinet.reset();
	}
}
