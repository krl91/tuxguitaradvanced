package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The position of the player's sequencer is the integral of {@code nanoTime} by a 10 ms timer: it moves by steps every 10 to 18 ms (measured,
 * research R6c) and its rate is off by up to -0.14 % from the nominal tempo. The interpolator removes the staircase and learns the real rate.
 * A scripted player (steps at irregular times, polled every 5.3 ms like the practice frames), no clock, no device.
 */
public class TickInterpolatorTest {

	private static final double NOMINAL = 1920.0;               // ticks a second at 120 bpm
	private static final long POLL_NANOS = 5_333_333L;
	private static final long T0 = 7_000_000_000L;

	/** The scripted player: true position {@code rate * seconds}, published at irregular steps of 10 to 18 ms, polled every 5.3 ms. */
	private static final class ScriptedPlayer {
		final TickInterpolator interpolator;
		final Random random = new Random(21L);
		double rate;
		double nextStepSeconds;
		long published;
		double seconds;
		/** Worst error, in ms, of the position asked 4 ms ahead of each poll (as a frame is dated) once two seconds have passed. */
		double worstErrorMs;

		ScriptedPlayer(TickInterpolator interpolator, double rate) {
			this.interpolator = interpolator;
			this.rate = rate;
		}

		/** The true position (ticks, fractional) at {@code seconds} since the start, at the current rate (a constant rate script). */
		double truth(double s) {
			return this.rate * s;
		}

		void run(double fromSeconds, double toSeconds) {
			this.seconds = fromSeconds;
			while( this.seconds < toSeconds ) {
				while( this.nextStepSeconds <= this.seconds ) {
					this.published = Math.round(truth(this.nextStepSeconds));
					this.nextStepSeconds += 0.010 + this.random.nextDouble() * 0.008;
				}
				this.interpolator.observe(T0 + (long) (this.seconds * 1e9), this.published);
				if( this.seconds > 2.0 && this.interpolator.isReady() ) {
					double ahead = this.seconds + 0.004;
					this.worstErrorMs = Math.max(this.worstErrorMs, Math.abs(this.interpolator.tickAt(wall(ahead)) - truth(ahead)) / this.rate * 1000.0);
				}
				this.seconds += POLL_NANOS / 1e9;
			}
		}
	}

	private static long wall(double seconds) {
		return T0 + (long) (seconds * 1e9);
	}

	@Test
	public void isNotReadyBeforeThePositionHasMoved() {
		TickInterpolator interpolator = new TickInterpolator(NOMINAL);
		assertFalse(interpolator.isReady());
		assertTrue(Double.isNaN(interpolator.tickAt(wall(0.1))));
		interpolator.observe(wall(0.0), 100);
		interpolator.observe(wall(0.005), 100);
		assertFalse(interpolator.isReady());
	}

	@Test
	public void removesTheStaircaseAndLearnsTheRealRate() {
		TickInterpolator interpolator = new TickInterpolator(NOMINAL);
		// the real rate is 0.14 % under the nominal one (measured at 120 bpm)
		double real = NOMINAL * 0.9986;
		ScriptedPlayer player = new ScriptedPlayer(interpolator, real);
		player.run(0.0, 12.0);

		assertTrue(interpolator.isReady());
		assertEquals(real, interpolator.ticksPerSecond(), 2.0, "the rate was not learned");
		double worstInterpolated = player.worstErrorMs;
		double worstStaircase = 0.0;
		// the raw staircase: the last published value
		TickInterpolator raw = new TickInterpolator(NOMINAL);
		ScriptedPlayer rawPlayer = new ScriptedPlayer(raw, real);
		long last = 0;
		java.util.Random random = new java.util.Random(21L);
		double next = 0;
		for( double s = 0 ; s < 12.0 ; s += 0.0013 ) {
			while( next <= s ) { last = Math.round(real * next); next += 0.010 + random.nextDouble() * 0.008; }
			if( s > 2.0 ) {
				worstStaircase = Math.max(worstStaircase, Math.abs(last - real * s) / real * 1000.0);
			}
		}
		assertTrue(worstInterpolated < 3.5, "the interpolated position is " + worstInterpolated + " ms off");
		assertTrue(worstStaircase > 10.0, "the staircase this replaces was " + worstStaircase + " ms off");
	}

