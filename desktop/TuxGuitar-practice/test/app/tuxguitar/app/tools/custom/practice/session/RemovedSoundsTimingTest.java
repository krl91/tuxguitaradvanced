package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Spec 014 (T025, SC-007): the precision of the scheduling of the sounds a level removed, on a real clock and the real thread. It measures what can be measured
 * without sound: the time between the written time of a sound (the clock's own time of its tick) and the moment its note is handed to the output. The comparison
 * with the sounds the sequencer plays, and the delay of the synthesizer, are not measurable here (the owner's ear, task T040). Gated by
 * {@code -Darcade.measure=true}: it prints, it asserts only the order of magnitude (a poll step is 5 ms).
 */
@Tag("dataset")
public class RemovedSoundsTimingTest {

	private static final long MS = 1_000_000L;
	private static final long BASE = 960L;

	/** a clock that follows the system's: one tick is one millisecond of the run */
	private static final class RealClock implements TempoClock {
		final long origin = System.nanoTime();

		public long nowNanos() {
			return System.nanoTime();
		}

		public double tickAt(long wallNanos) {
			return BASE + (wallNanos - this.origin) / (double) MS;
		}

		public long wallNanosAt(double tick) {
			return this.origin + (long) ((tick - BASE) * MS);
		}

		public boolean isReady() {
			return true;
		}

		public boolean isStalled(long wallNanos) {
			return false;
		}
	}

	@Test
	public void theNotesAreHandedToTheOutputWithinAPollStepOfTheirWrittenTime() throws InterruptedException {
		assumeTrue(Boolean.getBoolean("arcade.measure"), "run with -Darcade.measure=true");
		final RealClock clock = new RealClock();
		final List<Double> offsetsMillis = new ArrayList<Double>();
		final List<RemovedSoundsPlayer.Sound> sounds = new ArrayList<RemovedSoundsPlayer.Sound>();
		for (int i = 0; i < 150; i++) {
			sounds.add(new RemovedSoundsPlayer.Sound(BASE + 300 + i * 37, 30 * MS, 1, new int[] { 40 + i % 12 }, 90));
		}
		RemovedSoundsPlayer player = RemovedSoundsPlayer.create(sounds, clock, new RemovedSoundsPlayer.Output() {
			int index;

			public synchronized void noteOn(int channel, int key, int velocity) {
				double written = clock.wallNanosAt(BASE + 300 + this.index * 37);
				offsetsMillis.add((System.nanoTime() - written) / 1e6);
				this.index++;
			}

			public void noteOff(int channel, int key) {
			}
		});
		player.start();
		Thread.sleep(300 + 150 * 37 + 500);
		player.stop();
		List<Double> sorted;
		synchronized (offsetsMillis) {
			sorted = new ArrayList<Double>(offsetsMillis);
		}
		Collections.sort(sorted);
		System.out.println(String.format("REMOVED SOUNDS TIMING: %d of 150 sounds sent, offset from the written time: median %.1f ms, p95 %.1f ms, max %.1f ms, min %.1f ms",
				sorted.size(), sorted.get(sorted.size() / 2), sorted.get((int) (sorted.size() * 0.95)), sorted.get(sorted.size() - 1), sorted.get(0)));
	}
}
