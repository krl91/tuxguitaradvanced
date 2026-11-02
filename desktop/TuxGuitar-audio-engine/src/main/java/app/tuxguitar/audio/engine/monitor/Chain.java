package app.tuxguitar.audio.engine.monitor;

import java.util.List;

import app.tuxguitar.audio.engine.monitor.fx.Amp;
import app.tuxguitar.audio.engine.monitor.fx.Cabinet;
import app.tuxguitar.audio.engine.monitor.fx.Pedals;

/**
 * Runs the elements of the chain in order on the output thread: the pedals before the amp, the amp, the cabinet, the pedals after it
 * (spec 022, research R3). With no settings ({@code null}) the chain is dry. Every element is made and prepared once, one pedal per kind, so
 * applying settings never allocates (FR-006):
 * <ul>
 * <li>a pedal switched on or off, added or removed crossfades with its dry signal over 10 ms;</li>
 * <li>a knob ramps inside its pedal;</li>
 * <li>a change of order among the pedals that sound dips the chain's output for 10 ms (down in 5 ms, the new order, up in 5 ms), as the amp does
 * for a change of character: one instance per kind cannot run the old and the new order side by side.</li>
 * </ul>
 * The first settings, and the first after a dry chain, apply at once.
 */
final class Chain {

	private static final double FADE_SECONDS = 0.010;
	private static final EffectKind[] KINDS = EffectKind.values();

	private final Amp amp = new Amp();
	private final Cabinet cabinet = new Cabinet();
	private final Pedal[] pedals = new Pedal[KINDS.length];
	private final double[] mix = new double[KINDS.length];
	private final double[] target = new double[KINDS.length];
	private final Section before = new Section();
	private final Section after = new Section();
	private float[] dry;
	private double mixStep;
	private double dipStep;
	private double dip = 1.0;
	private boolean dipping;
	private ChainSettings settings;
	private boolean first = true;

	/** The order of one section, and the order waiting for the bottom of a dip. Arrays made once. */
	private static final class Section {
		final EffectKind[] order = new EffectKind[KINDS.length];
		int count;
		final EffectKind[] next = new EffectKind[KINDS.length];
		int nextCount;
		final EffectKind[] merged = new EffectKind[KINDS.length];
		int mergedCount;
	}

	Chain() {
		for( EffectKind kind : KINDS ) {
			this.pedals[kind.ordinal()] = Pedals.create(kind);
		}
	}

	void prepare(float sampleRate, int maxBlock) {
		for( Pedal pedal : this.pedals ) {
			pedal.prepare(sampleRate, maxBlock);
		}
		this.amp.prepare(sampleRate, maxBlock);
		this.cabinet.prepare(sampleRate, maxBlock);
		this.dry = new float[maxBlock];
		this.mixStep = 1.0 / (FADE_SECONDS * sampleRate);
		this.dipStep = 1.0 / (FADE_SECONDS / 2 * sampleRate);
	}

	/** Output thread only. */
	void apply(ChainSettings value) {
		this.settings = value;
		if( value == null ) {
			this.first = true;
			return;
		}
		for( EffectKind kind : KINDS ) {
			PedalSettings pedal = value.pedal(kind);
			this.target[kind.ordinal()] = pedal != null && pedal.enabled() ? 1.0 : 0.0;
			if( pedal != null ) {
				this.pedals[kind.ordinal()].set(pedal);
			}
		}
		this.amp.set(value.amp());
		this.cabinet.setEnabled(value.amp().cabinet());
		next(this.before, value.before());
		next(this.after, value.after());
		if( this.first ) {
			this.first = false;
			install();
			return;
		}
		boolean keepsOrder = merge(this.before) && merge(this.after);
		if( keepsOrder && !this.dipping ) {
			take(this.before);
			take(this.after);
		} else {
			this.dipping = true;
		}
	}

	private static void next(Section section, List<PedalSettings> pedals) {
		section.nextCount = 0;
		for( PedalSettings pedal : pedals ) {
			section.next[section.nextCount++] = pedal.kind();
		}
	}

