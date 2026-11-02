package app.tuxguitar.audio.engine.monitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Every change of the chain is heard at once and without a click (spec 022 FR-006, SC-003; task T032). A sine runs through the chain; half
 * way the settings change. The largest step from one sample to the next stays under 1.5 times the largest step of the same window with the
 * old settings alone and with the new settings alone, and the chain is done changing within 0.1 s.
 */
public class SmoothChangesTest {

	private static final int RATE = 48000;
	private static final int BLOCK = 128;
	private static final int BLOCKS = RATE / BLOCK;
	private static final int CHANGE = BLOCKS / 2;
	private static final int FROM = BLOCKS / 4;

	private static List<PedalSettings> defaults(EffectKind.Section section) {
		List<PedalSettings> pedals = new ArrayList<PedalSettings>();
		for( EffectKind kind : EffectKind.values() ) {
			if( kind.section() == section ) {
				pedals.add(PedalSettings.defaults(kind));
			}
		}
		return pedals;
	}

	/** Every pedal on at its defaults. */
	static ChainSettings full() {
		return new ChainSettings(defaults(EffectKind.Section.BEFORE), AmpSettings.defaults(), defaults(EffectKind.Section.AFTER));
	}

	private static List<PedalSettings> replaced(List<PedalSettings> pedals, PedalSettings pedal) {
		List<PedalSettings> copy = new ArrayList<PedalSettings>();
		for( PedalSettings each : pedals ) {
			copy.add(each.kind() == pedal.kind() ? pedal : each);
		}
		return copy;
	}

	private static ChainSettings with(ChainSettings chain, PedalSettings pedal) {
		return pedal.kind().section() == EffectKind.Section.BEFORE ? chain.withBefore(replaced(chain.before(), pedal))
				: chain.withAfter(replaced(chain.after(), pedal));
	}

	private static ChainSettings without(ChainSettings chain, EffectKind kind) {
		List<PedalSettings> before = new ArrayList<PedalSettings>(chain.before());
		List<PedalSettings> after = new ArrayList<PedalSettings>(chain.after());
		before.removeIf(pedal -> pedal.kind() == kind);
		after.removeIf(pedal -> pedal.kind() == kind);
		return new ChainSettings(before, chain.amp(), after);
	}

	private static final class Run {
		final float[] samples = new float[BLOCKS * BLOCK];
		int changingSamples;
	}

	private static Run run(ChainSettings first, ChainSettings second) {
		Chain chain = new Chain();
		chain.prepare(RATE, BLOCK);
		chain.apply(first);
		Run run = new Run();
		float[] block = new float[BLOCK];
		boolean counting = false;
		for( int b = 0 ; b < BLOCKS ; b++ ) {
			if( b == CHANGE && second != null ) {
				chain.apply(second);
				counting = true;
			}
			for( int i = 0 ; i < BLOCK ; i++ ) {
				block[i] = (float) (0.25 * Math.sin(2 * Math.PI * 220 * (b * BLOCK + i) / RATE));
			}
			chain.process(block, BLOCK);
			if( counting ) {
				if( chain.changing() ) {
					run.changingSamples += BLOCK;
				} else {
					counting = false;
				}
			}
			System.arraycopy(block, 0, run.samples, b * BLOCK, BLOCK);
		}
		return run;
	}

	private static double largestStep(float[] samples) {
		double largest = 0;
		for( int i = FROM * BLOCK + 1 ; i < samples.length ; i++ ) {
			largest = Math.max(largest, Math.abs(samples[i] - samples[i - 1]));
		}
		return largest;
	}

	private static void check(String what, ChainSettings first, ChainSettings second, List<String> failures) {
		double reference = Math.max(largestStep(run(first, null).samples), largestStep(run(second, null).samples));
		Run changed = run(first, second);
		double step = largestStep(changed.samples);
		if( !(step < 1.5 * reference) ) {
			failures.add(what + ": a step of " + step + " against " + reference);
		}
		if( changed.changingSamples > RATE / 10 ) {
			failures.add(what + ": still changing after " + changed.changingSamples * 1000 / RATE + " ms");
		}
	}

	private static void assertNone(List<String> failures) {
		Assertions.assertTrue(failures.isEmpty(), String.join("\n", failures));
	}

	@Test
	public void everyKnobJumpsFromItsMinimumToItsMaximum() {
		List<String> failures = new ArrayList<String>();
		ChainSettings base = full();
		for( EffectKind kind : EffectKind.values() ) {
			for( Knob knob : kind.knobs() ) {
				PedalSettings pedal = base.pedal(kind);
				check(kind + " " + knob.key(), with(base, pedal.with(knob.key(), knob.min())), with(base, pedal.with(knob.key(), knob.max())), failures);
			}
		}
		assertNone(failures);
	}

	@Test
	public void everyPedalIsSwitchedOnAndOff() {
		List<String> failures = new ArrayList<String>();
		ChainSettings base = full();
		for( EffectKind kind : EffectKind.values() ) {
			ChainSettings off = with(base, base.pedal(kind).withEnabled(false));
			check(kind + " off", base, off, failures);
			check(kind + " on", off, base, failures);
		}
		assertNone(failures);
	}

	@Test
	public void aPedalIsAddedRemovedAndMoved() {
		List<String> failures = new ArrayList<String>();
		ChainSettings base = full();
		for( EffectKind kind : EffectKind.values() ) {
			check(kind + " removed", base, without(base, kind), failures);
			check(kind + " added", without(base, kind), base, failures);
		}
		List<PedalSettings> before = new ArrayList<PedalSettings>(base.before());
		java.util.Collections.swap(before, 0, 1);
		check("compressor before the gate", base, base.withBefore(before), failures);
		List<PedalSettings> after = new ArrayList<PedalSettings>(base.after());
		java.util.Collections.swap(after, 1, 2);
		check("reverb before the echo", base, base.withAfter(after), failures);
		List<PedalSettings> reversed = new ArrayList<PedalSettings>(base.after());
		java.util.Collections.reverse(reversed);
		check("the after pedals reversed", base, base.withAfter(reversed), failures);
		assertNone(failures);
	}

	@Test
	public void eachReadyMadeSoundIsPickedAfterEachOther() {
		List<String> failures = new ArrayList<String>();
		for( ReadyMadeChains.Named from : ReadyMadeChains.all() ) {
			for( ReadyMadeChains.Named to : ReadyMadeChains.all() ) {
				if( from != to ) {
					check(from.name() + " to " + to.name(), from.chain(), to.chain(), failures);
				}
			}
		}
		check("dry to Lead", new ChainSettings(Arrays.<PedalSettings>asList(), AmpSettings.defaults(), Arrays.<PedalSettings>asList()),
				ReadyMadeChains.LEAD, failures);
		assertNone(failures);
	}
}