	@Test
	public void followsATempoChange() {
		TickInterpolator interpolator = new TickInterpolator(NOMINAL);
		ScriptedPlayer slow = new ScriptedPlayer(interpolator, 960.0 * 0.9990);
		slow.run(0.0, 4.0);
		// the tempo doubles at 4 s: the position goes on from where it was at twice the rate
		double fast = 1920.0 * 0.9990;
		double atChange = slow.truth(4.0);
		final long[] published = { Math.round(atChange) };
		java.util.Random random = new java.util.Random(5L);
		double next = 4.0;
		double worst = 0;
		for( double s = 4.0 ; s < 9.0 ; s += POLL_NANOS / 1e9 ) {
			while( next <= s ) { published[0] = Math.round(atChange + fast * (next - 4.0)); next += 0.010 + random.nextDouble() * 0.008; }
			interpolator.observe(wall(s), published[0]);
			if( s > 5.5 ) {
				worst = Math.max(worst, Math.abs(interpolator.tickAt(wall(s)) - (atChange + fast * (s - 4.0))) / fast * 1000.0);
			}
		}
		assertTrue(worst < 3.0, "a second and a half after the tempo change the position is " + worst + " ms off");
	}

	@Test
	public void aSeekIsFollowedAtOnce() {
		TickInterpolator interpolator = new TickInterpolator(NOMINAL);
		ScriptedPlayer player = new ScriptedPlayer(interpolator, NOMINAL);
		player.run(0.0, 4.0);
		assertTrue(interpolator.isReady());
		// the learner goes back to the start of a passage: the position is now 5000 ticks, then goes on at the same rate
		double base = 5000.0;
		java.util.Random random = new java.util.Random(8L);
		double next = 4.0;
		long published = 0;
		double worst = 0;
		for( double s = 4.0 ; s < 6.0 ; s += POLL_NANOS / 1e9 ) {
			while( next <= s ) { published = Math.round(base + NOMINAL * (next - 4.0)); next += 0.010 + random.nextDouble() * 0.008; }
			interpolator.observe(wall(s), published);
			if( s > 4.3 ) {
				worst = Math.max(worst, Math.abs(interpolator.tickAt(wall(s)) - (base + NOMINAL * (s - 4.0))) / NOMINAL * 1000.0);
			}
		}
		assertTrue(worst < 4.0, "after a seek back the position is " + worst + " ms off");
	}

	@Test
	public void aStoppedPlayerDoesNotMakeThePositionRunAway() {
		TickInterpolator interpolator = new TickInterpolator(NOMINAL);
		ScriptedPlayer player = new ScriptedPlayer(interpolator, NOMINAL);
		player.run(0.0, 3.0);
		long frozen = player.published;
		// the player stops: the position stays at the same value for two seconds
		for( double s = 3.0 ; s < 5.0 ; s += POLL_NANOS / 1e9 ) {
			interpolator.observe(wall(s), frozen);
		}
		double held = interpolator.tickAt(wall(5.0));
		assertTrue(held - frozen < NOMINAL * 0.032, "the position ran on by " + (held - frozen) + " ticks while the player was stopped");
		assertTrue(interpolator.isStalled(wall(5.0)));
	}

	@Test
	public void theInverseGivesTheWallTimeOfATick() {
		TickInterpolator interpolator = new TickInterpolator(NOMINAL);
		ScriptedPlayer player = new ScriptedPlayer(interpolator, NOMINAL);
		player.run(0.0, 6.0);
		for( double s : new double[] { 2.2, 3.7, 5.5 } ) {
			double tick = interpolator.tickAt(wall(s));
			long back = interpolator.wallNanosAt(tick);
			assertEquals(wall(s), back, 1_000_000L, "the inverse of the position at " + s + " s");
		}
	}
}