	private static int indexOf(EffectKind[] kinds, int count, EffectKind kind) {
		for( int i = 0 ; i < count ; i++ ) {
			if( kinds[i] == kind ) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The new order with the pedals that leave it while still sounding kept at their old places (after the pedal that came before them), so that they fade
	 * out where they are. False when the pedals that stay are in another order: then the chain dips.
	 */
	private boolean merge(Section section) {
		section.mergedCount = 0;
		for( int i = 0 ; i < section.nextCount ; i++ ) {
			section.merged[section.mergedCount++] = section.next[i];
		}
		for( int i = 0 ; i < section.count ; i++ ) {
			EffectKind kind = section.order[i];
			if( indexOf(section.next, section.nextCount, kind) >= 0 || this.mix[kind.ordinal()] == 0.0 ) {
				continue;
			}
			int at = 0;
			if( i > 0 ) {
				int previous = indexOf(section.merged, section.mergedCount, section.order[i - 1]);
				at = previous + 1;
			}
			System.arraycopy(section.merged, at, section.merged, at + 1, section.mergedCount - at);
			section.merged[at] = kind;
			section.mergedCount++;
		}
		int last = -1;
		for( int i = 0 ; i < section.count ; i++ ) {
			int at = indexOf(section.merged, section.mergedCount, section.order[i]);
			if( at < 0 ) {
				continue;
			}
			if( at < last ) {
				return false;
			}
			last = at;
		}
		return true;
	}

	private static void take(Section section) {
		System.arraycopy(section.merged, 0, section.order, 0, section.mergedCount);
		section.count = section.mergedCount;
	}

	/** The new order at once, every pedal at its switch's position (the first settings, or the bottom of a dip). */
	private void install() {
		installOrder(this.before);
		installOrder(this.after);
		for( int k = 0 ; k < KINDS.length ; k++ ) {
			if( this.mix[k] == 0.0 && this.target[k] == 1.0 ) {
				this.pedals[k].reset();
			}
			this.mix[k] = this.target[k];
		}
	}

	private static void installOrder(Section section) {
		System.arraycopy(section.next, 0, section.order, 0, section.nextCount);
		section.count = section.nextCount;
	}

	ChainSettings settings() {
		return this.settings;
	}

	/** True while a pedal is still crossfading, the order is changing or the amp or the cabinet is still switching (for the tests). */
	boolean changing() {
		if( this.dipping || this.dip < 1.0 || this.amp.changing() || this.cabinet.changing() ) {
			return true;
		}
		for( int k = 0 ; k < KINDS.length ; k++ ) {
			if( this.mix[k] != this.target[k] ) {
				return true;
			}
		}
		return false;
	}

	void process(float[] buffer, int n) {
		if( this.settings == null ) {
			return;
		}
		run(this.before, buffer, n);
		this.amp.process(buffer, buffer, n);
		this.cabinet.process(buffer, buffer, n);
		run(this.after, buffer, n);
		if( this.dipping || this.dip < 1.0 ) {
			dip(buffer, n);
		}
	}

	private void run(Section section, float[] buffer, int n) {
		for( int i = 0 ; i < section.count ; i++ ) {
			int k = section.order[i].ordinal();
			double m = this.mix[k];
			double t = this.target[k];
			if( m == 0.0 && t == 0.0 ) {
				continue;
			}
			if( m == 1.0 && t == 1.0 ) {
				this.pedals[k].process(buffer, buffer, n);
				continue;
			}
			if( m == 0.0 ) {
				// it starts from silence: no old tail
				this.pedals[k].reset();
			}
			System.arraycopy(buffer, 0, this.dry, 0, n);
			this.pedals[k].process(buffer, buffer, n);
			for( int s = 0 ; s < n ; s++ ) {
				m = t > m ? Math.min(t, m + this.mixStep) : Math.max(t, m - this.mixStep);
				buffer[s] = (float) (this.dry[s] + (buffer[s] - this.dry[s]) * m);
			}
			this.mix[k] = m;
		}
	}

	/** Down in 5 ms; at the bottom the rest of the block stays silent and the new order comes in; then up in 5 ms. */
	private void dip(float[] buffer, int n) {
		for( int s = 0 ; s < n ; s++ ) {
			if( this.dipping ) {
				this.dip = Math.max(0.0, this.dip - this.dipStep);
			} else {
				this.dip = Math.min(1.0, this.dip + this.dipStep);
			}
			buffer[s] = (float) (buffer[s] * (0.5 - 0.5 * Math.cos(Math.PI * this.dip)));
		}
		if( this.dipping && this.dip == 0.0 ) {
			this.dipping = false;
			install();
		}
	}

	void reset() {
		for( Pedal pedal : this.pedals ) {
			pedal.reset();
		}
		this.amp.reset();
		this.cabinet.reset();
	}
}
